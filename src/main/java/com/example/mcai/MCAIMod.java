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

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
            dispatcher.register(chatHandler.createAICommand());
            dispatcher.register(chatHandler.createWikiCommand());
            dispatcher.register(chatHandler.createQueryCommand());
            dispatcher.register(chatHandler.createAcceptCommand());
            dispatcher.register(chatHandler.createRejectCommand());
            dispatcher.register(chatHandler.createClearCommand());
            dispatcher.register(chatHandler.createReloadCommand());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                chatHandler.onPlayerDisconnect(handler.getPlayer()));

        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            this.server = s;
            chatReviewSystem = new ChatReviewSystem(this, behaviorTracker);
            if (config.isEnableAutoReview()) {
                chatReviewSystem.start();
                LOGGER.info("Auto behavior review enabled");
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            if (chatReviewSystem != null) chatReviewSystem.stop();
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
    public ChatReviewSystem getChatReviewSystem() { return chatReviewSystem; }
    public PlayerBehaviorTracker getBehaviorTracker() { return behaviorTracker; }

    public void reloadConfig() {
        config = ModConfig.load();
        aiClient = new OpenAIClient(config);
        knowledgeBase.load(net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("mcai_kb"));
        if (chatReviewSystem != null) chatReviewSystem.stop();
        behaviorTracker = new PlayerBehaviorTracker(config);
        chatReviewSystem = new ChatReviewSystem(this, behaviorTracker);
        if (config.isEnableAutoReview()) chatReviewSystem.start();
        LOGGER.info("MCAI config reloaded, KB: {} chunks",
                knowledgeBase.size());
    }
}
