package com.example.mcai.handler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.stream.Collectors;
public class PlayerContextBuilder {
    public String build(ServerPlayer player, MinecraftServer server) {
        if (server == null) return "";
        var level = player.level(); var pos = player.blockPosition();
        String playerList = server.getPlayerList().getPlayers().stream()
                .map(p -> String.format("%s (HP:%.0f %s %s)", p.getScoreboardName(), p.getHealth(),
                        p.level().dimension().identifier().getPath(), p.gameMode.getGameModeForPlayer().name()))
                .collect(Collectors.joining(", "));
        String advSummary = "";
        try {
            var advancements = player.getAdvancements();
            var allAdvs = server.getAdvancements().getAllAdvancements();
            int done = 0, total = 0;
            for (var adv : allAdvs) {
                if (adv.id().getNamespace().equals("minecraft") && adv.id().getPath().startsWith("story/")) { total++; if (advancements.getOrStartProgress(adv).isDone()) done++; }
            }
            advSummary = String.format(" | 进度: %d/%d (故事模式)", done, total);
        } catch (Exception ignored) {}
        float yRot = player.getYRot();
        String facing = yRot >= -45 && yRot < 45 ? "南" : yRot >= 45 && yRot < 135 ? "西" : yRot >= -135 && yRot < -45 ? "东" : "北";
        return String.format("版本: %s | 在线(%d/%d): [%s] | %s | 难度: %s\n说话者: %s | 坐标: [%d %d %d] | 朝向: %s | 维度: %s | HP: %.1f | 饱食度: %d | 模式: %s | 等级: %d%s",
                server.getServerModName(), server.getPlayerCount(), server.getMaxPlayers(), playerList,
                formatGameTime(level.getGameTime()), level.getDifficulty().getDisplayName().getString(),
                player.getScoreboardName(), pos.getX(), pos.getY(), pos.getZ(), facing,
                level.dimension().identifier(), player.getHealth(),
                player.getFoodData().getFoodLevel(), player.gameMode.getGameModeForPlayer().name(),
                player.experienceLevel, advSummary);
    }
    public static String formatGameTime(long ticks) {
        long day = ticks / 24000 + 1; long dayTicks = ticks % 24000; long adjusted = (dayTicks + 6000) % 24000;
        int hour = (int)(adjusted / 1000); int minute = (int)((adjusted % 1000) * 60 / 1000);
        String period; int displayHour;
        if (hour == 0) { displayHour = 12; period = "AM"; }
        else if (hour < 12) { displayHour = hour; period = "AM"; }
        else if (hour == 12) { displayHour = 12; period = "PM"; }
        else { displayHour = hour - 12; period = "PM"; }
        return String.format("第%d天 %d:%02d %s (tick=%d)", day, displayHour, minute, period, ticks);
    }
}
