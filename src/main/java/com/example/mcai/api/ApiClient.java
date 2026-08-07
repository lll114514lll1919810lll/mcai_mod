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

/**
 * HTTP 传输层，负责发送 API 请求和解析响应。
 * 封装 OpenAI 兼容协议的 HTTP 调用。
 */
public class ApiClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("MCAI-API");
    private static final Gson GSON = new GsonBuilder().create();

    private final HttpClient httpClient;
    private final String endpoint;
    private final String apiKey;
    private final int requestTimeoutSeconds;

    public ApiClient(ModConfig config, String endpoint, String apiKey) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.requestTimeoutSeconds = config.getApiRequestTimeoutSeconds();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.getApiConnectTimeoutSeconds()))
                .build();
    }

    /**
     * 发送 POST 请求到 API，解析响应，返回 choices[0].message 的 JsonObject。
     * 处理 HTTP 错误、JSON 解析、choices 提取。
     *
     * @param bodyJson 请求体 JSON 字符串
     * @return 包含 message JsonObject 的 ApiResult
     */
    public ApiResult<JsonObject> sendAndParseMessage(String bodyJson) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint.replaceAll("/+$", "") + "/chat/completions"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson));
        if (!apiKey.isEmpty()) builder.header("Authorization", "Bearer " + apiKey);

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            LOGGER.error("HTTP request failed: {}", e.getMessage());
            return ApiResult.err(e.getMessage());
        }

        if (response.statusCode() != 200) {
            return parseErrorResponse(response);
        }

        return parseSuccessResponse(response.body());
    }

    /**
     * 解析错误响应
     */
    private ApiResult<JsonObject> parseErrorResponse(HttpResponse<String> response) {
        String resp = response.body();
        try {
            JsonObject err = GSON.fromJson(resp, JsonObject.class);
            if (err.has("error")) {
                String msg = err.getAsJsonObject("error").get("message").getAsString();
                LOGGER.error("API error {}: {}", response.statusCode(), msg);
                return ApiResult.err(msg);
            }
        } catch (Exception ignored) {
        }
        LOGGER.error("API error {} (no JSON error): {}", response.statusCode(),
                resp.length() > 200 ? resp.substring(0, 200) + "..." : resp);
        return ApiResult.err("HTTP " + response.statusCode());
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
