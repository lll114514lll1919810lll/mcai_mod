package com.example.mcai.handler;
import com.example.mcai.kb.KnowledgeBase;
import com.example.mcai.kb.SearchProvider;
import com.example.mcai.kb.SearchRouter;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import java.util.List;
import java.util.UUID;
public class CommandRegistry {
    private final ChatHandler chatHandler;
    private final CommandExecutionService cmdExec;
    private final SearchProvider knowledgeBase;
    private final SuggestionProvider<CommandSourceStack> pendingIdSuggestions;
    public CommandRegistry(ChatHandler chatHandler, CommandExecutionService cmdExec, SearchProvider kb) {
        this.chatHandler = chatHandler; this.cmdExec = cmdExec; this.knowledgeBase = kb;
        this.pendingIdSuggestions = (ctx, builder) -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            if (p != null) {
                for (var pending : this.cmdExec.getPendingCommands(p.getUUID())) {
                    builder.suggest(String.valueOf(pending.id), Component.literal(pending.command));
                }
            } else {
                for (var pending : this.cmdExec.getAllPendingCommands()) {
                    builder.suggest(String.valueOf(pending.id), Component.literal(pending.requesterName + ": " + pending.command));
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
                    if (srv == null) { src.sendFailure(Component.literal("Server not ready")); return 0; }
                    src.sendSuccess(() -> Component.translatable("mcai.chat.thinking"), false);
                    var router = (knowledgeBase instanceof SearchRouter) ? (SearchRouter) knowledgeBase : null;
                    java.util.concurrent.ExecutorService asyncPool = router != null ? router.getExecutor() : null;
                    Runnable searchTask = () -> {
                        String r = KnowledgeBase.formatSearchResult(knowledgeBase.search(q, 5));
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
                if (cmds.isEmpty()) { ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.none"), false); return 0; }
                ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.title", cmds.size()), false);
                for (var pending : cmds) {
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.line", pending.id, pending.command), false);
                }
                ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.hint"), false);
            } else {
                var all = cmdExec.getAllPendingCommands();
                if (all.isEmpty()) { ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.none"), false); return 0; }
                ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.title_all"), false);
                for (var pending : all) {
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.line_all", pending.id, pending.requesterName, pending.command), false);
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
                    int r = cmdExec.approveCommand(player, id);
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
                    int r = cmdExec.rejectCommand(player, id);
                    if (r == 0) ctx.getSource().sendFailure(Component.translatable("mcai.cmd.reject.invalid"));
                    return r;
                })).executes(ctx -> { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.reject.usage")); return 0; });
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
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.debug.status", dbg.isEnabled() ? "§a开启" : "§c关闭", dbg.getCurrentLogFile() != null ? dbg.getCurrentLogFile() : "-"), false);
                    return 1;
                });
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
                .then(Commands.literal("chatlog").executes(ctx -> { String log = cl.peek(); if (log.isEmpty()) { ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.chatlog_empty"), false); } else { ctx.getSource().sendSuccess(() -> Component.literal("§e==== 聊天记录 ====\n§7"+log), false); } return Command.SINGLE_SUCCESS; }))
                .executes(ctx -> { ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.title"), false); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.score"), false); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.penalty"), false); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.reset"), false); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.set"), false); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.review"), false); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.test.chatlog"), false); return Command.SINGLE_SUCCESS; });
    }
    private SuggestionProvider<CommandSourceStack> playerNameSuggestions() { return (ctx, builder) -> { var srv = chatHandler.getServer(); if (srv != null) { for (var p : srv.getPlayerList().getPlayers()) builder.suggest(p.getScoreboardName()); } return builder.buildFuture(); }; }
    private static boolean isOp(MinecraftServer srv, GameProfile profile) { return srv.getPlayerList().isOp(new NameAndId(profile)); }
    private UUID lookupPlayer(String name) { var srv = chatHandler.getServer(); if (srv == null) return null; for (ServerPlayer p : srv.getPlayerList().getPlayers()) { if (p.getScoreboardName().equalsIgnoreCase(name)) return p.getUUID(); } return null; }
}
