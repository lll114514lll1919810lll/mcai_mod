package com.example.mcai.behavior;
import com.example.mcai.MCAIMod;
import com.example.mcai.api.OpenAIClient;
import com.example.mcai.handler.CommandExecutionService;
import com.google.gson.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import com.example.mcai.handler.ChatHandler;
public class ReviewEngine {
    static final Logger LOGGER = LoggerFactory.getLogger("MCAI-Review");
    private static final Gson GSON = new GsonBuilder().create();
    private final MCAIMod mod; private final PlayerBehaviorTracker tracker;
    private final PenaltyHistory penaltyHistory; private final AdminApprovalQueue approvalQueue;
    private volatile String lastRawResponse = ""; private volatile String lastReasoning = "";
    public ReviewEngine(MCAIMod mod, PlayerBehaviorTracker tracker, PenaltyHistory penaltyHistory, AdminApprovalQueue approvalQueue) { this.mod = mod; this.tracker = tracker; this.penaltyHistory = penaltyHistory; this.approvalQueue = approvalQueue; }
    public String getLastRawResponse() { return lastRawResponse; }
    public String getLastReasoning() { return lastReasoning; }
    public Component run() {
        penaltyHistory.advanceCycle(); lastRawResponse = ""; lastReasoning = "";
        String reviewPrompt = mod.getConfig().getReviewPrompt();
        String chatSnapshot = mod.getChatLog().peek(); if (chatSnapshot.isEmpty()) return Component.translatable("mcai.review.status.empty");
        StringBuilder roster = new StringBuilder("当前在线玩家:\n"); var srv = mod.getServer();
        if (srv != null) { for (ServerPlayer p : srv.getPlayerList().getPlayers()) { roster.append("- ").append(p.getScoreboardName()).append(isAdmin(p, srv) ? " (管理员)" : " (普通玩家)").append("\n"); } }
        roster.append("\n=== CHAT LOG START ===\n").append(ChatHandler.sanitizeChatLogForPrompt(chatSnapshot)).append("\n=== CHAT LOG END ===\n");
        String pj = penaltyHistory.getJson(); if (!pj.isEmpty()) roster.append("\n").append(pj);
        List<OpenAIClient.ChatMessage> messages = new ArrayList<>();
        messages.add(new OpenAIClient.ChatMessage("system", reviewPrompt)); messages.add(new OpenAIClient.ChatMessage("user", roster.toString()));
        var result = mod.getReviewClient().chatSimpleFull(messages);
        if (!result.success()) { LOGGER.warn("Review AI call failed: {}", result.error()); return Component.translatable("mcai.review.status.failed"); }
        var chatResult = result.value(); lastRawResponse = chatResult.content; lastReasoning = chatResult.reasoningContent != null ? chatResult.reasoningContent : "";
        String response = chatResult.content; LOGGER.info("Review AI response: {}", response); saveReviewFiles();
        List<PlayerViolation> violations = parseViolations(response);

        // Retry if AI didn't return valid JSON (but don't retry valid empty results)
        if (violations.isEmpty() && !isValidViolationsJson(response)) {
            LOGGER.warn("Review AI returned non-JSON, retrying with strict prompt");
            messages.add(new OpenAIClient.ChatMessage("user",
                    "错误：你刚才的回复不是有效的JSON格式。请只返回严格JSON，不要包含任何解释文字。正确格式: {\"violations\":[{\"player_name\":\"玩家名\",\"description\":\"描述\",\"severity\":-20,\"suggested_action\":\"warn\"}]} 或 {\"violations\":[]}"));
            var retryResult = mod.getReviewClient().chatSimpleFull(messages);
            if (retryResult.success()) {
                lastRawResponse = retryResult.value().content;
                lastReasoning = retryResult.value().reasoningContent != null ? retryResult.value().reasoningContent : "";
                response = retryResult.value().content;
                violations = parseViolations(response);
                saveReviewFiles();
                LOGGER.info("Review AI retry response: {}", response);
            } else {
                return Component.translatable("mcai.review.retry_failed", Component.literal(retryResult.error()));
            }
            if (violations.isEmpty() && !isValidViolationsJson(response)) {
                LOGGER.error("Review AI still returning non-JSON after retry");
                return Component.translatable("mcai.review.invalid_json");
            }
        }

        if (violations.isEmpty()) {
            // 使用同步块避免与 AI 查询线程同时操作聊天记录
            synchronized (mod.getChatLog()) {
                mod.getChatLog().clear();
            }
            int recovered = recoverScores();
            if (recovered > 0) penaltyHistory.addEvent(new PenaltyEvent("系统", recovered+"名玩家行为分已恢复", 0, 0, PenaltyEvent.PenaltyAction.SCORE_ONLY, -1, penaltyHistory.getCurrentCycle()));
            penaltyHistory.save(); penaltyHistory.purgeOld(); return Component.translatable("mcai.review.status.no_violations");
        }
        if (srv == null) return Component.translatable("mcai.review.status.server_not_ready");
        StringBuilder redCardActions = new StringBuilder(); Map<String, Integer> cyclePenalties = new HashMap<>();
        for (PlayerViolation v : violations) {
            UUID playerId = findPlayerUUID(v.playerName, srv); if (playerId == null) { LOGGER.warn("Player {} not found", v.playerName); continue; }
            ServerPlayer targetPlayer = srv.getPlayerList().getPlayer(playerId);
            if (targetPlayer != null && isAdmin(targetPlayer, srv)) { LOGGER.info("Skipping admin {}", v.playerName); continue; }
            int cumulative = cyclePenalties.getOrDefault(v.playerName, 0) + v.severity;
            if (cumulative < mod.getConfig().getRedCardThreshold()) { LOGGER.warn("Cumulative penalty {} for {} exceeds red card threshold {}, skipping", cumulative, v.playerName, mod.getConfig().getRedCardThreshold()); continue; }
            cyclePenalties.put(v.playerName, cumulative);
            int newScore = tracker.addScore(playerId, v.severity);
            LOGGER.info("Player {} score {} -> {} ({})", v.playerName, v.severity, newScore, v.description);
            if ("kick".equals(v.suggestedAction) || newScore <= mod.getConfig().getRedCardThreshold()) {
                long timeoutMs = mod.getConfig().getApprovalTimeoutMinutes() * 60_000L;
                int aid = approvalQueue.addItem(playerId, v.playerName, "kick", v.description, timeoutMs);
                broadcast(Component.translatable("mcai.review.kick_broadcast", v.playerName, v.description), srv);
                notifyAdmins(Component.translatable("mcai.review.admin_notify", v.playerName, newScore, aid, aid, aid), srv);
                redCardActions.append(v.playerName).append("(").append(aid).append(") ");
                penaltyHistory.addEvent(new PenaltyEvent(v.playerName, v.description, v.severity, newScore, PenaltyEvent.PenaltyAction.KICK, aid, penaltyHistory.getCurrentCycle()));
            } else if ("warn".equals(v.suggestedAction) || newScore <= mod.getConfig().getYellowCardThreshold()) {
                broadcast(Component.translatable("mcai.review.yellow_broadcast", v.playerName, v.description), srv);
                penaltyHistory.addEvent(new PenaltyEvent(v.playerName, v.description, v.severity, newScore, PenaltyEvent.PenaltyAction.WARN, -1, penaltyHistory.getCurrentCycle()));
            } else {
                penaltyHistory.addEvent(new PenaltyEvent(v.playerName, v.description, v.severity, newScore, PenaltyEvent.PenaltyAction.SCORE_ONLY, -1, penaltyHistory.getCurrentCycle()));
            }
        }
        synchronized (mod.getChatLog()) {
            mod.getChatLog().clear();
        }
        int recovered = recoverScores();
        if (recovered > 0) penaltyHistory.addEvent(new PenaltyEvent("系统", recovered+"名玩家行为分已恢复", 0, 0, PenaltyEvent.PenaltyAction.SCORE_ONLY, -1, penaltyHistory.getCurrentCycle()));
        String status = "§a审查完成，处理 "+violations.size()+" 项违规"; if (redCardActions.length() > 0) status += " §c[红牌: "+redCardActions+"]";
        penaltyHistory.save(); penaltyHistory.purgeOld(); return Component.literal(status);
    }
    private void broadcast(Component msg, MinecraftServer srv) {
        srv.execute(() -> { srv.getPlayerList().broadcastSystemMessage(msg, false); mod.getChatLog().add("MCAI", msg.getString().replaceAll("§[0-9a-fklmnor]", "").trim()); });
    }
    private void notifyAdmins(Component message, MinecraftServer srv) {
        srv.execute(() -> { for (ServerPlayer p : srv.getPlayerList().getPlayers()) { if (isAdmin(p, srv)) p.sendSystemMessage(message); } });
    }
    private int recoverScores() { var srv = mod.getServer(); if (srv == null) return 0; int c = 0; for (ServerPlayer p : srv.getPlayerList().getPlayers()) { if (!isAdmin(p, srv)) { int b = tracker.getScore(p.getUUID()); tracker.tryRecover(p.getUUID()); if (tracker.getScore(p.getUUID()) > b) c++; } } return c; }
    private static boolean isAdmin(ServerPlayer player, MinecraftServer srv) { return CommandExecutionService.isAdmin(player, srv); }
    private static UUID findPlayerUUID(String name, MinecraftServer srv) { ServerPlayer p = CommandExecutionService.findPlayerByName(name, srv); return p != null ? p.getUUID() : null; }
    private void saveReviewFiles() {
        try {
            Path d = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("mcai");
            Files.createDirectories(d);
            if (lastReasoning != null && !lastReasoning.isEmpty()) writeWithSplit(d, "review_last_reasoning.txt", lastReasoning);
            if (lastRawResponse != null && !lastRawResponse.isEmpty()) writeWithSplit(d, "review_last_response.txt", lastRawResponse);
        } catch (Exception e) { LOGGER.warn("Failed to save review files: {}", e.getMessage()); }
    }

    private static final long MAX_FILE_SIZE = 100 * 1024; // 100KB

    private static void writeWithSplit(Path dir, String baseName, String content) throws Exception {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        if (data.length <= MAX_FILE_SIZE) {
            Files.writeString(dir.resolve(baseName), content, StandardCharsets.UTF_8);
            return;
        }
        // 拆分写入：baseName -> baseName, baseName.1, baseName.2, ...
        int part = 0;
        int offset = 0;
        while (offset < data.length) {
            int len = (int) Math.min(MAX_FILE_SIZE, data.length - offset);
            String chunk = new String(data, offset, len, StandardCharsets.UTF_8);
            String fileName = part == 0 ? baseName : baseName.replace(".txt", "." + part + ".txt");
            Files.writeString(dir.resolve(fileName), chunk, StandardCharsets.UTF_8);
            offset += len;
            part++;
        }
        LOGGER.info("Split {} into {} parts ({}KB total)", baseName, part, data.length / 1024);
    }
    private static List<PlayerViolation> parseViolations(String json) {
        List<PlayerViolation> result = new ArrayList<>();
        try { String c = json.trim(); if (c.startsWith("```")) { int s = c.indexOf('\n'); int e = c.lastIndexOf("```"); if (s > 0 && e > s) c = c.substring(s, e).trim(); }
            JsonObject obj = GSON.fromJson(c, JsonObject.class); if (obj == null || !obj.has("violations")) return result; JsonArray arr = obj.getAsJsonArray("violations");
            for (JsonElement el : arr) { JsonObject v = el.getAsJsonObject(); if (!v.has("player_name") || !v.has("description")) continue; String name = v.get("player_name").getAsString().trim(); if (!name.matches("[a-zA-Z0-9_]{3,16}")) { LOGGER.warn("Invalid player name: {}", name); continue; } String desc = v.get("description").getAsString().trim(); if (desc.length() > 200) desc = desc.substring(0, 200); int severity = v.has("severity") ? v.get("severity").getAsInt() : -10;
            // 将 AI 返回的原始分数分档到三档：-10(轻微) / -20(中度) / -30(严重)
            if (severity >= -10) severity = -10;
            else if (severity >= -20) severity = -20;
            else severity = -30; String action = v.has("suggested_action") ? v.get("suggested_action").getAsString() : "none"; if (!"none".equals(action) && !"warn".equals(action) && !"kick".equals(action)) action = "none"; result.add(new PlayerViolation(name, desc, severity, action)); }
        } catch (Exception e) { LOGGER.error("Failed to parse review violations", e); }
        return result;
    }

    /** 检查AI响应是否为有效的违规JSON格式 */
    private static boolean isValidViolationsJson(String json) {
        try {
            String c = json.trim();
            if (c.startsWith("```")) { int s = c.indexOf('\n'); int e = c.lastIndexOf("```"); if (s > 0 && e > s) c = c.substring(s, e).trim(); }
            JsonObject obj = GSON.fromJson(c, JsonObject.class);
            return obj != null && obj.has("violations");
        } catch (Exception e) { return false; }
    }
}
