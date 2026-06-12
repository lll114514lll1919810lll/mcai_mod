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
        this.approvalIdSuggestions = (ctx, builder) -> { for (int id : approvalQueue.getUnresolvedIds()) { var item = approvalQueue.getItem(id); if (item != null) builder.suggest(id, Component.literal("§7"+item.targetPlayerName+" - "+item.reason)); else builder.suggest(id); } return builder.buildFuture(); };
    }
    public LiteralArgumentBuilder<CommandSourceStack> createAiCheckCommand() {
        return Commands.literal("aicheck")
                .requires(src -> { var p = src.getPlayer(); if (p == null || src.getServer() == null) return false; return src.getServer().getPlayerList().isOp(new NameAndId(p.getGameProfile())); })
                .then(Commands.literal("approve").then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(approvalIdSuggestions).executes(ctx -> { int id = IntegerArgumentType.getInteger(ctx, "id"); return handleApproval(ctx.getSource(), id, true); })))
                .then(Commands.literal("reject").then(Commands.argument("id", IntegerArgumentType.integer(1)).suggests(approvalIdSuggestions).executes(ctx -> { int id = IntegerArgumentType.getInteger(ctx, "id"); return handleApproval(ctx.getSource(), id, false); })))
                .then(Commands.literal("start").executes(ctx -> { ServerPlayer p = ctx.getSource().getPlayer(); if (p == null) return 0; chatReviewSystem.triggerManualReview(p); return Command.SINGLE_SUCCESS; }))
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
        server.execute(() -> { ServerPlayer target = server.getPlayerList().getPlayer(item.targetPlayerId); if (target == null) { LOGGER.info("Player {} offline, skipping {}", item.targetPlayerName, item.action); return; } if ("kick".equals(item.action)) { target.connection.disconnect(Component.translatable("mcai.review.kick_msg", item.reason)); LOGGER.info("Kicked {} (approval #{})", item.targetPlayerName, item.id); } });
    }
    private int showHelp(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer(); if (player == null) return 0;
        player.sendSystemMessage(Component.translatable("mcai.cmd.help.title"));
        player.sendSystemMessage(Component.translatable("mcai.cmd.help.start"));
        player.sendSystemMessage(Component.translatable("mcai.cmd.help.approve"));
        player.sendSystemMessage(Component.translatable("mcai.cmd.help.reject"));
        player.sendSystemMessage(Component.translatable("mcai.cmd.help.last"));
        player.sendSystemMessage(Component.translatable("mcai.cmd.help.reasoning"));
        return Command.SINGLE_SUCCESS;
    }
    private int showLastReview(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer(); if (player == null) return 0;
        String reasoning = reviewEngine.getLastReasoning(); String raw = reviewEngine.getLastRawResponse();
        if (raw.isEmpty()) { player.sendSystemMessage(Component.translatable("mcai.review.no_records")); return Command.SINGLE_SUCCESS; }
        if (!reasoning.isEmpty()) { player.sendSystemMessage(Component.translatable("mcai.review.ai_reasoning", reasoning.length() > 200 ? reasoning.substring(0, 200)+"..." : reasoning)); if (reasoning.length() > 200) player.sendSystemMessage(Component.translatable("mcai.review.ai_reasoning_full")); }
        player.sendSystemMessage(Component.translatable("mcai.review.raw_output", raw.length() > 500 ? raw.substring(0, 500)+"..." : raw));
        if (raw.length() > 500) player.sendSystemMessage(Component.translatable("mcai.review.raw_output_full"));
        return Command.SINGLE_SUCCESS;
    }
    private int showLastReasoning(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer(); if (player == null) return 0; String reasoning = reviewEngine.getLastReasoning();
        if (reasoning.isEmpty()) { player.sendSystemMessage(Component.translatable("mcai.review.no_reasoning")); }
        else if (reasoning.length() <= 2000) { player.sendSystemMessage(Component.translatable("mcai.review.ai_reasoning_complete", reasoning)); }
        else { player.sendSystemMessage(Component.translatable("mcai.review.ai_reasoning_truncated")); player.sendSystemMessage(Component.translatable("mcai.review.ai_reasoning_head", reasoning.substring(0, 600))); player.sendSystemMessage(Component.translatable("mcai.review.ai_reasoning_tail", reasoning.substring(reasoning.length()-400))); }
        return Command.SINGLE_SUCCESS;
    }
}
