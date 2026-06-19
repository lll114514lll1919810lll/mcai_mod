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
            "ai", "aiwiki", "aiquery", "aiaccept", "aireject", "aiclear", "aireload", "aitest", "aicheck"
    );

    private final MCAIMod mod;
    private final AtomicLong idGenerator = new AtomicLong(1);
    private final ConcurrentMap<Long, PendingCommand> pendingById = new ConcurrentHashMap<>();
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

        public PendingCommand(long id, UUID requesterId, String requesterName, String command) {
            this.id = id;
            this.requesterId = requesterId;
            this.requesterName = requesterName;
            this.command = command;
            this.createdAt = System.currentTimeMillis();
        }
    }

    public String executeCommand(String command, ServerPlayer player) {
        MinecraftServer server = mod.getServer();
        if (server == null) return "server not ready";

        String root = command.split("\\s+")[0].toLowerCase();
        if (FORBIDDEN_COMMANDS.contains(root)) {
            return "forbidden: mod internal command";
        }

        if (player != null && needsApproval(command)) {
            PendingCommand pending = addPendingCommand(player, command);
            notifyAdminsPending(pending, server);
            try {
                String result = pending.future.get(3, TimeUnit.MINUTES);
                return result != null ? result : "§7Command executed";
            } catch (java.util.concurrent.TimeoutException e) {
                removePending(pending.id);
                return "§7[Approval timeout] No admin approved in 3 minutes, cancelled: /" + command;
            } catch (Exception e) {
                removePending(pending.id);
                return "§7[Approval error] " + e.getMessage();
            }
        }

        CompletableFuture<String> future = new CompletableFuture<>();
        String playerName = player != null ? player.getScoreboardName() : "console";
        server.execute(() -> {
            try {
                String result = player != null ? executeAsOp(command, server, player) : executeAsOp(command, server);
                server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("mcai.cmd.exec.broadcast_direct", playerName, command, result.isEmpty() ? "" : result),
                        false
                );
                mod.getChatLog().add("AI → " + playerName, "/" + command + (result.isEmpty() || "Command executed".equals(result) ? "" : " (" + result + ")"));
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
            admin.sendSystemMessage(Component.translatable("mcai.cmd.accept.invalid"));
            return 0;
        }
        MinecraftServer server = mod.getServer();
        String result = "Command executed";
        if (server != null) {
            result = executeAsOp(pending.command, server, admin);
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
            admin.sendSystemMessage(Component.translatable("mcai.cmd.accept.invalid"));
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
        PendingCommand pending = new PendingCommand(id, player.getUUID(), player.getScoreboardName(), command);
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
            }
        }
    }

    private void notifyAdminsPending(PendingCommand pending, MinecraftServer server) {
        server.execute(() -> {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("mcai.cmd.exec.broadcast_approval_request", pending.requesterName, pending.command),
                    false
            );
            mod.getChatLog().add("系统", pending.requesterName + " 请求 /" + pending.command + " (审批中 #" + pending.id + ")");
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (isAdminPlayer(p, server)) {
                    p.sendSystemMessage(Component.translatable("mcai.cmd.exec.broadcast_approval_instructions", pending.id, pending.id));
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
}
