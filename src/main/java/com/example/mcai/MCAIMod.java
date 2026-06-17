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

import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class MCAIMod implements ModInitializer {
    public static final String MOD_ID = "mcai";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static MCAIMod instance;

    private volatile ModConfig config;
    private volatile OpenAIClient aiClient;
    private ChatLog chatLog;
    private ThinkingAnimation animation;
    private PlayerContextBuilder contextBuilder;
    private CommandExecutionService cmdExec;
    private ToolDispatcher toolDispatcher;
    private ChatHandler chatHandler;
    private CommandRegistry cmdReg;
    private volatile KnowledgeBase knowledgeBase;
    private volatile PlayerBehaviorTracker behaviorTracker;
    private volatile ChatReviewSystem chatReviewSystem;
    private volatile MinecraftServer server;
    private CommandDispatcher<CommandSourceStack> commandDispatcher;
    private WatchService configWatcher;
    private ScheduledExecutorService watcherScheduler;

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
            if (behaviorTracker != null) behaviorTracker.save();
            if (configWatcher != null) try { configWatcher.close(); } catch (Exception ignored) {}
            if (watcherScheduler != null) watcherScheduler.shutdownNow();
        });

        chatHandler.registerChatInterceptor();
        startConfigWatcher();

        LOGGER.info("MCAI initialized - prefix: '{}', endpoint: {}, KB: {} chunks",
                config.getTriggerPrefix(), config.getApiEndpoint(),
                knowledgeBase.size());
    }

    /** 启动配置文件监视器，自动热重载 */
    private void startConfigWatcher() {
        Path configDir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("mcai");
        try {
            Files.createDirectories(configDir);
            configWatcher = FileSystems.getDefault().newWatchService();
            configDir.register(configWatcher, StandardWatchEventKinds.ENTRY_MODIFY);

            AtomicLong lastReload = new AtomicLong(0);
            watcherScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "MCAI-ConfigWatcher");
                t.setDaemon(true);
                return t;
            });
            watcherScheduler.execute(() -> {
                while (true) {
                    try {
                        WatchKey key = configWatcher.take();
                        for (WatchEvent<?> event : key.pollEvents()) {
                            String fileName = ((Path) event.context()).toString();
                            if ("config.json".equals(fileName)) {
                                long now = System.currentTimeMillis();
                                // 防抖：2秒内不重复重载
                                if (now - lastReload.get() < 2000) continue;
                                lastReload.set(now);
                                LOGGER.info("Config file changed, auto-reloading...");
                                // 延迟 500ms 确保文件写入完成
                                watcherScheduler.schedule(() -> {
                                    try {
                                        if (server != null) {
                                            server.execute(() -> reloadConfig());
                                        }
                                    } catch (Exception e) {
                                        LOGGER.error("Auto-reload failed", e);
                                    }
                                }, 500, TimeUnit.MILLISECONDS);
                            }
                        }
                        key.reset();
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        LOGGER.error("Config watcher error", e);
                    }
                }
            });
            LOGGER.info("Config file watcher enabled for {}", configDir);
        } catch (Exception e) {
            LOGGER.warn("Failed to start config watcher: {}", e.getMessage());
        }
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
            chatReviewSystem.reloadConfig(config);
        }
        LOGGER.info("MCAI config reloaded, KB: {} chunks (history preserved)",
                knowledgeBase.size());
    }
}
