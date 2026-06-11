package com.example.mcai.handler;

import com.example.mcai.MCAIMod;
import com.example.mcai.api.OpenAIClient;
import com.example.mcai.kb.KnowledgeBase;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;

import net.minecraft.server.permissions.LevelBasedPermissionSet;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.players.NameAndId;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ChatHandler {
    private final MCAIMod mod;
    private final ExecutorService aiExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "MCAI-Worker");
        t.setDaemon(true);
        return t;
    });
    private final ScheduledExecutorService uiScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MCAI-UI");
        t.setDaemon(true);
        return t;
    });
    private final Map<UUID, LinkedList<OpenAIClient.ChatMessage>> history = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> pendingCommands = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledFuture<?>> thinkingAnimations = new ConcurrentHashMap<>();
    private final LinkedList<String> chatLog = new LinkedList<>();
    /** Pending approval futures: key = "playerUUID:num" → CompletableFuture for AI thread to block on */
    private final ConcurrentMap<String, CompletableFuture<String>> pendingFutures = new ConcurrentHashMap<>();

    public ChatHandler(MCAIMod mod) {
        this.mod = mod;
    }

    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createAICommand() {
        return Commands.literal("ai")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            String msg = StringArgumentType.getString(ctx, "message");

                            if (player != null) {
                                // Player AI query
                                var server = mod.getServer();
                                if (server != null) {
                                    server.getPlayerList().broadcastSystemMessage(
                                            Component.literal("§7[§f" + player.getScoreboardName()
                                                    + "§7]对AI说：§f" + msg), false);
                                }
                                addToChatLog(player.getScoreboardName(), msg, isAdminPlayer(player));
                                handleAIQuery(player, msg);
                            } else {
                                // Console AI query
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("§7[控制台] 正在向AI发送查询..."), false);
                                addToChatLog("控制台", msg, true);
                                handleConsoleAIQuery(ctx.getSource(), msg);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("用法: /ai <消息>"));
                    return 0;
                });
    }

    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createWikiCommand() {
        return Commands.literal("aikb")
                .requires(ChatHandler::isAdminOrConsole)
                .then(Commands.argument("query", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String query = StringArgumentType.getString(ctx, "query");
                            String result = mod.getKnowledgeBase().search(query, 5);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("§a=== 知识库结果 ===\n§7" + result), false);
                            return 1;
                        }))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("用法: /aikb <关键词>"));
                    return 0;
                });
    }

    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createQueryCommand() {
        return Commands.literal("aiquery")
                .requires(ChatHandler::isAdminOrConsole)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player != null) {
                        // Player: show own pending commands
                        List<String> cmds = pendingCommands.get(player.getUUID());
                        if (cmds == null || cmds.isEmpty()) {
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("§7[AI] 暂无待审批指令"), false);
                            return 0;
                        }
                        ctx.getSource().sendSuccess(
                                () -> Component.literal("§e==== 待审批指令 (" + cmds.size() + ") ===="), false);
                        for (int i = 0; i < cmds.size(); i++) {
                            final int num = i + 1;
                            final String cmd = cmds.get(i);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("§6  [" + num + "] /" + cmd), false);
                        }
                        ctx.getSource().sendSuccess(
                                () -> Component.literal("§e使用 §a/aiaccept <编号> 批准，§c/aireject <编号> 拒绝"), false);
                    } else {
                        // Console: show all pending commands grouped by player
                        var all = pendingCommands.entrySet();
                        if (all.isEmpty()) {
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("§7[AI] 暂无待审批指令"), false);
                            return 0;
                        }
                        ctx.getSource().sendSuccess(
                                () -> Component.literal("§e==== 全部待审批指令 ===="), false);
                        final java.util.concurrent.atomic.AtomicInteger total = new java.util.concurrent.atomic.AtomicInteger(0);
                        for (var entry : all) {
                            final String fname;
                            var srv = mod.getServer();
                            if (srv != null) {
                                var p = srv.getPlayerList().getPlayer(entry.getKey());
                                fname = p != null ? p.getScoreboardName() : "?";
                            } else {
                                fname = "?";
                            }
                            for (int i = 0; i < entry.getValue().size(); i++) {
                                final int ftotal = total.incrementAndGet();
                                final String cmd = entry.getValue().get(i);
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("§6  [" + ftotal + "] §f" + fname + " §7→ /" + cmd), false);
                            }
                        }
                    }
                    return 1;
                });
    }

    private final SuggestionProvider<CommandSourceStack> PENDING_ID_SUGGESTIONS = (ctx, builder) -> {
        ServerPlayer p = ctx.getSource().getPlayer();
        if (p != null) {
            var cmds = pendingCommands.get(p.getUUID());
            if (cmds != null) {
                for (int i = 1; i <= cmds.size(); i++) builder.suggest(i);
            }
        } else {
            // Console: show global indices across all players
            int total = 0;
            for (var entry : pendingCommands.entrySet()) {
                for (int i = 0; i < entry.getValue().size(); i++) {
                    total++;
                    builder.suggest(total);
                }
            }
        }
        return builder.buildFuture();
    };

    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createAcceptCommand() {
        return Commands.literal("aiaccept")
                .requires(ChatHandler::isAdminOrConsole)
                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                        .suggests(PENDING_ID_SUGGESTIONS)
                        .executes(ctx -> approveCommand(ctx.getSource().getPlayer(),
                                IntegerArgumentType.getInteger(ctx, "number"), ctx.getSource())))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("用法: /aiaccept <编号>"));
                    return 0;
                });
    }

    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createRejectCommand() {
        return Commands.literal("aireject")
                .requires(ChatHandler::isAdminOrConsole)
                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                        .suggests(PENDING_ID_SUGGESTIONS)
                        .executes(ctx -> rejectCommand(ctx.getSource().getPlayer(),
                                IntegerArgumentType.getInteger(ctx, "number"), ctx.getSource())))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("用法: /aireject <编号>"));
                    return 0;
                });
    }

    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createClearCommand() {
        return Commands.literal("aiclear")
                .requires(ChatHandler::isAdminOrConsole)
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    history.remove(player.getUUID());
                    pendingCommands.remove(player.getUUID());
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("§a[AI] 已清除对话历史和待审批指令"), false);
                    return 1;
                });
    }


    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createScoreCommand() {
        return Commands.literal("aiscore")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) {
                        ctx.getSource().sendSuccess(() -> Component.literal("§c控制台无行为分"), false);
                        return 0;
                    }
                    var tracker = mod.getBehaviorTracker();
                    var cfg = mod.getConfig();
                    int score = tracker.getScore(player.getUUID());
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§e===== 你的行为评分 ====="), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§f当前评分: " + (score >= 0 ? "§a" : "§c") + score), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§7━━━━ 处罚规则 ━━━━"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§7行为分初始为 §f0§7，违规扣分，良好表现可恢复"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§e黄牌阈值: §f" + cfg.getYellowCardThreshold()
                                    + " §7(公屏警告)"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§c红牌阈值: §f" + cfg.getRedCardThreshold()
                                    + " §7(踢出+管理员审批)"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§a每周期自动恢复: §f+" + cfg.getScoreRecoveryPerInterval()
                                    + " §7(上限恢复至0)"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§7━━━━━━━━━━━━━━"), false);
                    return Command.SINGLE_SUCCESS;
                });
    }

    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createReloadCommand() {
        return Commands.literal("aireload")
                .executes(ctx -> {
                    mod.reloadConfig();
                    history.clear();
                    pendingCommands.clear();
                    synchronized (chatLog) { chatLog.clear(); }
                    if (mod.getChatReviewSystem() != null) {
                        mod.getChatReviewSystem().clearPenaltyEvents();
                    }
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("§a[AI] 已重载，所有状态已清空"), true);
                    return 1;
                });
    }

    private void addToChatLog(String name, String message) {
        addToChatLog(name, message, false);
    }

    private void addToChatLog(String name, String message, boolean isAdmin) {
        synchronized (chatLog) {
            String time = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .format(java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai")));
            String prefix = isAdmin ? "[管理员] " : "";
            chatLog.add("[" + time + "] " + prefix + name + ": " + message);
            while (chatLog.size() > 50) chatLog.removeFirst();
        }
    }

    /** Snapshot chat log without clearing. Used by review system. */
    public String peekChatLog() {
        synchronized (chatLog) {
            return String.join("\n", chatLog);
        }
    }

    /** Clear chat log after successful review. */
    public void clearChatLog() {
        synchronized (chatLog) {
            chatLog.clear();
        }
    }

    public ExecutorService getAiExecutor() { return aiExecutor; }

    public void registerChatInterceptor() {
        if (!mod.getConfig().isEnableChatInterception()) return;
        try {
            // CHAT_MESSAGE: 记录所有消息（内容始终可用，包括未签名消息）
            ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
                if (sender != null) {
                    String text = message.decoratedContent() != null
                            ? message.decoratedContent().getString()
                            : "";
                    addToChatLog(sender.getScoreboardName(), text, isAdminPlayer(sender));
                }
            });
            // ALLOW_CHAT_MESSAGE: 仅用于拦截 !ai 前缀触发
            ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
                if (sender == null) return true;
                String text = message.decoratedContent() != null
                        ? message.decoratedContent().getString()
                        : (message.signedContent() != null ? message.signedContent() : "");
                String prefix = mod.getConfig().getTriggerPrefix();
                if (text.startsWith(prefix)) {
                    String query = text.substring(prefix.length()).trim();
                    if (!query.isEmpty()) {
                        var server = mod.getServer();
                        if (server != null) {
                            server.getPlayerList().broadcastSystemMessage(
                                    Component.literal("§7[§f" + sender.getScoreboardName()
                                            + "§7]对AI说：§f" + query), false);
                        }
                        handleAIQuery(sender, query);
                    }
                    return false;
                }
                return true;
            });
            ServerMessageEvents.GAME_MESSAGE.register((srv, msg, overlay) -> {
                try {
                    String text = msg.getString();
                    // Try to extract [PlayerName] message from /say command
                    // In 26.1 the format is typically "[PlayerName] message"
                    var matcher = java.util.regex.Pattern.compile("^\\[(.+?)\\] (.+)$").matcher(text);
                    if (matcher.matches()) {
                        String possibleName = matcher.group(1);
                        String content = matcher.group(2);
                        ServerPlayer player = srv.getPlayerList().getPlayer(possibleName);
                        if (player != null) {
                            addToChatLog(possibleName + "(命令)", content, isAdminPlayer(player));
                            return;
                        }
                    }
                    addToChatLog("系统", text);
                } catch (Exception e) {
                    MCAIMod.LOGGER.warn("GAME_MESSAGE log failed: {}", e.getMessage());
                    addToChatLog("系统", "<消息记录失败>");
                }
            });
            MCAIMod.LOGGER.info("Chat interception enabled");
        } catch (NoClassDefFoundError | Exception e) {
            MCAIMod.LOGGER.warn("Chat interception unavailable");
        }
    }

    public void onPlayerDisconnect(ServerPlayer player) {
        UUID id = player.getUUID();
        history.remove(id);
        pendingCommands.remove(id);
        // Reject all pending futures for this player
        pendingFutures.keySet().removeIf(k -> k.startsWith(id.toString() + ":"));
    }

    private int approveCommand(ServerPlayer player, int num, CommandSourceStack src) {
        List<String> cmds = pendingCommands.get(player.getUUID());
        int idx = num - 1;
        if (cmds == null || idx < 0 || idx >= cmds.size()) {
            src.sendFailure(Component.literal("§c编号无效，使用 /aiquery 查看"));
            // Also release any blocked AI call
            String key = player.getUUID() + ":" + num;
            CompletableFuture<String> f = pendingFutures.remove(key);
            if (f != null) f.complete("[审批失败] 编号无效");
            return 0;
        }
        String cmd = cmds.remove(idx);
        if (cmds.isEmpty()) pendingCommands.remove(player.getUUID());
        String result = "指令已执行";
        var server = mod.getServer();
        if (server != null) {
            try {
                server.getCommands().getDispatcher().execute(cmd, server.createCommandSourceStack());
            } catch (CommandSyntaxException e) {
                result = "指令语法错误: " + e.getMessage();
            }
        }
        src.sendSuccess(() -> Component.literal("§a[AI] 已批准 #" + num + " 并执行: /" + cmd), true);
        // Release the blocked AI call
        String key = player.getUUID() + ":" + num;
        CompletableFuture<String> f = pendingFutures.remove(key);
        if (f != null) f.complete(result);
        return 1;
    }

    private int rejectCommand(ServerPlayer player, int num, CommandSourceStack src) {
        List<String> cmds = pendingCommands.get(player.getUUID());
        int idx = num - 1;
        if (cmds == null || idx < 0 || idx >= cmds.size()) {
            src.sendFailure(Component.literal("§c编号无效，使用 /aiquery 查看"));
            String key = player.getUUID() + ":" + num;
            CompletableFuture<String> f = pendingFutures.remove(key);
            if (f != null) f.complete("[审批失败] 编号无效");
            return 0;
        }
        String cmd = cmds.remove(idx);
        if (cmds.isEmpty()) pendingCommands.remove(player.getUUID());
        src.sendSuccess(() -> Component.literal("§c[AI] 已拒绝 #" + num + ": /" + cmd), true);
        // Release the blocked AI call
        String key = player.getUUID() + ":" + num;
        CompletableFuture<String> f = pendingFutures.remove(key);
        if (f != null) f.complete("[审批拒绝] 管理员拒绝了指令: /" + cmd);
        return 1;
    }

    // ── 经验栏动画 ──

    private void startThinkingAnimation(ServerPlayer player) {
        UUID id = player.getUUID();
        stopThinkingAnimation(id);
        ScheduledFuture<?> f = uiScheduler.scheduleAtFixedRate(new Runnable() {
            int frame = 0;
            @Override
            public void run() {
                var srv = mod.getServer();
                if (srv == null || player.isRemoved()) {
                    stopThinkingAnimation(id);
                    return;
                }
                String bar = switch (frame % 4) {
                    case 0 -> "§7▌§8▌▌ §eAI 思考中...";
                    case 1 -> "§7▌▌§8▌ §eAI 思考中...";
                    case 2 -> "§7▌▌▌ §eAI 思考中...";
                    default -> "§8▌▌▌ §7AI 思考中...";
                };
                srv.execute(() -> player.connection.send(new ClientboundSetActionBarTextPacket(Component.literal(bar))));
                frame++;
            }
        }, 0, 400, TimeUnit.MILLISECONDS);
        thinkingAnimations.put(id, f);
    }

    private void stopThinkingAnimation(UUID id) {
        ScheduledFuture<?> f = thinkingAnimations.remove(id);
        if (f != null) f.cancel(false);
    }

    private void doneThinking(ServerPlayer player) {
        stopThinkingAnimation(player.getUUID());
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
    }

    // ── AI 查询 ──

    private void handleAIQuery(ServerPlayer player, String query) {
        var playerHistory = history.computeIfAbsent(player.getUUID(), k -> new LinkedList<>());
        int maxCtx = mod.getConfig().getContextMaxChars();
        final UUID pid = player.getUUID();
        final String pname = player.getScoreboardName();
        MCAIMod.LOGGER.info("AI query from {}: {}", pname, query);

        startThinkingAnimation(player);

        aiExecutor.execute(() -> {
            try {
                String context = buildPlayerContext(player);
                String userContent = context + "\n\n" + pname + " 说: " + query;

                List<OpenAIClient.ChatMessage> messages = new ArrayList<>();
                messages.add(new OpenAIClient.ChatMessage("system", mod.getConfig().getSystemPrompt()));

                String recentChat;
                synchronized (chatLog) {
                    recentChat = String.join("\n", chatLog);
                }
                if (!recentChat.isEmpty()) {
                    messages.add(new OpenAIClient.ChatMessage("system",
                            "最近的聊天记录（了解当前氛围）:\n" + recentChat));
                }

                // Inject recent penalty records for AI awareness
                String penaltySummary = mod.getChatReviewSystem() != null
                        ? mod.getChatReviewSystem().getRecentPenaltySummary() : "";
                if (!penaltySummary.isEmpty()) {
                    messages.add(new OpenAIClient.ChatMessage("system", penaltySummary));
                }

                synchronized (playerHistory) {
                    int totalChars = 0;
                    for (var msg : playerHistory) {
                        int c = msg.content != null ? msg.content.length() : 0;
                        if (totalChars + c > maxCtx) break;
                        totalChars += c;
                        messages.add(msg);
                    }
                }
                messages.add(new OpenAIClient.ChatMessage("user", userContent));

                var result = mod.getAiClient().chat(messages, toolCalls -> {
                    List<String> results = new ArrayList<>();
                    for (var tc : toolCalls) {
                        if ("search_knowledge_base".equals(tc.name)) {
                            results.add(mod.getKnowledgeBase().search(
                                    parseArg(tc.arguments, "query"), 5));
                        } else if ("read_knowledge_base".equals(tc.name)) {
                            results.add(mod.getKnowledgeBase().read(
                                    parseArg(tc.arguments, "title")));
                        } else if ("execute_minecraft_command".equals(tc.name)) {
                            results.add(executeCommand(parseArg(tc.arguments, "command"), player));
                        } else if ("get_server_status".equals(tc.name)) {
                            results.add(getServerStatus(player));
                        } else if ("get_game_rules".equals(tc.name)) {
                            results.add(getGameRules(player));
                        } else if ("get_debug_info".equals(tc.name)) {
                            results.add(getDebugInfo(player));
                        } else {
                            results.add("未知工具: " + tc.name);
                        }
                    }
                    return results;
                });

                var server = mod.getServer();
                if (server == null) {
                    MCAIMod.LOGGER.warn("Server null when handling AI query");
                    return;
                }

                MCAIMod.LOGGER.info("API result present: {}", result.isPresent());
                if (result.isPresent()) {
                    String response = result.get();
                    server.execute(() -> {
                        doneThinking(player);
                        if (response.startsWith("[API错误]") || response.startsWith("[API连接失败]")
                                || response.startsWith("[API异常]") || response.startsWith("[响应解析失败]")
                                || response.startsWith("[工具调用超限]") || response.startsWith("[工具调用异常]")) {
                            player.sendSystemMessage(Component.literal("§c" + response));
                        } else {
                            handleResponse(player, response);
                            synchronized (playerHistory) {
                                playerHistory.add(new OpenAIClient.ChatMessage("user", userContent));
                                playerHistory.add(new OpenAIClient.ChatMessage("assistant", response));
                                trimHistoryByChars(playerHistory, maxCtx);
                            }
                        }
                    });
                } else {
                    server.execute(() -> {
                        doneThinking(player);
                        player.sendSystemMessage(
                                Component.literal("§c[AI] 无响应，请检查控制台日志"));
                    });
                }
            } catch (Exception e) {
                MCAIMod.LOGGER.error("AI query failed", e);
                var server = mod.getServer();
                if (server != null) {
                    server.execute(() -> {
                        doneThinking(player);
                        player.sendSystemMessage(
                                Component.literal("§c[AI] 异常: " + e.getMessage()));
                    });
                }
            }
        });
    }

    private void trimHistoryByChars(LinkedList<OpenAIClient.ChatMessage> h, int maxChars) {
        while (!h.isEmpty()) {
            int total = 0;
            for (var msg : h) {
                total += msg.content != null ? msg.content.length() : 0;
            }
            if (total <= maxChars) break;
            h.removeFirst();
        }
    }

    private String parseArg(String json, String key) {
        try {
            var obj = new com.google.gson.GsonBuilder().create()
                    .fromJson(json, com.google.gson.JsonObject.class);
            if (obj.has(key)) return obj.get(key).getAsString();
        } catch (Exception ignored) {}
        return json;
    }

    // AI绝对禁止执行的mod内部指令
    private static final java.util.Set<String> FORBIDDEN_COMMANDS = java.util.Set.of(
            "ai", "aiwiki", "aiquery", "aiaccept", "aireject",
            "aiclear", "aireload", "aitest", "aicheck"
    );

    private String executeCommand(String command, ServerPlayer player) {
        var server = mod.getServer();
        if (server == null) return "服务器未就绪";
        String root = command.split("\\s+")[0].toLowerCase();
        if (FORBIDDEN_COMMANDS.contains(root)) {
            return "禁止AI执行Mod内部指令";
        }
        if (player != null && needsApproval(command)) {
            int num = addPendingCommand(player.getUUID(), command);
            String key = player.getUUID() + ":" + num;
            CompletableFuture<String> future = new CompletableFuture<>();
            pendingFutures.put(key, future);
            // Notify online admins
            notifyAdminsPending(player, command, num);
            // Block AI thread waiting for approval (max 3 minutes)
            try {
                String result = future.get(3, TimeUnit.MINUTES);
                return result != null ? result : "指令已执行";
            } catch (java.util.concurrent.TimeoutException e) {
                pendingFutures.remove(key);
                return "[审批超时] 3分钟内无人批准，指令已自动取消: /" + command;
            } catch (Exception e) {
                return "[审批异常] " + e.getMessage();
            }
        }
        CompletableFuture<String> future = new CompletableFuture<>();
        String playerName = player != null ? player.getScoreboardName() : "控制台";
        server.execute(() -> {
            try {
                StringBuilder out = new StringBuilder();
                var cs = server.createCommandSourceStack();
                var src = new CommandSourceStack(
                        new net.minecraft.commands.CommandSource() {
                            @Override
                            public void sendSystemMessage(Component msg) {
                                out.append(msg.getString()).append("\n");
                            }
                            @Override
                            public boolean acceptsSuccess() { return true; }
                            @Override
                            public boolean acceptsFailure() { return true; }
                            @Override
                            public boolean alwaysAccepts() { return false; }
                            @Override
                            public boolean shouldInformAdmins() { return false; }
                        },
                        cs.getPosition(), cs.getRotation(), cs.getLevel(),
                        LevelBasedPermissionSet.OWNER, cs.getTextName(), cs.getDisplayName(),
                        server, cs.getEntity());
                server.getCommands().getDispatcher().execute(command, src);
                String result = out.toString().trim();
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§7[AI] §f" + playerName + " §7→ §e/" + command
                                + (result.isEmpty() ? "" : " §7(" + result + ")")), false);
                // Log to chat log so review AI can see all commands
                if (player != null) {
                    addToChatLog(player.getScoreboardName(), "/" + command, isAdminPlayer(player));
                } else {
                    addToChatLog("控制台", "/" + command, true);
                }
                future.complete(result.isEmpty() ? "指令已执行" : result);
            } catch (Exception e) {
                future.complete("执行失败: " + e.getMessage());
            }
        });
        try { return future.get(10, TimeUnit.SECONDS); }
        catch (java.util.concurrent.TimeoutException e) { return "执行超时"; }
        catch (Exception e) { return "执行异常: " + e.getMessage(); }
    }

    /** Notify online admins about a pending command requiring approval. */
    private void notifyAdminsPending(ServerPlayer requester, String command, int num) {
        var server = mod.getServer();
        if (server == null) return;
        server.execute(() -> {
            // Broadcast request to all players
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "§e[AI] §f" + requester.getScoreboardName() + " §7请求执行: §e/" + command
                    + " §7(待审批)"), false);
            // Send approve/reject instructions only to admins
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (isAdminPlayer(p)) {
                    p.sendSystemMessage(Component.literal(
                            " §a/aiaccept " + num + " §7批准  §c/aireject " + num + " §7拒绝  §7(3分钟超时自动取消)"));
                }
            }
        });
    }

    private String getServerStatus(ServerPlayer player) {
        var server = mod.getServer();
        if (server == null) return "服务器未就绪";
        var level = player != null ? (net.minecraft.server.level.ServerLevel) player.level()
                : server.overworld();

        String time = formatGameTime(level.getGameTime());

        String weather;
        if (level.isThundering()) weather = "雷暴";
        else if (level.isRaining()) weather = "下雨";
        else weather = "晴朗";

        String biome;
        try {
            var biomeOpt = level.getBiome(player != null ? player.blockPosition() : net.minecraft.core.BlockPos.ZERO).unwrapKey();
            biome = biomeOpt.isPresent()
                    ? biomeOpt.get().identifier().getPath()
                    : "未知";
        } catch (Exception e) {
            biome = "未知";
        }

        float mspt = server.getCurrentSmoothedTickTime();
        double tps = Math.min(20.0, 1000.0 / Math.max(mspt, 0.001));
        String load;
        if (tps >= 19.5) load = "流畅";
        else if (tps >= 15) load = "轻微卡顿";
        else if (tps >= 10) load = "明显卡顿";
        else load = "严重卡顿";

        return String.format("""
                服务器状态:
                时间: %s
                天气: %s
                生物群系: %s
                负载: TPS=%.1f MSPT=%.1fms (%s)
                在线: %d/%d
                """, time, weather, biome, tps, mspt, load,
                server.getPlayerCount(), server.getMaxPlayers());
    }

    private String getGameRules(ServerPlayer player) {
        var server = mod.getServer();
        if (server == null) return "服务器未就绪";
        var level = player != null ? (ServerLevel) player.level() : server.overworld();
        var rules = level.getGameRules();

        return String.format("""
                游戏规则:
                昼夜循环: %s | 天气循环: %s | 火焰伤害: %s
                生物破坏: %s | 死亡不掉落: %s | 立即重生: %s
                生物生成: %s | 怪物生成: %s | 幻翼生成: %s
                灾厄巡逻队: %s | 流浪商人: %s | 监守者生成: %s
                命令方块输出: %s | 管理员日志: %s | 反馈信息: %s
                TNT爆炸: %s | 方块掉落: %s | 生物掉落: %s
                随机刻速度: %d | 重生半径: %d | 睡觉比例: %d%%
                """,
                yn(rules.get(GameRules.ADVANCE_TIME)),
                yn(rules.get(GameRules.ADVANCE_WEATHER)),
                yn(rules.get(GameRules.FIRE_DAMAGE)),
                yn(rules.get(GameRules.MOB_GRIEFING)),
                yn(rules.get(GameRules.KEEP_INVENTORY)),
                yn(rules.get(GameRules.IMMEDIATE_RESPAWN)),
                yn(rules.get(GameRules.SPAWN_MOBS)),
                yn(rules.get(GameRules.SPAWN_MONSTERS)),
                yn(rules.get(GameRules.SPAWN_PHANTOMS)),
                yn(rules.get(GameRules.SPAWN_PATROLS)),
                yn(rules.get(GameRules.SPAWN_WANDERING_TRADERS)),
                yn(rules.get(GameRules.SPAWN_WARDENS)),
                yn(rules.get(GameRules.COMMAND_BLOCK_OUTPUT)),
                yn(rules.get(GameRules.LOG_ADMIN_COMMANDS)),
                yn(rules.get(GameRules.SEND_COMMAND_FEEDBACK)),
                yn(rules.get(GameRules.TNT_EXPLODES)),
                yn(rules.get(GameRules.BLOCK_DROPS)),
                yn(rules.get(GameRules.MOB_DROPS)),
                rules.get(GameRules.RANDOM_TICK_SPEED),
                rules.get(GameRules.RESPAWN_RADIUS),
                rules.get(GameRules.PLAYERS_SLEEPING_PERCENTAGE)
        );
    }

    private static String yn(boolean b) { return b ? "§a是" : "§c否"; }

    private String getDebugInfo(ServerPlayer player) {
        var server = mod.getServer();
        if (server == null) return "服务器未就绪";
        var level = (ServerLevel) player.level();
        var pos = player.blockPosition();

        // 光照等级
        int blockLight = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
        int skyLight = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos);
        // 考虑天空变暗因子后的实际天空光
        int skyDarken = level.getSkyDarken();
        int actualSkyLight = Math.max(0, skyLight - skyDarken);

        // 区块坐标
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        String chunkLoaded = level.isLoaded(pos) ? "是" : "否";

        // 区域难度
        float regionalDifficulty;
        try {
            regionalDifficulty = level.getCurrentDifficultyAt(pos).getEffectiveDifficulty();
        } catch (Exception e) {
            regionalDifficulty = -1;
        }

        // 注视方块和实体
        String lookingAt;
        try {
            var hit = player.pick(50.0, 0.0f, false);
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                var blockHit = (net.minecraft.world.phys.BlockHitResult) hit;
                var blockState = level.getBlockState(blockHit.getBlockPos());
                lookingAt = "方块: " + blockState.getBlock().toString()
                        + " @ " + blockHit.getBlockPos().toShortString();
            } else if (hit.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                var entityHit = (net.minecraft.world.phys.EntityHitResult) hit;
                var entity = entityHit.getEntity();
                String name = entity.getDisplayName().getString();
                String category = entity.getType().getCategory().getName();
                StringBuilder info = new StringBuilder();
                info.append("实体: ").append(name).append(" (").append(category).append(")");
                if (entity instanceof net.minecraft.world.entity.LivingEntity le) {
                    info.append(" HP:").append(String.format("%.0f/%.0f", le.getHealth(), le.getMaxHealth()));
                }
                info.append(" @ ").append(entity.blockPosition().toShortString());
                lookingAt = info.toString();
            } else {
                lookingAt = "无目标";
            }
        } catch (Exception e) {
            lookingAt = "N/A (" + e.getClass().getSimpleName() + ")";
        }

        // 注视流体
        String fluidAtPos;
        try {
            var fluid = level.getFluidState(pos);
            fluidAtPos = fluid.isEmpty() ? "无" : fluid.getType().toString();
        } catch (Exception e) {
            fluidAtPos = "N/A";
        }

        return String.format("""
                F3 调试信息:
                坐标: [%d %d %d] (区块 §e%d, %d§r)
                朝向: %s
                方块光: %d | 天空光: %d (原始%d, 暗化-%d) | 脚下流体: %s
                区域难度: %.2f | 区块已加载: %s
                注视目标: %s
                """,
                pos.getX(), pos.getY(), pos.getZ(), chunkX, chunkZ,
                facingName(player.getYRot()),
                blockLight, actualSkyLight, skyLight, skyDarken, fluidAtPos,
                regionalDifficulty, chunkLoaded,
                lookingAt
        );
    }

    private static String facingName(float yRot) {
        if (yRot >= -45 && yRot < 45) return "南 (+Z)";
        if (yRot >= 45 && yRot < 135) return "西 (-X)";
        if (yRot >= -135 && yRot < -45) return "东 (+X)";
        return "北 (-Z)";
    }

    private String buildPlayerContext(ServerPlayer player) {
        var server = mod.getServer();
        if (server == null) return "";
        var level = player.level();
        var pos = player.blockPosition();
        String playerList = server.getPlayerList().getPlayers().stream()
                .map(p -> String.format("%s (HP:%.0f %s %s)",
                        p.getScoreboardName(), p.getHealth(),
                        p.level().dimension().identifier().getPath(),
                        p.gameMode.getGameModeForPlayer().name()))
                .collect(Collectors.joining(", "));

        String advSummary = "";
        try {
            var advancements = player.getAdvancements();
            var allAdvs = server.getAdvancements().getAllAdvancements();
            int done = 0, total = 0;
            for (var adv : allAdvs) {
                if (adv.id().getNamespace().equals("minecraft")
                        && adv.id().getPath().startsWith("story/")) {
                    total++;
                    if (advancements.getOrStartProgress(adv).isDone()) done++;
                }
            }
            advSummary = String.format(" | 进度: %d/%d (故事模式)", done, total);
        } catch (Exception ignored) {}

        float yRot = player.getYRot();
        String facing;
        if (yRot >= -45 && yRot < 45) facing = "南";
        else if (yRot >= 45 && yRot < 135) facing = "西";
        else if (yRot >= -135 && yRot < -45) facing = "东";
        else facing = "北";

        String gameTimeStr = formatGameTime(level.getGameTime());

        // Behavior score context
        String behaviorInfo = "";
        try {
            boolean isAdmin = server != null
                    && server.getPlayerList().isOp(new NameAndId(player.getGameProfile()));
            if (!isAdmin) {
                int score = mod.getBehaviorTracker().getScore(player.getUUID());
                behaviorInfo = " | 行为分: " + score;
            }
        } catch (Exception ignored) {}

        return String.format("""
                版本: %s | 在线(%d/%d): [%s] | %s | 难度: %s
                说话者: %s | 坐标: [%d %d %d] | 朝向: %s | 维度: %s | HP: %.1f | 饱食度: %d | 模式: %s | 等级: %d%s%s
                """, server.getServerModName(), server.getPlayerCount(), server.getMaxPlayers(),
                playerList, gameTimeStr, level.getDifficulty().getDisplayName().getString(),
                player.getScoreboardName(), pos.getX(), pos.getY(), pos.getZ(), facing,
                level.dimension().identifier(), player.getHealth(),
                player.getFoodData().getFoodLevel(), player.gameMode.getGameModeForPlayer().name(),
                player.experienceLevel, advSummary, behaviorInfo);
    }

    /**将 tick 数转为人类可读的游戏时间。0 tick = 第1天 6:00 AM */
    private static String formatGameTime(long ticks) {
        long day = ticks / 24000 + 1;
        long dayTicks = ticks % 24000;
        long adjusted = (dayTicks + 6000) % 24000;
        int hour = (int) (adjusted / 1000);
        int minute = (int) ((adjusted % 1000) * 60 / 1000);

        String period;
        int displayHour;
        if (hour == 0) { displayHour = 12; period = "AM"; }
        else if (hour < 12) { displayHour = hour; period = "AM"; }
        else if (hour == 12) { displayHour = 12; period = "PM"; }
        else { displayHour = hour - 12; period = "PM"; }

        return String.format("第%d天 %d:%02d %s (tick=%d)", day, displayHour, minute, period, ticks);
    }

    private boolean handleResponse(ServerPlayer player, String response) {
        response = response.trim();
        var server = mod.getServer();
        String pname = player.getScoreboardName();
        if (!response.startsWith("/") || !mod.getConfig().isEnableCommandExecution()) {
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§b[AI] → §f" + pname + "§b " + response), false);
            }
            addToChatLog("AI → " + pname, response);
            return true;
        }
        String cmd = response.lines().findFirst().orElse("").substring(1).trim();
        if (cmd.isEmpty()) {
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("§b[AI] → §f" + pname + "§b " + response), false);
            }
            return true;
        }
        if (needsApproval(cmd)) {
            int num = addPendingCommand(player.getUUID(), cmd);
            player.sendSystemMessage(Component.literal("§e[AI] 需要审批 §6[#" + num + "] §e: /" + cmd
                    + "\n§e使用 §a/aiaccept " + num + " §e批准或 §c/aireject " + num + " §e拒绝"));
            return false;
        }
        if (server != null) {
            try {
                server.getCommands().getDispatcher().execute(cmd, server.createCommandSourceStack());
                addToChatLog(player.getScoreboardName(), "/" + cmd, isAdminPlayer(player));
            } catch (CommandSyntaxException e) {
                player.sendSystemMessage(Component.literal("§c[AI] 指令语法错误: " + e.getMessage()));
                return false;
            }
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("§7[AI] → §e/" + cmd), false);
        }
        return true;
    }

    private int addPendingCommand(UUID pid, String cmd) {
        List<String> list = pendingCommands.computeIfAbsent(pid, k -> new CopyOnWriteArrayList<>());
        list.add(cmd);
        return list.size();
    }

    private boolean needsApproval(String cmd) {
        String root = cmd.split("\\s+")[0].toLowerCase();
        if (mod.getConfig().getRequireApprovalCommands().contains(root)) return true;
        // 严格模式：仅白名单内的绝对安全命令可免审批
        if (mod.getConfig().isStrictMode()) {
            for (String safe : mod.getConfig().getSafeCommands()) {
                if (safe.contains(" ")) {
                    // 多词模式（如 "data get"）匹配命令前缀
                    if (cmd.toLowerCase().startsWith(safe)) return false;
                } else {
                    // 单词模式匹配根命令名
                    if (root.equals(safe)) return false;
                }
            }
            return true;
        }
        return false;
    }

    /** Check if source is console (no player) or an OP player. */
    private static boolean isAdminOrConsole(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return true; // console
        var srv = src.getServer();
        return srv != null && srv.getPlayerList().isOp(new NameAndId(p.getGameProfile()));
    }

    /** Check if a ServerPlayer is admin (OP). */
    private boolean isAdminPlayer(ServerPlayer player) {
        var server = mod.getServer();
        return server != null && server.getPlayerList().isOp(new NameAndId(player.getGameProfile()));
    }

    /** Handle AI query from console (no player context). */
    private void handleConsoleAIQuery(CommandSourceStack src, String query) {
        MCAIMod.LOGGER.info("Console AI query: {}", query);

        aiExecutor.execute(() -> {
            try {
                var server = mod.getServer();
                String playerList = "";
                if (server != null) {
                    playerList = server.getPlayerList().getPlayers().stream()
                            .map(p -> p.getScoreboardName())
                            .collect(Collectors.joining(", "));
                }
                String context = String.format("""
                        版本: %s | 在线(%d/%d): [%s]
                        说话者: 控制台
                        """, server != null ? server.getServerModName() : "?",
                        server != null ? server.getPlayerCount() : 0,
                        server != null ? server.getMaxPlayers() : 0,
                        playerList.isEmpty() ? "无" : playerList);
                String userContent = context + "\n\n控制台 说: " + query;

                List<OpenAIClient.ChatMessage> messages = new ArrayList<>();
                messages.add(new OpenAIClient.ChatMessage("system", mod.getConfig().getSystemPrompt()));

                String recentChat;
                synchronized (chatLog) {
                    recentChat = String.join("\n", chatLog);
                }
                if (!recentChat.isEmpty()) {
                    messages.add(new OpenAIClient.ChatMessage("system",
                            "最近的聊天记录（了解当前氛围）:\n" + recentChat));
                }
                // Inject recent penalty records for AI awareness
                String penaltySummary = mod.getChatReviewSystem() != null
                        ? mod.getChatReviewSystem().getRecentPenaltySummary() : "";
                if (!penaltySummary.isEmpty()) {
                    messages.add(new OpenAIClient.ChatMessage("system", penaltySummary));
                }
                messages.add(new OpenAIClient.ChatMessage("user", userContent));

                var result = mod.getAiClient().chat(messages, toolCalls -> {
                    List<String> results = new ArrayList<>();
                    for (var tc : toolCalls) {
                        if ("search_knowledge_base".equals(tc.name)) {
                            results.add(mod.getKnowledgeBase().search(
                                    parseArg(tc.arguments, "query"), 5));
                        } else if ("read_knowledge_base".equals(tc.name)) {
                            results.add(mod.getKnowledgeBase().read(
                                    parseArg(tc.arguments, "title")));
                        } else if ("execute_minecraft_command".equals(tc.name)) {
                            results.add(executeCommand(parseArg(tc.arguments, "command"), null));
                        } else if ("get_server_status".equals(tc.name)) {
                            results.add(getServerStatus(null));
                        } else if ("get_game_rules".equals(tc.name)) {
                            results.add(getServerStatus(null)); // getGameRules needs player
                        } else if ("get_debug_info".equals(tc.name)) {
                            results.add("控制台无法获取调试信息");
                        } else {
                            results.add("未知工具: " + tc.name);
                        }
                    }
                    return results;
                });

                String reply = result.orElse("AI 无响应");
                // Send reply directly to console source
                src.sendSuccess(() -> Component.literal("§e[AI回复]\n§f" + reply), false);
                // Log AI reply to chat log for review
                addToChatLog("AI → 控制台", reply);
                MCAIMod.LOGGER.info("Console AI reply: {}", reply);
            } catch (Exception e) {
                MCAIMod.LOGGER.error("Console AI query failed", e);
                src.sendFailure(Component.literal("§cAI 查询失败: " + e.getMessage()));
            }
        });
    }
}
