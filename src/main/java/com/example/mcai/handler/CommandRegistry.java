package com.example.mcai.handler;
import com.example.mcai.kb.SearchRouter;
import com.example.mcai.kb.SearchProvider;
import com.example.mcai.kb.SearchResult;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import java.util.List;
import java.util.UUID;
public class CommandRegistry {
    private final ChatHandler chatHandler;
    private final CommandExecutionService cmdExec;
    private final SuggestionProvider<CommandSourceStack> pendingIdSuggestions;
    public CommandRegistry(ChatHandler chatHandler, CommandExecutionService cmdExec) {
        this.chatHandler = chatHandler; this.cmdExec = cmdExec;
        this.pendingIdSuggestions = (ctx, builder) -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            if (p != null) {
                for (var pending : this.cmdExec.getPendingCommands(p.getUUID())) {
                    builder.suggest(String.valueOf(pending.id), Component.literal(pending.command));
                }
                for (var chain : this.cmdExec.getPlayerPendingChains(p.getUUID())) {
                    builder.suggest(String.valueOf(chain.id), Component.translatable("mcai.cmd.suggest.chain", chain.commands.size()));
                }
            } else {
                for (var pending : this.cmdExec.getAllPendingCommands()) {
                    builder.suggest(String.valueOf(pending.id), Component.literal(pending.requesterName + ": " + pending.command));
                }
                for (var chain : this.cmdExec.getAllPendingChains()) {
                    builder.suggest(String.valueOf(chain.id), Component.translatable("mcai.cmd.suggest.chain_all", chain.requesterName, chain.commands.size()));
                }
            }
            return builder.buildFuture();
        };
    }
    public LiteralArgumentBuilder<CommandSourceStack> createAICommand() {
        return Commands.literal("ai").then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
            if (!chatHandler.isChatEnabled()) { ctx.getSource().sendFailure(Component.translatable("mcai.chat.disabled")); return 0; }
            ServerPlayer player = ctx.getSource().getPlayer(); String msg = StringArgumentType.getString(ctx, "message");
            if (player != null) {
                // Check cooldown BEFORE broadcasting
                Component cooldownError = chatHandler.checkPlayerCanUseAI(player);
                if (cooldownError != null) { ctx.getSource().sendFailure(cooldownError); return 0; }
                var server = chatHandler.getServer();
                if (server != null) server.getPlayerList().broadcastSystemMessage(Component.translatable("mcai.cmd.ai.broadcast", player.getScoreboardName(), msg), false);
                chatHandler.getChatLog().add(player.getScoreboardName(), msg);
                ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.ai.thinking"), false);
                chatHandler.handleAIQuery(player, msg);
            } else {
                chatHandler.getChatLog().add("控制台", msg, true);
                ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.ai.console_sending"), false);
                chatHandler.handleConsoleAIQuery(ctx.getSource(), msg);
            }
            return Command.SINGLE_SUCCESS;
        })).executes(ctx -> { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.ai.usage")); return 0; });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createWikiCommand() {
        return Commands.literal("aikb").requires(CommandExecutionService::isAdminOrConsole)
                .then(Commands.argument("query", StringArgumentType.greedyString()).executes(ctx -> {
                    String q = StringArgumentType.getString(ctx, "query");
                    var src = ctx.getSource();
                    var srv = src.getServer();
                    if (srv == null) { src.sendFailure(Component.translatable("mcai.cmd.server_not_ready")); return 0; }
                    src.sendSuccess(() -> Component.translatable("mcai.chat.searching"), false);
                    SearchProvider knowledgeBase = chatHandler.getMod().getSearchRouter();
                    var router = (knowledgeBase instanceof SearchRouter) ? (SearchRouter) knowledgeBase : null;
                    java.util.concurrent.ExecutorService asyncPool = router != null ? router.getExecutor() : null;
                    Runnable searchTask = () -> {
                        String r = ToolDispatcher.formatSearchResult(knowledgeBase.search(q, 7));
                        srv.execute(() -> src.sendSuccess(() -> Component.translatable("mcai.cmd.kb.result", r), false));
                    };
                    if (asyncPool != null) {
                        asyncPool.execute(searchTask);
                    } else {
                        searchTask.run();
                    }
                    return 1;
                })).executes(ctx -> { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.kb.usage")); return 0; });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createQueryCommand() {
        return Commands.literal("aiquery").requires(CommandExecutionService::isAdminOrConsole).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayer();
            if (player != null) {
                var cmds = cmdExec.getPendingCommands(player.getUUID());
                var chains = cmdExec.getPlayerPendingChains(player.getUUID());
                if (cmds.isEmpty() && chains.isEmpty()) { ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.none"), false); return 0; }
                int total = cmds.size() + chains.size();
                ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.title", total), false);
                // Show single commands
                if (!cmds.isEmpty()) {
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.single_header"), false);
                    for (var pending : cmds) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.line", pending.id, pending.command), false);
                    }
                }
                // Show chains
                if (!chains.isEmpty()) {
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.chain_header"), false);
                    for (var chain : chains) {
                        final long chainId = chain.id;
                        final int cmdCount = chain.commands.size();
                        final int interval = chain.intervalSeconds;
                        final List<String> cmdList = chain.commands;
                        ctx.getSource().sendSuccess(() -> {
                            MutableComponent header = Component.translatable("mcai.cmd.query.chain_line", chainId, cmdCount);
                            if (interval > 0) header = header.append(Component.translatable("mcai.cmd.query.chain_interval", interval));
                            MutableComponent msg = header.append("\n");
                            for (int i = 0; i < cmdList.size(); i++) {
                                msg = msg.append(Component.translatable("mcai.cmd.query.chain_item", i + 1, cmdList.get(i)).append("\n"));
                            }
                            return msg;
                        }, false);
                    }
                }
                ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.hint"), false);
            } else {
                var all = cmdExec.getAllPendingCommands();
                var allChains = cmdExec.getAllPendingChains();
                if (all.isEmpty() && allChains.isEmpty()) { ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.none"), false); return 0; }
                ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.title_all"), false);
                // Show single commands
                if (!all.isEmpty()) {
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.single_header"), false);
                    for (var pending : all) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.line_all", pending.id, pending.requesterName, pending.command), false);
                    }
                }
                // Show chains
                if (!allChains.isEmpty()) {
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.chain_header"), false);
                    for (var chain : allChains) {
                        final long chainId = chain.id;
                        final String requester = chain.requesterName;
                        final int cmdCount = chain.commands.size();
                        final int interval = chain.intervalSeconds;
                        final List<String> cmdList = chain.commands;
                        ctx.getSource().sendSuccess(() -> {
                            MutableComponent header = Component.translatable("mcai.cmd.query.chain_line_all", chainId, requester, cmdCount);
                            if (interval > 0) header = header.append(Component.translatable("mcai.cmd.query.chain_interval", interval));
                            MutableComponent msg = header.append("\n");
                            for (int i = 0; i < cmdList.size(); i++) {
                                msg = msg.append(Component.translatable("mcai.cmd.query.chain_item", i + 1, cmdList.get(i)).append("\n"));
                            }
                            return msg;
                        }, false);
                    }
                }
            }
            return 1;
        });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createAcceptCommand() {
        return Commands.literal("aiaccept").requires(CommandExecutionService::isAdminOrConsole)
                .then(Commands.argument("id", LongArgumentType.longArg(1)).suggests(pendingIdSuggestions).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    long id = LongArgumentType.getLong(ctx, "id");
                    if (player == null) { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.accept.no_console")); return 0; }
                    // Try single command first, then chain
                    int r = cmdExec.approveCommand(player, id);
                    if (r == 0) {
                        r = cmdExec.approveChain(player, id);
                    }
                    if (r == 0) ctx.getSource().sendFailure(Component.translatable("mcai.cmd.accept.invalid"));
                    return r;
                })).executes(ctx -> { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.accept.usage")); return 0; });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createRejectCommand() {
        return Commands.literal("aireject").requires(CommandExecutionService::isAdminOrConsole)
                .then(Commands.argument("id", LongArgumentType.longArg(1)).suggests(pendingIdSuggestions).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    long id = LongArgumentType.getLong(ctx, "id");
                    if (player == null) { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.reject.no_console")); return 0; }
                    // Try single command first, then chain
                    int r = cmdExec.rejectCommand(player, id);
                    if (r == 0) {
                        r = cmdExec.rejectChain(player, id);
                    }
                    if (r == 0) ctx.getSource().sendFailure(Component.translatable("mcai.cmd.reject.invalid"));
                    return r;
                })).executes(ctx -> { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.reject.usage")); return 0; });
    }

    public LiteralArgumentBuilder<CommandSourceStack> createCancelCommand() {
        return Commands.literal("aicancel")
                // /aicancel <id> - cancel specific command
                .then(Commands.argument("id", LongArgumentType.longArg(1)).suggests(pendingIdSuggestions).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.cancel.no_console")); return 0; }
                    long id = LongArgumentType.getLong(ctx, "id");
                    return cmdExec.cancelByPlayer(player, id);
                }))
                // /aicancel all - cancel all pending commands
                .then(Commands.literal("all").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.cancel.no_console")); return 0; }
                    return cmdExec.cancelAllByPlayer(player);
                }))
                // /aicancel - cancel latest pending command
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer();
                    if (player == null) { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.cancel.no_console")); return 0; }
                    return cmdExec.cancelLatestByPlayer(player);
                });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createClearCommand() {
        return Commands.literal("aiclear").requires(CommandExecutionService::isAdminOrConsole).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayer(); if (player == null) return 0;
            chatHandler.clearHistory(player.getUUID()); cmdExec.cleanupPlayer(player.getUUID());
            ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.clear.done"), false); return 1;
        });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createScoreCommand() {
        return Commands.literal("aiscore").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayer();
            if (player == null) { ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.score.no_console"), false); return 0; }
            var bt = chatHandler.getBehaviorTracker(); var cfg = chatHandler.getConfig();
            int score = bt.getScore(player.getUUID());
            ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.score.title"), false);
            ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.score.value", score >= 0 ? "§a" : "§c", score), false);
            ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.score.rules", cfg.getYellowCardThreshold(), cfg.getRedCardThreshold(), cfg.getScoreRecoveryPerInterval()), false);
            return Command.SINGLE_SUCCESS;
        });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createReloadCommand() {
        return Commands.literal("aireload").requires(CommandExecutionService::isAdminOrConsole).executes(ctx -> {
            chatHandler.reloadAll();
            ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.reload.done"), true); return 1;
        });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createResetPromptsCommand() {
        return Commands.literal("airesetprompts").requires(CommandExecutionService::isAdminOrConsole).executes(ctx -> {
            boolean ok = chatHandler.getMod().getConfig().resetPromptFiles();
            chatHandler.getMod().reloadConfig();
            ctx.getSource().sendSuccess(() -> ok ? Component.translatable("mcai.cmd.resetprompts.done") : Component.translatable("mcai.cmd.resetprompts.fail"), true);
            return ok ? 1 : 0;
        });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createKillCommand() {
        return Commands.literal("aikill").requires(CommandExecutionService::isAdminOrConsole).executes(ctx -> {
            int discarded = chatHandler.killAIThreads();
            ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.kill.done", discarded), true); return 1;
        });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createControlCommand() {
        return Commands.literal("aicontrol").requires(CommandExecutionService::isAdminOrConsole)
                .then(Commands.literal("chat")
                        .then(Commands.literal("on").executes(ctx -> {
                            chatHandler.setChatEnabled(true);
                            ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.control.chat.on"), true);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            chatHandler.setChatEnabled(false);
                            ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.control.chat.off"), true);
                            return 1;
                        })))
                .then(Commands.literal("review")
                        .then(Commands.literal("on").executes(ctx -> {
                            var crs = chatHandler.getMod().getChatReviewSystem();
                            if (crs != null) { crs.setReviewEnabled(true); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.control.review.on"), true); }
                            else { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.control.review.unavailable")); }
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            var crs = chatHandler.getMod().getChatReviewSystem();
                            if (crs != null) { crs.setReviewEnabled(false); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.control.review.off"), true); }
                            else { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.control.review.unavailable")); }
                            return 1;
                        })))
                .executes(ctx -> {
                    boolean chat = chatHandler.isChatEnabled();
                    var crs = chatHandler.getMod().getChatReviewSystem();
                    boolean review = crs != null && crs.isReviewEnabled();
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.control.status",
                            chat ? "§a开启" : "§c关闭",
                            review ? "§a开启" : "§c关闭"), false);
                    return 1;
                });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createDebugCommand() {
        var dbg = chatHandler.getMod().getDebugLogger();
        return Commands.literal("aidebug").requires(CommandExecutionService::isAdminOrConsole)
                .then(Commands.literal("start").executes(ctx -> {
                    dbg.start();
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.debug.started", dbg.getCurrentLogFile()), true);
                    return 1;
                }))
                .then(Commands.literal("stop").executes(ctx -> {
                    String file = dbg.getCurrentLogFile();
                    dbg.stop();
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.debug.stopped", file), true);
                    return 1;
                }))
                .then(Commands.literal("show").executes(ctx -> {
                    // Show last session
                    var session = dbg.getLastSession();
                    if (session == null) {
                        ctx.getSource().sendFailure(Component.translatable("mcai.cmd.debug.no_sessions"));
                        return 0;
                    }
                    sendSessionToPlayer(ctx, session);
                    return 1;
                }).then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(ctx -> {
                    // Show specific session by ID
                    int id = IntegerArgumentType.getInteger(ctx, "id");
                    var session = dbg.getSession(id);
                    if (session == null) {
                        ctx.getSource().sendFailure(Component.translatable("mcai.cmd.debug.session_not_found", id));
                        return 0;
                    }
                    sendSessionToPlayer(ctx, session);
                    return 1;
                })))
                .then(Commands.literal("list").executes(ctx -> {
                    var sessions = dbg.getLastSessions(10);
                    if (sessions.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.debug.no_sessions"), false);
                        return 1;
                    }
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.debug.session_list_header", sessions.size()), false);
                    for (var s : sessions) {
                        String time = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(s.timestamp));
                        String thinkingInfo = s.thinking != null ? "§7[§b思考 §e" + s.thinking.length() + "字§7]" : "";
                        String toolInfo = s.toolCalls.isEmpty() ? "" : "§7[§d工具 §e" + s.toolCalls.size() + "次§7]";
                        String respInfo = s.response != null ? "§7[§a回复 §e" + Math.min(s.response.length(), 50) + "字§7]" : "";
                        ctx.getSource().sendSuccess(() -> Component.literal(
                                "§6#" + s.id + " §f" + time + " §7" + s.playerName + " §8» §f" +
                                truncate(s.query, 40) + " " + thinkingInfo + toolInfo + respInfo
                        ), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("clear").executes(ctx -> {
                    dbg.clearSessions();
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.debug.sessions_cleared"), false);
                    return 1;
                }))
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.debug.status", dbg.isEnabled() ? "§a开启" : "§c关闭", dbg.getCurrentLogFile() != null ? dbg.getCurrentLogFile() : "-"), false);
                    return 1;
                });
    }

    private void sendSessionToPlayer(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, AIDebugLogger.DebugSession session) {
        String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date(session.timestamp));
        // Header
        ctx.getSource().sendSuccess(() -> Component.literal("§8════════════════════════════════════════"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§6§lAI Debug Session §e#" + session.id), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7时间: §f" + time + "  §7玩家: §f" + session.playerName), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7提问: §f" + session.query), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§8────────────────────────────────────────"), false);

        // Thinking
        if (session.thinking != null && !session.thinking.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§b§l[思考过程] §7(" + session.thinking.length() + " 字)"), false);
            sendLongText(ctx, "§8" + session.thinking);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§b§l[思考过程] §7(无)"), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§8────────────────────────────────────────"), false);

        // Tool calls
        if (session.toolCalls.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§d§l[工具调用] §7(无)"), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§d§l[工具调用] §7(共 " + session.toolCalls.size() + " 次)"), false);
            int idx = 0;
            for (var tc : session.toolCalls) {
                idx++;
                final int finalIdx = idx;
                final String toolName = tc.toolName;
                final String args = tc.arguments;
                final String result = tc.result;
                ctx.getSource().sendSuccess(() -> Component.literal("§d  [" + finalIdx + "] §e" + toolName + "§8(" + truncate(args, 200) + "§8)"), false);
                if (result != null) {
                    sendLongText(ctx, "§8    → " + truncate(result, 500));
                } else {
                    ctx.getSource().sendSuccess(() -> Component.literal("§8    → §7(无结果)"), false);
                }
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§8────────────────────────────────────────"), false);

        // Response
        if (session.response != null && !session.response.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§a§l[AI 回复] §7(" + session.response.length() + " 字)"), false);
            sendLongText(ctx, "§f" + session.response);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal("§a§l[AI 回复] §7(无)"), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§8════════════════════════════════════════"), false);
    }

    private void sendLongText(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx, String text) {
        // Minecraft chat messages have a practical limit; split into chunks
        int maxLen = 8000;
        if (text.length() <= maxLen) {
            final String finalText = text;
            ctx.getSource().sendSuccess(() -> Component.literal(finalText), false);
        } else {
            int chunks = (text.length() + maxLen - 1) / maxLen;
            for (int i = 0; i < chunks; i++) {
                int start = i * maxLen;
                int end = Math.min(start + maxLen, text.length());
                final String chunk = text.substring(start, end);
                final int chunkNum = i + 1;
                final int totalChunks = chunks;
                ctx.getSource().sendSuccess(() -> Component.literal("§7[" + chunkNum + "/" + totalChunks + "] " + chunk), false);
            }
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
    public LiteralArgumentBuilder<CommandSourceStack> createPersonaCommand() {
        var personaMgr = chatHandler.getMod().getPersonaManager();
        return Commands.literal("aipersona").requires(CommandExecutionService::isAdminOrConsole)
                // /aipersona list
                .then(Commands.literal("list").executes(ctx -> {
                    var personas = personaMgr.getAvailablePersonas();
                    String active = chatHandler.getConfig().getActivePersona();
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.persona.list_header", personas.size()), false);
                    for (int i = 0; i < personas.size(); i++) {
                        var rec = personas.get(i);
                        boolean isActive = rec.id.equals(active);
                        final int idx = i;
                        final Component displayName = resolvePersonaName(rec);
                        final Component summary = resolvePersonaSummary(rec);
                        String setCmd = "/aipersona set " + idx;
                        String viewCmd = "/aipersona view " + idx;

                        MutableComponent line = Component.literal("§7  ")
                                .append(Component.literal("§7[").withStyle(s -> s
                                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(setCmd))
                                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                                Component.translatable("mcai.cmd.persona.hover.set", idx)))))
                                .append(Component.literal("§e" + idx).withStyle(s -> s
                                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(setCmd))
                                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                                Component.translatable("mcai.cmd.persona.hover.set", idx)))))
                                .append(Component.literal("§7] "))
                                .append(displayName.copy().withStyle(s -> s
                                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(setCmd))
                                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                                Component.translatable("mcai.cmd.persona.hover.set", idx)))))
                                .append(Component.literal("§8 - §7"))
                                .append(summary.copy().withStyle(s -> s
                                        .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(viewCmd))
                                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                                Component.translatable("mcai.cmd.persona.hover.view", idx)))))
                                .append(isActive ? Component.translatable("mcai.cmd.persona.active_marker") : Component.literal(""));
                        ctx.getSource().sendSuccess(() -> line, false);
                    }
                    return 1;
                }))
                // /aipersona set <index>
                .then(Commands.literal("set").then(
                        Commands.argument("index", IntegerArgumentType.integer(0))
                                .suggests((ctx, builder) -> {
                                    var personas = personaMgr.getAvailablePersonas();
                                    for (int i = 0; i < personas.size(); i++) {
                                        builder.suggest(String.valueOf(i), resolvePersonaName(personas.get(i)));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    int index = IntegerArgumentType.getInteger(ctx, "index");
                                    var personas = personaMgr.getAvailablePersonas();
                                    if (index < 0 || index >= personas.size()) {
                                        ctx.getSource().sendFailure(Component.translatable("mcai.cmd.persona.invalid_index", index));
                                        return 0;
                                    }
                                    var record = personas.get(index);
                                    chatHandler.getConfig().setActivePersona(record.id);
                                    chatHandler.getConfig().save();
                                    var dbg = chatHandler.getMod().getDebugLogger();
                                    if (dbg.isEnabled()) dbg.logInfo("Persona switched to: " + record.id + " (" + record.name + ") index=" + index);
                                    final Component personaDisplayName = resolvePersonaName(record);
                                    final int personaIdx = index;
                                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.persona.set_done", personaIdx, personaDisplayName), true);
                                    return 1;
                                })
                ))
                // /aipersona current
                .then(Commands.literal("current").executes(ctx -> {
                    String active = chatHandler.getConfig().getActivePersona();
                    var record = personaMgr.getPersona(active);
                    if (record == null) record = PersonaManager.DEFAULT_PERSONA;
                    var personas = personaMgr.getAvailablePersonas();
                    int idx = 0;
                    for (int i = 0; i < personas.size(); i++) {
                        if (personas.get(i).id.equals(active)) { idx = i; break; }
                    }
                    final int finalIdx = idx;
                    final Component displayName = resolvePersonaName(record);
                    final Component summary = resolvePersonaSummary(record);
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.persona.current", finalIdx, displayName, summary), false);
                    return 1;
                }))
                // /aipersona view <index>
                .then(Commands.literal("view").then(
                        Commands.argument("index", IntegerArgumentType.integer(0))
                                .suggests((ctx, builder) -> {
                                    var personas = personaMgr.getAvailablePersonas();
                                    for (int i = 0; i < personas.size(); i++) {
                                        builder.suggest(String.valueOf(i), resolvePersonaName(personas.get(i)));
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    int index = IntegerArgumentType.getInteger(ctx, "index");
                                    var personas = personaMgr.getAvailablePersonas();
                                    if (index < 0 || index >= personas.size()) {
                                        ctx.getSource().sendFailure(Component.translatable("mcai.cmd.persona.invalid_index", index));
                                        return 0;
                                    }
                                    var record = personas.get(index);
                                    if ("default".equals(record.id)) {
                                        ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.persona.view_default"), false);
                                        return 1;
                                    }
                                    final int personaIdx = index;
                                    final Component displayName = resolvePersonaName(record);
                                    final Component summary = resolvePersonaSummary(record);
                                    final String personaContent = record.content;
                                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.persona.view_header", personaIdx, displayName, summary), false);
                                    ctx.getSource().sendSuccess(() -> Component.literal("§7" + personaContent), false);
                                    return 1;
                                })
                ))
                // /aipersona reload
                .then(Commands.literal("reload").executes(ctx -> {
                    personaMgr.refreshPersonaList();
                    var dbg = chatHandler.getMod().getDebugLogger();
                    if (dbg.isEnabled()) dbg.logInfo("Persona list reloaded, count=" + personaMgr.getAvailablePersonas().size());
                    int total = personaMgr.getLastTotalFiles();
                    int loaded = personaMgr.getLastLoadedCount();
                    int failed = personaMgr.getLastFailedCount();
                    var dupes = personaMgr.getLastDuplicateIds();
                    var fails = personaMgr.getLastFailedFiles();
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.persona.reloaded", total, loaded, failed), true);
                    if (!dupes.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.persona.duplicate_warn", String.join(", ", dupes)), false);
                    }
                    if (!fails.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.persona.failed_warn", String.join(", ", fails)), false);
                    }
                    return 1;
                }))
                // /aipersona (no args)
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.persona.usage"), false);
                    return 0;
                });
    }

    /** 解析人格显示名称（i18n key → 翻译组件；普通人格 → 按生效语言取本地化名称） */
    private Component resolvePersonaName(PersonaManager.PersonaRecord rec) {
        if (rec.i18n) return Component.translatable(rec.name);
        String lang = chatHandler.getMod().getPersonaManager().resolveEffectiveLanguage();
        return Component.literal(rec.localizedName(lang));
    }

    /** 解析人格简介（i18n key → 翻译组件；普通人格 → 按生效语言取本地化简介） */
    private Component resolvePersonaSummary(PersonaManager.PersonaRecord rec) {
        if (rec.i18n) return Component.translatable(rec.summary);
        String lang = chatHandler.getMod().getPersonaManager().resolveEffectiveLanguage();
        return Component.literal(rec.localizedSummary(lang));
    }

    public LiteralArgumentBuilder<CommandSourceStack> createTestCommand() {
        var ps = playerNameSuggestions(); var bt = chatHandler.getMod().getBehaviorTracker();
        var crs = chatHandler.getMod().getChatReviewSystem(); var cl = chatHandler.getChatLog();
        return Commands.literal("aitest").requires(src -> { var p = src.getPlayer(); if (p == null || src.getServer() == null) return false; return isOp(src.getServer(), p.getGameProfile()); })
                .then(Commands.literal("score").then(Commands.argument("player", StringArgumentType.word()).suggests(ps).executes(ctx -> { String n = StringArgumentType.getString(ctx, "player"); UUID id = lookupPlayer(n); if (id == null) { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.player_offline", n)); return 0; } ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.score_result", n, bt.getScore(id)), false); return Command.SINGLE_SUCCESS; })))
                .then(Commands.literal("penalty").then(Commands.argument("player", StringArgumentType.word()).suggests(ps).then(Commands.argument("points", IntegerArgumentType.integer(-100, -1)).executes(ctx -> { String n = StringArgumentType.getString(ctx, "player"); UUID id = lookupPlayer(n); if (id == null) { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.player_offline", n)); return 0; } int pts = IntegerArgumentType.getInteger(ctx, "points"); int ns = bt.addScore(id, pts); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.penalty_result", n, pts, ns), false); return Command.SINGLE_SUCCESS; }))))
                .then(Commands.literal("reset").then(Commands.argument("player", StringArgumentType.word()).suggests(ps).executes(ctx -> { String n = StringArgumentType.getString(ctx, "player"); UUID id = lookupPlayer(n); if (id == null) { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.player_offline", n)); return 0; } bt.resetScore(id); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.reset_result", n), false); return Command.SINGLE_SUCCESS; })))
                .then(Commands.literal("set").then(Commands.argument("player", StringArgumentType.word()).suggests(ps).then(Commands.argument("score", IntegerArgumentType.integer(-100, 0)).executes(ctx -> { String n = StringArgumentType.getString(ctx, "player"); UUID id = lookupPlayer(n); if (id == null) { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.player_offline", n)); return 0; } int s = IntegerArgumentType.getInteger(ctx, "score"); bt.setScore(id, s); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.set_result", n, s), false); return Command.SINGLE_SUCCESS; }))))
                .then(Commands.literal("review").executes(ctx -> { if (crs != null) { ServerPlayer p = ctx.getSource().getPlayer(); crs.triggerManualReview(p); } else { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.test.review_unavailable")); } return Command.SINGLE_SUCCESS; }))
                .then(Commands.literal("chatlog").executes(ctx -> { String log = cl.peek(); if (log.isEmpty()) { ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.chatlog_empty"), false); } else { ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.chatlog_output", log), false); } return Command.SINGLE_SUCCESS; }))
                .executes(ctx -> { ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.title"), false); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.score"), false); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.penalty"), false); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.reset"), false); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.set"), false); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.review"), false); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.chatlog"), false); return Command.SINGLE_SUCCESS; });
    }
    private SuggestionProvider<CommandSourceStack> playerNameSuggestions() { return (ctx, builder) -> { var srv = chatHandler.getServer(); if (srv != null) { for (var p : srv.getPlayerList().getPlayers()) builder.suggest(p.getScoreboardName()); } return builder.buildFuture(); }; }
    private static boolean isOp(MinecraftServer srv, GameProfile profile) { return srv.getPlayerList().isOp(new NameAndId(profile)); }
    private UUID lookupPlayer(String name) { var srv = chatHandler.getServer(); if (srv == null) return null; for (ServerPlayer p : srv.getPlayerList().getPlayers()) { if (p.getScoreboardName().equalsIgnoreCase(name)) return p.getUUID(); } return null; }
}
