package com.example.mcai.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.example.mcai.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * HTTP 传输层，负责发送 API 请求和解析响应。
 * 封装 OpenAI 兼容协议的 HTTP 调用，支持流式和非流式模式。
 */
public class ApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-API");
    private static final Gson GSON = new GsonBuilder().create();

    private final HttpClient httpClient;
    private final String endpoint;
    private final String apiKey;
    private final int requestTimeoutSeconds;
    private final boolean enableStream;

    public ApiClient(ModConfig config, String endpoint, String apiKey) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.requestTimeoutSeconds = config.getApiRequestTimeoutSeconds();
        this.enableStream = config.isEnableStream();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getApiConnectTimeoutSeconds()))
                .build();
    }

    /**
     * 发送 POST 请求到 API，解析响应，返回 choices[0].message 的 JsonObject。
     * 根据配置自动选择流式或非流式模式。
     *
     * @param bodyJson 请求体 JSON 字符串
     * @return 包含 message JsonObject 的 ApiResult
     */
    public ApiResult<JsonObject> sendAndParseMessage(String bodyJson) {
        if (enableStream) {
            return sendAndParseMessageStream(bodyJson);
        }
        return sendAndParseMessageSync(bodyJson);
    }

    // ═══════════════════════════════════════════════════════════════
    // Non-streaming (sync)
    // ═══════════════════════════════════════════════════════════════

    /**
     * 非流式请求：等待完整响应后解析
     */
    private ApiResult<JsonObject> sendAndParseMessageSync(String bodyJson) {
        HttpRequest request = buildRequest(bodyJson);

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            LOGGER.error("HTTP request failed: {}", e.getMessage());
            return ApiResult.err(e.getMessage());
        }

        if (response.statusCode() != 200) {
            return parseErrorResponse(response);
        }

        return parseSuccessResponse(response.body());
    }

    // ═══════════════════════════════════════════════════════════════
    // Streaming (SSE)
    // ═══════════════════════════════════════════════════════════════

    /**
     * 流式请求：逐行解析 SSE 响应，拼接 delta 内容
     */
    private ApiResult<JsonObject> sendAndParseMessageStream(String bodyJson) {
        // 添加 stream: true 到请求体
        JsonObject body = GSON.fromJson(bodyJson, JsonObject.class);
        body.addProperty("stream", true);
        String streamBodyJson = GSON.toJson(body);

        HttpRequest request = buildRequest(streamBodyJson);

        // 使用 BodyHandlers.ofLines() 处理流式响应
        HttpResponse<java.util.stream.Stream<String>> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        } catch (Exception e) {
            LOGGER.error("HTTP stream request failed: {}", e.getMessage());
            return ApiResult.err(e.getMessage());
        }

        if (response.statusCode() != 200) {
            // 流式请求错误时，读取完整响应体
            String errorBody = response.body().reduce("", (a, b) -> a + b);
            return parseErrorResponseString(response.statusCode(), errorBody);
        }

        return parseStreamResponse(response.body());
    }

    /**
     * 解析 SSE 流式响应
     * 格式: data: {"choices":[{"delta":{"content":"..."}}]}
     */
    private ApiResult<JsonObject> parseStreamResponse(java.util.stream.Stream<String> lines) {
        StringBuilder contentBuilder = new StringBuilder();
        String reasoningContent = null;
        String toolCallId = null;
        String toolCallName = null;
        StringBuilder toolCallArgs = new StringBuilder();

        try {
            for (String line : (Iterable<String>) lines::iterator) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.equals("data: [DONE]")) break;
                if (!line.startsWith("data: ")) continue;

                String jsonStr = line.substring(6);
                JsonObject chunk;
                try {
                    chunk = GSON.fromJson(jsonStr, JsonObject.class);
                } catch (Exception e) {
                    LOGGER.debug("Failed to parse SSE chunk: {}", jsonStr);
                    continue;
                }

                JsonArray choices = chunk.getAsJsonArray("choices");
                if (choices == null || choices.isEmpty()) continue;

                JsonObject choice = choices.get(0).getAsJsonObject();
                JsonObject delta = choice.getAsJsonObject("delta");
                if (delta == null) continue;

                // 处理 content
                if (delta.has("content") && !delta.get("content").isJsonNull()) {
                    contentBuilder.append(delta.get("content").getAsString());
                }

                // 处理 reasoning_content（思考模式）
                if (delta.has("reasoning_content") && !delta.get("reasoning_content").isJsonNull()) {
                    if (reasoningContent == null) reasoningContent = "";
                    reasoningContent += delta.get("reasoning_content").getAsString();
                }

                // 处理 tool_calls
                if (delta.has("tool_calls")) {
                    JsonArray toolCalls = delta.getAsJsonArray("tool_calls");
                    for (var tcEl : toolCalls) {
                        JsonObject tc = tcEl.getAsJsonObject();
                        if (tc.has("id") && !tc.get("id").isJsonNull()) {
                            toolCallId = tc.get("id").getAsString();
                        }
                        if (tc.has("function")) {
                            JsonObject fn = tc.getAsJsonObject("function");
                            if (fn.has("name") && !fn.get("name").isJsonNull()) {
                                toolCallName = fn.get("name").getAsString();
                            }
                            if (fn.has("arguments") && !fn.get("arguments").isJsonNull()) {
                                toolCallArgs.append(fn.get("arguments").getAsString());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read stream: {}", e.getMessage());
            return ApiResult.err("stream read failed: " + e.getMessage());
        }

        // 构建等效的 message JsonObject
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");

        if (contentBuilder.length() > 0) {
            message.addProperty("content", contentBuilder.toString());
        }

        if (reasoningContent != null && !reasoningContent.isEmpty()) {
            message.addProperty("reasoning_content", reasoningContent);
        }

        // 如果有 tool_calls，构建完整的 tool_calls 数组
        if (toolCallId != null && toolCallName != null) {
            JsonArray toolCallsArray = new JsonArray();
            JsonObject tcObj = new JsonObject();
            tcObj.addProperty("id", toolCallId);
            tcObj.addProperty("type", "function");
            JsonObject fnObj = new JsonObject();
            fnObj.addProperty("name", toolCallName);
            fnObj.addProperty("arguments", toolCallArgs.toString());
            tcObj.add("function", fnObj);
            toolCallsArray.add(tcObj);
            message.add("tool_calls", toolCallsArray);
        }

        if (!message.has("content") && !message.has("tool_calls")) {
            return ApiResult.err("empty stream response");
        }

        return ApiResult.ok(message);
    }

    // ═══════════════════════════════════════════════════════════════
    // Common helpers
    // ═══════════════════════════════════════════════════════════════

    /**
     * 构建 HTTP 请求
     */
    private HttpRequest buildRequest(String bodyJson) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint.replaceAll("/+$", "") + "/chat/completions"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson));
        if (!apiKey.isEmpty()) builder.header("Authorization", "Bearer " + apiKey);
        return builder.build();
    }

    /**
     * 解析错误响应
     */
    private ApiResult<JsonObject> parseErrorResponse(HttpResponse<String> response) {
        return parseErrorResponseString(response.statusCode(), response.body());
    }

    /**
     * 解析错误响应（通用）
     */
    private ApiResult<JsonObject> parseErrorResponseString(int statusCode, String body) {
        try {
            JsonObject err = GSON.fromJson(body, JsonObject.class);
            if (err.has("error")) {
                String msg = err.getAsJsonObject("error").get("message").getAsString();
                LOGGER.error("API error {}: {}", statusCode, msg);
                return ApiResult.err(msg);
            }
        } catch (Exception ignored) {
        }
        LOGGER.error("API error {} (no JSON error): {}", statusCode,
                body.length() > 200 ? body.substring(0, 200) + "..." : body);
        return ApiResult.err("HTTP " + statusCode);
    }

    /**
     * 解析成功响应，提取 choices[0].message
     */
    private ApiResult<JsonObject> parseSuccessResponse(String body) {
        JsonObject json;
        try {
            json = GSON.fromJson(body, JsonObject.class);
        } catch (Exception e) {
            LOGGER.error("Failed to parse API response: {}", e.getMessage());
            return ApiResult.err("response parse failed");
        }

        JsonArray choices = json.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) return ApiResult.err("no choices in response");

        JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
        if (message == null) return ApiResult.err("no message in choice");
        return ApiResult.ok(message);
    }
}
