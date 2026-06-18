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
            dispatcher.register(cmdReg.createKillCommand());
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
            if (behaviorTracker != null) behaviorTracker.saveImmediate();
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
        Path configFile = configDir.resolve("config.json");
        try {
            Files.createDirectories(configDir);
            configWatcher = FileSystems.getDefault().newWatchService();
            configDir.register(configWatcher,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE);

            AtomicLong lastReload = new AtomicLong(0);
            AtomicLong lastModified = new AtomicLong(0);
            watcherScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "MCAI-ConfigWatcher");
                t.setDaemon(true);
                return t;
            });

            Runnable doReload = () -> {
                long now = System.currentTimeMillis();
                if (now - lastReload.get() < 2000) return;
                lastReload.set(now);
                LOGGER.info("Config file changed, auto-reloading...");
                try {
                    MinecraftServer srv = server;
                    if (srv != null) {
                        srv.execute(() -> reloadConfig());
                    } else {
                        reloadConfig();
                    }
                } catch (Exception e) {
                    LOGGER.error("Auto-reload failed", e);
                }
            };

            // WatchService 线程
            watcherScheduler.execute(() -> {
                while (true) {
                    try {
                        WatchKey key = configWatcher.take();
                        boolean configChanged = false;
                        for (WatchEvent<?> event : key.pollEvents()) {
                            String fileName = ((Path) event.context()).toString();
                            if ("config.json".equals(fileName)) {
                                configChanged = true;
                            }
                        }
                        key.reset();
                        if (configChanged) {
                            watcherScheduler.schedule(doReload, 500, TimeUnit.MILLISECONDS);
                        }
                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        LOGGER.error("Config watcher error", e);
                    }
                }
            });

            // 兜底轮询线程（每5秒检查文件修改时间）
            watcherScheduler.scheduleAtFixedRate(() -> {
                try {
                    if (Files.exists(configFile)) {
                        long mtime = Files.getLastModifiedTime(configFile).toMillis();
                        if (mtime > lastModified.get()) {
                            lastModified.set(mtime);
                            if (lastReload.get() > 0) {
                                doReload.run();
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }, 5, 5, TimeUnit.SECONDS);

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
