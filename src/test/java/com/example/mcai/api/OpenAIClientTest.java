package com.example.mcai.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenAIClient 数据结构测试（不依赖 Mockito）。
 * 测试 ChatMessage、ToolCall、ChatSimpleResult 的构建和 JSON 序列化。
 */
class OpenAIClientTest {

    // ═══════════════════════════════════════════════════════════════
    // ToolCall tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void toolCall_constructor() {
        OpenAIClient.ToolCall tc = new OpenAIClient.ToolCall("id-1", "test_tool", "{\"arg\":\"val\"}");

        assertEquals("id-1", tc.id);
        assertEquals("test_tool", tc.name);
        assertEquals("{\"arg\":\"val\"}", tc.arguments);
    }

    // ═══════════════════════════════════════════════════════════════
    // ChatMessage tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void chatMessage_createsBasicMessage() {
        OpenAIClient.ChatMessage msg = new OpenAIClient.ChatMessage("user", "hello");

        assertEquals("user", msg.role);
        assertEquals("hello", msg.content);
        assertNull(msg.toolCalls);
        assertNull(msg.toolCallId);
    }

    @Test
    void chatMessage_toolCallRequest_withSingleTool() {
        OpenAIClient.ToolCall tc = new OpenAIClient.ToolCall("call-1", "search", "{\"query\":\"test\"}");
        OpenAIClient.ChatMessage msg = OpenAIClient.ChatMessage.toolCallRequest(tc);

        assertEquals("assistant", msg.role);
        assertEquals("", msg.content);
        assertNotNull(msg.toolCalls);
        assertEquals(1, msg.toolCalls.size());
        assertEquals("call-1", msg.toolCalls.get(0).id);
        assertEquals("search", msg.toolCalls.get(0).name);
    }

    @Test
    void chatMessage_toolCallRequest_withMultipleTools() {
        OpenAIClient.ToolCall tc1 = new OpenAIClient.ToolCall("call-1", "search", "{}");
        OpenAIClient.ToolCall tc2 = new OpenAIClient.ToolCall("call-2", "execute", "{}");
        OpenAIClient.ChatMessage msg = OpenAIClient.ChatMessage.toolCallRequest(List.of(tc1, tc2));

        assertEquals("assistant", msg.role);
        assertNotNull(msg.toolCalls);
        assertEquals(2, msg.toolCalls.size());
    }

    @Test
    void chatMessage_toolResult() {
        OpenAIClient.ChatMessage msg = OpenAIClient.ChatMessage.toolResult("call-1", "result data");

        assertEquals("tool", msg.role);
        assertEquals("result data", msg.content);
        assertEquals("call-1", msg.toolCallId);
    }

    // ═══════════════════════════════════════════════════════════════
    // ChatMessage.toJson() tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void chatMessage_toJson_basicMessage() {
        OpenAIClient.ChatMessage msg = new OpenAIClient.ChatMessage("user", "hello");
        JsonObject json = msg.toJson();

        assertEquals("user", json.get("role").getAsString());
        assertEquals("hello", json.get("content").getAsString());
        assertFalse(json.has("tool_calls"));
        assertFalse(json.has("tool_call_id"));
    }

    @Test
    void chatMessage_toJson_withToolCalls() {
        OpenAIClient.ToolCall tc = new OpenAIClient.ToolCall("call-1", "search", "{\"query\":\"test\"}");
        OpenAIClient.ChatMessage msg = OpenAIClient.ChatMessage.toolCallRequest(tc);
        JsonObject json = msg.toJson();

        assertEquals("assistant", json.get("role").getAsString());
        assertFalse(json.has("content")); // tool_calls消息不传content
        assertTrue(json.has("tool_calls"));

        JsonArray toolCalls = json.getAsJsonArray("tool_calls");
        assertEquals(1, toolCalls.size());

        JsonObject tcObj = toolCalls.get(0).getAsJsonObject();
        assertEquals("call-1", tcObj.get("id").getAsString());
        assertEquals("function", tcObj.get("type").getAsString());

        JsonObject fn = tcObj.getAsJsonObject("function");
        assertEquals("search", fn.get("name").getAsString());
        assertEquals("{\"query\":\"test\"}", fn.get("arguments").getAsString());
    }

    @Test
    void chatMessage_toJson_withToolResult() {
        OpenAIClient.ChatMessage msg = OpenAIClient.ChatMessage.toolResult("call-1", "result");
        JsonObject json = msg.toJson();

        assertEquals("tool", json.get("role").getAsString());
        assertEquals("result", json.get("content").getAsString());
        assertEquals("call-1", json.get("tool_call_id").getAsString());
    }

    @Test
    void chatMessage_toJson_withReasoningContent() {
        OpenAIClient.ChatMessage msg = new OpenAIClient.ChatMessage("assistant", "answer");
        msg.reasoningContent = "thinking process";
        JsonObject json = msg.toJson();

        assertEquals("thinking process", json.get("reasoning_content").getAsString());
    }

    @Test
    void chatMessage_toJson_nullContent() {
        OpenAIClient.ChatMessage msg = new OpenAIClient.ChatMessage("user", null);
        JsonObject json = msg.toJson();

        assertEquals("", json.get("content").getAsString());
    }

    // ═══════════════════════════════════════════════════════════════
    // ChatSimpleResult tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void chatSimpleResult_constructor() {
        OpenAIClient.ChatSimpleResult result = new OpenAIClient.ChatSimpleResult("content", "reasoning");

        assertEquals("content", result.content);
        assertEquals("reasoning", result.reasoningContent);
    }

    @Test
    void chatSimpleResult_withNullReasoning() {
        OpenAIClient.ChatSimpleResult result = new OpenAIClient.ChatSimpleResult("content", null);

        assertEquals("content", result.content);
        assertNull(result.reasoningContent);
    }
}
