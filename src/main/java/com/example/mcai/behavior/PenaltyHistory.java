package com.example.mcai.behavior;
import com.example.mcai.config.ModConfig;
import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
public class PenaltyHistory {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-Penalty");
    private static final Gson GSON = new GsonBuilder().create();
    private static final Path PENALTY_FILE = FabricLoader.getInstance().getConfigDir().resolve("mcai/penalties.json");
    private final AtomicInteger currentReviewCycle = new AtomicInteger(0);
    private final LinkedList<PenaltyEvent> recentPenalties = new LinkedList<>();
    private ModConfig config;
    public PenaltyHistory(ModConfig config) { this.config = config; load(); }
    public void reloadConfig(ModConfig newConfig) { this.config = newConfig; }
    public int getCurrentCycle() { return currentReviewCycle.get(); }
    public int advanceCycle() { return currentReviewCycle.incrementAndGet(); }
    public void addEvent(PenaltyEvent event) { synchronized (recentPenalties) { recentPenalties.addLast(event); } }
    public void purgeOld() { int minCycle = currentReviewCycle.get() - config.getMaxReviewCycles(); synchronized (recentPenalties) { recentPenalties.removeIf(e -> e.reviewCycle < minCycle); } }
    public String getSummary() {
        synchronized (recentPenalties) {
            if (recentPenalties.isEmpty()) return "";
            StringBuilder sb = new StringBuilder("===== 最近行为审查处罚记录 =====\n");
            for (PenaltyEvent e : recentPenalties) {
                long diff = System.currentTimeMillis() - e.timestamp; long minutes = diff / 60_000;
                String timeStr = minutes < 60 ? minutes + "分钟前" : (minutes / 60) + "小时前";
                String actionStr = switch (e.action) { case WARN -> "黄牌"; case KICK -> "红牌(待审批)"; case KICK_EXECUTED -> "已踢出"; default -> "扣分"; };
                sb.append("[").append(timeStr).append("] ").append(e.playerName).append(" | ").append(e.description).append(" | 扣").append(e.severity).append("分 | 当前").append(e.scoreAfter).append(" | ").append(actionStr);
                if (e.approvalId > 0 && e.action == PenaltyEvent.PenaltyAction.KICK) sb.append("(审批ID:").append(e.approvalId).append(")");
                sb.append("\n");
            }
            return sb.toString();
        }
    }
    public String getJson() {
        synchronized (recentPenalties) {
            if (recentPenalties.isEmpty()) return "";
            StringBuilder sb = new StringBuilder("\n最近已执行的处罚记录（避免重复处罚同一行为，但严重程度升级仍需报告）:\n[");
            boolean first = true;
            for (PenaltyEvent e : recentPenalties) {
                if (e.action == PenaltyEvent.PenaltyAction.SCORE_ONLY) continue;
                if (!first) sb.append(",\n"); first = false;
                String a = switch (e.action) { case WARN -> "warn"; case KICK, KICK_EXECUTED -> "kick"; default -> "none"; };
                sb.append("{\"player\":\"").append(e.playerName).append("\",\"action\":\"").append(a).append("\",\"score\":").append(e.scoreAfter).append(",\"reason\":\"").append(e.description).append("\"}");
            }
            if (first) return ""; sb.append("]"); return sb.toString();
        }
    }
    public void clear() { synchronized (recentPenalties) { recentPenalties.clear(); } save(); }
    public void save() {
        synchronized (recentPenalties) {
            try {
                Files.createDirectories(PENALTY_FILE.getParent()); JsonObject root = new JsonObject();
                root.addProperty("cycle", currentReviewCycle.get()); JsonArray arr = new JsonArray();
                for (PenaltyEvent e : recentPenalties) {
                    JsonObject ev = new JsonObject(); ev.addProperty("time", e.timestamp); ev.addProperty("player", e.playerName);
                    ev.addProperty("reason", e.description); ev.addProperty("severity", e.severity); ev.addProperty("score", e.scoreAfter);
                    ev.addProperty("action", e.action.name()); if (e.approvalId > 0) ev.addProperty("approvalId", e.approvalId);
                    ev.addProperty("cycle", e.reviewCycle); arr.add(ev);
                }
                root.add("events", arr); Files.writeString(PENALTY_FILE, GSON.toJson(root), StandardCharsets.UTF_8);
            } catch (Exception e) { LOGGER.warn("Failed to save penalties: {}", e.getMessage()); }
        }
    }
    private void load() {
        if (!Files.exists(PENALTY_FILE)) return;
        try {
            JsonObject obj = GSON.fromJson(Files.readString(PENALTY_FILE, StandardCharsets.UTF_8), JsonObject.class);
            if (obj == null || !obj.has("events")) return; JsonArray arr = obj.getAsJsonArray("events"); int loaded = 0;
            synchronized (recentPenalties) {
                recentPenalties.clear();
                for (JsonElement el : arr) {
                    try {
                        JsonObject ev = el.getAsJsonObject(); String player = ev.get("player").getAsString(); String reason = ev.get("reason").getAsString();
                        int severity = ev.get("severity").getAsInt(); int score = ev.get("score").getAsInt(); int cycle = ev.has("cycle") ? ev.get("cycle").getAsInt() : 0;
                        String actionStr = ev.has("action") ? ev.get("action").getAsString() : "SCORE_ONLY";
                        PenaltyEvent.PenaltyAction action; try { action = PenaltyEvent.PenaltyAction.valueOf(actionStr); } catch (IllegalArgumentException ex) { action = PenaltyEvent.PenaltyAction.SCORE_ONLY; }
                        int approvalId = ev.has("approvalId") ? ev.get("approvalId").getAsInt() : -1;
                        PenaltyEvent e = new PenaltyEvent(player, reason, severity, score, action, approvalId, cycle); e.timestamp = ev.get("time").getAsLong();
                        recentPenalties.addLast(e); loaded++;
                    } catch (Exception ex) { LOGGER.warn("Skipping invalid penalty entry: {}", ex.getMessage()); }
                }
            }
            LOGGER.info("Loaded {} penalty events from disk", loaded);
        } catch (Exception e) { LOGGER.warn("Failed to load penalties: {}", PENALTY_FILE, e.getMessage()); }
    }
}
