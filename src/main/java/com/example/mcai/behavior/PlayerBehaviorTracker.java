package com.example.mcai.behavior;

import com.example.mcai.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerBehaviorTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-Scores");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SCORE_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("mcai/scores.json");

    private final ModConfig config;
    private final ConcurrentMap<UUID, Integer> scores = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> lastRecoveryTime = new ConcurrentHashMap<>();
    private final ScheduledExecutorService saveScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MCAI-ScoreSaver"); t.setDaemon(true); return t;
    });
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);

    public PlayerBehaviorTracker(ModConfig config) {
        this.config = config;
        load();
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
        save();
        return newScore[0];
    }

    public void resetScore(UUID playerId) {
        scores.remove(playerId);
        lastRecoveryTime.remove(playerId);
        save();
    }

    public void setScore(UUID playerId, int score) {
        scores.put(playerId, score);
        save();
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
            save();
        }
    }

    // ── Persistence ──

    /** Force a full save of scores to disk. */
    public void save() {
        if (saveScheduled.compareAndSet(false, true)) {
            saveScheduler.schedule(() -> {
                saveScheduled.set(false);
                doSave();
            }, 5, TimeUnit.SECONDS);
        }
    }

    /** Immediate save (for shutdown). */
    public void saveImmediate() {
        saveScheduled.set(false);
        doSave();
    }

    private void doSave() {
        try {
            Files.createDirectories(SCORE_FILE.getParent());
            String json = GSON.toJson(Map.of(
                    "scores", scores,
                    "lastRecoveryTime", lastRecoveryTime
            ));
            Files.writeString(SCORE_FILE, json, StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.warn("Failed to save scores: {}", e.getMessage());
        }
    }

    /** Load scores from disk. */
    private void load() {
        if (!Files.exists(SCORE_FILE)) return;
        try {
            String json = Files.readString(SCORE_FILE, StandardCharsets.UTF_8);
            java.lang.reflect.Type mapType = new TypeToken<java.util.Map<String, Object>>() {}.getType();
            java.util.Map<String, Object> data = GSON.fromJson(json, mapType);
            if (data == null) return;
            if (data.containsKey("scores")) {
                java.lang.reflect.Type scoreMapType = new TypeToken<java.util.Map<String, Double>>() {}.getType();
                java.util.Map<String, Double> rawScores = GSON.fromJson(GSON.toJson(data.get("scores")), scoreMapType);
                if (rawScores != null) {
                    for (var entry : rawScores.entrySet()) {
                        scores.put(UUID.fromString(entry.getKey()), entry.getValue().intValue());
                    }
                }
            }
            if (data.containsKey("lastRecoveryTime")) {
                java.lang.reflect.Type timeMapType = new TypeToken<java.util.Map<String, Double>>() {}.getType();
                java.util.Map<String, Double> rawTimes = GSON.fromJson(GSON.toJson(data.get("lastRecoveryTime")), timeMapType);
                if (rawTimes != null) {
                    for (var entry : rawTimes.entrySet()) {
                        lastRecoveryTime.put(UUID.fromString(entry.getKey()), entry.getValue().longValue());
                    }
                }
            }
            LOGGER.info("Loaded {} player scores from disk", scores.size());
        } catch (Exception e) {
            LOGGER.warn("Failed to load scores: {}", e.getMessage());
        }
    }
}
