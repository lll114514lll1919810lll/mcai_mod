package com.example.mcai;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.mcai.config.ModConfig;
import com.example.mcai.api.OpenAIClient;
import com.example.mcai.handler.ChatHandler;
import com.example.mcai.kb.KnowledgeBase;
import com.example.mcai.behavior.PlayerBehaviorTracker;
import com.example.mcai.behavior.ChatReviewSystem;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.util.UUID;

public class MCAIMod implements ModInitializer {
    public static final String MOD_ID = "mcai";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static MCAIMod instance;

    private ModConfig config;
    private OpenAIClient aiClient;
    private ChatHandler chatHandler;
    private KnowledgeBase knowledgeBase;
    private PlayerBehaviorTracker behaviorTracker;
    private ChatReviewSystem chatReviewSystem;
    private volatile MinecraftServer server;
    private CommandDispatcher<net.minecraft.commands.CommandSourceStack> commandDispatcher;

    private final SuggestionProvider<net.minecraft.commands.CommandSourceStack> PLAYER_NAME_SUGGESTIONS = (ctx, builder) -> {
        var srv = server;
        if (srv != null) {
            for (var p : srv.getPlayerList().getPlayers()) {
                builder.suggest(p.getScoreboardName());
            }
        }
        return builder.buildFuture();
    };

    @Override
    public void onInitialize() {
        instance = this;
        config = ModConfig.load();
        knowledgeBase = new KnowledgeBase();
        knowledgeBase.load(net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("mcai/kb"));
        aiClient = new OpenAIClient(config);
        chatHandler = new ChatHandler(this);
        behaviorTracker = new PlayerBehaviorTracker(config);

        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            this.commandDispatcher = dispatcher;
            dispatcher.register(chatHandler.createAICommand());
            dispatcher.register(chatHandler.createWikiCommand());
            dispatcher.register(chatHandler.createQueryCommand());
            dispatcher.register(chatHandler.createAcceptCommand());
            dispatcher.register(chatHandler.createRejectCommand());
            dispatcher.register(chatHandler.createClearCommand());
            dispatcher.register(chatHandler.createReloadCommand());
            dispatcher.register(chatHandler.createScoreCommand());
            // Test commands
            dispatcher.register(createTestCommand());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                chatHandler.onPlayerDisconnect(handler.getPlayer()));

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            this.server = s;
            // Initialize review system after server is ready
            chatReviewSystem = new ChatReviewSystem(this, behaviorTracker);
            // Register /aicheck commands now that chatReviewSystem is ready
            if (commandDispatcher != null) {
                commandDispatcher.register(chatReviewSystem.createAiCheckCommand());
            }
            if (config.isEnableAutoReview()) {
                chatReviewSystem.start();
                LOGGER.info("Auto behavior review enabled");
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            if (chatReviewSystem != null) {
                chatReviewSystem.stop();
            }
            if (behaviorTracker != null) {
                behaviorTracker.save();
            }
        });

        chatHandler.registerChatInterceptor();

        LOGGER.info("MCAI initialized - prefix: '{}', endpoint: {}, KB: {} chunks",
                config.getTriggerPrefix(), config.getApiEndpoint(),
                knowledgeBase.size());
    }

    public static MCAIMod getInstance() { return instance; }
    public ModConfig getConfig() { return config; }
    public OpenAIClient getAiClient() { return aiClient; }
    public KnowledgeBase getKnowledgeBase() { return knowledgeBase; }
    public MinecraftServer getServer() { return server; }
    public ChatHandler getChatHandler() { return chatHandler; }
    public ChatReviewSystem getChatReviewSystem() { return chatReviewSystem; }
    public PlayerBehaviorTracker getBehaviorTracker() { return behaviorTracker; }

    public void reloadConfig() {
        config = ModConfig.load();
        aiClient = new OpenAIClient(config);
        knowledgeBase.load(net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("mcai/kb"));
        // Restart review system with new config
        if (chatReviewSystem != null) {
            chatReviewSystem.stop();
        }
        behaviorTracker = new PlayerBehaviorTracker(config);
        chatReviewSystem = new ChatReviewSystem(this, behaviorTracker);
        // Re-register /aicheck to point to the new chatReviewSystem instance
        if (commandDispatcher != null) {
            commandDispatcher.register(chatReviewSystem.createAiCheckCommand());
        }
        if (config.isEnableAutoReview()) {
            chatReviewSystem.start();
        }
        LOGGER.info("MCAI config reloaded, KB: {} chunks",
                knowledgeBase.size());
    }

    // 鈹€鈹€ Test Commands 鈹€鈹€

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack> createTestCommand() {
        return Commands.literal("aitest")
                .requires(src -> {
                    var p = src.getPlayer();
                    if (p == null || src.getServer() == null) return false;
                    return isOp(src.getServer(), p.getGameProfile());
                })
                .then(Commands.literal("score")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_NAME_SUGGESTIONS)
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "player");
                                    UUID id = lookupPlayer(name);
                                    if (id == null) {
                                        ctx.getSource().sendFailure(Component.literal("搂c鐜╁涓嶅湪绾? " + name));
                                        return 0;
                                    }
                                    int score = behaviorTracker.getScore(id);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("搂e[娴嬭瘯] 鐜╁ " + name + " 琛屼负鍒? " + score), false);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("penalty")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_NAME_SUGGESTIONS)
                                .then(Commands.argument("points", IntegerArgumentType.integer(-100, -1))
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "player");
                                            UUID id = lookupPlayer(name);
                                            if (id == null) {
                                                ctx.getSource().sendFailure(Component.literal("搂c鐜╁涓嶅湪绾? " + name));
                                                return 0;
                                            }
                                            int pts = IntegerArgumentType.getInteger(ctx, "points");
                                            int newScore = behaviorTracker.addScore(id, pts);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("搂c[娴嬭瘯] 鐜╁ " + name
                                                            + " 鎵ｅ垎 " + pts + "锛屽綋鍓嶈涓哄垎: " + newScore), false);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_NAME_SUGGESTIONS)
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "player");
                                    UUID id = lookupPlayer(name);
                                    if (id == null) {
                                        ctx.getSource().sendFailure(Component.literal("搂c鐜╁涓嶅湪绾? " + name));
                                        return 0;
                                    }
                                    behaviorTracker.resetScore(id);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("搂a[娴嬭瘯] 宸查噸缃帺瀹?" + name + " 琛屼负鍒?), false);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("set")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_NAME_SUGGESTIONS)
                                .then(Commands.argument("score", IntegerArgumentType.integer(-100, 0))
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "player");
                                            UUID id = lookupPlayer(name);
                                            if (id == null) {
                                                ctx.getSource().sendFailure(Component.literal("搂c鐜╁涓嶅湪绾? " + name));
                                                return 0;
                                            }
                                            int score = IntegerArgumentType.getInteger(ctx, "score");
                                            behaviorTracker.setScore(id, score);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("搂e[娴嬭瘯] 宸茶缃帺瀹?" + name
                                                            + " 琛屼负鍒嗕负: " + score), false);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("review")
                        .executes(ctx -> {
                            if (chatReviewSystem != null) {
                                ServerPlayer p = ctx.getSource().getPlayer();
                                chatReviewSystem.triggerManualReview(p);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("搂c瀹℃煡绯荤粺鏈氨缁?));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("chatlog")
                        .executes(ctx -> {
                            String log = chatHandler.peekChatLog();
                            if (log.isEmpty()) {
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("搂7[娴嬭瘯] 鑱婂ぉ璁板綍涓虹┖"), false);
                            } else {
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("搂e==== 鑱婂ぉ璁板綍 ====\n搂7" + log), false);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("搂e==== MCAI 娴嬭瘯鎸囦护 ===="), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("搂7/aitest score <鐜╁> 搂f- 鏌ヨ琛屼负鍒?), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("搂7/aitest penalty <鐜╁> <鍒嗘暟> 搂f- 妯℃嫙鎵ｅ垎"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("搂7/aitest reset <鐜╁> 搂f- 閲嶇疆琛屼负鍒?), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("搂7/aitest set <鐜╁> <鍒嗘暟> 搂f- 璁剧疆琛屼负鍒?-100~0)"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("搂7/aitest review 搂f- 鎵嬪姩瑙﹀彂瀹℃煡"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("搂7/aitest chatlog 搂f- 鏌ョ湅鑱婂ぉ璁板綍"), false);
                    return Command.SINGLE_SUCCESS;
                });
    }

    /** Check if a player is OP. */
    private static boolean isOp(MinecraftServer srv, GameProfile profile) {
        return srv.getPlayerList().isOp(new NameAndId(profile));
    }

    /** Look up online player UUID by name. */
    private UUID lookupPlayer(String name) {
        if (server == null) return null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getScoreboardName().equalsIgnoreCase(name)) {
                return p.getUUID();
            }
        }
        return null;
    }
}
