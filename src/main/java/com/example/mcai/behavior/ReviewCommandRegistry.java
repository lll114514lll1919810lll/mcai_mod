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
    private final SuggestionProvider<CommandSourceStack> approvalIdSuggestions;
    public ReviewCommandRegistry(ChatReviewSystem crs, AdminApprovalQueue aq, ReviewEngine re) {
        this.chatReviewSystem = crs; this.approvalQueue = aq; this.reviewEngine = re;
        this.approvalIdSuggestions = (ctx, builder) -> { for (int id : approvalQueue.getUnresolvedIds()) { var item = approvalQueue.getItem(id); if (item != null) builder.suggest(id, Component.translatable("mcai.review.suggest.item", item.targetPlayerName, item.reason)); else builder.suggest(id); } return builder.buildFuture(); };
    }
    public LiteralArgumentBuilder<CommandSourceStack> createAiReviewCommand() {
        return Commands.literal("aireview")
                .requires(src -> { var p = src.getPlayer(); if (p == null) return true; if (src.getServer() == null) return false; return src.getServer().getPlayerList().isOp(new NameAndId(p.getGameProfile())); })
                .then(Commands.literal("approve").then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(approvalIdSuggestions).executes(ctx -> { int id = IntegerArgumentType.getInteger(ctx, "id"); return handleApproval(ctx.getSource(), id, true); })))
                .then(Commands.literal("reject").then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(approvalIdSuggestions).executes(ctx -> { int id = IntegerArgumentType.getInteger(ctx, "id"); return handleApproval(ctx.getSource(), id, false); })))
                .then(Commands.literal("start").executes(ctx -> { chatReviewSystem.triggerManualReview(ctx.getSource().getPlayer()); ctx.getSource().sendSuccess(() -> Component.translatable("mcai.review.started"), false); return Command.SINGLE_SUCCESS; }))
                .then(Commands.literal("last").executes(ctx -> showLastReview(ctx.getSource())).then(Commands.literal("reasoning").executes(ctx -> showLastReasoning(ctx.getSource()))))
                .executes(ctx -> showHelp(ctx.getSource()));
    }
    private int handleApproval(CommandSourceStack src, int id, boolean approve) {
        ServerPlayer admin = src.getPlayer(); if (admin == null) return 0;
        if (approve) { var item = approvalQueue.tryApprove(id); if (item != null) { src.sendSuccess(() -> Component.translatable("mcai.review.approve.done", id, item.targetPlayerName, item.action), true); executeApprovedAction(item); return 1; } else { src.sendFailure(Component.translatable("mcai.review.approve.invalid", id)); return 0; } }
        else { var item = approvalQueue.tryReject(id); if (item != null) { src.sendSuccess(() -> Component.translatable("mcai.review.reject.done", id, item.targetPlayerName, item.action), true); return 1; } else { src.sendFailure(Component.translatable("mcai.review.reject.invalid", id)); return 0; } }
    }
    private void executeApprovedAction(AdminApprovalQueue.ApprovalItem item) {
        var server = chatReviewSystem.getServer(); if (server == null) return;
        server.execute(() -> {
            ServerPlayer target = server.getPlayerList().getPlayer(item.targetPlayerId);
            if (target == null) { LOGGER.info("Player {} offline, skipping {}", item.targetPlayerName, item.action); return; }
            if ("kick".equals(item.action)) {
                target.connection.disconnect(Component.translatable("mcai.review.kick_msg", item.reason));
                LOGGER.info("Kicked {} (approval #{})", item.targetPlayerName, item.id);
                chatReviewSystem.getPenaltyHistory().addEvent(new PenaltyEvent(
                        item.targetPlayerName, item.reason, 0, 0,
                        PenaltyEvent.PenaltyAction.KICK_EXECUTED, item.id,
                        chatReviewSystem.getPenaltyHistory().getCurrentCycle()));
            }
        });
    }
    private int showHelp(CommandSourceStack src) {
        src.sendSuccess(() -> Component.translatable("mcai.cmd.help.title"), false);
        src.sendSuccess(() -> Component.translatable("mcai.cmd.help.start"), false);
        src.sendSuccess(() -> Component.translatable("mcai.cmd.help.approve"), false);
        src.sendSuccess(() -> Component.translatable("mcai.cmd.help.reject"), false);
        src.sendSuccess(() -> Component.translatable("mcai.cmd.help.last"), false);
        src.sendSuccess(() -> Component.translatable("mcai.cmd.help.reasoning"), false);
        return Command.SINGLE_SUCCESS;
    }
    private int showLastReview(CommandSourceStack src) {
        String reasoning = reviewEngine.getLastReasoning(); String raw = reviewEngine.getLastRawResponse();
        if (raw.isEmpty()) { src.sendSuccess(() -> Component.translatable("mcai.review.no_records"), false); return Command.SINGLE_SUCCESS; }
        if (!reasoning.isEmpty()) { src.sendSuccess(() -> Component.translatable("mcai.review.ai_reasoning", reasoning.length() > 200 ? reasoning.substring(0, 200)+"..." : reasoning), false); if (reasoning.length() > 200) src.sendSuccess(() -> Component.translatable("mcai.review.ai_reasoning_full"), false); }
        src.sendSuccess(() -> Component.translatable("mcai.review.raw_output", raw.length() > 500 ? raw.substring(0, 500)+"..." : raw), false);
        if (raw.length() > 500) src.sendSuccess(() -> Component.translatable("mcai.review.raw_output_full"), false);
        return Command.SINGLE_SUCCESS;
    }
    private int showLastReasoning(CommandSourceStack src) {
        String reasoning = reviewEngine.getLastReasoning();
        if (reasoning.isEmpty()) { src.sendSuccess(() -> Component.translatable("mcai.review.no_reasoning"), false); }
        else if (reasoning.length() <= 2000) { src.sendSuccess(() -> Component.translatable("mcai.review.ai_reasoning_complete", reasoning), false); }
        else { src.sendSuccess(() -> Component.translatable("mcai.review.ai_reasoning_truncated"), false); src.sendSuccess(() -> Component.translatable("mcai.review.ai_reasoning_head", reasoning.substring(0, 600)), false); src.sendSuccess(() -> Component.translatable("mcai.review.ai_reasoning_tail", reasoning.substring(reasoning.length()-400)), false); }
        return Command.SINGLE_SUCCESS;
    }
}
