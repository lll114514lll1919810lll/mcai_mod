package com.example.mcai.handler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
public class ThinkingAnimation {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "MCAI-UI"); t.setDaemon(true); return t;
    });
    private final Map<UUID, ScheduledFuture<?>> active = new ConcurrentHashMap<>();
    public void start(ServerPlayer player, MinecraftServer server) {
        UUID id = player.getUUID(); stop(id);
        ScheduledFuture<?> f = scheduler.scheduleAtFixedRate(() -> {
            if (server == null || player.isRemoved()) { stop(id); return; }
            String dots = switch ((int)(System.currentTimeMillis() / 400) % 4) {
                case 0 -> "§7▌§8▌▌";
                case 1 -> "§7▌▌§8▌";
                case 2 -> "§7▌▌▌";
                default -> "§8▌▌▌";
            };
            Component bar = Component.translatable("mcai.chat.thinking_anim", dots);
            server.execute(() -> { if (player.connection != null && !player.isRemoved()) player.connection.send(new ClientboundSetActionBarTextPacket(bar)); });
        }, 0, 400, TimeUnit.MILLISECONDS);
        active.put(id, f);
    }
    public void stop(UUID playerId) { ScheduledFuture<?> f = active.remove(playerId); if (f != null) f.cancel(false); }
    public void done(ServerPlayer player) {
        stop(player.getUUID());
        if (player.connection != null && !player.isRemoved()) {
            player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
        }
    }
}
