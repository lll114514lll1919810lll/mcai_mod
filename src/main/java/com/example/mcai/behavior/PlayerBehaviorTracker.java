package com.example.mcai.behavior;

import com.example.mcai.config.ModConfig;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PlayerBehaviorTracker {
    private final ModConfig config;
    private final ConcurrentMap<UUID, Integer> scores = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> lastRecoveryTime = new ConcurrentHashMap<>();

    public PlayerBehaviorTracker(ModConfig config) {
        this.config = config;
    }

    public int getScore(UUID playerId) {
        return scores.getOrDefault(playerId, 0);
    }

    /** Add delta to score, return new score. Negative delta = penalty. */
    public int addScore(UUID playerId, int delta) {
        int[] newScore = new int[1];
        scores.compute(playerId, (id, existing) -> {
            int s = (existing == null ? 0 : existing) + delta;
            newScore[0] = s;
            return s;
        });
        return newScore[0];
    }

    public void resetScore(UUID playerId) {
        scores.remove(playerId);
        lastRecoveryTime.remove(playerId);
    }

    /** Gradually recover score if enough time has passed since last recovery. */
    public void tryRecover(UUID playerId) {
        int current = scores.getOrDefault(playerId, 0);
        if (current >= 0) return; // no need to recover
        long now = System.currentTimeMillis();
        long last = lastRecoveryTime.getOrDefault(playerId, 0L);
        long intervalMs = config.getReviewIntervalMinutes() * 60_000L;
        if (now - last >= intervalMs) {
            int recoverAmount = config.getScoreRecoveryPerInterval();
            scores.compute(playerId, (id, existing) -> {
                int s = (existing == null ? 0 : existing) + recoverAmount;
                return Math.min(s, 0); // cap at 0 (don't go positive from recovery)
            });
            lastRecoveryTime.put(playerId, now);
        }
    }
}
