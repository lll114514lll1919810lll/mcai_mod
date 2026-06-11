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
    /** Pending approval futures: key = "playerUUID:num" 鈫?CompletableFuture for AI thread to block on */
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
                                            Component.literal("搂7[搂f" + player.getScoreboardName()
                                                    + "搂7]瀵笰I璇达細搂f" + msg), false);
                                }
                                addToChatLog(player.getScoreboardName(), msg, isAdminPlayer(player));
                                handleAIQuery(player, msg);
                            } else {
                                // Console AI query
                                ctx.getSource().sendSuccess(
                                        () -> Component.literal("搂7[鎺у埗鍙癩 姝ｅ湪鍚慉I鍙戦€佹煡璇?.."), false);
                                addToChatLog("鎺у埗鍙?, msg, true);
                                handleConsoleAIQuery(ctx.getSource(), msg);
                            }
                            return Command.SINGLE_SUCCESS;
                        }))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("鐢ㄦ硶: /ai <娑堟伅>"));
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
                                    () -> Component.literal("搂a=== 鐭ヨ瘑搴撶粨鏋?===\n搂7" + result), false);
                            return 1;
                        }))
                .executes(ctx -> {
                    ctx.getSource().sendFailure(Component.literal("鐢ㄦ硶: /aikb <鍏抽敭璇?"));
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
                                    () -> Component.literal("搂7[AI] 鏆傛棤寰呭鎵规寚浠?), false);
                            return 0;
                        }
                        ctx.getSource().sendSuccess(
                                () -> Component.literal("搂e==== 寰呭鎵规寚浠?(" + cmds.size() + ") ===="), false);
                        for (int i = 0; i < cmds.size(); i++) {
                            final int num = i + 1;
                            final String cmd = cmds.get(i);
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("搂6  [" + num + "] /" + cmd), false);
                        }
                        ctx.getSource().sendSuccess(
                                () -> Component.literal("搂e浣跨敤 搂a/aiaccept <缂栧彿> 鎵瑰噯锛屄/aireject <缂栧彿> 鎷掔粷"), false);
                    } else {
                        // Console: show all pending commands grouped by player
                        var all = pendingCommands.entrySet();
                        if (all.isEmpty()) {
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("搂7[AI] 鏆傛棤寰呭鎵规寚浠?), false);
                            return 0;
                        }
                        ctx.getSource().sendSuccess(
                                () -> Component.literal("搂e==== 鍏ㄩ儴寰呭鎵规寚浠?===="), false);
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
                                        () -> Component.literal("搂6  [" + ftotal + "] 搂f" + fname + " 搂7鈫?/" + cmd), false);
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
                    ctx.getSource().sendFailure(Component.literal("鐢ㄦ硶: /aiaccept <缂栧彿>"));
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
                    ctx.getSource().sendFailure(Component.literal("鐢ㄦ硶: /aireject <缂栧彿>"));
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
                            () -> Component.literal("搂a[AI] 宸叉竻闄ゅ璇濆巻鍙插拰寰呭鎵规寚浠?), false);
                    return 1;
                });
    }


    public com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> createScoreCommand() {
        return Commands.literal("aiscore")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) {
                        ctx.getSource().sendSuccess(() -> Component.literal("搂c鎺у埗鍙版棤琛屼负鍒?), false);
                        return 0;
                    }
                    var tracker = mod.getBehaviorTracker();
                    var cfg = mod.getConfig();
                    int score = tracker.getScore(player.getUUID());
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "搂e===== 浣犵殑琛屼负璇勫垎 ====="), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "搂f褰撳墠璇勫垎: " + (score >= 0 ? "搂a" : "搂c") + score), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "搂7鈹佲攣鈹佲攣 澶勭綒瑙勫垯 鈹佲攣鈹佲攣"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "搂7琛屼负鍒嗗垵濮嬩负 搂f0搂7锛岃繚瑙勬墸鍒嗭紝鑹ソ琛ㄧ幇鍙仮澶?), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "搂e榛勭墝闃堝€? 搂f" + cfg.getYellowCardThreshold()
                                    + " 搂7(鍏睆璀﹀憡)"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "搂c绾㈢墝闃堝€? 搂f" + cfg.getRedCardThreshold()
                                    + " 搂7(韪㈠嚭+绠＄悊鍛樺鎵?"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "搂a姣忓懆鏈熻嚜鍔ㄦ仮澶? 搂f+" + cfg.getScoreRecoveryPerInterval()
                                    + " 搂7(涓婇檺鎭㈠鑷?)"), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "搂7鈹佲攣鈹佲攣鈹佲攣鈹佲攣鈹佲攣鈹佲攣鈹佲攣"), false);
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
                            () -> Component.literal("搂a[AI] 宸查噸杞斤紝鎵€鏈夌姸鎬佸凡娓呯┖"), true);
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
            String prefix = isAdmin ? "[绠＄悊鍛榏 " : "";
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
            // CHAT_MESSAGE: 璁板綍鎵€鏈夋秷鎭紙鍐呭濮嬬粓鍙敤锛屽寘鎷湭绛惧悕娑堟伅锛?            ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
                if (sender != null) {
                    String text = message.decoratedContent() != null
                            ? message.decoratedContent().getString()
                            : "";
                    addToChatLog(sender.getScoreboardName(), text, isAdminPlayer(sender));
                }
            });
            // ALLOW_CHAT_MESSAGE: 浠呯敤浜庢嫤鎴?!ai 鍓嶇紑瑙﹀彂
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
                                    Component.literal("搂7[搂f" + sender.getScoreboardName()
                                            + "搂7]瀵笰I璇达細搂f" + query), false);
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
                            addToChatLog(possibleName + "(鍛戒护)", content, isAdminPlayer(player));
                            return;
                        }
                    }
                    addToChatLog("绯荤粺", text);
                } catch (Exception e) {
                    MCAIMod.LOGGER.warn("GAME_MESSAGE log failed: {}", e.getMessage());
                    addToChatLog("绯荤粺", "<娑堟伅璁板綍澶辫触>");
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
            src.sendFailure(Component.literal("搂c缂栧彿鏃犳晥锛屼娇鐢?/aiquery 鏌ョ湅"));
            // Also release any blocked AI call
            String key = player.getUUID() + ":" + num;
            CompletableFuture<String> f = pendingFutures.remove(key);
            if (f != null) f.complete("[瀹℃壒澶辫触] 缂栧彿鏃犳晥");
            return 0;
        }
        String cmd = cmds.remove(idx);
        if (cmds.isEmpty()) pendingCommands.remove(player.getUUID());
        String result = "鎸囦护宸叉墽琛?;
        var server = mod.getServer();
        if (server != null) {
            try {
                server.getCommands().getDispatcher().execute(cmd, server.createCommandSourceStack());
            } catch (CommandSyntaxException e) {
                result = "鎸囦护璇硶閿欒: " + e.getMessage();
            }
        }
        src.sendSuccess(() -> Component.literal("搂a[AI] 宸叉壒鍑?#" + num + " 骞舵墽琛? /" + cmd), true);
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
            src.sendFailure(Component.literal("搂c缂栧彿鏃犳晥锛屼娇鐢?/aiquery 鏌ョ湅"));
            String key = player.getUUID() + ":" + num;
            CompletableFuture<String> f = pendingFutures.remove(key);
            if (f != null) f.complete("[瀹℃壒澶辫触] 缂栧彿鏃犳晥");
            return 0;
        }
        String cmd = cmds.remove(idx);
        if (cmds.isEmpty()) pendingCommands.remove(player.getUUID());
        src.sendSuccess(() -> Component.literal("搂c[AI] 宸叉嫆缁?#" + num + ": /" + cmd), true);
        // Release the blocked AI call
        String key = player.getUUID() + ":" + num;
        CompletableFuture<String> f = pendingFutures.remove(key);
        if (f != null) f.complete("[瀹℃壒鎷掔粷] 绠＄悊鍛樻嫆缁濅簡鎸囦护: /" + cmd);
        return 1;
    }

    // 鈹€鈹€ 缁忛獙鏍忓姩鐢?鈹€鈹€

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
                    case 0 -> "搂7鈻屄?鈻屸枌 搂eAI 鎬濊€冧腑...";
                    case 1 -> "搂7鈻屸枌搂8鈻?搂eAI 鎬濊€冧腑...";
                    case 2 -> "搂7鈻屸枌鈻?搂eAI 鎬濊€冧腑...";
                    default -> "搂8鈻屸枌鈻?搂7AI 鎬濊€冧腑...";
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

    // 鈹€鈹€ AI 鏌ヨ 鈹€鈹€

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
                String userContent = context + "\n\n" + pname + " 璇? " + query;

                List<OpenAIClient.ChatMessage> messages = new ArrayList<>();
                messages.add(new OpenAIClient.ChatMessage("system", mod.getConfig().getSystemPrompt()));

                String recentChat;
                synchronized (chatLog) {
                    recentChat = String.join("\n", chatLog);
                }
                if (!recentChat.isEmpty()) {
                    messages.add(new OpenAIClient.ChatMessage("system",
                            "鏈€杩戠殑鑱婂ぉ璁板綍锛堜簡瑙ｅ綋鍓嶆皼鍥达級:\n" + recentChat));
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
                            results.add("鏈煡宸ュ叿: " + tc.name);
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
                        if (response.startsWith("[API閿欒]") || response.startsWith("[API杩炴帴澶辫触]")
                                || response.startsWith("[API寮傚父]") || response.startsWith("[鍝嶅簲瑙ｆ瀽澶辫触]")
                                || response.startsWith("[宸ュ叿璋冪敤瓒呴檺]") || response.startsWith("[宸ュ叿璋冪敤寮傚父]")) {
                            player.sendSystemMessage(Component.literal("搂c" + response));
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
                                Component.literal("搂c[AI] 鏃犲搷搴旓紝璇锋鏌ユ帶鍒跺彴鏃ュ織"));
                    });
                }
            } catch (Exception e) {
                MCAIMod.LOGGER.error("AI query failed", e);
                var server = mod.getServer();
                if (server != null) {
                    server.execute(() -> {
                        doneThinking(player);
                        player.sendSystemMessage(
                                Component.literal("搂c[AI] 寮傚父: " + e.getMessage()));
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

    // AI缁濆绂佹鎵ц鐨刴od鍐呴儴鎸囦护
    private static final java.util.Set<String> FORBIDDEN_COMMANDS = java.util.Set.of(
            "ai", "aiwiki", "aiquery", "aiaccept", "aireject",
            "aiclear", "aireload", "aitest", "aicheck"
    );

    private String executeCommand(String command, ServerPlayer player) {
        var server = mod.getServer();
        if (server == null) return "鏈嶅姟鍣ㄦ湭灏辩华";
        String root = command.split("\\s+")[0].toLowerCase();
        if (FORBIDDEN_COMMANDS.contains(root)) {
            return "绂佹AI鎵цMod鍐呴儴鎸囦护";
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
                return result != null ? result : "鎸囦护宸叉墽琛?;
            } catch (java.util.concurrent.TimeoutException e) {
                pendingFutures.remove(key);
                return "[瀹℃壒瓒呮椂] 3鍒嗛挓鍐呮棤浜烘壒鍑嗭紝鎸囦护宸茶嚜鍔ㄥ彇娑? /" + command;
            } catch (Exception e) {
                return "[瀹℃壒寮傚父] " + e.getMessage();
            }
        }
        CompletableFuture<String> future = new CompletableFuture<>();
        String playerName = player != null ? player.getScoreboardName() : "鎺у埗鍙?;
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
                        Component.literal("搂7[AI] 搂f" + playerName + " 搂7鈫?搂e/" + command
                                + (result.isEmpty() ? "" : " 搂7(" + result + ")")), false);
                // Log to chat log so review AI can see all commands
                if (player != null) {
                    addToChatLog(player.getScoreboardName(), "/" + command, isAdminPlayer(player));
                } else {
                    addToChatLog("鎺у埗鍙?, "/" + command, true);
                }
                future.complete(result.isEmpty() ? "鎸囦护宸叉墽琛? : result);
            } catch (Exception e) {
                future.complete("鎵ц澶辫触: " + e.getMessage());
            }
        });
        try { return future.get(10, TimeUnit.SECONDS); }
        catch (java.util.concurrent.TimeoutException e) { return "鎵ц瓒呮椂"; }
        catch (Exception e) { return "鎵ц寮傚父: " + e.getMessage(); }
    }

    /** Notify online admins about a pending command requiring approval. */
    private void notifyAdminsPending(ServerPlayer requester, String command, int num) {
        var server = mod.getServer();
        if (server == null) return;
        server.execute(() -> {
            // Broadcast request to all players
            server.getPlayerList().broadcastSystemMessage(Component.literal(
                    "搂e[AI] 搂f" + requester.getScoreboardName() + " 搂7璇锋眰鎵ц: 搂e/" + command
                    + " 搂7(寰呭鎵?"), false);
            // Send approve/reject instructions only to admins
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (isAdminPlayer(p)) {
                    p.sendSystemMessage(Component.literal(
                            " 搂a/aiaccept " + num + " 搂7鎵瑰噯  搂c/aireject " + num + " 搂7鎷掔粷  搂7(3鍒嗛挓瓒呮椂鑷姩鍙栨秷)"));
                }
            }
        });
    }

    private String getServerStatus(ServerPlayer player) {
        var server = mod.getServer();
        if (server == null) return "鏈嶅姟鍣ㄦ湭灏辩华";
        var level = player != null ? (net.minecraft.server.level.ServerLevel) player.level()
                : server.overworld();

        String time = formatGameTime(level.getGameTime());

        String weather;
        if (level.isThundering()) weather = "闆锋毚";
        else if (level.isRaining()) weather = "涓嬮洦";
        else weather = "鏅存湕";

        String biome;
        try {
            var biomeOpt = level.getBiome(player != null ? player.blockPosition() : net.minecraft.core.BlockPos.ZERO).unwrapKey();
            biome = biomeOpt.isPresent()
                    ? biomeOpt.get().identifier().getPath()
                    : "鏈煡";
        } catch (Exception e) {
            biome = "鏈煡";
        }

        float mspt = server.getCurrentSmoothedTickTime();
        double tps = Math.min(20.0, 1000.0 / Math.max(mspt, 0.001));
        String load;
        if (tps >= 19.5) load = "娴佺晠";
        else if (tps >= 15) load = "杞诲井鍗￠】";
        else if (tps >= 10) load = "鏄庢樉鍗￠】";
        else load = "涓ラ噸鍗￠】";

        return String.format("""
                鏈嶅姟鍣ㄧ姸鎬?
                鏃堕棿: %s
                澶╂皵: %s
                鐢熺墿缇ょ郴: %s
                璐熻浇: TPS=%.1f MSPT=%.1fms (%s)
                鍦ㄧ嚎: %d/%d
                """, time, weather, biome, tps, mspt, load,
                server.getPlayerCount(), server.getMaxPlayers());
    }

    private String getGameRules(ServerPlayer player) {
        var server = mod.getServer();
        if (server == null) return "鏈嶅姟鍣ㄦ湭灏辩华";
        var level = player != null ? (ServerLevel) player.level() : server.overworld();
        var rules = level.getGameRules();

        return String.format("""
                娓告垙瑙勫垯:
                鏄煎寰幆: %s | 澶╂皵寰幆: %s | 鐏劙浼ゅ: %s
                鐢熺墿鐮村潖: %s | 姝讳骸涓嶆帀钀? %s | 绔嬪嵆閲嶇敓: %s
                鐢熺墿鐢熸垚: %s | 鎬墿鐢熸垚: %s | 骞荤考鐢熸垚: %s
                鐏惧巹宸￠€婚槦: %s | 娴佹氮鍟嗕汉: %s | 鐩戝畧鑰呯敓鎴? %s
                鍛戒护鏂瑰潡杈撳嚭: %s | 绠＄悊鍛樻棩蹇? %s | 鍙嶉淇℃伅: %s
                TNT鐖嗙偢: %s | 鏂瑰潡鎺夎惤: %s | 鐢熺墿鎺夎惤: %s
                闅忔満鍒婚€熷害: %d | 閲嶇敓鍗婂緞: %d | 鐫¤姣斾緥: %d%%
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

    private static String yn(boolean b) { return b ? "搂a鏄? : "搂c鍚?; }

    private String getDebugInfo(ServerPlayer player) {
        var server = mod.getServer();
        if (server == null) return "鏈嶅姟鍣ㄦ湭灏辩华";
        var level = (ServerLevel) player.level();
        var pos = player.blockPosition();

        // 鍏夌収绛夌骇
        int blockLight = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos);
        int skyLight = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos);
        // 鑰冭檻澶╃┖鍙樻殫鍥犲瓙鍚庣殑瀹為檯澶╃┖鍏?        int skyDarken = level.getSkyDarken();
        int actualSkyLight = Math.max(0, skyLight - skyDarken);

        // 鍖哄潡鍧愭爣
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        String chunkLoaded = level.isLoaded(pos) ? "鏄? : "鍚?;

        // 鍖哄煙闅惧害
        float regionalDifficulty;
        try {
            regionalDifficulty = level.getCurrentDifficultyAt(pos).getEffectiveDifficulty();
        } catch (Exception e) {
            regionalDifficulty = -1;
        }

        // 娉ㄨ鏂瑰潡鍜屽疄浣?        String lookingAt;
        try {
            var hit = player.pick(50.0, 0.0f, false);
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                var blockHit = (net.minecraft.world.phys.BlockHitResult) hit;
                var blockState = level.getBlockState(blockHit.getBlockPos());
                lookingAt = "鏂瑰潡: " + blockState.getBlock().toString()
                        + " @ " + blockHit.getBlockPos().toShortString();
            } else if (hit.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                var entityHit = (net.minecraft.world.phys.EntityHitResult) hit;
                var entity = entityHit.getEntity();
                String name = entity.getDisplayName().getString();
                String category = entity.getType().getCategory().getName();
                StringBuilder info = new StringBuilder();
                info.append("瀹炰綋: ").append(name).append(" (").append(category).append(")");
                if (entity instanceof net.minecraft.world.entity.LivingEntity le) {
                    info.append(" HP:").append(String.format("%.0f/%.0f", le.getHealth(), le.getMaxHealth()));
                }
                info.append(" @ ").append(entity.blockPosition().toShortString());
                lookingAt = info.toString();
            } else {
                lookingAt = "鏃犵洰鏍?;
            }
        } catch (Exception e) {
            lookingAt = "N/A (" + e.getClass().getSimpleName() + ")";
        }

        // 娉ㄨ娴佷綋
        String fluidAtPos;
        try {
            var fluid = level.getFluidState(pos);
            fluidAtPos = fluid.isEmpty() ? "鏃? : fluid.getType().toString();
        } catch (Exception e) {
            fluidAtPos = "N/A";
        }

        return String.format("""
                F3 璋冭瘯淇℃伅:
                鍧愭爣: [%d %d %d] (鍖哄潡 搂e%d, %d搂r)
                鏈濆悜: %s
                鏂瑰潡鍏? %d | 澶╃┖鍏? %d (鍘熷%d, 鏆楀寲-%d) | 鑴氫笅娴佷綋: %s
                鍖哄煙闅惧害: %.2f | 鍖哄潡宸插姞杞? %s
                娉ㄨ鐩爣: %s
                """,
                pos.getX(), pos.getY(), pos.getZ(), chunkX, chunkZ,
                facingName(player.getYRot()),
                blockLight, actualSkyLight, skyLight, skyDarken, fluidAtPos,
                regionalDifficulty, chunkLoaded,
                lookingAt
        );
    }

    private static String facingName(float yRot) {
        if (yRot >= -45 && yRot < 45) return "鍗?(+Z)";
        if (yRot >= 45 && yRot < 135) return "瑗?(-X)";
        if (yRot >= -135 && yRot < -45) return "涓?(+X)";
        return "鍖?(-Z)";
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
            advSummary = String.format(" | 杩涘害: %d/%d (鏁呬簨妯″紡)", done, total);
        } catch (Exception ignored) {}

        float yRot = player.getYRot();
        String facing;
        if (yRot >= -45 && yRot < 45) facing = "鍗?;
        else if (yRot >= 45 && yRot < 135) facing = "瑗?;
        else if (yRot >= -135 && yRot < -45) facing = "涓?;
        else facing = "鍖?;

        String gameTimeStr = formatGameTime(level.getGameTime());

        // Behavior score context
        String behaviorInfo = "";
        try {
            boolean isAdmin = server != null
                    && server.getPlayerList().isOp(new NameAndId(player.getGameProfile()));
            if (!isAdmin) {
                int score = mod.getBehaviorTracker().getScore(player.getUUID());
                behaviorInfo = " | 琛屼负鍒? " + score;
            }
        } catch (Exception ignored) {}

        return String.format("""
                鐗堟湰: %s | 鍦ㄧ嚎(%d/%d): [%s] | %s | 闅惧害: %s
                璇磋瘽鑰? %s | 鍧愭爣: [%d %d %d] | 鏈濆悜: %s | 缁村害: %s | HP: %.1f | 楗遍搴? %d | 妯″紡: %s | 绛夌骇: %d%s%s
                """, server.getServerModName(), server.getPlayerCount(), server.getMaxPlayers(),
                playerList, gameTimeStr, level.getDifficulty().getDisplayName().getString(),
                player.getScoreboardName(), pos.getX(), pos.getY(), pos.getZ(), facing,
                level.dimension().identifier(), player.getHealth(),
                player.getFoodData().getFoodLevel(), player.gameMode.getGameModeForPlayer().name(),
                player.experienceLevel, advSummary, behaviorInfo);
    }

    /**灏?tick 鏁拌浆涓轰汉绫诲彲璇荤殑娓告垙鏃堕棿銆? tick = 绗?澶?6:00 AM */
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

        return String.format("绗?d澶?%d:%02d %s (tick=%d)", day, displayHour, minute, period, ticks);
    }

    private boolean handleResponse(ServerPlayer player, String response) {
        response = response.trim();
        var server = mod.getServer();
        String pname = player.getScoreboardName();
        if (!response.startsWith("/") || !mod.getConfig().isEnableCommandExecution()) {
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("搂b[AI] 鈫?搂f" + pname + "搂b " + response), false);
            }
            addToChatLog("AI 鈫?" + pname, response);
            return true;
        }
        String cmd = response.lines().findFirst().orElse("").substring(1).trim();
        if (cmd.isEmpty()) {
            if (server != null) {
                server.getPlayerList().broadcastSystemMessage(
                        Component.literal("搂b[AI] 鈫?搂f" + pname + "搂b " + response), false);
            }
            return true;
        }
        if (needsApproval(cmd)) {
            int num = addPendingCommand(player.getUUID(), cmd);
            player.sendSystemMessage(Component.literal("搂e[AI] 闇€瑕佸鎵?搂6[#" + num + "] 搂e: /" + cmd
                    + "\n搂e浣跨敤 搂a/aiaccept " + num + " 搂e鎵瑰噯鎴?搂c/aireject " + num + " 搂e鎷掔粷"));
            return false;
        }
        if (server != null) {
            try {
                server.getCommands().getDispatcher().execute(cmd, server.createCommandSourceStack());
                addToChatLog(player.getScoreboardName(), "/" + cmd, isAdminPlayer(player));
            } catch (CommandSyntaxException e) {
                player.sendSystemMessage(Component.literal("搂c[AI] 鎸囦护璇硶閿欒: " + e.getMessage()));
                return false;
            }
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal("搂7[AI] 鈫?搂e/" + cmd), false);
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
        // 涓ユ牸妯″紡锛氫粎鐧藉悕鍗曞唴鐨勭粷瀵瑰畨鍏ㄥ懡浠ゅ彲鍏嶅鎵?        if (mod.getConfig().isStrictMode()) {
            for (String safe : mod.getConfig().getSafeCommands()) {
                if (safe.contains(" ")) {
                    // 澶氳瘝妯″紡锛堝 "data get"锛夊尮閰嶅懡浠ゅ墠缂€
                    if (cmd.toLowerCase().startsWith(safe)) return false;
                } else {
                    // 鍗曡瘝妯″紡鍖归厤鏍瑰懡浠ゅ悕
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
                        鐗堟湰: %s | 鍦ㄧ嚎(%d/%d): [%s]
                        璇磋瘽鑰? 鎺у埗鍙?                        """, server != null ? server.getServerModName() : "?",
                        server != null ? server.getPlayerCount() : 0,
                        server != null ? server.getMaxPlayers() : 0,
                        playerList.isEmpty() ? "鏃? : playerList);
                String userContent = context + "\n\n鎺у埗鍙?璇? " + query;

                List<OpenAIClient.ChatMessage> messages = new ArrayList<>();
                messages.add(new OpenAIClient.ChatMessage("system", mod.getConfig().getSystemPrompt()));

                String recentChat;
                synchronized (chatLog) {
                    recentChat = String.join("\n", chatLog);
                }
                if (!recentChat.isEmpty()) {
                    messages.add(new OpenAIClient.ChatMessage("system",
                            "鏈€杩戠殑鑱婂ぉ璁板綍锛堜簡瑙ｅ綋鍓嶆皼鍥达級:\n" + recentChat));
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
                            results.add("鎺у埗鍙版棤娉曡幏鍙栬皟璇曚俊鎭?);
                        } else {
                            results.add("鏈煡宸ュ叿: " + tc.name);
                        }
                    }
                    return results;
                });

                String reply = result.orElse("AI 鏃犲搷搴?);
                // Send reply directly to console source
                src.sendSuccess(() -> Component.literal("搂e[AI鍥炲]\n搂f" + reply), false);
                // Log AI reply to chat log for review
                addToChatLog("AI 鈫?鎺у埗鍙?, reply);
                MCAIMod.LOGGER.info("Console AI reply: {}", reply);
            } catch (Exception e) {
                MCAIMod.LOGGER.error("Console AI query failed", e);
                src.sendFailure(Component.literal("搂cAI 鏌ヨ澶辫触: " + e.getMessage()));
            }
        });
    }
}
