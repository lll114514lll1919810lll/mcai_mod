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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * OpenAI 兼容 API 客户端门面类。
 * 组合 ApiClient（HTTP 传输）和 ToolDefinitions（工具定义），提供 chat/chatSimpleFull 方法。
 */
public class OpenAIClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-API");
    private static final Gson GSON = new GsonBuilder().create();

    private final ModConfig config;
    private final ApiClient apiClient;
    private final JsonArray toolDefinitions;
    private final String resolvedModel;

    public OpenAIClient(ModConfig config) {
        this(config, config.getApiEndpoint(), config.getApiKey(), config.getModel());
    }

    public OpenAIClient(ModConfig config, String endpoint, String apiKey, String model) {
        this.config = config;
        this.resolvedModel = model;
        this.apiClient = new ApiClient(config, endpoint, apiKey);
        this.toolDefinitions = ToolDefinitions.buildAll();
    }

    // ═══════════════════════════════════════════════════════════════
    // Data classes
    // ═══════════════════════════════════════════════════════════════

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

    public static class ChatSimpleResult {
        public final String content;
        public final String reasoningContent;

        public ChatSimpleResult(String content, String reasoningContent) {
            this.content = content;
            this.reasoningContent = reasoningContent;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════

    /**
     * 带工具调用的多轮对话。
     * 支持最多 maxToolCalls 轮工具调用，总超时 apiLoopTimeoutSeconds。
     */
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

            var dbg = MCAIMod.getInstance() != null ? MCAIMod.getInstance().getDebugLogger() : null;
            var result = sendChatRequest(messages, true);
            if (!result.success()) {
                if (dbg != null && dbg.isEnabled()) dbg.logError("API", result.error());
                return ApiResult.err(result.error());
            }
            JsonObject msg = result.value();

            String reasoningContent = extractReasoningContent(msg);
            if (dbg != null && dbg.isEnabled()) {
                if (reasoningContent != null && !reasoningContent.isEmpty()) dbg.logThinking(reasoningContent);
            }

            List<ToolCall> toolCalls = parseToolCalls(msg);
            if (!toolCalls.isEmpty()) {
                var asstMsg = ChatMessage.toolCallRequest(toolCalls);
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

            String content = extractContent(msg);
            if (content.isEmpty()) return ApiResult.err("empty response content");
            if (dbg != null && dbg.isEnabled()) dbg.logAIResponse(content);
            return ApiResult.ok(content);
        }

        return forceFinalResponse(messages);
    }

    /**
     * 简单单轮对话（无工具调用）。
     * 用于行为审查等场景。
     */
    public ApiResult<ChatSimpleResult> chatSimpleFull(List<ChatMessage> messages) {
        var result = sendChatRequest(messages, false);
        if (!result.success()) return ApiResult.err(result.error());
        JsonObject msg = result.value();

        String reasoningContent = extractReasoningContent(msg);
        String content = extractContent(msg);
        if (content.isEmpty()) return ApiResult.err("empty response content");

        return ApiResult.ok(new ChatSimpleResult(content, reasoningContent));
    }

    // ═══════════════════════════════════════════════════════════════
    // Private methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * 发送聊天请求
     */
    private ApiResult<JsonObject> sendChatRequest(List<ChatMessage> messages, boolean includeTools) {
        JsonObject body = buildRequestBody(messages, includeTools);
        String bodyJson = GSON.toJson(body);
        return apiClient.sendAndParseMessage(bodyJson);
    }

    /**
     * 构建请求体
     */
    private JsonObject buildRequestBody(List<ChatMessage> messages, boolean includeTools) {
        JsonObject body = buildBaseRequestBody();

        JsonArray msgArray = new JsonArray();
        for (ChatMessage msg : messages) {
            msgArray.add(msg.toJson());
        }
        body.add("messages", msgArray);

        if (includeTools) {
            body.add("tools", toolDefinitions);
        }

        return body;
    }

    /**
     * 构建基础请求体。兼容模式下只发送 model/messages。
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
     * 强制最终响应（工具调用次数用完时）
     */
    private ApiResult<String> forceFinalResponse(List<ChatMessage> messages) {
        messages.add(new ChatMessage("user",
                "本轮工具调用次数已用完。请基于已有信息给出最终回答，然后结束对话。不要尝试再次调用工具。"));

        var result = sendChatRequest(messages, false);
        if (result.success()) {
            String content = extractContent(result.value());
            if (!content.isEmpty()) return ApiResult.ok(content);
        }
        return ApiResult.err("工具调用次数已达上限，请稍后重试或简化请求");
    }

    /**
     * 从响应中提取 reasoning_content
     */
    private String extractReasoningContent(JsonObject msg) {
        if (msg.has("reasoning_content") && !msg.get("reasoning_content").isJsonNull()) {
            return msg.get("reasoning_content").getAsString();
        }
        return null;
    }

    /**
     * 从响应中提取 content
     */
    private String extractContent(JsonObject msg) {
        if (msg.has("content") && !msg.get("content").isJsonNull()) {
            return msg.get("content").getAsString();
        }
        return "";
    }

    /**
     * 解析工具调用
     */
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

    /**
     * 根据模型类型向请求体添加思考模式参数
     */
    private void addThinkingParams(JsonObject body, int thinkingLevel) {
        if (thinkingLevel < 1) return;
        if ("agnes-2.0-flash".equals(resolvedModel)) {
            JsonObject kwargs = new JsonObject();
            kwargs.addProperty("enable_thinking", true);
            body.add("chat_template_kwargs", kwargs);
        } else {
            JsonObject t = new JsonObject();
            t.addProperty("type", "enabled");
            body.add("thinking", t);
            if (thinkingLevel >= 3) {
                body.addProperty("reasoning_effort", "max");
            }
        }
    }
}
