package com.example.mcai.behavior;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
public class ReviewCommandRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-ReviewCmd");
    private final ChatReviewSystem chatReviewSystem;
    private final AdminApprovalQueue approvalQueue;
    private final ReviewEngine reviewEngine;
    public ReviewCommandRegistry(ChatReviewSystem crs, AdminApprovalQueue aq, ReviewEngine re) { this.chatReviewSystem = crs; this.approvalQueue = aq; this.reviewEngine = re; }
    public LiteralArgumentBuilder<CommandSourceStack> createAiCheckCommand() {
        var approvalIdSuggestions = (SuggestionProvider<CommandSourceStack>)(ctx, builder) -> { for (int id : approvalQueue.getUnresolvedIds()) builder.suggest(id); return builder.buildFuture(); };
        return Commands.literal("aicheck")
                .requires(src -> { var p = src.getPlayer(); if (p == null || src.getServer() == null) return false; return src.getServer().getPlayerList().isOp(new NameAndId(p.getGameProfile())); })
                .then(Commands.literal("approve").then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(approvalIdSuggestions).executes(ctx -> { int id = IntegerArgumentType.getInteger(ctx, "id"); return handleApproval(ctx.getSource(), id, true); })))
                .then(Commands.literal("reject").then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(approvalIdSuggestions).executes(ctx -> { int id = IntegerArgumentType.getInteger(ctx, "id"); return handleApproval(ctx.getSource(), id, false); })))
                .then(Commands.literal("last").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer(); if (player == null) return 0;
                    String reasoning = reviewEngine.getLastReasoning(); String raw = reviewEngine.getLastRawResponse();
                    if (raw.isEmpty()) { player.sendSystemMessage(Component.literal("§7[审查] 暂无上次审查记录")); return Command.SINGLE_SUCCESS; }
                    if (!reasoning.isEmpty()) { String sr = reasoning.length() > 200 ? reasoning.substring(0, 200)+"..." : reasoning; player.sendSystemMessage(Component.literal("§7===== AI 推理过程(前200字符) =====\n§8"+sr)); if (reasoning.length() > 200) player.sendSystemMessage(Component.literal("§7完整推理见: §econfig/mcai/review_last_reasoning.txt")); }
                    String sr = raw.length() > 500 ? raw.substring(0, 500)+"..." : raw; player.sendSystemMessage(Component.literal("§7===== AI 原始输出 =====\n§f"+sr)); if (raw.length() > 500) player.sendSystemMessage(Component.literal("§7完整输出见: §econfig/mcai/review_last_response.txt"));
                    return Command.SINGLE_SUCCESS;
                }).then(Commands.literal("reasoning").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayer(); if (player == null) return 0; String reasoning = reviewEngine.getLastReasoning();
                    if (reasoning.isEmpty()) { player.sendSystemMessage(Component.literal("§7[审查] 无推理过程记录")); }
                    else if (reasoning.length() <= 2000) { player.sendSystemMessage(Component.literal("§7===== AI 推理过程(完整) =====\n§8"+reasoning)); }
                    else { String head = reasoning.substring(0, 600); String tail = reasoning.substring(reasoning.length()-400); player.sendSystemMessage(Component.literal("§7===== AI 推理过程(截断) =====\n§7完整内容请查看 §econfig/mcai/review_last_reasoning.txt")); player.sendSystemMessage(Component.literal("§8--- 开头 ---\n"+head)); player.sendSystemMessage(Component.literal("§8--- 结尾 ---\n"+tail)); }
                    return Command.SINGLE_SUCCESS;
                })))
                .executes(ctx -> { ServerPlayer player = ctx.getSource().getPlayer(); if (player == null) return 0; chatReviewSystem.triggerManualReview(player); return Command.SINGLE_SUCCESS; });
    }
    private int handleApproval(CommandSourceStack src, int id, boolean approve) {
        ServerPlayer admin = src.getPlayer(); if (admin == null) return 0;
        if (approve) { var item = approvalQueue.tryApprove(id); if (item != null) { src.sendSuccess(() -> Component.literal("§a[审批] 已批准 #"+id+" - 对 "+item.targetPlayerName+" 执行 "+item.action), true); executeApprovedAction(item); return 1; } else { src.sendFailure(Component.literal("§c[审批] 无效或已处理的审批 #"+id)); return 0; } }
        else { var item = approvalQueue.tryReject(id); if (item != null) { src.sendSuccess(() -> Component.literal("§c[审批] 已拒绝 #"+id+" - "+item.targetPlayerName+" "+item.action), true); return 1; } else { src.sendFailure(Component.literal("§c[审批] 无效或已处理的审批 #"+id)); return 0; } }
    }
    private void executeApprovedAction(AdminApprovalQueue.ApprovalItem item) {
        var server = chatReviewSystem.getServer(); if (server == null) return;
        server.execute(() -> { ServerPlayer target = server.getPlayerList().getPlayer(item.targetPlayerId); if (target == null) { LOGGER.info("Player {} offline, skipping {}", item.targetPlayerName, item.action); return; } if ("kick".equals(item.action)) { target.connection.disconnect(Component.literal("§c你的行为评分过低，已被系统移出服务器。\n理由: "+item.reason)); LOGGER.info("Kicked {} (approval #{})", item.targetPlayerName, item.id); } });
    }
}
