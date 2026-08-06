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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ChatHandler {
    private final MCAIMod mod;
    private final ChatLog chatLog;
    private final ThinkingAnimation animation;
    private final PlayerContextBuilder contextBuilder;
    private final CommandExecutionService cmdExec;
    private volatile ToolDispatcher toolDispatcher;

    private volatile ExecutorService aiExecutor = newExecutor();
    private final Map<UUID, LinkedList<OpenAIClient.ChatMessage>> history = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> lastAICallTime = new ConcurrentHashMap<>();
    private final AtomicInteger concurrentNonAdminCalls = new AtomicInteger(0);
    private volatile boolean chatEnabled = true;

    public ChatHandler(MCAIMod mod, ChatLog chatLog, ThinkingAnimation animation,
                       PlayerContextBuilder contextBuilder, CommandExecutionService cmdExec,
                       ToolDispatcher toolDispatcher) {
        this.mod = mod; this.chatLog = chatLog; this.animation = animation;
        this.contextBuilder = contextBuilder; this.cmdExec = cmdExec; this.toolDispatcher = toolDispatcher;
    }

    public ChatLog getChatLog() { return chatLog; }
    public MinecraftServer getServer() { return mod.getServer(); }
    public MCAIMod getMod() { return mod; }
    public void setToolDispatcher(ToolDispatcher td) { this.toolDispatcher = td; }
    public com.example.mcai.behavior.PlayerBehaviorTracker getBehaviorTracker() { return mod.getBehaviorTracker(); }
    public com.example.mcai.config.ModConfig getConfig() { return mod.getConfig(); }
    public boolean isChatEnabled() { return chatEnabled; }
    public void setChatEnabled(boolean enabled) { this.chatEnabled = enabled; }

    private static ExecutorService newExecutor() {
        return new ThreadPoolExecutor(4, 8, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(32),
                r -> { Thread t = new Thread(r, "MCAI-Worker"); t.setDaemon(true); return t; },
                (r, executor) -> MCAIMod.LOGGER.warn("AI executor queue full, task rejected"));
    }

    /** 销毁所有 AI 工作线程并重建线程池 */
    public int killAIThreads() {
        ExecutorService old = aiExecutor;
        aiExecutor = newExecutor();
        int terminated = old.shutdownNow().size();
        concurrentNonAdminCalls.set(0);
        MCAIMod.LOGGER.warn("AI threads killed, {} tasks discarded", terminated);
        return terminated;
    }

    /**
     * 对玩家消息进行 Prompt Injection 防护：
     * 1. 去除控制字符（保留换行）
     * 2. 限制长度
     * 3. 用结构化分隔符包裹，使 AI 明确区分玩家内容与系统指令
     */
    public static String sanitizeForPrompt(String playerName, String message) {
        if (message == null) message = "";
        // 去除控制字符（保留 \n \t）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c == '\n' || c == '\t' || (c >= 0x20 && c != 0x7F)) {
                sb.append(c);
            }
        }
        String clean = sb.toString().trim();
        // 限制单条消息长度
        if (clean.length() > 500) clean = clean.substring(0, 500) + "...(truncated)";
        // 用不可伪造的分隔符包裹，明确标注这是玩家发言而非系统指令
        return "[PLAYER:" + playerName + "] " + clean;
    }

    /**
     * 对聊天记录进行 Prompt Injection 防护：
     * 逐行过滤，去除控制字符和注入尝试模式
     */
    public static String sanitizeChatLogForPrompt(String chatLog) {
        if (chatLog == null || chatLog.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : chatLog.split("\n")) {
            // 去除控制字符
            StringBuilder clean = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c >= 0x20 && c != 0x7F) clean.append(c);
            }
            String sanitized = clean.toString().trim();
            if (!sanitized.isEmpty()) sb.append(sanitized).append("\n");
        }
        return sb.toString().trim();
    }

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
                if (text.startsWith(prefix)) {
                    String query = text.substring(prefix.length()).trim();
                    if (!query.isEmpty()) {
                        if (!chatEnabled) {
                            sender.sendSystemMessage(Component.translatable("mcai.chat.disabled"));
                            return false;
                        }
                        // Check cooldown BEFORE broadcasting
                        Component cooldownError = checkPlayerCanUseAI(sender);
                        if (cooldownError != null) {
                            sender.sendSystemMessage(cooldownError);
                            return false;
                        }
                        var server = mod.getServer();
                        if (server != null) server.getPlayerList().broadcastSystemMessage(Component.translatable("mcai.cmd.ai.broadcast", sender.getScoreboardName(), query), false);
                        handleAIQuery(sender, query);
                    }
                    return false;
                }
                return true;
            });
            ServerMessageEvents.GAME_MESSAGE.register((srv, msg, overlay) -> {
                try { String text = msg.getString(); var matcher = Pattern.compile("^\\[(.+?)\\] (.+)$").matcher(text); if (matcher.matches()) { String pn = matcher.group(1); String ct = matcher.group(2); ServerPlayer player = srv.getPlayerList().getPlayer(pn); if (player != null) { chatLog.add(pn + "(命令)", ct); return; } } chatLog.add("系统", text); } catch (Exception e) { MCAIMod.LOGGER.warn("GAME_MESSAGE log failed: {}", e.getMessage()); chatLog.add("系统", "<消息记录失败>"); }
            });
            MCAIMod.LOGGER.info("Chat interception enabled");
        } catch (NoClassDefFoundError | Exception e) { MCAIMod.LOGGER.warn("Chat interception unavailable"); }
    }

    public void onPlayerDisconnect(ServerPlayer player) { UUID id = player.getUUID(); history.remove(id); cmdExec.cleanupPlayer(id); lastAICallTime.remove(id); }

    /**
     * 检查非管理员玩家是否可以使用 AI（冷却 + 并发限制）。
     * 返回 null 表示可以使用，否则返回错误消息 Component。
     */
    public Component checkPlayerCanUseAI(ServerPlayer player) {
        MinecraftServer server = mod.getServer();
        if (server == null) return Component.translatable("mcai.chat.exception", "Server not ready");
        if (CommandExecutionService.isAdmin(player, server)) return null; // 管理员不受限

        int cooldown = mod.getConfig().getAiCooldownSeconds();
        if (cooldown > 0) {
            long last = lastAICallTime.getOrDefault(player.getUUID(), 0L);
            long elapsed = (System.currentTimeMillis() - last) / 1000;
            if (elapsed < cooldown) {
                return Component.translatable("mcai.chat.cooldown", cooldown - elapsed);
            }
        }
        int maxConcurrent = mod.getConfig().getAiMaxConcurrent();
        if (maxConcurrent > 0 && concurrentNonAdminCalls.get() >= maxConcurrent) {
            return Component.translatable("mcai.chat.concurrent_limit");
        }
        return null;
    }

    public void handleAIQuery(ServerPlayer player, String query) {
        MinecraftServer server = mod.getServer(); if (server == null) return;
        final UUID pid = player.getUUID();
        final String pname = player.getScoreboardName();
        var dbg = mod.getDebugLogger();
        if (dbg.isEnabled()) dbg.logQuery(pname, query);

        // 非管理员限频检查
        boolean isAdmin = CommandExecutionService.isAdmin(player, server);
        if (!isAdmin) {
            int cooldown = mod.getConfig().getAiCooldownSeconds();
            if (cooldown > 0) {
                long last = lastAICallTime.getOrDefault(pid, 0L);
                long elapsed = (System.currentTimeMillis() - last) / 1000;
                if (elapsed < cooldown) {
                    player.sendSystemMessage(Component.translatable("mcai.chat.cooldown", cooldown - elapsed));
                    return;
                }
            }
            int maxConcurrent = mod.getConfig().getAiMaxConcurrent();
            if (maxConcurrent > 0 && concurrentNonAdminCalls.get() >= maxConcurrent) {
                player.sendSystemMessage(Component.translatable("mcai.chat.concurrent_limit"));
                return;
            }
            lastAICallTime.put(pid, System.currentTimeMillis());
            concurrentNonAdminCalls.incrementAndGet();
        }

        var playerHistory = history.computeIfAbsent(pid, k -> new LinkedList<>());
        int maxCtx = mod.getConfig().getContextMaxChars();
        MCAIMod.LOGGER.info("AI query from {}: {}", pname, query);
        dbg.startSession(pname, query);
        animation.start(player, server);
        final boolean finalIsAdmin = isAdmin;
        try {
            aiExecutor.submit(() -> {
            try {
                String context = contextBuilder.build(player, mod.getServer());
                String userContent = context + "\n\n" + sanitizeForPrompt(pname, query);
                List<OpenAIClient.ChatMessage> messages = new ArrayList<>();
                messages.add(new OpenAIClient.ChatMessage("system", mod.getConfig().getSystemPrompt()));
                String recentChat = chatLog.peek();
                if (!recentChat.isEmpty()) messages.add(new OpenAIClient.ChatMessage("system", "最近的聊天记录（了解当前氛围）:\n" + sanitizeChatLogForPrompt(recentChat)));
                String penaltySummary = mod.getChatReviewSystem() != null ? mod.getChatReviewSystem().getPenaltyHistory().getSummary() : "";
                if (!penaltySummary.isEmpty()) messages.add(new OpenAIClient.ChatMessage("system", penaltySummary));
                synchronized (playerHistory) { int totalChars = 0; for (var msg : playerHistory) { int c = msg.content != null ? msg.content.length() : 0; if (totalChars + c > maxCtx) break; totalChars += c; messages.add(msg); } }
                messages.add(new OpenAIClient.ChatMessage("user", userContent));
                var result = mod.getAiClient().chat(messages, toolCalls -> toolDispatcher.dispatch(toolCalls, player));
                MinecraftServer server2 = mod.getServer(); if (server2 == null) return;
                if (result.success()) {
                    String response = result.value();
                    server2.execute(() -> { try { animation.done(player); handleResponse(player, response); synchronized (playerHistory) { playerHistory.add(new OpenAIClient.ChatMessage("user", userContent)); playerHistory.add(new OpenAIClient.ChatMessage("assistant", response)); trimHistoryByChars(playerHistory, maxCtx); } } catch (Exception ex) { MCAIMod.LOGGER.error("AI response handler error", ex); } });
                } else { server2.execute(() -> { try { animation.done(player); player.sendSystemMessage(Component.translatable("mcai.chat.error", result.error())); } catch (Exception ex) { MCAIMod.LOGGER.error("AI error handler error", ex); } }); }
            } catch (Exception e) { MCAIMod.LOGGER.error("AI query failed", e); MinecraftServer server2 = mod.getServer(); if (server2 != null) { server2.execute(() -> { try { animation.done(player); player.sendSystemMessage(Component.translatable("mcai.chat.exception", e.getMessage())); } catch (Exception ex) { MCAIMod.LOGGER.error("AI exception handler error", ex); } }); } }
            finally { if (!finalIsAdmin) concurrentNonAdminCalls.decrementAndGet(); dbg.endSession(); }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // 任务被拒绝（线程池满），回滚计数器和冷却时间
            if (!finalIsAdmin) {
                concurrentNonAdminCalls.decrementAndGet();
                lastAICallTime.remove(pid);
            }
            MCAIMod.LOGGER.warn("AI executor rejected task for {}", pname);
            dbg.endSession();
            server.execute(() -> {
                animation.done(player);
                player.sendSystemMessage(Component.translatable("mcai.chat.concurrent_limit"));
            });
        }
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
                if (!recentChat.isEmpty()) messages.add(new OpenAIClient.ChatMessage("system", "最近的聊天记录（了解当前氛围）:\n" + sanitizeChatLogForPrompt(recentChat)));
                messages.add(new OpenAIClient.ChatMessage("user", context + "\n\n控制台 说: " + query));
                var result = mod.getAiClient().chat(messages, toolCalls -> toolDispatcher.dispatchConsole(toolCalls));
                String reply = result.success() ? result.value() : (result.error());
                src.sendSuccess(() -> Component.translatable("mcai.chat.reply.console", reply), false);
                chatLog.add("AI → 控制台", reply);
            } catch (Exception e) { MCAIMod.LOGGER.error("Console AI query failed", e); src.sendFailure(Component.translatable("mcai.chat.error", e.getMessage())); }
        });
    }

    private boolean handleResponse(ServerPlayer player, String response) {
        response = response.trim();
        MinecraftServer server = mod.getServer();
        String pname = player.getScoreboardName();

        // 安全策略：AI 不允许直接在聊天文本中输出以 / 开头的命令来执行。
        // 所有命令执行必须通过 execute_minecraft_command 工具走审批流程。
        if (response.startsWith("/") && mod.getConfig().isEnableCommandExecution()) {
            String cmd = response.lines().findFirst().orElse("").substring(1).trim();
            MCAIMod.LOGGER.warn("AI attempted to output command as text: /{}. Refused; expected tool call.", cmd);
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("mcai.chat.blocked_text_command"), false);
            }
            chatLog.add("AI → " + pname, "[blocked] /" + cmd);
            return true;
        }

        if (server != null) {
            server.getPlayerList().broadcastSystemMessage(Component.translatable("mcai.chat.reply", pname + "§b " + response), false);
        }
        chatLog.add("AI → " + pname, response);
        return true;
    }

    public void clearHistory(UUID playerId) { history.remove(playerId); }
    public void reloadAll() { mod.reloadConfig(); }
    private void trimHistoryByChars(LinkedList<OpenAIClient.ChatMessage> h, int maxChars) {
        int total = 0;
        for (var msg : h) { total += msg.content != null ? msg.content.length() : 0; }
        while (total > maxChars && !h.isEmpty()) {
            var removed = h.removeFirst();
            total -= removed.content != null ? removed.content.length() : 0;
        }
    }
}
