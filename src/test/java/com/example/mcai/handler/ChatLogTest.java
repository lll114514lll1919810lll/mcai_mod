package com.example.mcai.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatLogTest {

    private ChatLog chatLog;

    @BeforeEach
    void setUp() {
        chatLog = new ChatLog();
    }

    // ═══════════════════════════════════════════════════════════════
    // Basic operations
    // ═══════════════════════════════════════════════════════════════

    @Test
    void peek_emptyLog_returnsEmptyString() {
        String result = chatLog.peek();

        assertEquals("", result);
    }

    @Test
    void add_singleMessage() {
        chatLog.add("Steve", "Hello!");

        String result = chatLog.peek();
        assertTrue(result.contains("Steve"));
        assertTrue(result.contains("Hello!"));
    }

    @Test
    void add_multipleMessages() {
        chatLog.add("Steve", "Hello!");
        chatLog.add("Alex", "Hi there!");

        String result = chatLog.peek();
        assertTrue(result.contains("Steve"));
        assertTrue(result.contains("Hello!"));
        assertTrue(result.contains("Alex"));
        assertTrue(result.contains("Hi there!"));
    }

    @Test
    void add_adminMessage_hasPrefix() {
        chatLog.add("Admin", "Rule announcement", true);

        String result = chatLog.peek();
        assertTrue(result.contains("[管理员]"));
        assertTrue(result.contains("Admin"));
    }

    @Test
    void add_normalMessage_noAdminPrefix() {
        chatLog.add("Steve", "Hello!");

        String result = chatLog.peek();
        assertFalse(result.contains("[管理员]"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Max size limit
    // ═══════════════════════════════════════════════════════════════

    @Test
    void add_exceedsMaxSize_removesOldest() {
        // 添加 55 条消息（超过 MAX_SIZE=50）
        for (int i = 0; i < 55; i++) {
            chatLog.add("Player" + i, "Msg-" + i + "-end");
        }

        String result = chatLog.peek();
        // 最早的 5 条应该被移除
        assertFalse(result.contains("Msg-0-end"));
        assertFalse(result.contains("Msg-4-end"));
        // 最新的应该保留
        assertTrue(result.contains("Msg-54-end"));
        assertTrue(result.contains("Msg-50-end"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Clear
    // ═══════════════════════════════════════════════════════════════

    @Test
    void clear_emptiesLog() {
        chatLog.add("Steve", "Hello!");
        chatLog.add("Alex", "Hi!");

        chatLog.clear();

        assertEquals("", chatLog.peek());
    }

    @Test
    void clear_onEmptyLog_doesNotThrow() {
        assertDoesNotThrow(() -> chatLog.clear());
    }

    // ═══════════════════════════════════════════════════════════════
    // Caching behavior
    // ═══════════════════════════════════════════════════════════════

    @Test
    void peek_returnsCachedResult() {
        chatLog.add("Steve", "Hello!");

        String first = chatLog.peek();
        String second = chatLog.peek();

        // 应该返回相同的缓存结果
        assertSame(first, second);
    }

    @Test
    void add_invalidatesCache() {
        chatLog.add("Steve", "Hello!");
        String first = chatLog.peek();

        chatLog.add("Alex", "Hi!");
        String second = chatLog.peek();

        // 缓存应该被刷新
        assertNotEquals(first, second);
    }

    @Test
    void clear_invalidatesCache() {
        chatLog.add("Steve", "Hello!");
        chatLog.peek(); // 填充缓存

        chatLog.clear();
        String result = chatLog.peek();

        assertEquals("", result);
    }

    // ═══════════════════════════════════════════════════════════════
    // Timestamp format
    // ═══════════════════════════════════════════════════════════════

    @Test
    void add_containsTimestamp() {
        chatLog.add("Steve", "Hello!");

        String result = chatLog.peek();
        // 应该包含时间戳格式 [YYYY-MM-DD HH:MM:SS]
        assertTrue(result.matches(".*\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\].*"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Message format
    // ═══════════════════════════════════════════════════════════════

    @Test
    void add_messageFormat_correct() {
        chatLog.add("Steve", "Hello!");

        String result = chatLog.peek();
        // 格式: [timestamp] name: message
        assertTrue(result.contains("Steve: Hello!"));
    }

    @Test
    void add_adminMessageFormat_correct() {
        chatLog.add("Admin", "Rule", true);

        String result = chatLog.peek();
        assertTrue(result.contains("[管理员] Admin: Rule"));
    }
}
