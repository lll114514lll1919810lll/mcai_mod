package com.example.mcai.behavior;

import com.example.mcai.MCAIMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatReviewSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-Review");

    private final MCAIMod mod;
    private final PlayerBehaviorTracker tracker;
    private final AdminApprovalQueue approvalQueue;
    private final ReviewEngine reviewEngine;
    private final PenaltyHistory penaltyHistory;
    private final ReviewCommandRegistry cmdReg;

    private final ScheduledExecutorService reviewScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MCAI-Review");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean reviewInProgress = new AtomicBoolean(false);
    private ScheduledFuture<?> scheduledReview;
    private volatile Component lastReviewStatus = Component.literal("");

    public ChatReviewSystem(MCAIMod mod, PlayerBehaviorTracker tracker) {
        this.mod = mod;
        this.tracker = tracker;
        this.penaltyHistory = new PenaltyHistory(mod.getConfig());
        this.approvalQueue = new AdminApprovalQueue(reviewScheduler, item -> {
            executeApprovedAction(item);
        });
        this.reviewEngine = new ReviewEngine(mod, tracker, penaltyHistory, approvalQueue);
        this.cmdReg = new ReviewCommandRegistry(this, approvalQueue, reviewEngine);
    }

    public MinecraftServer getServer() { return mod.getServer(); }
    public ReviewCommandRegistry getCommandRegistry() { return cmdReg; }
    public PenaltyHistory getPenaltyHistory() { return penaltyHistory; }

    public void start() {
        if (scheduledReview != null && !scheduledReview.isCancelled()) {
            scheduledReview.cancel(false);
        }
        int interval = mod.getConfig().getReviewIntervalMinutes();
        scheduledReview = reviewScheduler.scheduleAtFixedRate(
                this::runReview, interval, interval, TimeUnit.MINUTES);
        LOGGER.info("Auto review started, interval={}min", interval);
    }

    /** 热重载配置：更新配置引用，仅在间隔变化时重新调度 */
    public void reloadConfig(com.example.mcai.config.ModConfig newConfig) {
        penaltyHistory.reloadConfig(newConfig);
        int newInterval = newConfig.getReviewIntervalMinutes();
        if (scheduledReview != null && !scheduledReview.isCancelled()) {
            scheduledReview.cancel(false);
            scheduledReview = reviewScheduler.scheduleAtFixedRate(
                    this::runReview, newInterval, newInterval, TimeUnit.MINUTES);
        }
        LOGGER.info("Review system config reloaded, interval={}min", newInterval);
    }

    public void stop() {
        if (scheduledReview != null) {
            scheduledReview.cancel(false);
            scheduledReview = null;
        }
        reviewScheduler.shutdown();
    }

    public void triggerManualReview() {
        triggerManualReview(null);
    }

    public void triggerManualReview(ServerPlayer notifier) {
        if (reviewInProgress.get()) {
            lastReviewStatus = Component.translatable("mcai.review.status.in_progress");
            if (notifier != null) notifier.sendSystemMessage(lastReviewStatus);
            return;
        }
        lastReviewStatus = Component.translatable("mcai.review.status.starting");
        if (notifier != null) notifier.sendSystemMessage(lastReviewStatus);
        reviewScheduler.execute(() -> {
            runReview();
            if (notifier != null) {
                var srv = mod.getServer();
                if (srv != null) {
                    srv.execute(() -> notifier.sendSystemMessage(getLastReviewStatus()));
                }
            }
        });
    }

    public Component getLastReviewStatus() { return lastReviewStatus; }

    private void runReview() {
        if (!reviewInProgress.compareAndSet(false, true)) {
            LOGGER.debug("Review already in progress, skipping");
            return;
        }
        try {
            lastReviewStatus = reviewEngine.run();
            LOGGER.info("Review complete: {}", lastReviewStatus.getString());
        } catch (Exception e) {
            LOGGER.error("Review failed", e);
            lastReviewStatus = Component.translatable("mcai.review.status.exception", e.getMessage());
        } finally {
            reviewInProgress.set(false);
        }
    }

    private void executeApprovedAction(AdminApprovalQueue.ApprovalItem item) {
        var server = mod.getServer();
        if (server == null) return;

        server.execute(() -> {
            ServerPlayer target = server.getPlayerList().getPlayer(item.targetPlayerId);
            if (target == null) {
                LOGGER.info("Player {} offline, skipping {}", item.targetPlayerName, item.action);
                return;
            }
            if ("kick".equals(item.action)) {
                target.connection.disconnect(Component.translatable("mcai.review.kick_msg", item.reason));
                LOGGER.info("Kicked {} (approval #{})", item.targetPlayerName, item.id);
                penaltyHistory.addEvent(new PenaltyEvent(item.targetPlayerName,
                        item.reason, 0, tracker.getScore(item.targetPlayerId),
                        PenaltyEvent.PenaltyAction.KICK_EXECUTED, item.id, penaltyHistory.getCurrentCycle()));
            }
        });
    }
}
