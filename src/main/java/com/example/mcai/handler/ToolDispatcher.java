package com.example.mcai.handler;
import com.example.mcai.MCAIMod;
import com.example.mcai.api.OpenAIClient;
import com.example.mcai.kb.KnowledgeBase;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.LightLayer;
import java.util.ArrayList;
import java.util.List;
public class ToolDispatcher {
    private static final Gson GSON = new GsonBuilder().create();
    private final KnowledgeBase knowledgeBase;
    private final CommandExecutionService cmdExec;
    private final MCAIMod mod;
    public ToolDispatcher(KnowledgeBase kb, CommandExecutionService cmdExec, MCAIMod mod) { this.knowledgeBase = kb; this.cmdExec = cmdExec; this.mod = mod; }
    public List<String> dispatch(List<OpenAIClient.ToolCall> toolCalls, ServerPlayer player) {
        List<String> results = new ArrayList<>();
        for (var tc : toolCalls) {
            switch (tc.name) {
                case "search_knowledge_base" -> results.add(knowledgeBase.search(parseArg(tc.arguments, "query"), 5));
                case "read_knowledge_base" -> results.add(knowledgeBase.read(parseArg(tc.arguments, "title")));
                case "execute_minecraft_command" -> results.add(cmdExec.executeCommand(parseArg(tc.arguments, "command"), player));
                case "get_server_status" -> results.add(getServerStatus(player));
                case "get_game_rules" -> results.add(getGameRules(player));
                case "get_debug_info" -> results.add(getDebugInfo(player));
                case "get_installed_mods" -> results.add(getInstalledMods());
                default -> results.add("未知工具: " + tc.name);
            }
        }
        return results;
    }
    public List<String> dispatchConsole(List<OpenAIClient.ToolCall> toolCalls) {
        MinecraftServer server = mod.getServer();
        List<String> results = new ArrayList<>();
        for (var tc : toolCalls) {
            switch (tc.name) {
                case "search_knowledge_base" -> results.add(knowledgeBase.search(parseArg(tc.arguments, "query"), 5));
                case "read_knowledge_base" -> results.add(knowledgeBase.read(parseArg(tc.arguments, "title")));
                case "execute_minecraft_command" -> results.add(server != null ? cmdExec.executeAsOp(parseArg(tc.arguments, "command"), server) : "服务器未就绪");
                case "get_server_status" -> results.add(getServerStatus(null));
                case "get_game_rules" -> results.add("控制台无法获取游戏规则");
                case "get_debug_info" -> results.add("控制台无法获取调试信息");
                case "get_installed_mods" -> results.add(getInstalledMods());
                default -> results.add("未知工具: " + tc.name);
            }
        }
        return results;
    }
    private String getServerStatus(ServerPlayer player) {
        MinecraftServer server = mod.getServer(); if (server == null) return "服务器未就绪";
        var level = player != null ? (ServerLevel)player.level() : server.overworld();
        String time = PlayerContextBuilder.formatGameTime(level.getGameTime());
        String weather = level.isThundering() ? "雷暴" : level.isRaining() ? "下雨" : "晴朗";
        String biome;
        try {
            var pos = player != null ? player.blockPosition() : BlockPos.ZERO;
            var biomeOpt = level.getBiome(pos).unwrapKey();
            biome = biomeOpt.isPresent() ? biomeOpt.get().identifier().getPath() : "未知";
        } catch (Exception e) { biome = "未知"; }
        float mspt = server.getCurrentSmoothedTickTime(); double tps = Math.min(20.0, 1000.0 / Math.max(mspt, 0.001));
        String load = tps >= 19.5 ? "流畅" : tps >= 15 ? "轻微卡顿" : tps >= 10 ? "明显卡顿" : "严重卡顿";
        return String.format("服务器状态:\n时间: %s\n天气: %s\n生物群系: %s\n负载: TPS=%.1f MSPT=%.1fms (%s)\n在线: %d/%d", time, weather, biome, tps, mspt, load, server.getPlayerCount(), server.getMaxPlayers());
    }
    private String getGameRules(ServerPlayer player) {
        MinecraftServer server = mod.getServer(); if (server == null) return "服务器未就绪";
        var level = player != null ? (ServerLevel)player.level() : server.overworld();
        var rules = level.getGameRules();
        return String.format("游戏规则:\n昼夜循环: %s | 天气循环: %s | 火焰伤害: %s\n生物破坏: %s | 死亡不掉落: %s | 立即重生: %s\n生物生成: %s | 幻翼生成: %s | 灾厄巡逻队: %s\n流浪商人: %s | 监守者生成: %s\n命令方块输出: %s | 管理员日志: %s | 反馈信息: %s\nTNT爆炸: %s | 方块掉落: %s | 生物掉落: %s\n随机刻速度: %d | 重生半径: %d | 睡觉比例: %d%%",
                yn(rules.get(GameRules.ADVANCE_TIME)), yn(rules.get(GameRules.ADVANCE_WEATHER)), yn(rules.get(GameRules.FIRE_DAMAGE)),
                yn(rules.get(GameRules.MOB_GRIEFING)), yn(rules.get(GameRules.KEEP_INVENTORY)), yn(rules.get(GameRules.IMMEDIATE_RESPAWN)),
                yn(rules.get(GameRules.SPAWN_MOBS)), yn(rules.get(GameRules.SPAWN_PHANTOMS)), yn(rules.get(GameRules.SPAWN_PATROLS)),
                yn(rules.get(GameRules.SPAWN_WANDERING_TRADERS)), yn(rules.get(GameRules.SPAWN_WARDENS)),
                yn(rules.get(GameRules.COMMAND_BLOCK_OUTPUT)), yn(rules.get(GameRules.LOG_ADMIN_COMMANDS)), yn(rules.get(GameRules.SEND_COMMAND_FEEDBACK)),
                yn(rules.get(GameRules.TNT_EXPLODES)), yn(rules.get(GameRules.BLOCK_DROPS)), yn(rules.get(GameRules.MOB_DROPS)),
                intVal(rules.get(GameRules.RANDOM_TICK_SPEED)), intVal(rules.get(GameRules.RESPAWN_RADIUS)), intVal(rules.get(GameRules.PLAYERS_SLEEPING_PERCENTAGE)));
    }
    private String getDebugInfo(ServerPlayer player) {
        MinecraftServer server = mod.getServer(); if (server == null) return "服务器未就绪";
        var level = (ServerLevel)player.level(); var pos = player.blockPosition();
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos); int skyLight = level.getBrightness(LightLayer.SKY, pos);
        int skyDarken = level.getSkyDarken(); int actualSkyLight = Math.max(0, skyLight - skyDarken);
        int chunkX = pos.getX() >> 4; int chunkZ = pos.getZ() >> 4;
        float regionalDifficulty;
        try { regionalDifficulty = level.getCurrentDifficultyAt(pos).getEffectiveDifficulty(); } catch (Exception e) { regionalDifficulty = -1; }
        String fluid;
        try { var f = level.getFluidState(pos); fluid = f.isEmpty() ? "无" : f.getType().toString(); } catch (Exception e) { fluid = "N/A"; }
        String lookingAt;
        try {
            var hit = player.pick(50.0, 0.0f, false);
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                var b = (net.minecraft.world.phys.BlockHitResult)hit; lookingAt = "方块: " + level.getBlockState(b.getBlockPos()).getBlock() + " @ " + b.getBlockPos().toShortString();
            } else if (hit.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
                var e = (net.minecraft.world.phys.EntityHitResult)hit; var entity = e.getEntity();
                lookingAt = "实体: " + entity.getDisplayName().getString() + (entity instanceof net.minecraft.world.entity.LivingEntity le ? " HP:" + String.format("%.0f/%.0f", le.getHealth(), le.getMaxHealth()) : "") + " @ " + entity.blockPosition().toShortString();
            } else { lookingAt = "无目标"; }
        } catch (Exception e) { lookingAt = "N/A (" + e.getClass().getSimpleName() + ")"; }
        return String.format("F3 调试信息:\n坐标: [%d %d %d] (区块 %d, %d)\n朝向: %s\n方块光: %d | 天空光: %d (原始%d, 暗化-%d) | 脚下流体: %s\n区域难度: %.2f | 区块已加载: %s\n注视目标: %s",
                pos.getX(), pos.getY(), pos.getZ(), chunkX, chunkZ, facingName(player.getYRot()), blockLight, actualSkyLight, skyLight, skyDarken, fluid, regionalDifficulty, level.isLoaded(pos) ? "是" : "否", lookingAt);
    }
    private static String facingName(float yRot) {
        if (yRot >= -45 && yRot < 45) return "南 (+Z)"; if (yRot >= 45 && yRot < 135) return "西 (-X)";
        if (yRot >= -135 && yRot < -45) return "东 (+X)"; return "北 (-Z)";
    }
    private String getInstalledMods() {
        var mods = net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods();
        StringBuilder sb = new StringBuilder("已安装的Mod列表（用于确定物品命名空间格式 <modid>:<item>）:\n");
        for (var container : mods) { var meta = container.getMetadata(); String id = meta.getId(); String name = meta.getName(); String ver = meta.getVersion().getFriendlyString(); if (id.equals("fabric-api") || id.equals("fabric") || id.equals("mcai") || id.startsWith("fabric-")) continue; sb.append("- ").append(name).append(" (").append(id).append(") §7v").append(ver).append("\n"); }
        sb.append("§7提示: Minecraft物品ID格式为 §e<命名空间>:<物品名>§7，如 minecraft:diamond_sword。Mod物品需使用其modid作为命名空间。");
        return sb.toString();
    }
    private static int intVal(Object v) { if (v instanceof Number n) return n.intValue(); return 0; }
    private static String yn(Object v) {
        if (v instanceof Boolean b) return b ? "§a是" : "§c否";
        try { return Boolean.parseBoolean(v.toString()) ? "§a是" : "§c否"; } catch (Exception e) { return v != null ? v.toString() : "?"; }
    }
    private static String parseArg(String json, String key) {
        try {
            var obj = GSON.fromJson(json, com.google.gson.JsonObject.class);
            if (obj != null && obj.has(key)) {
                var elem = obj.get(key);
                if (elem.isJsonPrimitive()) return elem.getAsString();
                return elem.toString();
            }
        } catch (Exception e) {
            MCAIMod.LOGGER.warn("Invalid tool argument JSON: {}", json.length() > 100 ? json.substring(0, 100) + "..." : json);
        }
        return "";
    }
}
