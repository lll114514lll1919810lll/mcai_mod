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
            ServerPlayer player = ctx.getSource().getPlayer(); String msg = StringArgumentType.getString(ctx, "message");
            if (player != null) {
                var server = chatHandler.getServer();
                if (server != null) server.getPlayerList().broadcastSystemMessage(Component.literal("§7[§f"+player.getScoreboardName()+"§7]对AI说：§f"+msg), false);
                chatHandler.getChatLog().add(player.getScoreboardName(), msg);
                ctx.getSource().sendSuccess(() -> Component.literal("§7[AI] 思考中..."), false);
                chatHandler.handleAIQuery(player, msg);
            } else {
                chatHandler.getChatLog().add("控制台", msg, true);
                ctx.getSource().sendSuccess(() -> Component.literal("§7[控制台] 正在向AI发送查询..."), false);
                chatHandler.handleConsoleAIQuery(ctx.getSource(), msg);
            }
            return Command.SINGLE_SUCCESS;
        })).executes(ctx -> { ctx.getSource().sendFailure(Component.literal("用法: /ai <消息>")); return 0; });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createWikiCommand() {
        return Commands.literal("aikb").requires(CommandExecutionService::isAdminOrConsole)
                .then(Commands.argument("query", StringArgumentType.greedyString()).executes(ctx -> {
                    String q = StringArgumentType.getString(ctx, "query"); String r = knowledgeBase.search(q, 5);
                    ctx.getSource().sendSuccess(() -> Component.literal("§a=== 知识库结果 ===\n§7"+r), false); return 1;
                })).executes(ctx -> { ctx.getSource().sendFailure(Component.literal("用法: /aikb <关键词>")); return 0; });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createQueryCommand() {
        return Commands.literal("aiquery").requires(CommandExecutionService::isAdminOrConsole).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayer();
            if (player != null) {
                List<String> cmds = cmdExec.getPendingCommands(player.getUUID());
                if (cmds == null || cmds.isEmpty()) { ctx.getSource().sendSuccess(() -> Component.literal("§7[AI] 暂无待审批指令"), false); return 0; }
                ctx.getSource().sendSuccess(() -> Component.literal("§e==== 待审批指令 ("+cmds.size()+") ===="), false);
                for (int i = 0; i < cmds.size(); i++) { final int n = i+1; final String c = cmds.get(i); ctx.getSource().sendSuccess(() -> Component.literal("§6  ["+n+"] /"+c), false); }
                ctx.getSource().sendSuccess(() -> Component.literal("§e使用 §a/aiaccept <编号> 批准，§c/aireject <编号> 拒绝"), false);
            } else {
                var all = cmdExec.getAllPendingCommands();
                if (all.isEmpty()) { ctx.getSource().sendSuccess(() -> Component.literal("§7[AI] 暂无待审批指令"), false); return 0; }
                ctx.getSource().sendSuccess(() -> Component.literal("§e==== 全部待审批指令 ===="), false);
                final AtomicInteger total = new AtomicInteger(0);
                for (var e : all.entrySet()) {
                    final String fn; var srv = chatHandler.getServer();
                    if (srv != null) { var p = srv.getPlayerList().getPlayer(e.getKey()); fn = p != null ? p.getScoreboardName() : "?"; } else { fn = "?"; }
                    for (int i = 0; i < e.getValue().size(); i++) { final int ft = total.incrementAndGet(); final String c = e.getValue().get(i); ctx.getSource().sendSuccess(() -> Component.literal("§6  ["+ft+"] §f"+fn+" §7→ /"+c), false); }
                }
            }
            return 1;
        });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createAcceptCommand() {
        return Commands.literal("aiaccept").requires(CommandExecutionService::isAdminOrConsole)
                .then(Commands.argument("number", IntegerArgumentType.integer(1)).suggests(pendingIdSuggestions).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer(); int num = IntegerArgumentType.getInteger(ctx, "number");
                    if (player == null) { ctx.getSource().sendFailure(Component.literal("§c控制台不支持审批")); return 0; }
                    int r = cmdExec.approveCommand(player, num); if (r == 0) ctx.getSource().sendFailure(Component.literal("§c编号无效，使用 /aiquery 查看")); return r;
                })).executes(ctx -> { ctx.getSource().sendFailure(Component.literal("用法: /aiaccept <编号>")); return 0; });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createRejectCommand() {
        return Commands.literal("aireject").requires(CommandExecutionService::isAdminOrConsole)
                .then(Commands.argument("number", IntegerArgumentType.integer(1)).suggests(pendingIdSuggestions).executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer(); int num = IntegerArgumentType.getInteger(ctx, "number");
                    if (player == null) { ctx.getSource().sendFailure(Component.literal("§c控制台不支持拒绝")); return 0; }
                    int r = cmdExec.rejectCommand(player, num); if (r == 0) ctx.getSource().sendFailure(Component.literal("§c编号无效，使用 /aiquery 查看")); return r;
                })).executes(ctx -> { ctx.getSource().sendFailure(Component.literal("用法: /aireject <编号>")); return 0; });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createClearCommand() {
        return Commands.literal("aiclear").requires(CommandExecutionService::isAdminOrConsole).executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayer(); if (player == null) return 0;
            chatHandler.clearHistory(player.getUUID()); cmdExec.cleanupPlayer(player.getUUID());
            ctx.getSource().sendSuccess(() -> Component.literal("§a[AI] 已清除对话历史和待审批指令"), false); return 1;
        });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createScoreCommand() {
        return Commands.literal("aiscore").executes(ctx -> {
            ServerPlayer player = ctx.getSource().getPlayer();
            if (player == null) { ctx.getSource().sendSuccess(() -> Component.literal("§c控制台无行为分"), false); return 0; }
            var bt = chatHandler.getBehaviorTracker(); var cfg = chatHandler.getConfig();
            int score = bt.getScore(player.getUUID());
            ctx.getSource().sendSuccess(() -> Component.literal("§e===== 你的行为评分 ====="), false);
            ctx.getSource().sendSuccess(() -> Component.literal("§f当前评分: "+(score >= 0 ? "§a" : "§c")+score), false);
            ctx.getSource().sendSuccess(() -> Component.literal("§7━━━━ 处罚规则 ━━━━\n§7行为分初始为 §f0§7，违规扣分，良好表现可恢复\n§e黄牌阈值: §f"+cfg.getYellowCardThreshold()+" §7(公屏警告)\n§c红牌阈值: §f"+cfg.getRedCardThreshold()+" §7(踢出+管理员审批)\n§a每周期自动恢复: §f+"+cfg.getScoreRecoveryPerInterval()+" §7(上限恢复至0)\n§7━━━━━━━━━━━━━━"), false);
            return Command.SINGLE_SUCCESS;
        });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createReloadCommand() {
        return Commands.literal("aireload").requires(CommandExecutionService::isAdminOrConsole).executes(ctx -> {
            chatHandler.reloadAll();
            ctx.getSource().sendSuccess(() -> Component.literal("§a[AI] 已重载，所有状态已清空"), true); return 1;
        });
    }
    public LiteralArgumentBuilder<CommandSourceStack> createTestCommand() {
        var ps = playerNameSuggestions(); var bt = chatHandler.getMod().getBehaviorTracker();
        var crs = chatHandler.getMod().getChatReviewSystem(); var cl = chatHandler.getChatLog();
        return Commands.literal("aitest").requires(src -> { var p = src.getPlayer(); if (p == null || src.getServer() == null) return false; return isOp(src.getServer(), p.getGameProfile()); })
                .then(Commands.literal("score").then(Commands.argument("player", StringArgumentType.word()).suggests(ps).executes(ctx -> { String n = StringArgumentType.getString(ctx, "player"); UUID id = lookupPlayer(n); if (id == null) { ctx.getSource().sendFailure(Component.literal("§c玩家不在线: "+n)); return 0; } ctx.getSource().sendSuccess(() -> Component.literal("§e[测试] 玩家 "+n+" 行为分: "+bt.getScore(id)), false); return Command.SINGLE_SUCCESS; })))
                .then(Commands.literal("penalty").then(Commands.argument("player", StringArgumentType.word()).suggests(ps).then(Commands.argument("points", IntegerArgumentType.integer(-100, -1)).executes(ctx -> { String n = StringArgumentType.getString(ctx, "player"); UUID id = lookupPlayer(n); if (id == null) { ctx.getSource().sendFailure(Component.literal("§c玩家不在线: "+n)); return 0; } int pts = IntegerArgumentType.getInteger(ctx, "points"); int ns = bt.addScore(id, pts); ctx.getSource().sendSuccess(() -> Component.literal("§c[测试] 玩家 "+n+" 扣分 "+pts+"，当前行为分: "+ns), false); return Command.SINGLE_SUCCESS; }))))
                .then(Commands.literal("reset").then(Commands.argument("player", StringArgumentType.word()).suggests(ps).executes(ctx -> { String n = StringArgumentType.getString(ctx, "player"); UUID id = lookupPlayer(n); if (id == null) { ctx.getSource().sendFailure(Component.literal("§c玩家不在线: "+n)); return 0; } bt.resetScore(id); ctx.getSource().sendSuccess(() -> Component.literal("§a[测试] 已重置玩家 "+n+" 行为分"), false); return Command.SINGLE_SUCCESS; })))
                .then(Commands.literal("set").then(Commands.argument("player", StringArgumentType.word()).suggests(ps).then(Commands.argument("score", IntegerArgumentType.integer(-100, 0)).executes(ctx -> { String n = StringArgumentType.getString(ctx, "player"); UUID id = lookupPlayer(n); if (id == null) { ctx.getSource().sendFailure(Component.literal("§c玩家不在线: "+n)); return 0; } int s = IntegerArgumentType.getInteger(ctx, "score"); bt.setScore(id, s); ctx.getSource().sendSuccess(() -> Component.literal("§e[测试] 已设置玩家 "+n+" 行为分为: "+s), false); return Command.SINGLE_SUCCESS; }))))
                .then(Commands.literal("review").executes(ctx -> { if (crs != null) { ServerPlayer p = ctx.getSource().getPlayer(); crs.triggerManualReview(p); } else { ctx.getSource().sendFailure(Component.literal("§c审查系统未就绪")); } return Command.SINGLE_SUCCESS; }))
                .then(Commands.literal("chatlog").executes(ctx -> { String log = cl.peek(); if (log.isEmpty()) { ctx.getSource().sendSuccess(() -> Component.literal("§7[测试] 聊天记录为空"), false); } else { ctx.getSource().sendSuccess(() -> Component.literal("§e==== 聊天记录 ====\n§7"+log), false); } return Command.SINGLE_SUCCESS; }))
                .executes(ctx -> { ctx.getSource().sendSuccess(() -> Component.literal("§e==== MCAI 测试指令 ===="), false); ctx.getSource().sendSuccess(() -> Component.literal("§7/aitest score <玩家> §f- 查询行为分\n§7/aitest penalty <玩家> <分数> §f- 模拟扣分\n§7/aitest reset <玩家> §f- 重置行为分\n§7/aitest set <玩家> <分数> §f- 设置行为分(-100~0)\n§7/aitest review §f- 手动触发审查\n§7/aitest chatlog §f- 查看聊天记录"), false); return Command.SINGLE_SUCCESS; });
    }
    private SuggestionProvider<CommandSourceStack> playerNameSuggestions() { return (ctx, builder) -> { var srv = chatHandler.getServer(); if (srv != null) { for (var p : srv.getPlayerList().getPlayers()) builder.suggest(p.getScoreboardName()); } return builder.buildFuture(); }; }
    private static boolean isOp(MinecraftServer srv, GameProfile profile) { return srv.getPlayerList().isOp(new NameAndId(profile)); }
    private UUID lookupPlayer(String name) { var srv = chatHandler.getServer(); if (srv == null) return null; for (ServerPlayer p : srv.getPlayerList().getPlayers()) { if (p.getScoreboardName().equalsIgnoreCase(name)) return p.getUUID(); } return null; }
}
