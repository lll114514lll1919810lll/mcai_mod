package com.example.mcai.handler;

import com.example.mcai.MCAIMod;
import com.example.mcai.api.OpenAIClient;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChatHandler {
    private final MCAIMod mod;
    private final ChatLog chatLog;
    private final ThinkingAnimation animation;
    private final PlayerContextBuilder contextBuilder;
    private final CommandExecutionService cmdExec;
    private final ToolDispatcher toolDispatcher;

    private final ExecutorService aiExecutor = new ThreadPoolExecutor(4, 8, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(32),
            r -> { Thread t = new Thread(r, "MCAI-Worker"); t.setDaemon(true); return t; },
            (r, executor) -> MCAIMod.LOGGER.warn("AI executor queue full, task rejected"));
    private final Map<UUID, LinkedList<OpenAIClient.ChatMessage>> history = new ConcurrentHashMap<>();

    public ChatHandler(MCAIMod mod, ChatLog chatLog, ThinkingAnimation animation,
                       PlayerContextBuilder contextBuilder, CommandExecutionService cmdExec,
                       ToolDispatcher toolDispatcher) {
        this.mod = mod; this.chatLog = chatLog; this.animation = animation;
        this.contextBuilder = contextBuilder; this.cmdExec = cmdExec; this.toolDispatcher = toolDispatcher;
    }

    public ChatLog getChatLog() { return chatLog; }
    public MinecraftServer getServer() { return mod.getServer(); }
    public MCAIMod getMod() { return mod; }
    public com.example.mcai.behavior.PlayerBehaviorTracker getBehaviorTracker() { return mod.getBehaviorTracker(); }
    public com.example.mcai.config.ModConfig getConfig() { return mod.getConfig(); }

    public void registerChatInterceptor() {
        if (!mod.getConfig().isEnableChatInterception()) return;
        try {
            ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
                if (sender != null) { String text = message.decoratedContent() != null ? message.decoratedContent().getString() : ""; chatLog.add(sender.getScoreboardName(), text); }
            });
            ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
                if (sender == null) return true;
                String text = message.decoratedContent() != null ? message.decoratedContent().getString() : (message.signedContent() != null ? message.signedContent() : "");
                String prefix = mod.getConfig().getTriggerPrefix();
                if (text.startsWith(prefix)) { String query = text.substring(prefix.length()).trim(); if (!query.isEmpty()) { var server = mod.getServer(); if (server != null) server.getPlayerList().broadcastSystemMessage(Component.translatable("mcai.cmd.ai.broadcast", sender.getScoreboardName(), query), false); handleAIQuery(sender, query); } return false; }
                return true;
            });
            ServerMessageEvents.GAME_MESSAGE.register((srv, msg, overlay) -> {
                try { String text = msg.getString(); var matcher = Pattern.compile("^\\[(.+?)\\] (.+)$").matcher(text); if (matcher.matches()) { String pn = matcher.group(1); String ct = matcher.group(2); ServerPlayer player = srv.getPlayerList().getPlayer(pn); if (player != null) { chatLog.add(pn + "(命令)", ct); return; } } chatLog.add("系统", text); } catch (Exception e) { MCAIMod.LOGGER.warn("GAME_MESSAGE log failed: {}", e.getMessage()); chatLog.add("系统", "<消息记录失败>"); }
            });
            MCAIMod.LOGGER.info("Chat interception enabled");
        } catch (NoClassDefFoundError | Exception e) { MCAIMod.LOGGER.warn("Chat interception unavailable"); }
    }

    public void onPlayerDisconnect(ServerPlayer player) { UUID id = player.getUUID(); history.remove(id); cmdExec.cleanupPlayer(id); }

    public void handleAIQuery(ServerPlayer player, String query) {
        var playerHistory = history.computeIfAbsent(player.getUUID(), k -> new LinkedList<>());
        int maxCtx = mod.getConfig().getContextMaxChars(); final UUID pid = player.getUUID();
        final String pname = player.getScoreboardName(); MCAIMod.LOGGER.info("AI query from {}: {}", pname, query);
        MinecraftServer server = mod.getServer(); if (server == null) return;
        animation.start(player, server);
        aiExecutor.execute(() -> {
            try {
                String context = contextBuilder.build(player, mod.getServer());
                String userContent = context + "\n\n" + pname + " 说: " + query;
                List<OpenAIClient.ChatMessage> messages = new ArrayList<>();
                messages.add(new OpenAIClient.ChatMessage("system", mod.getConfig().getSystemPrompt()));
                String recentChat = chatLog.peek();
                if (!recentChat.isEmpty()) messages.add(new OpenAIClient.ChatMessage("system", "最近的聊天记录（了解当前氛围）:\n" + recentChat));
                String penaltySummary = mod.getChatReviewSystem() != null ? mod.getChatReviewSystem().getPenaltyHistory().getSummary() : "";
                if (!penaltySummary.isEmpty()) messages.add(new OpenAIClient.ChatMessage("system", penaltySummary));
                synchronized (playerHistory) { int totalChars = 0; for (var msg : playerHistory) { int c = msg.content != null ? msg.content.length() : 0; if (totalChars + c > maxCtx) break; totalChars += c; messages.add(msg); } }
                messages.add(new OpenAIClient.ChatMessage("user", userContent));
                var result = mod.getAiClient().chat(messages, toolCalls -> toolDispatcher.dispatch(toolCalls, player));
                MinecraftServer server2 = mod.getServer(); if (server2 == null) return;
                if (result.success()) {
                    String response = result.value();
                    server2.execute(() -> { animation.done(player); handleResponse(player, response); synchronized (playerHistory) { playerHistory.add(new OpenAIClient.ChatMessage("user", userContent)); playerHistory.add(new OpenAIClient.ChatMessage("assistant", response)); trimHistoryByChars(playerHistory, maxCtx); } });
                } else { server2.execute(() -> { animation.done(player); player.sendSystemMessage(Component.translatable("mcai.chat.error", result.error())); }); }
            } catch (Exception e) { MCAIMod.LOGGER.error("AI query failed", e); MinecraftServer server2 = mod.getServer(); if (server2 != null) { server2.execute(() -> { animation.done(player); player.sendSystemMessage(Component.translatable("mcai.chat.exception", e.getMessage())); }); } }
        });
    }

    public void handleConsoleAIQuery(CommandSourceStack src, String query) {
        MinecraftServer server = mod.getServer(); if (server == null) return;
        aiExecutor.execute(() -> {
            try {
                String playerList = server.getPlayerList().getPlayers().stream().map(p -> p.getScoreboardName()).collect(Collectors.joining(", "));
                String context = String.format("版本: %s | 在线(%d/%d): [%s]\n说话者: 控制台", server.getServerModName(), server.getPlayerCount(), server.getMaxPlayers(), playerList.isEmpty() ? "无" : playerList);
                List<OpenAIClient.ChatMessage> messages = new ArrayList<>();
                messages.add(new OpenAIClient.ChatMessage("system", mod.getConfig().getSystemPrompt()));
                String recentChat = chatLog.peek();
                if (!recentChat.isEmpty()) messages.add(new OpenAIClient.ChatMessage("system", "最近的聊天记录（了解当前氛围）:\n" + recentChat));
                messages.add(new OpenAIClient.ChatMessage("user", context + "\n\n控制台 说: " + query));
                var result = mod.getAiClient().chat(messages, toolCalls -> toolDispatcher.dispatchConsole(toolCalls));
                String reply = result.success() ? result.value() : (result.error());
                src.sendSuccess(() -> Component.translatable("mcai.chat.reply.console", reply), false);
                chatLog.add("AI → 控制台", reply);
            } catch (Exception e) { MCAIMod.LOGGER.error("Console AI query failed", e); src.sendFailure(Component.translatable("mcai.chat.error", e.getMessage())); }
        });
    }

    private boolean handleResponse(ServerPlayer player, String response) {
        response = response.trim(); MinecraftServer server = mod.getServer(); String pname = player.getScoreboardName();
        if (!response.startsWith("/") || !mod.getConfig().isEnableCommandExecution()) {
            if (server != null) server.getPlayerList().broadcastSystemMessage(Component.translatable("mcai.chat.reply", pname + "§b " + response), false);
            chatLog.add("AI → " + pname, response); return true;
        }
        String cmd = response.lines().findFirst().orElse("").substring(1).trim();
        if (cmd.isEmpty()) { if (server != null) server.getPlayerList().broadcastSystemMessage(Component.translatable("mcai.chat.reply", pname + "§b " + response), false); return true; }
        if (server == null) return true;
        if (cmdExec.needsApproval(cmd)) { String result = cmdExec.executeCommand(cmd, player); player.sendSystemMessage(Component.translatable("mcai.cmd.exec.approval_pending", result)); return false; }
        String result = cmdExec.executeAsOp(cmd, server);
        if (result != null && !result.isEmpty() && !result.equals("Command executed")) server.getPlayerList().broadcastSystemMessage(Component.literal("§7[AI] → §e/" + cmd + " §7(" + result + ")"), false);
        else server.getPlayerList().broadcastSystemMessage(Component.literal("§7[AI] → §e/" + cmd), false);
        chatLog.add("AI → " + pname, "/" + cmd + (result.isEmpty() || "Command executed".equals(result) ? "" : " (" + result + ")"));
        return true;
    }

    public void clearHistory(UUID playerId) { history.remove(playerId); }
    public void clearAllHistory() { history.clear(); }
    public void reloadAll() { mod.reloadConfig(); history.clear(); cmdExec.clearAll(); chatLog.clear(); }
    private void trimHistoryByChars(LinkedList<OpenAIClient.ChatMessage> h, int maxChars) { while (!h.isEmpty()) { int total = 0; for (var msg : h) { total += msg.content != null ? msg.content.length() : 0; } if (total <= maxChars) break; h.removeFirst(); } }
}
