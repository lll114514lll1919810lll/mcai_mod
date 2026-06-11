package com.example.mcai.handler;

import com.example.mcai.MCAIMod;
import com.example.mcai.api.OpenAIClient;
import com.example.mcai.kb.KnowledgeBase;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import net.minecraft.command.permission.LeveledPermissionPredicate;

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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ChatHandler {
    private final MCAIMod mod;
    private final ExecutorService aiExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "MCAI-Worker");
        t.setDaemon(true);
        return t;
    });
    private final Map<UUID, LinkedList<OpenAIClient.ChatMessage>> history = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> pendingCommands = new ConcurrentHashMap<>();
    private final LinkedList<String> chatLog = new LinkedList<>();

    public ChatHandler(MCAIMod mod) {
        this.mod = mod;
    }

    public LiteralArgumentBuilder<ServerCommandSource> createAICommand() {
        return CommandManager.literal("ai")
                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) return 0;
                            String msg = StringArgumentType.getString(ctx, "message");

                            // Broadcast to all players
                            var server = mod.getServer();
                            if (server != null) {
                                server.getPlayerManager().broadcast(
                                        Text.literal("§7[§f" + player.getNameForScoreboard()
                                                + "§7]对AI说：§f" + msg), false);
                            }

                            addToChatLog(player.getNameForScoreboard(), msg);
                            ctx.getSource().sendFeedback(
                                    () -> Text.literal("§7[AI] 思考中..."), false);
                            handleAIQuery(player, msg);
                            return Command.SINGLE_SUCCESS;
                        }))
                .executes(ctx -> {
                    ctx.getSource().sendError(Text.literal("用法: /ai <消息>"));
                    return 0;
                });
    }

    public LiteralArgumentBuilder<ServerCommandSource> createWikiCommand() {
        return CommandManager.literal("aikb")
                .then(CommandManager.argument("query", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String query = StringArgumentType.getString(ctx, "query");
                            String result = mod.getKnowledgeBase().search(query, 5);
                            ctx.getSource().sendFeedback(
                                    () -> Text.literal("§a=== 知识库结果 ===\n§7" + result), false);
                            return 1;
                        }))
                .executes(ctx -> {
                    ctx.getSource().sendError(Text.literal("用法: /aikb <关键词>"));
                    return 0;
                });
    }

    public LiteralArgumentBuilder<ServerCommandSource> createQueryCommand() {
        return CommandManager.literal("aiquery")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    List<String> cmds = pendingCommands.get(player.getUuid());
                    if (cmds == null || cmds.isEmpty()) {
                        ctx.getSource().sendFeedback(
                                () -> Text.literal("§7[AI] 暂无待审批指令"), false);
                        return 0;
                    }
                    ctx.getSource().sendFeedback(
                            () -> Text.literal("§e==== 待审批指令 (" + cmds.size() + ") ===="), false);
                    for (int i = 0; i < cmds.size(); i++) {
                        final int num = i + 1;
                        final String cmd = cmds.get(i);
                        ctx.getSource().sendFeedback(
                                () -> Text.literal("§6  [" + num + "] /" + cmd), false);
                    }
                    ctx.getSource().sendFeedback(
                            () -> Text.literal("§e使用 §a/aiaccept <编号> 批准，§c/aireject <编号> 拒绝"), false);
                    return 1;
                });
    }

    public LiteralArgumentBuilder<ServerCommandSource> createAcceptCommand() {
        return CommandManager.literal("aiaccept")
                .then(CommandManager.argument("number", IntegerArgumentType.integer(1))
                        .executes(ctx -> approveCommand(ctx.getSource().getPlayer(),
                                IntegerArgumentType.getInteger(ctx, "number"), ctx.getSource())))
                .executes(ctx -> {
                    ctx.getSource().sendError(Text.literal("用法: /aiaccept <编号>"));
                    return 0;
                });
    }

    public LiteralArgumentBuilder<ServerCommandSource> createRejectCommand() {
        return CommandManager.literal("aireject")
                .then(CommandManager.argument("number", IntegerArgumentType.integer(1))
                        .executes(ctx -> rejectCommand(ctx.getSource().getPlayer(),
                                IntegerArgumentType.getInteger(ctx, "number"), ctx.getSource())))
                .executes(ctx -> {
                    ctx.getSource().sendError(Text.literal("用法: /aireject <编号>"));
                    return 0;
                });
    }

    public LiteralArgumentBuilder<ServerCommandSource> createClearCommand() {
        return CommandManager.literal("aiclear")
                .executes(ctx -> {
                    ServerPlayerEntity player = ctx.getSource().getPlayer();
                    if (player == null) return 0;
                    history.remove(player.getUuid());
                    pendingCommands.remove(player.getUuid());
                    ctx.getSource().sendFeedback(
                            () -> Text.literal("§a[AI] 已清除对话历史和待审批指令"), false);
                    return 1;
                });
    }

    public LiteralArgumentBuilder<ServerCommandSource> createMemoryCommand() {
        return CommandManager.literal("aimemory")
                .executes(ctx -> {
                    String mem = mod.getMemory().getAll();
                    if (mem.isEmpty()) {
                        ctx.getSource().sendFeedback(
                                () -> Text.literal("§7[AI] 暂无记忆"), false);
                    } else {
                        ctx.getSource().sendFeedback(
                                () -> Text.literal("§e==== AI 持久记忆 (" + mod.getMemory().size() + " 条) ===="), false);
                        for (String line : mem.split("\n")) {
                            String display = line;
                            ctx.getSource().sendFeedback(
                                    () -> Text.literal("§7" + display), false);
                        }
                    }
                    return 1;
                });
    }

    public LiteralArgumentBuilder<ServerCommandSource> createReloadCommand() {
        return CommandManager.literal("aireload")
                .executes(ctx -> {
                    mod.reloadConfig();
                    history.clear();
                    pendingCommands.clear();
                    synchronized (chatLog) { chatLog.clear(); }
                    ctx.getSource().sendFeedback(
                            () -> Text.literal("§a[AI] 已重载，所有状态已清空"), true);
                    return 1;
                });
    }

    private void addToChatLog(String name, String message) {
        synchronized (chatLog) {
            chatLog.add(name + ": " + message);
            // Keep last 50 messages max
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

    public void onPlayerDisconnect(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        history.remove(id);
        pendingCommands.remove(id);
    }

    private boolean onChatMessage(SignedMessage message, ServerPlayerEntity sender,
                                   MessageType.Parameters params) {
        if (sender == null) return true;
        String text = message.getContent().getString();
        String prefix = mod.getConfig().getTriggerPrefix();

        // Log ALL chat messages
        addToChatLog(sender.getNameForScoreboard(), text);

        if (text.startsWith(prefix)) {
            String query = text.substring(prefix.length()).trim();
            if (!query.isEmpty()) {
                // Broadcast the AI query
                var server = mod.getServer();
                if (server != null) {
                    server.getPlayerManager().broadcast(
                            Text.literal("§7[§f" + sender.getNameForScoreboard()
                                    + "§7]对AI说：§f" + query), false);
                }
                handleAIQuery(sender, query);
            }
            return false;
        }
        return true;
    }

    private int approveCommand(ServerPlayerEntity player, int num, ServerCommandSource src) {
        List<String> cmds = pendingCommands.get(player.getUuid());
        int idx = num - 1;
        if (cmds == null || idx < 0 || idx >= cmds.size()) {
            src.sendError(Text.literal("§c编号无效，使用 /aiquery 查看"));
            return 0;
        }
        String cmd = cmds.remove(idx);
        if (cmds.isEmpty()) pendingCommands.remove(player.getUuid());
        var server = mod.getServer();
        if (server != null) {
            try {
                server.getCommandManager().getDispatcher().execute(cmd, server.getCommandSource());
            } catch (CommandSyntaxException e) {
                src.sendError(Text.literal("§c指令语法错误: " + e.getMessage()));
                return 0;
            }
        }
        src.sendFeedback(() -> Text.literal("§a[AI] 已批准 #" + num + " 并执行: /" + cmd), true);
        return 1;
    }

    private int rejectCommand(ServerPlayerEntity player, int num, ServerCommandSource src) {
        List<String> cmds = pendingCommands.get(player.getUuid());
        int idx = num - 1;
        if (cmds == null || idx < 0 || idx >= cmds.size()) {
            src.sendError(Text.literal("§c编号无效，使用 /aiquery 查看"));
            return 0;
        }
        String cmd = cmds.remove(idx);
        if (cmds.isEmpty()) pendingCommands.remove(player.getUuid());
        src.sendFeedback(() -> Text.literal("§c[AI] 已拒绝 #" + num + ": /" + cmd), true);
        return 1;
    }

    private void handleAIQuery(ServerPlayerEntity player, String query) {
        var playerHistory = history.computeIfAbsent(player.getUuid(), k -> new LinkedList<>());
        int maxCtx = mod.getConfig().getContextMaxChars();
        final UUID pid = player.getUuid();
        final String pname = player.getNameForScoreboard();
        MCAIMod.LOGGER.info("AI query from {}: {}", pname, query);
        aiExecutor.execute(() -> {
            try {
                String context = buildPlayerContext(player);
                String userContent = context + "\n\n" + pname + " 说: " + query;

                List<OpenAIClient.ChatMessage> messages = new ArrayList<>();
                messages.add(new OpenAIClient.ChatMessage("system", mod.getConfig().getSystemPrompt()));

                // Include recent chat log
                String recentChat;
                synchronized (chatLog) {
                    recentChat = String.join("\n", chatLog);
                }
                if (!recentChat.isEmpty()) {
                    messages.add(new OpenAIClient.ChatMessage("system",
                            "最近的聊天记录（了解当前氛围）:\n" + recentChat));
                }

                // Include persistent memory
                String memoryContent = mod.getMemory().getAll();
                if (!memoryContent.isEmpty()) {
                    messages.add(new OpenAIClient.ChatMessage("system",
                            "以下是之前记住的信息。你可以用 remember 工具追加新记忆，用 recall 重新读取：\n" + memoryContent));
                }

                // Include player history (char-capped)
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
                        if (response.startsWith("[API错误]") || response.startsWith("[API连接失败]")
                                || response.startsWith("[API异常]") || response.startsWith("[响应解析失败]")
                                || response.startsWith("[工具调用超限]") || response.startsWith("[工具调用异常]")) {
                            player.sendMessage(Text.literal("§c" + response));
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
                    server.execute(() -> player.sendMessage(
                            Text.literal("§c[AI] 无响应，请检查控制台日志")));
                }
            } catch (Exception e) {
                MCAIMod.LOGGER.error("AI query failed", e);
                var server = mod.getServer();
                if (server != null) {
                    server.execute(() -> player.sendMessage(
                            Text.literal("§c[AI] 异常: " + e.getMessage())));
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

    private String executeCommand(String command, ServerPlayerEntity player) {
        var server = mod.getServer();
        if (server == null) return "服务器未就绪";
        if (needsApproval(command)) {
            int num = addPendingCommand(player.getUuid(), command);
            return "[需要审批] 已加入审批队列 #" + num + "，管理员可使用 /aiaccept " + num + " 批准";
        }
        CompletableFuture<String> future = new CompletableFuture<>();
        String playerName = player.getNameForScoreboard();
        server.execute(() -> {
            try {
                StringBuilder out = new StringBuilder();
                var cs = server.getCommandSource();
                var src = new net.minecraft.server.command.ServerCommandSource(
                        new net.minecraft.server.command.CommandOutput() {
                            public void sendMessage(net.minecraft.text.Text msg) {
                                out.append(msg.getString()).append("\n");
                            }
                            public boolean shouldReceiveFeedback() { return true; }
                            public boolean shouldTrackOutput() { return true; }
                            public boolean shouldBroadcastConsoleToOps() { return false; }
                        },
                        cs.getPosition(), cs.getRotation(), cs.getWorld(),
                        LeveledPermissionPredicate.OWNERS, cs.getName(), cs.getDisplayName(),
                        server, cs.getEntity());
                server.getCommandManager().getDispatcher().execute(command, src);
                String result = out.toString().trim();
                // Broadcast command execution to all players
                server.getPlayerManager().broadcast(
                        Text.literal("§7[AI] §f" + playerName + " §7→ §e/" + command
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

    private String buildPlayerContext(ServerPlayerEntity player) {
        var server = mod.getServer();
        if (server == null) return "";
        var world = player.getEntityWorld();
        var pos = player.getBlockPos();
        String playerList = server.getPlayerManager().getPlayerList().stream()
                .map(p -> String.format("%s (HP:%.0f %s %s)",
                        p.getNameForScoreboard(), p.getHealth(),
                        p.getEntityWorld().getRegistryKey().getValue().getPath(),
                        p.interactionManager.getGameMode().asString()))
                .collect(Collectors.joining(", "));

        // Advancement summary
        String advSummary = "";
        try {
            var tracker = player.getAdvancementTracker();
            var allAdvs = server.getAdvancementLoader().getAdvancements();
            int done = 0, total = 0;
            for (var adv : allAdvs) {
                if (adv.id().getNamespace().equals("minecraft")
                        && adv.id().getPath().startsWith("story/")) {
                    total++;
                    if (tracker.getProgress(adv).isDone()) done++;
                }
            }
            advSummary = String.format(" | 进度: %d/%d (故事模式)", done, total);
        } catch (Exception ignored) {}

        float yaw = player.getYaw();
        String facing;
        if (yaw >= -45 && yaw < 45) facing = "南";
        else if (yaw >= 45 && yaw < 135) facing = "西";
        else if (yaw >= -135 && yaw < -45) facing = "东";
        else facing = "北";

        return String.format("""
                版本: %s | 在线(%d/%d): [%s] | 时间: %d | 难度: %s
                说话者: %s | 坐标: [%d %d %d] | 朝向: %s | 维度: %s | HP: %.1f | 饱食度: %d | 模式: %s | 等级: %d%s
                """, server.getVersion(), server.getCurrentPlayerCount(), server.getMaxPlayerCount(),
                playerList, world.getTimeOfDay(), world.getDifficulty().getName(),
                player.getNameForScoreboard(), pos.getX(), pos.getY(), pos.getZ(), facing,
                world.getRegistryKey().getValue(), player.getHealth(),
                player.getHungerManager().getFoodLevel(), player.interactionManager.getGameMode().asString(),
                player.experienceLevel, advSummary);
    }

    private boolean handleResponse(ServerPlayerEntity player, String response) {
        response = response.trim();
        if (!response.startsWith("/") || !mod.getConfig().isEnableCommandExecution()) {
            player.sendMessage(Text.literal("§b[AI] " + response));
            addToChatLog("AI", response);
            return true;
        }
        String cmd = response.lines().findFirst().orElse("").substring(1).trim();
        if (cmd.isEmpty()) {
            player.sendMessage(Text.literal("§b[AI] " + response));
            return true;
        }
        if (needsApproval(cmd)) {
            int num = addPendingCommand(player.getUuid(), cmd);
            player.sendMessage(Text.literal("§e[AI] 需要审批 §6[#" + num + "] §e: /" + cmd
                    + "\n§e使用 §a/aiaccept " + num + " §e批准或 §c/aireject " + num + " §e拒绝"));
            return false;
        }
        var server = mod.getServer();
        if (server != null) {
            try {
                server.getCommandManager().getDispatcher().execute(cmd, server.getCommandSource());
            } catch (CommandSyntaxException e) {
                player.sendMessage(Text.literal("§c[AI] 指令语法错误: " + e.getMessage()));
                return false;
            }
            player.sendMessage(Text.literal("§7[AI] 已执行: /" + cmd));
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
