package com.example.mcai.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.example.mcai.config.ModConfig;
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

    public OpenAIClient(ModConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
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
                // tool_calls 消息不传 content，防止 API 报错
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
        String endpoint = config.getApiEndpoint().replaceAll("/+$", "") + "/chat/completions";
        int maxTurns = config.getMaxToolCalls();

        for (int turn = 0; turn < maxTurns; turn++) {
            JsonObject body = new JsonObject();
            body.addProperty("model", config.getModel());
            body.addProperty("max_tokens", config.getMaxTokens());
            body.addProperty("temperature", config.getTemperature());

            int tl = config.getThinkingLevel();
            if (tl >= 1) {
                JsonObject t = new JsonObject();
                t.addProperty("type", "enabled");
                body.add("thinking", t);
                if (tl >= 3) {
                    body.addProperty("reasoning_effort", "max");
                }
            }

            JsonArray msgArray = new JsonArray();
            for (ChatMessage msg : messages) {
                msgArray.add(msg.toJson());
            }
            body.add("messages", msgArray);

            body.add("tools", toolDefinitions);

            String bodyJson = GSON.toJson(body);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson));

            String key = config.getApiKey();
            if (!key.isEmpty()) {
                builder.header("Authorization", "Bearer " + key);
            }

            HttpResponse<String> response;
            try {
                response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                LOGGER.error("HTTP request failed: {}", e.getMessage());
                return ApiResult.err(e.getMessage());
            }

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
            try {
                json = GSON.fromJson(response.body(), JsonObject.class);
            } catch (Exception e) {
                LOGGER.error("Failed to parse API response: {}", e.getMessage());
                return ApiResult.err("response parse failed, check API compatibility");
            }

            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                return ApiResult.err("no choices in response");
            }

            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject msg = choice.getAsJsonObject("message");

            // Capture reasoning_content for thinking mode
            String reasoningContent = msg.has("reasoning_content") && !msg.get("reasoning_content").isJsonNull()
                    ? msg.get("reasoning_content").getAsString() : null;

            // Check for tool_calls directly in the message
            JsonArray toolCallsJson = msg.getAsJsonArray("tool_calls");
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
                    messages.add(ChatMessage.toolResult(
                            toolCalls.get(i).id,
                            results.size() > i ? results.get(i) : "无结果"));
                }
                continue;
            }

            String content = msg.has("content") && !msg.get("content").isJsonNull()
                    ? msg.get("content").getAsString() : "";
            if (content.isEmpty()) return ApiResult.err("empty response content");
            return ApiResult.ok(content);
        }

        return ApiResult.err("exceeded " + maxTurns + " tool call limit");
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
     * Supports thinking mode if configured.
     */
    public ApiResult<String> chatSimple(List<ChatMessage> messages) {
        var result = chatSimpleFull(messages);
        if (result.success()) return ApiResult.ok(result.value().content);
        return ApiResult.err(result.error());
    }

    /**
     * Like chatSimple but returns both content and reasoning_content.
     */
    public ApiResult<ChatSimpleResult> chatSimpleFull(List<ChatMessage> messages) {
        String endpoint = config.getApiEndpoint().replaceAll("/+$", "") + "/chat/completions";

        JsonObject body = new JsonObject();
        body.addProperty("model", config.getModel());
        body.addProperty("max_tokens", config.getMaxTokens());
        body.addProperty("temperature", config.getTemperature());

        int tl = config.getThinkingLevel();
        if (tl >= 1) {
            JsonObject t = new JsonObject();
            t.addProperty("type", "enabled");
            body.add("thinking", t);
            if (tl >= 3) {
                body.addProperty("reasoning_effort", "max");
            }
        }

        JsonArray msgArray = new JsonArray();
        for (ChatMessage msg : messages) {
            msgArray.add(msg.toJson());
        }
        body.add("messages", msgArray);

        String bodyJson = GSON.toJson(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson));

        String key = config.getApiKey();
        if (!key.isEmpty()) {
            builder.header("Authorization", "Bearer " + key);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            LOGGER.error("HTTP request failed: {}", e.getMessage());
            return ApiResult.err(e.getMessage());
        }

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
        try {
            json = GSON.fromJson(response.body(), JsonObject.class);
        } catch (Exception e) {
            LOGGER.error("Failed to parse API response: {}", e.getMessage());
            return ApiResult.err("response parse failed, check API compatibility");
        }

        JsonArray choices = json.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            return ApiResult.err("no choices in response");
        }

        JsonObject choice = choices.get(0).getAsJsonObject();
        JsonObject msg = choice.getAsJsonObject("message");

        String reasoningContent = msg.has("reasoning_content") && !msg.get("reasoning_content").isJsonNull()
                ? msg.get("reasoning_content").getAsString() : null;

        String content = msg.has("content") && !msg.get("content").isJsonNull()
                ? msg.get("content").getAsString() : "";
        if (content.isEmpty()) return ApiResult.err("empty response content");
        return ApiResult.ok(new ChatSimpleResult(content, reasoningContent));
    }

    private List<ToolCall> parseToolCalls(JsonObject msg) {
        List<ToolCall> calls = new ArrayList<>();
        JsonArray arr = msg.getAsJsonArray("tool_calls");
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
                "搜索本地知识库。可用中文或英文关键词。先调用 get_installed_mods 了解已安装的Mod，再用其modid作为命名空间搜索。如搜 create:brass_ingot 可用 \"黄铜锭\" 或 \"brass ingot\"。",
                "query", "string", "搜索关键词（中文或英文）");

        JsonObject readTool = buildTool("read_knowledge_base",
                "读取知识库中某个条目的完整内容。先用 search_knowledge_base 搜索到目标条目后，用此工具获取全文。",
                "title", "string", "条目标题（从 search_knowledge_base 的结果中获取）");

        JsonObject cmdTool = buildTool("execute_minecraft_command",
                "在服务器上执行一条 Minecraft 指令。玩家提出的任何指令请求都可以用此工具执行（如给物品、传送、修改游戏规则等），不需要你判断权限——所有指令会自动送去管理员审批，审批通过后才会执行。只管调用工具，把结果告诉玩家即可。",
                "command", "string", "要执行的指令，不要带开头的 /");


        JsonObject statusTool = new JsonObject();
        statusTool.addProperty("type", "function");
        JsonObject statusFn = new JsonObject();
        statusFn.addProperty("name", "get_server_status");
        statusFn.addProperty("description", "获取服务器实时状态：当前游戏时间和日期、天气（晴/雨/雷暴）、所在生物群系、服务器负载（TPS/MSPT）。无需参数，自动使用当前玩家的位置。");
        statusFn.add("parameters", GSON.fromJson("{\"type\": \"object\"}", JsonObject.class));
        statusTool.add("function", statusFn);

        JsonObject rulesTool = new JsonObject();
        rulesTool.addProperty("type", "function");
        JsonObject rulesFn = new JsonObject();
        rulesFn.addProperty("name", "get_game_rules");
        rulesFn.addProperty("description", "获取服务器游戏规则状态，包括昼夜循环、火焰蔓延、生物破坏、死亡不掉落、生物生成、天气循环、命令方块输出等关键规则。无需参数。");
        rulesFn.add("parameters", GSON.fromJson("{\"type\": \"object\"}", JsonObject.class));
        rulesTool.add("function", rulesFn);

        JsonArray tools = new JsonArray();
        tools.add(kbTool);
        tools.add(readTool);
        tools.add(cmdTool);
        JsonObject debugTool = new JsonObject();
        debugTool.addProperty("type", "function");
        JsonObject debugFn = new JsonObject();
        debugFn.addProperty("name", "get_debug_info");
        debugFn.addProperty("description", "获取玩家当前位置的F3调试信息：光照等级（方块光/天空光）、所在区块坐标、注视的方块或实体、区域难度。无需参数。");
        debugFn.add("parameters", GSON.fromJson("{\"type\": \"object\"}", JsonObject.class));
        debugTool.add("function", debugFn);

        JsonObject modsTool = new JsonObject();
        modsTool.addProperty("type", "function");
        JsonObject modsFn = new JsonObject();
        modsFn.addProperty("name", "get_installed_mods");
        modsFn.addProperty("description", "获取服务器上安装的所有Mod列表及其版本号。了解安装了哪些Mod后，你就能知道物品的命名空间格式（如 create:brass_ingot、thermal:copper_gear），从而在搜索知识库或执行指令时使用正确的Mod物品ID。无需参数。");
        modsFn.add("parameters", GSON.fromJson("{\"type\": \"object\"}", JsonObject.class));
        modsTool.add("function", modsFn);

        tools.add(statusTool);
        tools.add(rulesTool);
        tools.add(debugTool);
        tools.add(modsTool);
        return tools;
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
}
