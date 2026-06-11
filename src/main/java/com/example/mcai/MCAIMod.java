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
            if (config.isEnableAutoReview() && s.isDedicatedServer()) {
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
        if (config.isEnableAutoReview() && this.server != null && this.server.isDedicatedServer()) {
            chatReviewSystem.start();
        }
        LOGGER.info("MCAI config reloaded, KB: {} chunks",
                knowledgeBase.size());
    }

    // ── Test Commands ──

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
                                        ctx.getSource().sendFailure(Component.literal("§c玩家不在线: " + name));
                                        return 0;
                                    }
                                    int score = behaviorTracker.getScore(id);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("§e[测试] 玩家 " + name + " 行为分: " + score), false);
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
                                                ctx.getSource().sendFailure(Component.literal("§c玩家不在线: " + name));
                                                return 0;
                                            }
                                            int pts = IntegerArgumentType.getInteger(ctx, "points");
                                            int newScore = behaviorTracker.addScore(id, pts);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("§c[测试] 玩家 " + name
                                                            + " 扣分 " + pts + "，当前行为分: " + newScore), false);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_NAME_SUGGESTIONS)
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "player");
                                    UUID id = lookupPlayer(name);
                                    if (id == null) {
                                        ctx.getSource().sendFailure(Component.literal("§c玩家不在线: " + name));
                                        return 0;
                                    }
                                    behaviorTracker.resetScore(id);
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("§a[测试] 已重置玩家 " + name + " 行为分"), false);
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
                                                ctx.getSource().sendFailure(Component.literal("§c玩家不在线: " + name));
                                                return 0;
                                            }
                                            int score = IntegerArgumentType.getInteger(ctx, "score");
                                            behaviorTracker.setScore(id, score);
                                            ctx.getSource().sendSuccess(
                                                    () -> Component.literal("§e[测试] 已设置玩家 " + name
                                                            + " 行为分为: " + score), false);
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("review")
                        .executes(ctx -> {
                            if (chatReviewSystem != null) {
                                ServerPlayer p = ctx.getSource().getPlayer();
                                chatReviewSystem.triggerManualReview(p);
                            } else {
                                ctx.getSource().sendFailure(Component.literal("§c审查系统未就绪"));
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("chatlog")
                        .executes(ctx -> {
                            String log = chatHandler.peekChatLog();
                            if (log.isEmpty()) {
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("§7[测试] 聊天记录为空"), false);
                            } else {
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("§e==== 聊天记录 ====\n§7" + log), false);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("§e==== MCAI 测试指令 ===="), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7/aitest score <玩家> §f- 查询行为分"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7/aitest penalty <玩家> <分数> §f- 模拟扣分"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7/aitest reset <玩家> §f- 重置行为分"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7/aitest set <玩家> <分数> §f- 设置行为分(-100~0)"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7/aitest review §f- 手动触发审查"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7/aitest chatlog §f- 查看聊天记录"), false);
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
