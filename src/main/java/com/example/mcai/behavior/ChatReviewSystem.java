package com.example.mcai.behavior;

import com.example.mcai.MCAIMod;
import com.example.mcai.api.OpenAIClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatReviewSystem {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-Review");
    private static final Gson GSON = new GsonBuilder().create();

    private static final String REVIEW_PROMPT = """
            你是一个Minecraft服务器的行为审查AI。分析以下聊天记录，判断非管理员玩家是否存在违规行为。

            审查规则：
            1. 仅审查非管理员玩家（OP等级低于2的普通玩家）
            2. 违规行为包括：辱骂/攻击性语言、刷屏、恶意破坏、使用外挂、利用漏洞等
            3. 如果玩家没有违规，不要报告
            4. 注意：玩家之间的正常交流和玩笑不属于违规

            返回严格的JSON格式（不要包含任何其他文字或markdown格式）：
            {"violations":[{"player_name":"玩家名","description":"违规行为描述","severity":-20,"suggested_action":"warn"}]}

            severity取值：-10(轻微)、-20(中度)、-30(严重)
            suggested_action取值："none"(仅扣分)、"warn"(建议警告)、"kick"(建议踢出)
            如果没有违规，返回: {"violations":[]}
            """;

    private final MCAIMod mod;
    private final PlayerBehaviorTracker tracker;
    private final AdminApprovalQueue approvalQueue;
    private final ScheduledExecutorService reviewScheduler;
    private final AtomicBoolean reviewInProgress = new AtomicBoolean(false);
    private ScheduledFuture<?> scheduledReview;
    private volatile long lastReviewTime = 0;
    private volatile String lastReviewStatus = "";

    public ChatReviewSystem(MCAIMod mod, PlayerBehaviorTracker tracker) {
        this.mod = mod;
        this.tracker = tracker;
        this.reviewScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MCAI-Review");
            t.setDaemon(true);
            return t;
        });
        this.approvalQueue = new AdminApprovalQueue(reviewScheduler, item -> {
            // Auto-approve timeout callback - executes on reviewScheduler thread
            executeApprovedAction(item);
        });
    }

    /** Start the periodic review timer. */
    public void start() {
        if (scheduledReview != null && !scheduledReview.isCancelled()) {
            scheduledReview.cancel(false);
        }
        int interval = mod.getConfig().getReviewIntervalMinutes();
        scheduledReview = reviewScheduler.scheduleAtFixedRate(
                this::runReview, interval, interval, TimeUnit.MINUTES);
        LOGGER.info("Auto review started, interval={}min", interval);
    }

    /** Stop the periodic review timer. */
    public void stop() {
        if (scheduledReview != null) {
            scheduledReview.cancel(false);
            scheduledReview = null;
        }
        reviewScheduler.shutdown();
    }

    /** Trigger a manual review. Called from /aicheck command on server thread. */
    public void triggerManualReview() {
        if (reviewInProgress.get()) {
            lastReviewStatus = "§e审查正在进行中，请稍候...";
            return;
        }
        lastReviewStatus = "§a审查已启动...";
        reviewScheduler.execute(() -> {
            runReview();
            lastReviewStatus = "§a审查完成";
        });
    }

    public String getLastReviewStatus() {
        return lastReviewStatus;
    }

    public AdminApprovalQueue getApprovalQueue() {
        return approvalQueue;
    }

    // ── Review Command ──

    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createAiCheckCommand() {
        return Commands.literal("aicheck")
                .requires(src -> {
                    var p = src.getPlayer();
                    if (p == null || src.getServer() == null) return false;
                    return isOpByName(src.getServer(), p.getGameProfile());
                })
                .then(Commands.literal("approve")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int id = IntegerArgumentType.getInteger(ctx, "id");
                                    return handleApproval(ctx.getSource(), id, true);
                                })))
                .then(Commands.literal("reject")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int id = IntegerArgumentType.getInteger(ctx, "id");
                                    return handleApproval(ctx.getSource(), id, false);
                                })))
                .executes(ctx -> {
                    // /aicheck (no args) - trigger manual review
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    triggerManualReview();
                    player.sendSystemMessage(Component.literal(lastReviewStatus));
                    return Command.SINGLE_SUCCESS;
                });
    }

    private int handleApproval(CommandSourceStack src, int id, boolean approve) {
        ServerPlayer admin = src.getPlayer();
        if (admin == null) return 0;

        if (approve) {
            AdminApprovalQueue.ApprovalItem item = approvalQueue.tryApprove(id);
            if (item != null) {
                src.sendSuccess(() -> Component.literal(
                        "§a[审批] 已批准 #" + id + " - 对 " + item.targetPlayerName + " 执行 " + item.action), true);
                executeApprovedAction(item);
                return 1;
            } else {
                src.sendFailure(Component.literal("§c[审批] 无效或已处理的审批 #" + id));
                return 0;
            }
        } else {
            AdminApprovalQueue.ApprovalItem item = approvalQueue.tryReject(id);
            if (item != null) {
                src.sendSuccess(() -> Component.literal(
                        "§c[审批] 已拒绝 #" + id + " - " + item.targetPlayerName + " " + item.action), true);
                return 1;
            } else {
                src.sendFailure(Component.literal("§c[审批] 无效或已处理的审批 #" + id));
                return 0;
            }
        }
    }

    // ── Main Review Cycle ──

    private void runReview() {
        if (!reviewInProgress.compareAndSet(false, true)) {
            LOGGER.debug("Review already in progress, skipping");
            return;
        }
        try {
            String chatSnapshot = mod.getChatHandler().peekChatLog();
            if (chatSnapshot.isEmpty()) {
                LOGGER.debug("No chat to review");
                return;
            }

            // Build AI messages
            List<OpenAIClient.ChatMessage> messages = new ArrayList<>();
            messages.add(new OpenAIClient.ChatMessage("system", REVIEW_PROMPT));
            messages.add(new OpenAIClient.ChatMessage("user", chatSnapshot));

            // Call AI
            var result = mod.getAiClient().chatSimple(messages);
            if (result.isEmpty() || result.get().startsWith("[API")) {
                LOGGER.warn("Review AI call failed: {}", result.orElse("no response"));
                lastReviewStatus = "§c审查AI调用失败";
                return;
            }

            String response = result.get();
            LOGGER.info("Review AI response: {}", response);

            // Parse violations
            List<PlayerViolation> violations = parseViolations(response);
            if (violations.isEmpty()) {
                LOGGER.info("No violations found in review");
                // Still clear chat and recover scores
                mod.getChatHandler().clearChatLog();
                recoverScores();
                lastReviewStatus = "§a未发现违规行为";
                return;
            }

            // Process violations
            var server = mod.getServer();
            if (server == null) return;

            String redCardActions = "";
            for (PlayerViolation v : violations) {
                UUID playerId = findPlayerUUID(v.playerName);
                if (playerId == null) {
                    LOGGER.warn("Player {} not found, skipping violation", v.playerName);
                    continue;
                }
                // Skip admins
                ServerPlayer targetPlayer = server.getPlayerList().getPlayer(playerId);
                if (targetPlayer != null && isAdmin(targetPlayer)) {
                    LOGGER.info("Skipping admin {} in review", v.playerName);
                    continue;
                }

                int newScore = tracker.addScore(playerId, v.severity);
                LOGGER.info("Player {} score changed by {} -> {} ({})",
                        v.playerName, v.severity, newScore, v.description);

                if ("kick".equals(v.suggestedAction) || newScore <= mod.getConfig().getRedCardThreshold()) {
                    // Red card: create approval item
                    long timeoutMs = mod.getConfig().getApprovalTimeoutMinutes() * 60_000L;
                    int approvalId = approvalQueue.addItem(
                            playerId, v.playerName, "kick", v.description, timeoutMs);

                    // Broadcast red card
                    String broadcastMsg = "§c[MCAI] §4玩家 " + v.playerName + " 触发红牌: " + v.description;
                    server.execute(() ->
                            server.getPlayerList().broadcastSystemMessage(
                                    Component.literal(broadcastMsg), false));

                    // Notify admins privately
                    String adminMsg = "§c[MCAI] §4" + v.playerName + " §c行为分 "+ newScore
                            + " 触发红牌踢出 (ID=" + approvalId + ")\n"
                            + "§7使用 §e/aicheck approve " + approvalId + " §7批准 或 §c/aicheck reject "
                            + approvalId + " §7拒绝\n§7(10分钟无响应自动批准)";
                    notifyAdmins(adminMsg);
                    redCardActions += v.playerName + "(" + approvalId + ") ";

                } else if ("warn".equals(v.suggestedAction) || newScore <= mod.getConfig().getYellowCardThreshold()) {
                    // Yellow card: broadcast warning
                    String broadcastMsg = "§c[MCAI] §e⚠ 玩家 " + v.playerName + " 黄牌警告: " + v.description;
                    server.execute(() ->
                            server.getPlayerList().broadcastSystemMessage(
                                    Component.literal(broadcastMsg), false));
                }
            }

            // Clear chat after successful review
            mod.getChatHandler().clearChatLog();

            // Recover scores for all online non-admin players
            recoverScores();

            lastReviewStatus = "§a审查完成，处理 " + violations.size() + " 项违规";
            if (!redCardActions.isEmpty()) {
                lastReviewStatus += " §c[红牌: " + redCardActions + "]";
            }
            LOGGER.info("Review complete: {} violations", violations.size());

        } catch (Exception e) {
            LOGGER.error("Review failed", e);
            lastReviewStatus = "§c审查异常: " + e.getMessage();
        } finally {
            reviewInProgress.set(false);
        }
    }

    // ── Actions ──

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
                String kickMsg = "你的行为评分过低，已被系统移出服务器。\n理由: " + item.reason;
                target.connection.disconnect(Component.literal("§c" + kickMsg));
                LOGGER.info("Kicked {} (approval #{})", item.targetPlayerName, item.id);
            }
        });
    }

    // ── Helpers ──

    private void recoverScores() {
        var server = mod.getServer();
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isAdmin(player)) {
                tracker.tryRecover(player.getUUID());
            }
        }
    }

    private void notifyAdmins(String message) {
        var server = mod.getServer();
        if (server == null) return;
        server.execute(() -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (isAdmin(player)) {
                    player.sendSystemMessage(Component.literal(message));
                }
            }
        });
    }

    private boolean isAdmin(ServerPlayer player) {
        var server = mod.getServer();
        return server != null && isOpByName(server, player.getGameProfile());
    }

    private static boolean isOpByName(MinecraftServer srv, GameProfile profile) {
        return srv.getPlayerList().isOp(new NameAndId(profile));
    }

    private UUID findPlayerUUID(String name) {
        var server = mod.getServer();
        if (server == null) return null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getScoreboardName().equalsIgnoreCase(name)) {
                return player.getUUID();
            }
        }
        return null;
    }

    private List<PlayerViolation> parseViolations(String json) {
        List<PlayerViolation> result = new ArrayList<>();
        try {
            // Strip markdown code block markers if present
            String cleaned = json.trim();
            if (cleaned.startsWith("```")) {
                int start = cleaned.indexOf('\n');
                int end = cleaned.lastIndexOf("```");
                if (start > 0 && end > start) {
                    cleaned = cleaned.substring(start, end).trim();
                }
            }
            JsonObject obj = GSON.fromJson(cleaned, JsonObject.class);
            if (obj == null || !obj.has("violations")) return result;
            JsonArray arr = obj.getAsJsonArray("violations");
            for (JsonElement el : arr) {
                JsonObject v = el.getAsJsonObject();
                String name = v.get("player_name").getAsString();
                String desc = v.get("description").getAsString();
                int severity = v.has("severity") ? v.get("severity").getAsInt() : -10;
                String action = v.has("suggested_action") ? v.get("suggested_action").getAsString() : "none";
                result.add(new PlayerViolation(name, desc, severity, action));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse review violations from: {}", json, e);
        }
        return result;
    }
}
