package com.example.mcai.handler;

import com.example.mcai.MCAIMod;
import com.example.mcai.config.ModConfig;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.permissions.LevelBasedPermissionSet;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class CommandExecutionService {
    private static final java.util.Set<String> FORBIDDEN_COMMANDS = java.util.Set.of(
            "ai", "aiwiki", "aiquery", "aiaccept", "aireject", "aicancel", "aiclear", "aireload", "aitest", "aicheck"
    );

    private static final int MAX_CHAIN_COMMANDS = 10;
    private static final int MAX_CHAIN_INTERVAL = 10;

    private final MCAIMod mod;
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final ConcurrentMap<Long, PendingCommand> pendingById = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, PendingChain> pendingChains = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Set<Long>> pendingByPlayer = new ConcurrentHashMap<>();

    public CommandExecutionService(MCAIMod mod) {
        this.mod = mod;
    }

    /**
     * 等待管理员审批的命令项。id 全局唯一，不会因为删除/审批而漂移。
     */
    public static class PendingCommand {
        public final long id;
        public final UUID requesterId;
        public final String requesterName;
        public final String command;
        public final long createdAt;
        public final CompletableFuture<String> future = new CompletableFuture<>();
        /** 请求者上下文（审批执行时使用，而非管理员上下文） */
        public final net.minecraft.server.level.ServerLevel requesterLevel;
        public final net.minecraft.core.BlockPos requesterPos;
        public final net.minecraft.world.phys.Vec2 requesterRot;

        public PendingCommand(long id, UUID requesterId, String requesterName, String command,
                              net.minecraft.server.level.ServerLevel level,
                              net.minecraft.core.BlockPos pos, net.minecraft.world.phys.Vec2 rot) {
            this.id = id;
            this.requesterId = requesterId;
            this.requesterName = requesterName;
            this.command = command;
            this.createdAt = System.currentTimeMillis();
            this.requesterLevel = level;
            this.requesterPos = pos;
            this.requesterRot = rot;
        }
    }

    /**
     * 等待管理员审批的命令链。多条命令作为一个审批单元。
     */
    public static class PendingChain {
        public final long id;
        public final UUID requesterId;
        public final String requesterName;
        public final List<String> commands;
        public final int intervalSeconds;
        public final long createdAt;
        public final CompletableFuture<String> future = new CompletableFuture<>();
        public volatile boolean executing = false;
        public volatile Thread executionThread = null;

        public PendingChain(long id, UUID requesterId, String requesterName,
                            List<String> commands, int intervalSeconds) {
            this.id = id;
            this.requesterId = requesterId;
            this.requesterName = requesterName;
            this.commands = List.copyOf(commands);
            this.intervalSeconds = intervalSeconds;
            this.createdAt = System.currentTimeMillis();
        }
    }

    public String executeCommand(String command, ServerPlayer player) {
        MinecraftServer server = mod.getServer();
        if (server == null) return "server not ready";
        final String normalizedCommand = normalizeCommand(command);

        String root = normalizedCommand.split("\\s+")[0].toLowerCase();
        if (FORBIDDEN_COMMANDS.contains(root)) {
            return "forbidden: mod internal command";
        }

        if (player != null && needsApproval(normalizedCommand)) {
            PendingCommand pending = addPendingCommand(player, normalizedCommand);
            notifyAdminsPending(pending, server);
            try {
                String result = pending.future.get(3, TimeUnit.MINUTES);
                return result != null ? result : "§7Command executed";
            } catch (java.util.concurrent.TimeoutException e) {
                removePending(pending.id);
                return "§7[Approval timeout] No admin approved in 3 minutes, cancelled: /" + normalizedCommand;
            } catch (Exception e) {
                removePending(pending.id);
                return "§7[Approval error] " + e.getMessage();
            }
        }

        CompletableFuture<String> future = new CompletableFuture<>();
        String playerName = player != null ? player.getScoreboardName() : "console";
        server.execute(() -> {
            try {
                String result = player != null ? executeAsOp(normalizedCommand, server, player) : executeAsOp(normalizedCommand, server);
                server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("mcai.cmd.exec.broadcast_direct", playerName, normalizedCommand, result.isEmpty() ? "" : result),
                        false
                );
                mod.getChatLog().add("AI → " + playerName, "/" + normalizedCommand + (result.isEmpty() || "Command executed".equals(result) ? "" : " (" + result + ")"));
                future.complete(result.isEmpty() ? "Command executed" : result);
            } catch (Exception e) {
                future.complete("Execution failed: " + e.getMessage());
            }
        });
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            return "Execution timeout";
        } catch (Exception e) {
            return "Execution error: " + e.getMessage();
        }
    }

    public String executeAsOp(String command, MinecraftServer server) {
        return executeAsOp(command, server, null, null, null);
    }

    public String executeAsOp(String command, MinecraftServer server, ServerPlayer player) {
        return executeAsOp(command, server, (net.minecraft.server.level.ServerLevel) player.level(), player.blockPosition(), player.getRotationVector());
    }

    private String executeAsOp(String command, MinecraftServer server, net.minecraft.server.level.ServerLevel level,
                               net.minecraft.core.BlockPos pos, net.minecraft.world.phys.Vec2 rot) {
        command = normalizeCommand(command);
        try {
            StringBuilder out = new StringBuilder();
            var src = new CommandSourceStack(new CommandSource() {
                public void sendSystemMessage(Component msg) { out.append(msg.getString()).append("\n"); }
                public boolean acceptsSuccess() { return true; }
                public boolean acceptsFailure() { return true; }
                public boolean alwaysAccepts() { return false; }
                public boolean shouldInformAdmins() { return false; }
            }, pos != null ? net.minecraft.world.phys.Vec3.atCenterOf(pos) : server.createCommandSourceStack().getPosition(),
                    rot != null ? rot : server.createCommandSourceStack().getRotation(),
                    level != null ? level : server.createCommandSourceStack().getLevel(),
                    LevelBasedPermissionSet.OWNER,
                    server.createCommandSourceStack().getTextName(),
                    server.createCommandSourceStack().getDisplayName(),
                    server, null);
            server.getCommands().getDispatcher().execute(command, src);
            String result = out.toString().trim();
            return result.isEmpty() ? "Command executed" : result;
        } catch (CommandSyntaxException e) {
            return "Syntax error: " + e.getMessage();
        } catch (Exception e) {
            return "Execution failed: " + e.getMessage();
        }
    }

    public int approveCommand(ServerPlayer admin, long id) {
        PendingCommand pending = removePending(id);
        if (pending == null) {
            return 0;
        }
        MinecraftServer server = mod.getServer();
        String result = "Command executed";
        if (server != null) {
            // 使用请求者的上下文执行命令，而非管理员上下文
            result = executeAsOp(pending.command, server, pending.requesterLevel,
                    pending.requesterPos, pending.requesterRot);
        }
        admin.sendSystemMessage(Component.translatable("mcai.cmd.exec.approved", id, pending.command));
        if (!pending.future.isDone()) {
            pending.future.complete(result);
        }
        return 1;
    }

    public int rejectCommand(ServerPlayer admin, long id) {
        PendingCommand pending = removePending(id);
        if (pending == null) {
            return 0;
        }
        admin.sendSystemMessage(Component.translatable("mcai.cmd.exec.rejected", id, pending.command));
        if (!pending.future.isDone()) {
            pending.future.complete("[Approval rejected] Admin rejected: /" + pending.command);
        }
        return 1;
    }

    private PendingCommand addPendingCommand(ServerPlayer player, String command) {
        long id = idGenerator.getAndIncrement();
        PendingCommand pending = new PendingCommand(id, player.getUUID(), player.getScoreboardName(), command,
                (net.minecraft.server.level.ServerLevel) player.level(), player.blockPosition(), player.getRotationVector());
        pendingById.put(id, pending);
        pendingByPlayer.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(id);
        return pending;
    }

    private PendingCommand removePending(long id) {
        PendingCommand pending = pendingById.remove(id);
        if (pending != null) {
            Set<Long> set = pendingByPlayer.get(pending.requesterId);
            if (set != null) {
                set.remove(id);
                if (set.isEmpty()) pendingByPlayer.remove(pending.requesterId);
            }
        }
        return pending;
    }

    public boolean needsApproval(String command) {
        ModConfig config = mod.getConfig();
        String root = command.split("\\s+")[0].toLowerCase();
        if (config.getRequireApprovalCommands().contains(root)) return true;
        if (config.isStrictMode()) {
            for (String safe : config.getSafeCommands()) {
                if (safe.contains(" ")) {
                    if (command.toLowerCase().startsWith(safe)) return false;
                } else {
                    if (root.equals(safe)) return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 获取某玩家当前待审批命令（按创建时间排序）。
     */
    public List<PendingCommand> getPendingCommands(UUID playerId) {
        Set<Long> ids = pendingByPlayer.get(playerId);
        if (ids == null || ids.isEmpty()) return List.of();
        List<PendingCommand> list = new ArrayList<>();
        for (Long id : ids) {
            PendingCommand pending = pendingById.get(id);
            if (pending != null) list.add(pending);
        }
        list.sort(Comparator.comparingLong(p -> p.createdAt));
        return list;
    }

    /**
     * 获取所有待审批命令（按创建时间排序）。
     */
    public List<PendingCommand> getAllPendingCommands() {
        if (pendingById.isEmpty()) return List.of();
        List<PendingCommand> list = new ArrayList<>(pendingById.values());
        list.sort(Comparator.comparingLong(p -> p.createdAt));
        return list;
    }

    public void cleanupPlayer(UUID playerId) {
        Set<Long> ids = pendingByPlayer.remove(playerId);
        if (ids != null) {
            for (Long id : ids) {
                PendingCommand pending = pendingById.remove(id);
                if (pending != null && !pending.future.isDone()) {
                    pending.future.complete("[Approval cancelled] Requester disconnected");
                }
                PendingChain chain = pendingChains.remove(id);
                if (chain != null && !chain.future.isDone()) {
                    if (chain.executing && chain.executionThread != null) {
                        chain.executionThread.interrupt();
                    }
                    chain.future.complete("[Approval cancelled] Requester disconnected");
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Command Chain Methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * 提交命令链等待审批。如果链中任一命令需要审批，整链进入审批流程；
     * 否则直接执行。
     */
    public String submitChain(List<String> commands, int intervalSeconds, ServerPlayer player) {
        MinecraftServer server = mod.getServer();
        if (server == null) return "server not ready";

        // Validation
        if (commands == null || commands.isEmpty()) {
            return "§c命令链不能为空";
        }
        if (commands.size() > MAX_CHAIN_COMMANDS) {
            return "§c命令链最多 " + MAX_CHAIN_COMMANDS + " 条命令";
        }
        if (intervalSeconds < 0 || intervalSeconds > MAX_CHAIN_INTERVAL) {
            return "§c命令间隔必须在 0-" + MAX_CHAIN_INTERVAL + " 秒之间";
        }

        List<String> normalized = new ArrayList<>(commands.size());
        for (String cmd : commands) {
            normalized.add(normalizeCommand(cmd));
        }
        commands = normalized;

        // Check forbidden commands
        for (String cmd : commands) {
            String root = cmd.split("\\s+")[0].toLowerCase();
            if (FORBIDDEN_COMMANDS.contains(root)) {
                return "§c命令链包含禁止的命令: " + root;
            }
        }

        // Check if any command needs approval
        boolean needsApproval = player != null && commands.stream().anyMatch(this::needsApproval);

        if (!needsApproval) {
            // Execute directly without approval
            return executeChainDirect(commands, intervalSeconds, player, server);
        }

        // Create pending chain
        long id = idGenerator.getAndIncrement();
        PendingChain chain = new PendingChain(id, player.getUUID(), player.getScoreboardName(),
                commands, intervalSeconds);
        pendingChains.put(id, chain);
        pendingByPlayer.computeIfAbsent(player.getUUID(), k -> ConcurrentHashMap.newKeySet()).add(id);

        // Notify admins
        notifyAdminsPendingChain(chain, server);

        // Wait for approval
        try {
            String result = chain.future.get(3, TimeUnit.MINUTES);
            return result != null ? result : "§7命令链已执行";
        } catch (java.util.concurrent.TimeoutException e) {
            pendingChains.remove(id);
            Set<Long> set = pendingByPlayer.get(player.getUUID());
            if (set != null) set.remove(id);
            return "§7[审批超时] 3分钟内无人批准，命令链已自动取消";
        } catch (Exception e) {
            pendingChains.remove(id);
            Set<Long> set = pendingByPlayer.get(player.getUUID());
            if (set != null) set.remove(id);
            return "§7[审批异常] " + e.getMessage();
        }
    }

    /**
     * 直接执行命令链（无需审批）。
     */
    private String executeChainDirect(List<String> commands, int intervalSeconds,
                                       ServerPlayer player, MinecraftServer server) {
        StringBuilder summary = new StringBuilder();
        summary.append("命令链执行完毕 (").append(commands.size()).append(" 条):\n");
        int success = 0, failed = 0;

        for (int i = 0; i < commands.size(); i++) {
            if (i > 0 && intervalSeconds > 0) {
                try { Thread.sleep(intervalSeconds * 1000L); } catch (InterruptedException e) { break; }
            }
            String cmd = commands.get(i);
            CompletableFuture<String> future = new CompletableFuture<>();
            String playerName = player != null ? player.getScoreboardName() : "console";
            server.execute(() -> {
                try {
                    String result = player != null ? executeAsOp(cmd, server, player) : executeAsOp(cmd, server);
                    server.getPlayerList().broadcastSystemMessage(
                            Component.translatable("mcai.cmd.exec.broadcast_direct", playerName, cmd,
                                    result.isEmpty() ? "" : result),
                            false
                    );
                    mod.getChatLog().add("AI → " + playerName, "/" + cmd + (result.isEmpty() || "Command executed".equals(result) ? "" : " (" + result + ")"));
                    future.complete(result.isEmpty() ? "Command executed" : result);
                } catch (Exception e) {
                    future.complete("Execution failed: " + e.getMessage());
                }
            });
            try {
                String result = future.get(10, TimeUnit.SECONDS);
                summary.append("  ").append(i + 1).append(". /").append(cmd).append(" → ").append(result).append("\n");
                if (result.contains("failed") || result.contains("error") || result.contains("Syntax")) {
                    failed++;
                } else {
                    success++;
                }
            } catch (Exception e) {
                summary.append("  ").append(i + 1).append(". /").append(cmd).append(" → 执行超时\n");
                failed++;
            }
        }

        summary.append("成功: ").append(success).append(" 失败: ").append(failed);
        return summary.toString();
    }

    /**
     * 管理员批准命令链。
     */
    public int approveChain(ServerPlayer admin, long id) {
        PendingChain chain = pendingChains.remove(id);
        if (chain == null) {
            return 0;
        }

        Set<Long> set = pendingByPlayer.get(chain.requesterId);
        if (set != null) set.remove(id);

        MinecraftServer server = mod.getServer();
        if (server == null) {
            admin.sendSystemMessage(Component.translatable("mcai.cmd.exec.server_not_ready"));
            if (!chain.future.isDone()) chain.future.complete("服务器未就绪");
            return 1;
        }

        // Find requester for execution context
        ServerPlayer requesterFound = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(chain.requesterId)) {
                requesterFound = p;
                break;
            }
        }
        final ServerPlayer requester = requesterFound;

        admin.sendSystemMessage(Component.translatable("mcai.cmd.exec.chain_approved", id, chain.commands.size()));

        // Execute chain in a separate thread
        chain.executing = true;
        Thread execThread = new Thread(() -> {
            chain.executionThread = Thread.currentThread();
            StringBuilder summary = new StringBuilder();
            summary.append("命令链 #").append(id).append(" 执行完毕 (").append(chain.commands.size()).append(" 条):\n");
            int success = 0, failed = 0;

            for (int i = 0; i < chain.commands.size(); i++) {
                if (Thread.currentThread().isInterrupted()) {
                    summary.append("  [执行被中断]\n");
                    break;
                }
                if (i > 0 && chain.intervalSeconds > 0) {
                    try { Thread.sleep(chain.intervalSeconds * 1000L); } catch (InterruptedException e) { break; }
                }
                String cmd = chain.commands.get(i);
                CompletableFuture<String> future = new CompletableFuture<>();
                server.execute(() -> {
                    try {
                        String result = requester != null ? executeAsOp(cmd, server, requester) : executeAsOp(cmd, server);
                        server.getPlayerList().broadcastSystemMessage(
                                Component.translatable("mcai.cmd.exec.broadcast_direct",
                                        chain.requesterName, cmd, result.isEmpty() ? "" : result),
                                false
                        );
                        mod.getChatLog().add("AI → " + chain.requesterName, "/" + cmd + (result.isEmpty() || "Command executed".equals(result) ? "" : " (" + result + ")"));
                        future.complete(result.isEmpty() ? "Command executed" : result);
                    } catch (Exception e) {
                        future.complete("Execution failed: " + e.getMessage());
                    }
                });
                try {
                    String result = future.get(10, TimeUnit.SECONDS);
                    summary.append("  ").append(i + 1).append(". /").append(cmd).append(" → ").append(result).append("\n");
                    if (result.contains("failed") || result.contains("error") || result.contains("Syntax")) {
                        failed++;
                    } else {
                        success++;
                    }
                } catch (Exception e) {
                    summary.append("  ").append(i + 1).append(". /").append(cmd).append(" → 执行超时\n");
                    failed++;
                }
            }

            summary.append("成功: ").append(success).append(" 失败: ").append(failed);
            if (!chain.future.isDone()) {
                chain.future.complete(summary.toString());
            }
        }, "MCAI-Chain-" + id);
        execThread.setDaemon(true);
        execThread.start();

        return 1;
    }

    /**
     * 管理员拒绝命令链。
     */
    public int rejectChain(ServerPlayer admin, long id) {
        PendingChain chain = pendingChains.remove(id);
        if (chain == null) {
            return 0;
        }

        Set<Long> set = pendingByPlayer.get(chain.requesterId);
        if (set != null) set.remove(id);

        admin.sendSystemMessage(Component.translatable("mcai.cmd.exec.chain_rejected", id));
        if (!chain.future.isDone()) {
            chain.future.complete("[审批拒绝] 管理员拒绝了此命令链");
        }
        return 1;
    }

    /**
     * 玩家取消自己的待审批命令/命令链。
     */
    public int cancelByPlayer(ServerPlayer player, long id) {
        // Check single command
        PendingCommand pending = pendingById.get(id);
        if (pending != null) {
            if (!pending.requesterId.equals(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("mcai.cmd.cancel.not_owner"));
                return 0;
            }
            pendingById.remove(id);
            Set<Long> set = pendingByPlayer.get(player.getUUID());
            if (set != null) {
                set.remove(id);
                if (set.isEmpty()) pendingByPlayer.remove(player.getUUID());
            }
            if (!pending.future.isDone()) {
                pending.future.complete("[玩家取消] " + player.getScoreboardName() + " 主动取消了此命令。请勿在本轮对话中再次尝试相同命令。");
            }
            player.sendSystemMessage(Component.translatable("mcai.cmd.cancel.done", id));
            mod.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("mcai.cmd.cancel.broadcast", player.getScoreboardName(), id),
                    false
            );
            return 1;
        }

        // Check chain
        PendingChain chain = pendingChains.get(id);
        if (chain != null) {
            if (!chain.requesterId.equals(player.getUUID())) {
                player.sendSystemMessage(Component.translatable("mcai.cmd.cancel.not_owner"));
                return 0;
            }
            pendingChains.remove(id);
            Set<Long> set = pendingByPlayer.get(player.getUUID());
            if (set != null) {
                set.remove(id);
                if (set.isEmpty()) pendingByPlayer.remove(player.getUUID());
            }
            // Interrupt execution if in progress
            if (chain.executing && chain.executionThread != null) {
                chain.executionThread.interrupt();
            }
            if (!chain.future.isDone()) {
                chain.future.complete("[玩家取消] " + player.getScoreboardName() + " 主动取消了此命令链。请勿在本轮对话中再次尝试相同命令。");
            }
            player.sendSystemMessage(Component.translatable("mcai.cmd.cancel.done", id));
            mod.getServer().getPlayerList().broadcastSystemMessage(
                    Component.translatable("mcai.cmd.cancel.chain_broadcast", player.getScoreboardName(), id),
                    false
            );
            return 1;
        }

        player.sendSystemMessage(Component.translatable("mcai.cmd.accept.invalid"));
        return 0;
    }

    /**
     * 玩家取消自己最近一条待审批命令（无参数时使用）。
     */
    public int cancelLatestByPlayer(ServerPlayer player) {
        Set<Long> ids = pendingByPlayer.get(player.getUUID());
        if (ids == null || ids.isEmpty()) {
            player.sendSystemMessage(Component.translatable("mcai.cmd.cancel.none"));
            return 0;
        }

        // Find the latest one
        long latestId = -1;
        long latestTime = 0;
        for (Long id : ids) {
            PendingCommand pending = pendingById.get(id);
            if (pending != null && pending.createdAt > latestTime) {
                latestTime = pending.createdAt;
                latestId = id;
            }
            PendingChain chain = pendingChains.get(id);
            if (chain != null && chain.createdAt > latestTime) {
                latestTime = chain.createdAt;
                latestId = id;
            }
        }

        if (latestId == -1) {
            player.sendSystemMessage(Component.translatable("mcai.cmd.cancel.none"));
            return 0;
        }

        if (ids.size() > 1) {
            player.sendSystemMessage(Component.translatable("mcai.cmd.cancel.multiple", ids.size(), latestId));
        }

        return cancelByPlayer(player, latestId);
    }

    /**
     * 玩家取消所有待审批命令/命令链。
     */
    public int cancelAllByPlayer(ServerPlayer player) {
        Set<Long> ids = pendingByPlayer.remove(player.getUUID());
        if (ids == null || ids.isEmpty()) {
            player.sendSystemMessage(Component.translatable("mcai.cmd.cancel.none"));
            return 0;
        }

        int count = 0;
        for (Long id : ids) {
            PendingCommand pending = pendingById.remove(id);
            if (pending != null && !pending.future.isDone()) {
                pending.future.complete("[玩家取消] " + player.getScoreboardName() + " 主动取消了此命令。请勿在本轮对话中再次尝试相同命令。");
                count++;
            }
            PendingChain chain = pendingChains.remove(id);
            if (chain != null && !chain.future.isDone()) {
                if (chain.executing && chain.executionThread != null) {
                    chain.executionThread.interrupt();
                }
                chain.future.complete("[玩家取消] " + player.getScoreboardName() + " 主动取消了此命令链。请勿在本轮对话中再次尝试相同命令。");
                count++;
            }
        }

        player.sendSystemMessage(Component.translatable("mcai.cmd.cancel.all_done", count));
        mod.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("mcai.cmd.cancel.all_broadcast", player.getScoreboardName(), count),
                false
        );
        return count;
    }

    /**
     * 获取待审批命令链。
     */
    public PendingChain getPendingChain(long id) {
        return pendingChains.get(id);
    }

    /**
     * 获取所有待审批命令链（按创建时间排序）。
     */
    public List<PendingChain> getAllPendingChains() {
        if (pendingChains.isEmpty()) return List.of();
        List<PendingChain> list = new ArrayList<>(pendingChains.values());
        list.sort(Comparator.comparingLong(p -> p.createdAt));
        return list;
    }

    /**
     * 获取某玩家待审批命令链（按创建时间排序）。
     */
    public List<PendingChain> getPlayerPendingChains(UUID playerId) {
        Set<Long> ids = pendingByPlayer.get(playerId);
        if (ids == null || ids.isEmpty()) return List.of();
        List<PendingChain> list = new ArrayList<>();
        for (Long id : ids) {
            PendingChain chain = pendingChains.get(id);
            if (chain != null) list.add(chain);
        }
        list.sort(Comparator.comparingLong(p -> p.createdAt));
        return list;
    }

    /**
     * 广播命令链审批请求。
     */
    private void notifyAdminsPendingChain(PendingChain chain, MinecraftServer server) {
        server.execute(() -> {
            // Build chain display using i18n
            Component requestMsg = Component.translatable("mcai.cmd.chain.request", chain.requesterName, chain.id);
            Component infoMsg = Component.translatable("mcai.cmd.chain.request_info", chain.commands.size());
            
            StringBuilder fullMsg = new StringBuilder();
            fullMsg.append(requestMsg.getString()).append(infoMsg.getString());
            if (chain.intervalSeconds > 0) {
                fullMsg.append(Component.translatable("mcai.cmd.chain.request_interval", chain.intervalSeconds).getString());
            }
            fullMsg.append(Component.translatable("mcai.cmd.chain.request_close").getString()).append("\n");
            for (int i = 0; i < chain.commands.size(); i++) {
                fullMsg.append(Component.translatable("mcai.cmd.chain.command_item", i + 1, chain.commands.get(i)).getString()).append("\n");
            }

            // Broadcast to all players
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(fullMsg.toString().trim()),
                    false
            );
            mod.getChatLog().add("系统", chain.requesterName + " 请求命令链 #" + chain.id + " (" + chain.commands.size() + "条命令) (审批中)");

            // Send clickable cancel hint to the requester
            ServerPlayer requester = server.getPlayerList().getPlayer(chain.requesterId);
            if (requester != null) {
                Component cancelHint = Component.translatable("mcai.cmd.exec.cancel_hint", chain.id)
                        .withStyle(style -> style
                                .withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA))
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/aicancel " + chain.id))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.translatable("mcai.cmd.hover.cancel_chain")))
                        );
                requester.sendSystemMessage(cancelHint);
            }

            // Send clickable instructions to admins
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (isAdminPlayer(p, server)) {
                    Component approveBtn = Component.translatable("mcai.cmd.button.approve")
                            .withStyle(style -> style
                                    .withColor(net.minecraft.network.chat.TextColor.fromRgb(0x55FF55))
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/aiaccept " + chain.id))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.translatable("mcai.cmd.hover.approve_chain")))
                            );
                    Component rejectBtn = Component.translatable("mcai.cmd.button.reject")
                            .withStyle(style -> style
                                    .withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFF5555))
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/aireject " + chain.id))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.translatable("mcai.cmd.hover.reject_chain")))
                            );
                    Component cancelBtn = Component.translatable("mcai.cmd.button.cancel")
                            .withStyle(style -> style
                                    .withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA))
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/aicancel " + chain.id))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.translatable("mcai.cmd.hover.cancel_chain")))
                            );
                    Component hint = Component.translatable("mcai.cmd.hover.timeout")
                            .withStyle(style -> style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x888888)));

                    p.sendSystemMessage(Component.empty()
                            .append(approveBtn).append(Component.literal(" "))
                            .append(rejectBtn).append(Component.literal(" "))
                            .append(cancelBtn).append(hint));
                }
            }
        });
    }

    private void notifyAdminsPending(PendingCommand pending, MinecraftServer server) {
        server.execute(() -> {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("mcai.cmd.exec.broadcast_approval_request", pending.requesterName, pending.command),
                    false
            );
            mod.getChatLog().add("系统", pending.requesterName + " 请求 /" + pending.command + " (审批中 #" + pending.id + ")");

            // Send clickable cancel hint to the requester
            ServerPlayer requester = server.getPlayerList().getPlayer(pending.requesterId);
            if (requester != null) {
                Component cancelHint = Component.translatable("mcai.cmd.exec.cancel_hint", pending.id)
                        .withStyle(style -> style
                                .withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA))
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/aicancel " + pending.id))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.translatable("mcai.cmd.hover.cancel_command")))
                        );
                requester.sendSystemMessage(cancelHint);
            }

            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (isAdminPlayer(p, server)) {
                    Component approveBtn = Component.translatable("mcai.cmd.button.approve")
                            .withStyle(style -> style
                                    .withColor(net.minecraft.network.chat.TextColor.fromRgb(0x55FF55))
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/aiaccept " + pending.id))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.translatable("mcai.cmd.hover.approve_command")))
                            );
                    Component rejectBtn = Component.translatable("mcai.cmd.button.reject")
                            .withStyle(style -> style
                                    .withColor(net.minecraft.network.chat.TextColor.fromRgb(0xFF5555))
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/aireject " + pending.id))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.translatable("mcai.cmd.hover.reject_command")))
                            );
                    Component cancelBtn = Component.translatable("mcai.cmd.button.cancel")
                            .withStyle(style -> style
                                    .withColor(net.minecraft.network.chat.TextColor.fromRgb(0xAAAAAA))
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/aicancel " + pending.id))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.translatable("mcai.cmd.hover.cancel_command")))
                            );
                    Component hint = Component.translatable("mcai.cmd.hover.timeout")
                            .withStyle(style -> style.withColor(net.minecraft.network.chat.TextColor.fromRgb(0x888888)));

                    p.sendSystemMessage(Component.empty()
                            .append(approveBtn).append(Component.literal(" "))
                            .append(rejectBtn).append(Component.literal(" "))
                            .append(cancelBtn).append(hint));
                }
            }
        });
    }

    private boolean isAdminPlayer(ServerPlayer player, MinecraftServer server) {
        return isAdmin(player, server);
    }

    public static boolean isAdminOrConsole(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return true;
        return isAdmin(p, src.getServer());
    }

    public static boolean isAdmin(ServerPlayer player, MinecraftServer server) {
        return server != null && server.getPlayerList().isOp(new NameAndId(player.getGameProfile()));
    }

    public static ServerPlayer findPlayerByName(String name, MinecraftServer server) {
        if (server == null) return null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getScoreboardName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    /** 移除命令字符串开头的 /，兼容 AI 多带斜杠的情况 */
    public static String normalizeCommand(String command) {
        if (command == null) return "";
        String trimmed = command.trim();
        while (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1).trim();
        }
        return trimmed;
    }
}
