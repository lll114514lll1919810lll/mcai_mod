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
public class CommandExecutionService {
    private static final java.util.Set<String> FORBIDDEN_COMMANDS = java.util.Set.of("ai","aiwiki","aiquery","aiaccept","aireject","aiclear","aireload","aitest","aicheck");
    private final MCAIMod mod;
    private final ConcurrentMap<UUID, List<String>> pendingCommands = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<String>> pendingFutures = new ConcurrentHashMap<>();
    public CommandExecutionService(MCAIMod mod) { this.mod = mod; }
    public String executeCommand(String command, ServerPlayer player) {
        MinecraftServer server = mod.getServer(); if (server == null) return "服务器未就绪";
        String root = command.split("\\s+")[0].toLowerCase();
        if (FORBIDDEN_COMMANDS.contains(root)) return "禁止AI执行Mod内部指令";
        if (player != null && needsApproval(command)) {
            int num = addPendingCommand(player.getUUID(), command);
            String key = player.getUUID() + ":" + num;
            CompletableFuture<String> future = new CompletableFuture<>();
            pendingFutures.put(key, future);
            notifyAdminsPending(player, command, num, server);
            try { String result = future.get(3, TimeUnit.MINUTES); return result != null ? result : "指令已执行"; }
            catch (java.util.concurrent.TimeoutException e) { pendingFutures.remove(key); return "§7[审批超时] 3分钟内无人批准，指令已自动取消: /" + command; }
            catch (Exception e) { return "§7[审批异常] " + e.getMessage(); }
        }
        CompletableFuture<String> future = new CompletableFuture<>();
        String playerName = player != null ? player.getScoreboardName() : "控制台";
        server.execute(() -> {
            try {
                String result = executeAsOp(command, server);
                server.getPlayerList().broadcastSystemMessage(Component.translatable("mcai.cmd.exec.broadcast_direct", playerName, command, result.isEmpty() ? "" : result), false);
                mod.getChatLog().add("AI → " + playerName, "/" + command + (result.isEmpty() || "指令已执行".equals(result) ? "" : " (" + result + ")"));
                future.complete(result.isEmpty() ? "指令已执行" : result);
            } catch (Exception e) { future.complete("执行失败: " + e.getMessage()); }
        });
        try { return future.get(10, TimeUnit.SECONDS); }
        catch (java.util.concurrent.TimeoutException e) { return "执行超时"; }
        catch (Exception e) { return "执行异常: " + e.getMessage(); }
    }
    public String executeAsOp(String command, MinecraftServer server) {
        try {
            StringBuilder out = new StringBuilder();
            var cs = server.createCommandSourceStack();
            var src = new CommandSourceStack(new CommandSource() {
                public void sendSystemMessage(Component msg) { out.append(msg.getString()).append("\n"); }
                public boolean acceptsSuccess() { return true; }
                public boolean acceptsFailure() { return true; }
                public boolean alwaysAccepts() { return false; }
                public boolean shouldInformAdmins() { return false; }
            }, cs.getPosition(), cs.getRotation(), cs.getLevel(), LevelBasedPermissionSet.OWNER, cs.getTextName(), cs.getDisplayName(), server, cs.getEntity());
            server.getCommands().getDispatcher().execute(command, src);
            String result = out.toString().trim();
            return result.isEmpty() ? "指令已执行" : result;
        } catch (CommandSyntaxException e) { return "指令语法错误: " + e.getMessage(); }
        catch (Exception e) { return "执行失败: " + e.getMessage(); }
    }
    public int approveCommand(ServerPlayer admin, int num) {
        UUID pid = admin.getUUID(); List<String> cmds = pendingCommands.get(pid); int idx = num - 1;
        if (cmds == null || idx < 0 || idx >= cmds.size()) { String key = pid + ":" + num; CompletableFuture<String> f = pendingFutures.remove(key); if (f != null) f.complete(""); return 0; }
        String cmd = cmds.remove(idx); if (cmds.isEmpty()) pendingCommands.remove(pid);
        String result = "指令已执行"; MinecraftServer server = mod.getServer();
        if (server != null) result = executeAsOp(cmd, server);
        admin.sendSystemMessage(Component.translatable("mcai.cmd.exec.approved", num, cmd));
        String key = pid + ":" + num; CompletableFuture<String> f = pendingFutures.remove(key);
        if (f != null) f.complete(result); return 1;
    }
    public int rejectCommand(ServerPlayer admin, int num) {
        UUID pid = admin.getUUID(); List<String> cmds = pendingCommands.get(pid); int idx = num - 1;
        if (cmds == null || idx < 0 || idx >= cmds.size()) { String key = pid + ":" + num; CompletableFuture<String> f = pendingFutures.remove(key); if (f != null) f.complete(""); return 0; }
        String cmd = cmds.remove(idx); if (cmds.isEmpty()) pendingCommands.remove(pid);
        admin.sendSystemMessage(Component.translatable("mcai.cmd.exec.rejected", num, cmd));
        String key = pid + ":" + num; CompletableFuture<String> f = pendingFutures.remove(key);
        if (f != null) f.complete("[审批拒绝] 管理员拒绝了指令: /" + cmd); return 1;
    }
    public boolean needsApproval(String command) {
        ModConfig config = mod.getConfig(); String root = command.split("\\s+")[0].toLowerCase();
        if (config.getRequireApprovalCommands().contains(root)) return true;
        if (config.isStrictMode()) { for (String safe : config.getSafeCommands()) { if (safe.contains(" ")) { if (command.toLowerCase().startsWith(safe)) return false; } else { if (root.equals(safe)) return false; } } return true; }
        return false;
    }
    public List<String> getPendingCommands(UUID playerId) { return pendingCommands.get(playerId); }
    public Map<UUID, List<String>> getAllPendingCommands() { return pendingCommands; }
    public void cleanupPlayer(UUID playerId) { pendingCommands.remove(playerId); pendingFutures.keySet().removeIf(k -> k.startsWith(playerId.toString() + ":")); }
    public void clearAll() { pendingCommands.clear(); pendingFutures.clear(); }
    private int addPendingCommand(UUID pid, String cmd) { List<String> list = pendingCommands.computeIfAbsent(pid, k -> new CopyOnWriteArrayList<>()); list.add(cmd); return list.size(); }
    private void notifyAdminsPending(ServerPlayer requester, String command, int num, MinecraftServer server) {
        server.execute(() -> {
            server.getPlayerList().broadcastSystemMessage(Component.translatable("mcai.cmd.exec.broadcast_approval_request", requester.getScoreboardName(), command), false);
            mod.getChatLog().add("系统", requester.getScoreboardName() + " 请求 /" + command + " (审批中)");
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (isAdminPlayer(p, server)) p.sendSystemMessage(Component.translatable("mcai.cmd.exec.broadcast_approval_instructions", num, num));
            }
        });
    }
    private boolean isAdminPlayer(ServerPlayer player, MinecraftServer server) { return server != null && server.getPlayerList().isOp(new NameAndId(player.getGameProfile())); }
    public static boolean isAdminOrConsole(CommandSourceStack src) {
        ServerPlayer p = src.getPlayer(); if (p == null) return true;
        MinecraftServer srv = src.getServer(); return srv != null && srv.getPlayerList().isOp(new NameAndId(p.getGameProfile()));
    }
}
