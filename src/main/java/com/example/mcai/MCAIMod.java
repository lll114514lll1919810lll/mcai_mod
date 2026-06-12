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
import com.example.mcai.handler.*;
import com.example.mcai.kb.KnowledgeBase;
import com.example.mcai.behavior.PlayerBehaviorTracker;
import com.example.mcai.behavior.ChatReviewSystem;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

public class MCAIMod implements ModInitializer {
    public static final String MOD_ID = "mcai";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static MCAIMod instance;

    private ModConfig config;
    private OpenAIClient aiClient;
    private ChatLog chatLog;
    private ThinkingAnimation animation;
    private PlayerContextBuilder contextBuilder;
    private CommandExecutionService cmdExec;
    private ToolDispatcher toolDispatcher;
    private ChatHandler chatHandler;
    private CommandRegistry cmdReg;
    private KnowledgeBase knowledgeBase;
    private PlayerBehaviorTracker behaviorTracker;
    private ChatReviewSystem chatReviewSystem;
    private volatile MinecraftServer server;
    private CommandDispatcher<CommandSourceStack> commandDispatcher;

    @Override
    public void onInitialize() {
        instance = this;
        config = ModConfig.load();
        // 初始化时触发提示词文件自动创建（如不存在则以内置默认内容创建）
        config.getSystemPrompt();
        config.getReviewPrompt();
        knowledgeBase = new KnowledgeBase();
        knowledgeBase.load(net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("mcai/kb"));
        aiClient = new OpenAIClient(config);

        chatLog = new ChatLog();
        animation = new ThinkingAnimation();
        contextBuilder = new PlayerContextBuilder();
        cmdExec = new CommandExecutionService(this);
        toolDispatcher = new ToolDispatcher(knowledgeBase, cmdExec, this);
        chatHandler = new ChatHandler(this, chatLog, animation, contextBuilder, cmdExec, toolDispatcher);
        cmdReg = new CommandRegistry(chatHandler, cmdExec, knowledgeBase);

        behaviorTracker = new PlayerBehaviorTracker(config);

        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> {
            this.commandDispatcher = dispatcher;
            dispatcher.register(cmdReg.createAICommand());
            dispatcher.register(cmdReg.createWikiCommand());
            dispatcher.register(cmdReg.createQueryCommand());
            dispatcher.register(cmdReg.createAcceptCommand());
            dispatcher.register(cmdReg.createRejectCommand());
            dispatcher.register(cmdReg.createClearCommand());
            dispatcher.register(cmdReg.createReloadCommand());
            dispatcher.register(cmdReg.createScoreCommand());
            dispatcher.register(cmdReg.createTestCommand());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) ->
                chatHandler.onPlayerDisconnect(handler.getPlayer()));

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            this.server = s;
            if (s.isDedicatedServer()) {
                chatReviewSystem = new ChatReviewSystem(this, behaviorTracker);
                if (commandDispatcher != null) {
                    commandDispatcher.register(chatReviewSystem.getCommandRegistry().createAiCheckCommand());
                }
                if (config.isEnableAutoReview()) {
                    chatReviewSystem.start();
                    LOGGER.info("Auto behavior review enabled");
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            if (chatReviewSystem != null) chatReviewSystem.stop();
            if (animation != null) animation.shutdown();
            if (behaviorTracker != null) behaviorTracker.save();
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
    public ChatLog getChatLog() { return chatLog; }
    public ChatReviewSystem getChatReviewSystem() { return chatReviewSystem; }
    public PlayerBehaviorTracker getBehaviorTracker() { return behaviorTracker; }

    public void reloadConfig() {
        config = ModConfig.load();
        config.clearPromptCache();
        config.getSystemPrompt();
        config.getReviewPrompt();
        aiClient = new OpenAIClient(config);
        knowledgeBase.load(net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("mcai/kb"));
        if (chatReviewSystem != null) {
            chatReviewSystem.stop();
            behaviorTracker = new PlayerBehaviorTracker(config);
            chatReviewSystem = new ChatReviewSystem(this, behaviorTracker);
            if (commandDispatcher != null) {
                commandDispatcher.register(chatReviewSystem.getCommandRegistry().createAiCheckCommand());
            }
            if (config.isEnableAutoReview() && this.server != null && this.server.isDedicatedServer()) {
                chatReviewSystem.start();
            }
        }
        LOGGER.info("MCAI config reloaded, KB: {} chunks",
                knowledgeBase.size());
    }
}
