package com.example.mcai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * AI 工具定义构建器。
 * 负责构建 OpenAI 兼容的 tools JSON 数组。
 */
public class ToolDefinitions {

    /**
     * 构建所有可用工具的定义
     */
    public static JsonArray buildAll() {
        JsonArray tools = new JsonArray();

        tools.add(buildSearchKnowledgeBase());
        tools.add(buildExecuteCommand());
        tools.add(buildExecuteCommandChain());
        tools.add(buildGetServerStatus());
        tools.add(buildGetGameRules());
        tools.add(buildGetDebugInfo());
        tools.add(buildGetInstalledMods());
        tools.add(buildGetPlayerEffects());
        tools.add(buildGetPlayerAdvancements());
        tools.add(buildGetPlayerInventory());

        return tools;
    }

    // ═══════════════════════════════════════════════════════════════
    // Knowledge Base
    // ═══════════════════════════════════════════════════════════════

    private static JsonObject buildSearchKnowledgeBase() {
        return buildTool("search_knowledge_base",
                "搜索 Minecraft 知识库。通过在线 Wiki（minecraft.wiki 或 zh.minecraft.wiki）搜索最新原版知识。可用中文或英文关键词。先调用 get_installed_mods 了解已安装的Mod，再用其modid作为命名空间搜索。如搜 create:brass_ingot 可用 \"黄铜锭\" 或 \"brass ingot\"。结果会在返回摘要时直接包含完整内容。",
                "query", "string", "搜索关键词（中文或英文）");
    }

    // ═══════════════════════════════════════════════════════════════
    // Command Execution
    // ═══════════════════════════════════════════════════════════════

    private static JsonObject buildExecuteCommand() {
        return buildTool("execute_minecraft_command",
                "在服务器上执行一条 Minecraft 指令。玩家提出的任何指令请求都可以用此工具执行（如给物品、传送、修改游戏规则等），不需要你判断权限——所有指令会自动送去管理员审批，审批通过后才会执行。只管调用工具，把结果告诉玩家即可。",
                "command", "string", "要执行的指令，不要带开头的 /");
    }

    private static JsonObject buildExecuteCommandChain() {
        JsonObject chainTool = new JsonObject();
        chainTool.addProperty("type", "function");

        JsonObject chainFn = new JsonObject();
        chainFn.addProperty("name", "execute_command_chain");
        chainFn.addProperty("description",
                "将多条 Minecraft 指令打包为一个命令链提交。所有指令作为一个审批单元，管理员一次审批即可全部执行。" +
                        "支持设置命令间执行间隔。当任务需要多条指令时（如给物品+传送+附魔），优先使用此工具减少审批次数。" +
                        "命令链提交后会阻塞等待审批结果，执行完成后返回所有命令的结果汇总。");

        JsonObject chainParams = new JsonObject();
        chainParams.addProperty("type", "object");
        chainParams.addProperty("additionalProperties", false);

        JsonObject chainProps = new JsonObject();

        // commands array parameter
        JsonObject commandsParam = new JsonObject();
        commandsParam.addProperty("type", "array");
        JsonObject itemsObj = new JsonObject();
        itemsObj.addProperty("type", "string");
        itemsObj.addProperty("description", "一条指令，不要带开头的 /");
        commandsParam.add("items", itemsObj);
        commandsParam.addProperty("description", "要按顺序执行的指令列表，不要带开头的 /。最多10条。");
        chainProps.add("commands", commandsParam);

        // interval parameter (optional)
        JsonObject intervalParam = new JsonObject();
        intervalParam.addProperty("type", "integer");
        intervalParam.addProperty("description", "命令之间的等待秒数，默认0（立即执行）。例如设为1表示每条命令间隔1秒。最大10秒。");
        chainProps.add("interval", intervalParam);

        chainParams.add("properties", chainProps);
        JsonArray chainReq = new JsonArray();
        chainReq.add("commands");
        chainParams.add("required", chainReq);
        chainFn.add("parameters", chainParams);
        chainTool.add("function", chainFn);

        return chainTool;
    }

    // ═══════════════════════════════════════════════════════════════
    // Server Info
    // ═══════════════════════════════════════════════════════════════

    private static JsonObject buildGetServerStatus() {
        return buildToolWithEmptyParams("get_server_status",
                "获取服务器实时状态：当前游戏时间和日期、天气（晴/雨/雷暴）、所在生物群系、服务器负载（TPS/MSPT）。无需参数，自动使用当前玩家的位置。");
    }

    private static JsonObject buildGetGameRules() {
        return buildToolWithEmptyParams("get_game_rules",
                "获取服务器游戏规则状态，包括昼夜循环、火焰蔓延、生物破坏、死亡不掉落、生物生成、天气循环、命令方块输出等关键规则。无需参数。");
    }

    private static JsonObject buildGetDebugInfo() {
        return buildToolWithEmptyParams("get_debug_info",
                "获取玩家当前位置的F3调试信息：光照等级（方块光/天空光）、所在区块坐标、注视的方块或实体、区域难度。无需参数。");
    }

    private static JsonObject buildGetInstalledMods() {
        return buildToolWithEmptyParams("get_installed_mods",
                "获取服务器上安装的所有Mod列表及其版本号。了解安装了哪些Mod后，你就能知道物品的命名空间格式（如 create:brass_ingot、thermal:copper_gear），从而在搜索知识库或执行指令时使用正确的Mod物品ID。无需参数。");
    }

    // ═══════════════════════════════════════════════════════════════
    // Player Info
    // ═══════════════════════════════════════════════════════════════

    private static JsonObject buildGetPlayerEffects() {
        return buildToolWithEmptyParams("get_player_effects",
                "获取玩家当前的药水效果，包括效果名称、等级、剩余时间。无需参数。");
    }

    private static JsonObject buildGetPlayerAdvancements() {
        return buildToolWithEmptyParams("get_player_advancements",
                "获取玩家的进度完成情况，包括已完成数量和正在进行的进度。无需参数。");
    }

    private static JsonObject buildGetPlayerInventory() {
        return buildToolWithEmptyParams("get_player_inventory",
                "获取玩家物品栏内容，包括主手、副手、装备和背包中的所有物品及其数量和耐久。无需参数。");
    }

    // ═══════════════════════════════════════════════════════════════
    // Helper methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * 构建带参数的工具定义
     */
    private static JsonObject buildTool(String name, String desc,
                                        String paramName, String paramType, String paramDesc) {
        JsonObject t = new JsonObject();
        t.addProperty("type", "function");

        JsonObject fn = new JsonObject();
        fn.addProperty("name", name);
        fn.addProperty("description", desc);

        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        params.addProperty("additionalProperties", false);

        JsonObject props = new JsonObject();
        JsonObject p = new JsonObject();
        p.addProperty("type", paramType);
        p.addProperty("description", paramDesc);
        props.add(paramName, p);
        params.add("properties", props);

        JsonArray req = new JsonArray();
        req.add(paramName);
        params.add("required", req);

        fn.add("parameters", params);
        t.add("function", fn);
        return t;
    }

    /**
     * 构建无参数的工具定义
     */
    private static JsonObject buildToolWithEmptyParams(String name, String desc) {
        JsonObject t = new JsonObject();
        t.addProperty("type", "function");

        JsonObject fn = new JsonObject();
        fn.addProperty("name", name);
        fn.addProperty("description", desc);
        fn.add("parameters", emptyParameters());

        t.add("function", fn);
        return t;
    }

    /**
     * 返回严格的空 parameters 对象，包含 properties 和 required，避免 LM Studio 等校验失败
     */
    private static JsonObject emptyParameters() {
        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        params.add("properties", new JsonObject());
        params.add("required", new JsonArray());
        return params;
    }
}
