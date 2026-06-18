package com.example.mcai.handler;
import com.example.mcai.kb.KnowledgeBase;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import java.util.concurrent.atomic.AtomicInteger;
public class CommandRegistry {
    private final ChatHandler chatHandler;
    private final CommandExecutionService cmdExec;
    private final KnowledgeBase knowledgeBase;
    private final SuggestionProvider<CommandSourceStack> pendingIdSuggestions;
    public CommandRegistry(ChatHandler chatHandler, CommandExecutionService cmdExec, KnowledgeBase kb) {
        this.chatHandler = chatHandler; this.cmdExec = cmdExec; this.knowledgeBase = kb;
        this.pendingIdSuggestions = (ctx, builder) -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            if (p != null) { var cmds = this.cmdExec.getPendingCommands(p.getUUID()); if (cmds != null) { for (int i = 1; i <= cmds.size(); i++) builder.suggest(i); } }
            else { int total = 0; for (var e : this.cmdExec.getAllPendingCommands().entrySet()) { for (int i = 0; i < e.getValue().size(); i++) { total++; builder.suggest(total); } } }
            return builder.buildFuture();
        };
    }
    public LiteralArgumentBuilder<CommandSourceStack> createAICommand() {
        return Commands.literal("ai").then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
            if (!chatHandler.isAIEnabled()) { ctx.getSource().sendFailure(Component.translatable("mcai.chat.disabled")); return 0; }
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
                    String q = StringArgumentType.getString(ctx, "query"); String r = knowledgeBase.search(q, 5);
                    ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.kb.result", r), false); return 1;
                })).executes(ctx -> { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.kb.usage")); return 0; });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createQueryCommand() {
        return Commands.literal("aiquery").requires(CommandExecutionService::isAdminOrConsole).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayer();
            if (player != null) {
                List<String> cmds = cmdExec.getPendingCommands(player.getUUID());
                if (cmds == null || cmds.isEmpty()) { ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.none"), false); return 0; }
                ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.title", cmds.size()), false);
                for (int i = 0; i < cmds.size(); i++) { final int n = i+1; final String c = cmds.get(i); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.line", n, c), false); }
                ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.hint"), false);
            } else {
                var all = cmdExec.getAllPendingCommands();
                if (all.isEmpty()) { ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.none"), false); return 0; }
                ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.title_all"), false);
                final AtomicInteger total = new AtomicInteger(0);
                for (var e : all.entrySet()) {
                    final String fn; var srv = chatHandler.getServer();
                    if (srv != null) { var p = srv.getPlayerList().getPlayer(e.getKey()); fn = p != null ? p.getScoreboardName() : "?"; } else { fn = "?"; }
                    for (int i = 0; i < e.getValue().size(); i++) { final int ft = total.incrementAndGet(); final String c = e.getValue().get(i); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.query.line_all", ft, fn, c), false); }
                }
            }
            return 1;
        });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createAcceptCommand() {
        return Commands.literal("aiaccept").requires(CommandExecutionService::isAdminOrConsole)
                .then(Commands.argument("number", IntegerArgumentType.integer(1)).suggests(pendingIdSuggestions).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer(); int num = IntegerArgumentType.getInteger(ctx, "number");
                    if (player == null) { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.accept.no_console")); return 0; }
                    int r = cmdExec.approveCommand(player, num); if (r == 0) ctx.getSource().sendFailure(Component.translatable("mcai.cmd.accept.invalid")); return r;
                })).executes(ctx -> { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.accept.usage")); return 0; });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createRejectCommand() {
        return Commands.literal("aireject").requires(CommandExecutionService::isAdminOrConsole)
                .then(Commands.argument("number", IntegerArgumentType.integer(1)).suggests(pendingIdSuggestions).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer(); int num = IntegerArgumentType.getInteger(ctx, "number");
                    if (player == null) { ctx.getSource().sendFailure(Component.translatable("mcai.cmd.reject.no_console")); return 0; }
                    int r = cmdExec.rejectCommand(player, num); if (r == 0) ctx.getSource().sendFailure(Component.translatable("mcai.cmd.accept.invalid")); return r;
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
    public LiteralArgumentBuilder<CommandSourceStack> createOnCommand() {
        return Commands.literal("aion").requires(CommandExecutionService::isAdminOrConsole).executes(ctx -> {
            chatHandler.setAIEnabled(true);
            ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.on.done"), true);
            var srv = chatHandler.getServer();
            if (srv != null) srv.getPlayerList().broadcastSystemMessage(Component.translatable("mcai.cmd.on.broadcast"), false);
            return 1;
        });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createOffCommand() {
        return Commands.literal("aioff").requires(CommandExecutionService::isAdminOrConsole).executes(ctx -> {
            chatHandler.setAIEnabled(false);
            ctx.getSource().sendSuccess(() -> Component.translatable("mcai.cmd.off.done"), true);
            var srv = chatHandler.getServer();
            if (srv != null) srv.getPlayerList().broadcastSystemMessage(Component.translatable("mcai.cmd.off.broadcast"), false);
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
