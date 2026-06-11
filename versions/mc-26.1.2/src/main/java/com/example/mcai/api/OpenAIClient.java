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
import java.util.Optional;
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
                // tool_calls 娑堟伅涓嶄紶 content锛岄槻姝?API 鎶ラ敊
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

    public Optional<String> chat(List<ChatMessage> messages,
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
                return Optional.of("[API杩炴帴澶辫触] " + e.getMessage());
            }

            if (response.statusCode() != 200) {
                String resp = response.body();
                // Try to extract error message from JSON response
                try {
                    JsonObject err = GSON.fromJson(resp, JsonObject.class);
                    if (err.has("error")) {
                        String msg = err.getAsJsonObject("error").get("message").getAsString();
                        LOGGER.error("API error {}: {}", response.statusCode(), msg);
                        return Optional.of("[API閿欒] " + msg);
                    }
                } catch (Exception ignored) {}
                LOGGER.error("API error {} (no JSON error): {}", response.statusCode(),
                        resp.length() > 200 ? resp.substring(0, 200) + "..." : resp);
                return Optional.of("[API閿欒] HTTP " + response.statusCode());
            }

            JsonObject json;
            try {
                json = GSON.fromJson(response.body(), JsonObject.class);
            } catch (Exception e) {
                LOGGER.error("Failed to parse API response: {}", e.getMessage());
                return Optional.of("[鍝嶅簲瑙ｆ瀽澶辫触] 璇锋鏌?API 鏄惁鍏煎");
            }

            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                return Optional.of("[API寮傚父] 鍝嶅簲涓棤 choices");
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
                    return Optional.of("[宸ュ叿璋冪敤寮傚父] 绌虹殑 tool_calls");
                }

                var asstMsg = ChatMessage.toolCallRequest(toolCalls);
            // 鍙洖浼犻潪绌?reasoning_content
            if (reasoningContent != null && !reasoningContent.isEmpty()) {
                asstMsg.reasoningContent = reasoningContent;
            }
                messages.add(asstMsg);

                List<String> results = toolExecutor.apply(toolCalls);
                for (int i = 0; i < toolCalls.size(); i++) {
                    messages.add(ChatMessage.toolResult(
                            toolCalls.get(i).id,
                            results.size() > i ? results.get(i) : "鏃犵粨鏋?));
                }
                continue;
            }

            String content = msg.has("content") && !msg.get("content").isJsonNull()
                    ? msg.get("content").getAsString() : "";
            if (content.isEmpty()) return Optional.of("[API寮傚父] 鍝嶅簲鍐呭涓虹┖");
            return Optional.of(content);
        }

        // Tool call limit reached 鈥?make one final call without tools for text-only response
        JsonObject body = new JsonObject();
        body.addProperty("model", config.getModel());
        body.addProperty("max_tokens", config.getMaxTokens());
        body.addProperty("temperature", config.getTemperature());
        JsonArray msgArray = new JsonArray();
        for (ChatMessage msg : messages) {
            msgArray.add(msg.toJson());
        }
        body.add("messages", msgArray);
        // Intentionally omit tools 鈥?AI can only reply with text

        try {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(endpoint))
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofSeconds(60))
                            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                JsonArray choices = json.getAsJsonArray("choices");
                if (choices != null && !choices.isEmpty()) {
                    JsonObject msg = choices.get(0).getAsJsonObject().getAsJsonObject("message");
                    String content = msg.has("content") && !msg.get("content").isJsonNull()
                            ? msg.get("content").getAsString() : "";
                    if (!content.isEmpty()) return Optional.of(content);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Final text-only call failed: {}", e.getMessage());
        }
        return Optional.of("[宸ュ叿璋冪敤瓒呴檺] 瓒呰繃 " + maxTurns + " 杞伐鍏疯皟鐢?);
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
    public Optional<String> chatSimple(List<ChatMessage> messages) {
        var result = chatSimpleFull(messages);
        return result.map(r -> r.content);
    }

    /**
     * Like chatSimple but returns both content and reasoning_content.
     */
    public Optional<ChatSimpleResult> chatSimpleFull(List<ChatMessage> messages) {
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
            return Optional.of(new ChatSimpleResult("[API杩炴帴澶辫触] " + e.getMessage(), null));
        }

        if (response.statusCode() != 200) {
            String resp = response.body();
            try {
                JsonObject err = GSON.fromJson(resp, JsonObject.class);
                if (err.has("error")) {
                    String msg = err.getAsJsonObject("error").get("message").getAsString();
                    LOGGER.error("API error {}: {}", response.statusCode(), msg);
                    return Optional.of(new ChatSimpleResult("[API閿欒] " + msg, null));
                }
            } catch (Exception ignored) {}
            LOGGER.error("API error {} (no JSON error): {}", response.statusCode(),
                    resp.length() > 200 ? resp.substring(0, 200) + "..." : resp);
            return Optional.of(new ChatSimpleResult("[API閿欒] HTTP " + response.statusCode(), null));
        }

        JsonObject json;
        try {
            json = GSON.fromJson(response.body(), JsonObject.class);
        } catch (Exception e) {
            LOGGER.error("Failed to parse API response: {}", e.getMessage());
            return Optional.of(new ChatSimpleResult("[鍝嶅簲瑙ｆ瀽澶辫触] 璇锋鏌?API 鏄惁鍏煎", null));
        }

        JsonArray choices = json.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            return Optional.of(new ChatSimpleResult("[API寮傚父] 鍝嶅簲涓棤 choices", null));
        }

        JsonObject choice = choices.get(0).getAsJsonObject();
        JsonObject msg = choice.getAsJsonObject("message");

        String reasoningContent = msg.has("reasoning_content") && !msg.get("reasoning_content").isJsonNull()
                ? msg.get("reasoning_content").getAsString() : null;

        String content = msg.has("content") && !msg.get("content").isJsonNull()
                ? msg.get("content").getAsString() : "";
        if (content.isEmpty()) return Optional.of(new ChatSimpleResult("[API寮傚父] 鍝嶅簲鍐呭涓虹┖", reasoningContent));
        return Optional.of(new ChatSimpleResult(content, reasoningContent));
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
                "鎼滅储鏈湴 Minecraft Wiki 鐭ヨ瘑搴撱€傝緭鍏ヤ腑鏂囧叧閿瘝锛岃繑鍥炲尮閰嶆潯鐩爣棰樺拰鎽樿銆傚闇€鏌ョ湅瀹屾暣鍐呭锛屽啀鐢?read_knowledge_base銆?,
                "query", "string", "鎼滅储鍏抽敭璇嶏紙涓枃锛?);

        JsonObject readTool = buildTool("read_knowledge_base",
                "璇诲彇鐭ヨ瘑搴撲腑鏌愪釜鏉＄洰鐨勫畬鏁村唴瀹广€傚厛鐢?search_knowledge_base 鎼滅储鍒扮洰鏍囨潯鐩悗锛岀敤姝ゅ伐鍏疯幏鍙栧叏鏂囥€?,
                "title", "string", "鏉＄洰鏍囬锛堜粠 search_knowledge_base 鐨勭粨鏋滀腑鑾峰彇锛?);

        JsonObject cmdTool = buildTool("execute_minecraft_command",
                "鍦ㄦ湇鍔″櫒涓婃墽琛屼竴鏉?Minecraft 鎸囦护銆傜帺瀹舵彁鍑虹殑浠讳綍鎸囦护璇锋眰閮藉彲浠ョ敤姝ゅ伐鍏锋墽琛岋紙濡傜粰鐗╁搧銆佷紶閫併€佷慨鏀规父鎴忚鍒欑瓑锛夛紝涓嶉渶瑕佷綘鍒ゆ柇鏉冮檺鈥斺€旀墍鏈夋寚浠や細鑷姩閫佸幓绠＄悊鍛樺鎵癸紝瀹℃壒閫氳繃鍚庢墠浼氭墽琛屻€傚彧绠¤皟鐢ㄥ伐鍏凤紝鎶婄粨鏋滃憡璇夌帺瀹跺嵆鍙€?,
                "command", "string", "瑕佹墽琛岀殑鎸囦护锛屼笉瑕佸甫寮€澶寸殑 /");


        JsonObject statusTool = new JsonObject();
        statusTool.addProperty("type", "function");
        JsonObject statusFn = new JsonObject();
        statusFn.addProperty("name", "get_server_status");
        statusFn.addProperty("description", "鑾峰彇鏈嶅姟鍣ㄥ疄鏃剁姸鎬侊細褰撳墠娓告垙鏃堕棿鍜屾棩鏈熴€佸ぉ姘旓紙鏅?闆?闆锋毚锛夈€佹墍鍦ㄧ敓鐗╃兢绯汇€佹湇鍔″櫒璐熻浇锛圱PS/MSPT锛夈€傛棤闇€鍙傛暟锛岃嚜鍔ㄤ娇鐢ㄥ綋鍓嶇帺瀹剁殑浣嶇疆銆?);
        statusFn.add("parameters", GSON.fromJson("{\"type\": \"object\"}", JsonObject.class));
        statusTool.add("function", statusFn);

        JsonObject rulesTool = new JsonObject();
        rulesTool.addProperty("type", "function");
        JsonObject rulesFn = new JsonObject();
        rulesFn.addProperty("name", "get_game_rules");
        rulesFn.addProperty("description", "鑾峰彇鏈嶅姟鍣ㄦ父鎴忚鍒欑姸鎬侊紝鍖呮嫭鏄煎寰幆銆佺伀鐒拌敁寤躲€佺敓鐗╃牬鍧忋€佹浜′笉鎺夎惤銆佺敓鐗╃敓鎴愩€佸ぉ姘斿惊鐜€佸懡浠ゆ柟鍧楄緭鍑虹瓑鍏抽敭瑙勫垯銆傛棤闇€鍙傛暟銆?);
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
        debugFn.addProperty("description", "鑾峰彇鐜╁褰撳墠浣嶇疆鐨凢3璋冭瘯淇℃伅锛氬厜鐓х瓑绾э紙鏂瑰潡鍏?澶╃┖鍏夛級銆佹墍鍦ㄥ尯鍧楀潗鏍囥€佹敞瑙嗙殑鏂瑰潡鎴栧疄浣撱€佸尯鍩熼毦搴︺€傛棤闇€鍙傛暟銆?);
        debugFn.add("parameters", GSON.fromJson("{\"type\": \"object\"}", JsonObject.class));
        debugTool.add("function", debugFn);

        tools.add(statusTool);
        tools.add(rulesTool);
        tools.add(debugTool);
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
