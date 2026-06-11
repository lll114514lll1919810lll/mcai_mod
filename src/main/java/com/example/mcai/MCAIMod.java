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
import com.example.mcai.memory.MemoryFile;

public class MCAIMod implements ModInitializer {
    public static final String MOD_ID = "mcai";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static MCAIMod instance;

    private ModConfig config;
    private OpenAIClient aiClient;
    private ChatHandler chatHandler;
    private KnowledgeBase knowledgeBase;
    private MemoryFile memory;
    private volatile MinecraftServer server;

    @Override
    public void onInitialize() {
        instance = this;
        config = ModConfig.load();
        memory = new MemoryFile();
        memory.load();
        knowledgeBase = new KnowledgeBase();
        knowledgeBase.load(net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("mcai_kb"));
        aiClient = new OpenAIClient(config);
        chatHandler = new ChatHandler(this);

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
            dispatcher.register(chatHandler.createAICommand());
            dispatcher.register(chatHandler.createWikiCommand());
            dispatcher.register(chatHandler.createQueryCommand());
            dispatcher.register(chatHandler.createAcceptCommand());
            dispatcher.register(chatHandler.createRejectCommand());
            dispatcher.register(chatHandler.createClearCommand());
            dispatcher.register(chatHandler.createMemoryCommand());
            dispatcher.register(chatHandler.createReloadCommand());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                chatHandler.onPlayerDisconnect(handler.getPlayer()));

        ServerLifecycleEvents.SERVER_STARTED.register(s -> this.server = s);

        chatHandler.registerChatInterceptor();

        LOGGER.info("MCAI initialized - prefix: '{}', endpoint: {}, KB: {} chunks, memory: {} entries",
                config.getTriggerPrefix(), config.getApiEndpoint(),
                knowledgeBase.size(), memory.size());
    }

    public static MCAIMod getInstance() { return instance; }
    public ModConfig getConfig() { return config; }
    public OpenAIClient getAiClient() { return aiClient; }
    public KnowledgeBase getKnowledgeBase() { return knowledgeBase; }
    public MemoryFile getMemory() { return memory; }
    public MinecraftServer getServer() { return server; }

    public void reloadConfig() {
        config = ModConfig.load();
        aiClient = new OpenAIClient(config);
        knowledgeBase.load(net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve("mcai_kb"));
        memory.load();
        LOGGER.info("MCAI config reloaded, KB: {} chunks, memory: {} entries",
                knowledgeBase.size(), memory.size());
    }
}
