package com.example.mcai.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.example.mcai.config.ModConfig;
import com.example.mcai.MCAIMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class OpenAIClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-API");
    private static final Gson GSON = new GsonBuilder().create();

    private final ModConfig config;
    private final HttpClient httpClient;
    private final JsonArray toolDefinitions;
    /** 构造时解析的 API 配置（支持 review 系统使用独立模型） */
    private final String resolvedEndpoint;
    private final String resolvedApiKey;
    private final String resolvedModel;

    public OpenAIClient(ModConfig config) {
        this(config, config.getApiEndpoint(), config.getApiKey(), config.getModel());
    }

    /** 使用指定的 endpoint/key/model 创建客户端（用于 review 系统独立模型） */
    public OpenAIClient(ModConfig config, String endpoint, String apiKey, String model) {
        this.config = config;
        this.resolvedEndpoint = endpoint;
        this.resolvedApiKey = apiKey;
        this.resolvedModel = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getApiConnectTimeoutSeconds()))
                .build();
        this.toolDefinitions = buildToolDefinitions();
    }

    public static class ToolCall {
        public final String id;
        public final String name;
        public final String arguments;

        public ToolCall(String id, String name, String arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }
    }

    public static class ChatMessage {
        public String role;
        public String content;
        public String toolCallId;
        public String reasoningContent;
        public List<ToolCall> toolCalls;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public static ChatMessage toolCallRequest(ToolCall tc) {
            ChatMessage msg = new ChatMessage("assistant", "");
            msg.toolCalls = List.of(tc);
            return msg;
        }

        public static ChatMessage toolCallRequest(List<ToolCall> tcs) {
            ChatMessage msg = new ChatMessage("assistant", "");
            msg.toolCalls = List.copyOf(tcs);
            return msg;
        }

        public static ChatMessage toolResult(String toolCallId, String content) {
            ChatMessage msg = new ChatMessage("tool", content);
            msg.toolCallId = toolCallId;
            return msg;
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("role", role);
            if (toolCalls != null && !toolCalls.isEmpty()) {
                // tool_calls 消息不传 content
            } else {
                obj.addProperty("content", content != null ? content : "");
            }
            if (reasoningContent != null && !reasoningContent.isEmpty()) {
                obj.addProperty("reasoning_content", reasoningContent);
            }
            if (toolCallId != null) obj.addProperty("tool_call_id", toolCallId);
            if (toolCalls != null && !toolCalls.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (ToolCall tc : toolCalls) {
                    JsonObject tcObj = new JsonObject();
                    tcObj.addProperty("id", tc.id);
                    tcObj.addProperty("type", "function");
                    JsonObject fn = new JsonObject();
                    fn.addProperty("name", tc.name);
                    fn.addProperty("arguments", tc.arguments);
                    tcObj.add("function", fn);
                    arr.add(tcObj);
                }
                obj.add("tool_calls", arr);
            }
            return obj;
        }
    }

    public ApiResult<String> chat(List<ChatMessage> messages,
                                   Function<List<ToolCall>, List<String>> toolExecutor) {
        int maxTurns = config.getMaxToolCalls();
        long startTime = System.currentTimeMillis();
        long totalTimeoutMs = config.getApiLoopTimeoutSeconds() * 1000L;

        for (int turn = 0; turn < maxTurns; turn++) {
            if (System.currentTimeMillis() - startTime > totalTimeoutMs) {
                LOGGER.warn("Tool call loop timed out after {}ms, forcing final response", totalTimeoutMs);
                break;
            }
            JsonObject body = buildBaseRequestBody();
            JsonArray msgArray = new JsonArray();
            for (ChatMessage msg : messages) {
                msgArray.add(msg.toJson());
            }
            body.add("messages", msgArray);

            body.add("tools", toolDefinitions);

            String bodyJson = GSON.toJson(body);

            var dbg = MCAIMod.getInstance() != null ? MCAIMod.getInstance().getDebugLogger() : null;
            var result = sendAndParseMessage(bodyJson);
            if (!result.success()) {
                if (dbg != null && dbg.isEnabled()) dbg.logError("API", result.error());
                return ApiResult.err(result.error());
            }
            JsonObject msg = result.value();

            // Capture reasoning_content for thinking mode
            String reasoningContent = msg.has("reasoning_content") && !msg.get("reasoning_content").isJsonNull()
                    ? msg.get("reasoning_content").getAsString() : null;

            // Check for tool_calls directly in the message
            JsonElement tcElement = msg.get("tool_calls");
            JsonArray toolCallsJson = (tcElement != null && !tcElement.isJsonNull()) ? tcElement.getAsJsonArray() : null;

            if (dbg != null && dbg.isEnabled()) {
                if (reasoningContent != null && !reasoningContent.isEmpty()) dbg.logThinking(reasoningContent);
            }

            if (toolCallsJson != null && toolCallsJson.size() > 0) {
                List<ToolCall> toolCalls = parseToolCalls(msg);
                if (toolCalls.isEmpty()) {
                    return ApiResult.err("empty tool_calls");
                }

                var asstMsg = ChatMessage.toolCallRequest(toolCalls);
            // 只回传非空 reasoning_content
            if (reasoningContent != null && !reasoningContent.isEmpty()) {
                asstMsg.reasoningContent = reasoningContent;
            }
                messages.add(asstMsg);

                List<String> results = toolExecutor.apply(toolCalls);
                for (int i = 0; i < toolCalls.size(); i++) {
                    String toolResult = results.size() > i ? results.get(i) : "无结果";
                    messages.add(ChatMessage.toolResult(toolCalls.get(i).id, toolResult));
                    if (dbg != null && dbg.isEnabled()) {
                        dbg.logToolCall(toolCalls.get(i).name, toolCalls.get(i).arguments);
                        dbg.logToolResult(toolCalls.get(i).name, toolResult);
                    }
                }
                continue;
            }

            String content = msg.has("content") && !msg.get("content").isJsonNull()
                    ? msg.get("content").getAsString() : "";
            if (content.isEmpty()) return ApiResult.err("empty response content");
            if (dbg != null && dbg.isEnabled()) dbg.logAIResponse(content);
            return ApiResult.ok(content);
        }

        // Tool call limit reached: tell AI to wrap up, make one final call without tools
        messages.add(new ChatMessage("user",
                "本轮工具调用次数已用完。请基于已有信息给出最终回答，然后结束对话。不要尝试再次调用工具。"));
        // Final call without tool definitions
        JsonObject body = buildBaseRequestBody();
        JsonArray msgArray = new JsonArray();
        for (ChatMessage msg : messages) msgArray.add(msg.toJson());
        body.add("messages", msgArray);
        var fbResult = sendAndParseMessage(GSON.toJson(body));
        if (fbResult.success()) {
            String content = fbResult.value().has("content") && !fbResult.value().get("content").isJsonNull()
                    ? fbResult.value().get("content").getAsString() : "";
            if (!content.isEmpty()) return ApiResult.ok(content);
        }
        return ApiResult.err("工具调用次数已达上限，请稍后重试或简化请求");
    }

    /**
     * Result of a simple chat call, containing content and optional reasoning content.
     */
    public static class ChatSimpleResult {
        public final String content;
        public final String reasoningContent;

        public ChatSimpleResult(String content, String reasoningContent) {
            this.content = content;
            this.reasoningContent = reasoningContent;
        }
    }

    /**
     * Simple chat call without tool definitions and single-turn.
     * Used for behavior review where no tool execution is needed.
     * Supports thinking mode if configured. Returns both content and reasoning_content.
     */
    public ApiResult<ChatSimpleResult> chatSimpleFull(List<ChatMessage> messages) {
        JsonObject body = buildBaseRequestBody();
        JsonArray msgArray = new JsonArray();
        for (ChatMessage msg : messages) msgArray.add(msg.toJson());
        body.add("messages", msgArray);
        return executeChatCompletion(body);
    }

    /**
     * 构建请求体。兼容模式下只发送 model/messages，避免本地 LM Studio 等不支持的字段导致 400。
     * 普通模式下按完整 OpenAI 字段发送。
     */
    private JsonObject buildBaseRequestBody() {
        JsonObject body = new JsonObject();
        body.addProperty("model", resolvedModel);
        if (config.isCompatibilityMode()) {
            return body;
        }
        body.addProperty("max_tokens", config.getMaxTokens());
        body.addProperty("temperature", config.getTemperature());
        int tl = config.getThinkingLevel();
        addThinkingParams(body, tl);
        return body;
    }

    /**
     * 发送 chat/completions 请求并解析最简响应（审查系统使用）。
     */
    private ApiResult<ChatSimpleResult> executeChatCompletion(JsonObject body) {
        var result = sendAndParseMessage(GSON.toJson(body));
        if (!result.success()) return ApiResult.err(result.error());
        JsonObject msg = result.value();
        String reasoningContent = msg.has("reasoning_content") && !msg.get("reasoning_content").isJsonNull()
                ? msg.get("reasoning_content").getAsString() : null;
        String content = msg.has("content") && !msg.get("content").isJsonNull()
                ? msg.get("content").getAsString() : "";
        if (content.isEmpty()) return ApiResult.err("empty response content");
        return ApiResult.ok(new ChatSimpleResult(content, reasoningContent));
    }

    /**
     * 发送 POST 请求到 API，解析响应，返回 choices[0].message 的 JsonObject。
     * 处理 HTTP 错误、JSON 解析、choices 提取。两个 chat 方法共享此请求逻辑。
     */
    private ApiResult<JsonObject> sendAndParseMessage(String bodyJson) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(resolvedEndpoint.replaceAll("/+$", "") + "/chat/completions"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(config.getApiRequestTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson));
        if (!resolvedApiKey.isEmpty()) builder.header("Authorization", "Bearer " + resolvedApiKey);

        HttpResponse<String> response;
        try { response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString()); }
        catch (Exception e) { LOGGER.error("HTTP request failed: {}", e.getMessage()); return ApiResult.err(e.getMessage()); }

        if (response.statusCode() != 200) {
            String resp = response.body();
            try {
                JsonObject err = GSON.fromJson(resp, JsonObject.class);
                if (err.has("error")) {
                    String msg = err.getAsJsonObject("error").get("message").getAsString();
                    LOGGER.error("API error {}: {}", response.statusCode(), msg);
                    return ApiResult.err(msg);
                }
            } catch (Exception ignored) {}
            LOGGER.error("API error {} (no JSON error): {}", response.statusCode(),
                    resp.length() > 200 ? resp.substring(0, 200) + "..." : resp);
            return ApiResult.err("HTTP " + response.statusCode());
        }

        JsonObject json;
        try { json = GSON.fromJson(response.body(), JsonObject.class); }
        catch (Exception e) { LOGGER.error("Failed to parse API response: {}", e.getMessage()); return ApiResult.err("response parse failed"); }

        JsonArray choices = json.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) return ApiResult.err("no choices in response");

        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null) return ApiResult.err("no message in choice");
        return ApiResult.ok(message);
    }

    private List<ToolCall> parseToolCalls(JsonObject msg) {
        List<ToolCall> calls = new ArrayList<>();
        JsonElement arrEl = msg.get("tool_calls");
        if (arrEl == null || arrEl.isJsonNull()) return calls;
        JsonArray arr = arrEl.getAsJsonArray();
        if (arr == null) return calls;
        for (JsonElement el : arr) {
            JsonObject tc = el.getAsJsonObject();
            JsonObject fn = tc.getAsJsonObject("function");
            calls.add(new ToolCall(
                    tc.get("id").getAsString(),
                    fn.get("name").getAsString(),
                    fn.get("arguments").getAsString()
            ));
        }
        return calls;
    }

    private JsonArray buildToolDefinitions() {
        JsonObject kbTool = buildTool("search_knowledge_base",
                "搜索 Minecraft 知识库。通过在线 Wiki（minecraft.wiki 或 zh.minecraft.wiki）搜索最新原版知识。可用中文或英文关键词。先调用 get_installed_mods 了解已安装的Mod，再用其modid作为命名空间搜索。如搜 create:brass_ingot 可用 \"黄铜锭\" 或 \"brass ingot\"。结果会在返回摘要时直接包含完整内容。",
                "query", "string", "搜索关键词（中文或英文）");

        JsonObject cmdTool = buildTool("execute_minecraft_command",
                "在服务器上执行一条 Minecraft 指令。玩家提出的任何指令请求都可以用此工具执行（如给物品、传送、修改游戏规则等），不需要你判断权限——所有指令会自动送去管理员审批，审批通过后才会执行。只管调用工具，把结果告诉玩家即可。",
                "command", "string", "要执行的指令，不要带开头的 /");

        // Command chain tool - for multi-command tasks
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


        JsonObject statusTool = new JsonObject();
        statusTool.addProperty("type", "function");
        JsonObject statusFn = new JsonObject();
        statusFn.addProperty("name", "get_server_status");
        statusFn.addProperty("description", "获取服务器实时状态：当前游戏时间和日期、天气（晴/雨/雷暴）、所在生物群系、服务器负载（TPS/MSPT）。无需参数，自动使用当前玩家的位置。");
        statusFn.add("parameters", emptyParameters());
        statusTool.add("function", statusFn);

        JsonObject rulesTool = new JsonObject();
        rulesTool.addProperty("type", "function");
        JsonObject rulesFn = new JsonObject();
        rulesFn.addProperty("name", "get_game_rules");
        rulesFn.addProperty("description", "获取服务器游戏规则状态，包括昼夜循环、火焰蔓延、生物破坏、死亡不掉落、生物生成、天气循环、命令方块输出等关键规则。无需参数。");
        rulesFn.add("parameters", emptyParameters());
        rulesTool.add("function", rulesFn);

        JsonArray tools = new JsonArray();
        tools.add(kbTool);
        tools.add(cmdTool);
        tools.add(chainTool);
        JsonObject debugTool = new JsonObject();
        debugTool.addProperty("type", "function");
        JsonObject debugFn = new JsonObject();
        debugFn.addProperty("name", "get_debug_info");
        debugFn.addProperty("description", "获取玩家当前位置的F3调试信息：光照等级（方块光/天空光）、所在区块坐标、注视的方块或实体、区域难度。无需参数。");
        debugFn.add("parameters", emptyParameters());
        debugTool.add("function", debugFn);

        JsonObject modsTool = new JsonObject();
        modsTool.addProperty("type", "function");
        JsonObject modsFn = new JsonObject();
        modsFn.addProperty("name", "get_installed_mods");
        modsFn.addProperty("description", "获取服务器上安装的所有Mod列表及其版本号。了解安装了哪些Mod后，你就能知道物品的命名空间格式（如 create:brass_ingot、thermal:copper_gear），从而在搜索知识库或执行指令时使用正确的Mod物品ID。无需参数。");
        modsFn.add("parameters", emptyParameters());
        modsTool.add("function", modsFn);

        tools.add(statusTool);
        tools.add(rulesTool);
        tools.add(debugTool);
        tools.add(modsTool);

        JsonObject effectsTool = new JsonObject();
        effectsTool.addProperty("type", "function");
        JsonObject effectsFn = new JsonObject();
        effectsFn.addProperty("name", "get_player_effects");
        effectsFn.addProperty("description", "获取玩家当前的药水效果，包括效果名称、等级、剩余时间。无需参数。");
        effectsFn.add("parameters", emptyParameters());
        effectsTool.add("function", effectsFn);
        tools.add(effectsTool);

        JsonObject advTool = new JsonObject();
        advTool.addProperty("type", "function");
        JsonObject advFn = new JsonObject();
        advFn.addProperty("name", "get_player_advancements");
        advFn.addProperty("description", "获取玩家的进度完成情况，包括已完成数量和正在进行的进度。无需参数。");
        advFn.add("parameters", emptyParameters());
        advTool.add("function", advFn);
        tools.add(advTool);

        JsonObject invTool = new JsonObject();
        invTool.addProperty("type", "function");
        JsonObject invFn = new JsonObject();
        invFn.addProperty("name", "get_player_inventory");
        invFn.addProperty("description", "获取玩家物品栏内容，包括主手、副手、装备和背包中的所有物品及其数量和耐久。无需参数。");
        invFn.add("parameters", emptyParameters());
        invTool.add("function", invFn);
        tools.add(invTool);

        return tools;
    }

    /** 返回严格的空 parameters 对象，包含 properties 和 required，避免 LM Studio 等校验失败 */
    private static JsonObject emptyParameters() {
        JsonObject params = new JsonObject();
        params.addProperty("type", "object");
        params.add("properties", new JsonObject());
        params.add("required", new JsonArray());
        return params;
    }

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

    /** 判断模型是否使用 agnes 风格的思考参数（chat_template_kwargs.enable_thinking） */
    private boolean isAgnesThinkingModel() {
        return "agnes-2.0-flash".equals(resolvedModel);
    }

    /** 根据模型类型向请求体添加思考模式参数 */
    private void addThinkingParams(JsonObject body, int thinkingLevel) {
        if (thinkingLevel < 1) return;
        if (isAgnesThinkingModel()) {
            // agnes-2.0-flash 使用 chat_template_kwargs.enable_thinking
            JsonObject kwargs = new JsonObject();
            kwargs.addProperty("enable_thinking", true);
            body.add("chat_template_kwargs", kwargs);
        } else {
            // DeepSeek 风格：thinking.type = "enabled"
            JsonObject t = new JsonObject();
            t.addProperty("type", "enabled");
            body.add("thinking", t);
            if (thinkingLevel >= 3) {
                body.addProperty("reasoning_effort", "max");
            }
        }
    }
}
