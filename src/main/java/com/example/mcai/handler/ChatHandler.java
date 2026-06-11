package com.example.mcai.handler;

import com.example.mcai.MCAIMod;
import com.example.mcai.api.OpenAIClient;
import com.example.mcai.kb.KnowledgeBase;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.server.permissions.LevelBasedPermissionSet;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

    public ChatHandler(MCAIMod mod) {
        this.mod = mod;
    }

    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createAICommand() {
        return Commands.literal("ai")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            if (player == null) return 0;
                            String msg = StringArgumentType.getString(ctx, "message");

                            var server = mod.getServer();
                            if (server != null) {
                                server.getPlayerList().broadcastSystemMessage(
                                        Component.literal("§7[§f" + player.getScoreboardName()
                                                + "§7]对AI说：§f" + msg), false);
                            }

                            addToChatLog(player.getScoreboardName(), msg);
                            handleAIQuery(player, msg);
                            return Command.SINGLE_SUCCESS;
                        }))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("用法: /ai <消息>"));
                    return 0;
                });
    }

    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createWikiCommand() {
        return Commands.literal("aikb")
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
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
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
                    return 1;
                });
    }

    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createAcceptCommand() {
        return Commands.literal("aiaccept")
                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                        .executes(ctx -> approveCommand(ctx.getSource().getPlayer(),
                                IntegerArgumentType.getInteger(ctx, "number"), ctx.getSource())))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("用法: /aiaccept <编号>"));
                    return 0;
                });
    }

    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createRejectCommand() {
        return Commands.literal("aireject")
                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                        .executes(ctx -> rejectCommand(ctx.getSource().getPlayer(),
                                IntegerArgumentType.getInteger(ctx, "number"), ctx.getSource())))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("用法: /aireject <编号>"));
                    return 0;
                });
    }

    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createClearCommand() {
        return Commands.literal("aiclear")
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

    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createMemoryCommand() {
        return Commands.literal("aimemory")
                .executes(ctx -> {
                    String mem = mod.getMemory().getAll();
                    if (mem.isEmpty()) {
                        ctx.getSource().sendSuccess(
                                () -> Component.literal("§7[AI] 暂无记忆"), false);
                    } else {
                        ctx.getSource().sendSuccess(
                                () -> Component.literal("§e==== AI 持久记忆 (" + mod.getMemory().size() + " 条) ===="), false);
                        for (String line : mem.split("\n")) {
                            String display = line;
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("§7" + display), false);
                        }
                    }
                    return 1;
                });
    }

    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createReloadCommand() {
        return Commands.literal("aireload")
                .executes(ctx -> {
                    mod.reloadConfig();
                    history.clear();
                    pendingCommands.clear();
                    synchronized (chatLog) { chatLog.clear(); }
                    ctx.getSource().sendSuccess(
                            () -> Component.literal("§a[AI] 已重载，所有状态已清空"), true);
                    return 1;
                });
    }

    private void addToChatLog(String name, String message) {
        synchronized (chatLog) {
            chatLog.add(name + ": " + message);
            while (chatLog.size() > 50) chatLog.removeFirst();
        }
    }

    public void registerChatInterceptor() {
        if (!mod.getConfig().isEnableChatInterception()) return;
        try {
            ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(this::onChatMessage);
            ServerMessageEvents.GAME_MESSAGE.register((srv, msg, overlay) ->
                    addToChatLog("系统", msg.getString()));
            MCAIMod.LOGGER.info("Chat interception enabled");
        } catch (NoClassDefFoundError | Exception e) {
            MCAIMod.LOGGER.warn("Chat interception unavailable");
        }
    }

    public void onPlayerDisconnect(ServerPlayer player) {
        UUID id = player.getUUID();
        history.remove(id);
        pendingCommands.remove(id);
    }

    private boolean onChatMessage(PlayerChatMessage message, ServerPlayer sender,
                                   ChatType.Bound params) {
        if (sender == null) return true;
        String text = message.signedContent();
        String prefix = mod.getConfig().getTriggerPrefix();

        addToChatLog(sender.getScoreboardName(), text);

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
    }

    private int approveCommand(ServerPlayer player, int num, CommandSourceStack src) {
        List<String> cmds = pendingCommands.get(player.getUUID());
        int idx = num - 1;
        if (cmds == null || idx < 0 || idx >= cmds.size()) {
            src.sendFailure(Component.literal("§c编号无效，使用 /aiquery 查看"));
            return 0;
        }
        String cmd = cmds.remove(idx);
        if (cmds.isEmpty()) pendingCommands.remove(player.getUUID());
        var server = mod.getServer();
        if (server != null) {
            try {
                server.getCommands().getDispatcher().execute(cmd, server.createCommandSourceStack());
            } catch (CommandSyntaxException e) {
                src.sendFailure(Component.literal("§c指令语法错误: " + e.getMessage()));
                return 0;
            }
        }
        src.sendSuccess(() -> Component.literal("§a[AI] 已批准 #" + num + " 并执行: /" + cmd), true);
        return 1;
    }

    private int rejectCommand(ServerPlayer player, int num, CommandSourceStack src) {
        List<String> cmds = pendingCommands.get(player.getUUID());
        int idx = num - 1;
        if (cmds == null || idx < 0 || idx >= cmds.size()) {
            src.sendFailure(Component.literal("§c编号无效，使用 /aiquery 查看"));
            return 0;
        }
        String cmd = cmds.remove(idx);
        if (cmds.isEmpty()) pendingCommands.remove(player.getUUID());
        src.sendSuccess(() -> Component.literal("§c[AI] 已拒绝 #" + num + ": /" + cmd), true);
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

                String memoryContent = mod.getMemory().getAll();
                if (!memoryContent.isEmpty()) {
                    messages.add(new OpenAIClient.ChatMessage("system",
                            "以下是之前记住的信息。你可以用 remember 工具追加新记忆，用 recall 重新读取：\n" + memoryContent));
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
                        } else if ("remember".equals(tc.name)) {
                            String content = parseArg(tc.arguments, "content");
                            mod.getMemory().addEntry(content);
                            results.add("已记住: " + content);
                        } else if ("recall".equals(tc.name)) {
                            String mem = mod.getMemory().getAll();
                            results.add(mem.isEmpty() ? "暂无记忆" : mem);
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

    private String executeCommand(String command, ServerPlayer player) {
        var server = mod.getServer();
        if (server == null) return "服务器未就绪";
        if (needsApproval(command)) {
            int num = addPendingCommand(player.getUUID(), command);
            return "[需要审批] 已加入审批队列 #" + num + "，管理员可使用 /aiaccept " + num + " 批准";
        }
        CompletableFuture<String> future = new CompletableFuture<>();
        String playerName = player.getScoreboardName();
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
                future.complete(result.isEmpty() ? "指令已执行" : result);
            } catch (Exception e) {
                future.complete("执行失败: " + e.getMessage());
            }
        });
        try { return future.get(10, TimeUnit.SECONDS); }
        catch (java.util.concurrent.TimeoutException e) { return "执行超时"; }
        catch (Exception e) { return "执行异常: " + e.getMessage(); }
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

        return String.format("""
                版本: %s | 在线(%d/%d): [%s] | %s | 难度: %s
                说话者: %s | 坐标: [%d %d %d] | 朝向: %s | 维度: %s | HP: %.1f | 饱食度: %d | 模式: %s | 等级: %d%s
                """, server.getServerModName(), server.getPlayerCount(), server.getMaxPlayers(),
                playerList, gameTimeStr, level.getDifficulty().getDisplayName().getString(),
                player.getScoreboardName(), pos.getX(), pos.getY(), pos.getZ(), facing,
                level.dimension().identifier(), player.getHealth(),
                player.getFoodData().getFoodLevel(), player.gameMode.getGameModeForPlayer().name(),
                player.experienceLevel, advSummary);
    }

    /** 将 tick 数转为人类可读的游戏时间。0 tick = 第1天 6:00 AM */
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
        if (!response.startsWith("/") || !mod.getConfig().isEnableCommandExecution()) {
            player.sendSystemMessage(Component.literal("§b[AI] " + response));
            addToChatLog("AI", response);
            return true;
        }
        String cmd = response.lines().findFirst().orElse("").substring(1).trim();
        if (cmd.isEmpty()) {
            player.sendSystemMessage(Component.literal("§b[AI] " + response));
            return true;
        }
        if (needsApproval(cmd)) {
            int num = addPendingCommand(player.getUUID(), cmd);
            player.sendSystemMessage(Component.literal("§e[AI] 需要审批 §6[#" + num + "] §e: /" + cmd
                    + "\n§e使用 §a/aiaccept " + num + " §e批准或 §c/aireject " + num + " §e拒绝"));
            return false;
        }
        var server = mod.getServer();
        if (server != null) {
            try {
                server.getCommands().getDispatcher().execute(cmd, server.createCommandSourceStack());
            } catch (CommandSyntaxException e) {
                player.sendSystemMessage(Component.literal("§c[AI] 指令语法错误: " + e.getMessage()));
                return false;
            }
            player.sendSystemMessage(Component.literal("§7[AI] 已执行: /" + cmd));
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
        if (mod.getConfig().isStrictMode() && mod.getConfig().getStrictCommands().contains(root)) return true;
        return false;
    }
}
