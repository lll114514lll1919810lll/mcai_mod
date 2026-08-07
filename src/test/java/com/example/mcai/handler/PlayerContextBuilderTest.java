package com.example.mcai.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PlayerContextBuilder 测试。
 * 只测试静态方法 formatGameTime，不需要 Minecraft 依赖。
 */
class PlayerContextBuilderTest {

    // ═══════════════════════════════════════════════════════════════
    // formatGameTime tests
    // ═══════════════════════════════════════════════════════════════

    @Test
    void formatGameTime_day1_morning() {
        // tick=0 是第1天 6:00 AM
        String result = PlayerContextBuilder.formatGameTime(0);

        assertTrue(result.contains("第1天"));
        assertTrue(result.contains("6:00 AM"));
        assertTrue(result.contains("tick=0"));
    }

    @Test
    void formatGameTime_day1_noon() {
        // tick=6000 是第1天 12:00 PM (中午)
        String result = PlayerContextBuilder.formatGameTime(6000);

        assertTrue(result.contains("第1天"));
        assertTrue(result.contains("12:00 PM"));
        assertTrue(result.contains("tick=6000"));
    }

    @Test
    void formatGameTime_day1_midnight() {
        // tick=18000 是第1天 12:00 AM (午夜)
        String result = PlayerContextBuilder.formatGameTime(18000);

        assertTrue(result.contains("第1天"));
        assertTrue(result.contains("12:00 AM"));
        assertTrue(result.contains("tick=18000"));
    }

    @Test
    void formatGameTime_day2() {
        // tick=24000 是第2天 6:00 AM
        String result = PlayerContextBuilder.formatGameTime(24000);

        assertTrue(result.contains("第2天"));
        assertTrue(result.contains("6:00 AM"));
        assertTrue(result.contains("tick=24000"));
    }

    @Test
    void formatGameTime_afternoon() {
        // tick=12000 是第1天 6:00 PM
        String result = PlayerContextBuilder.formatGameTime(12000);

        assertTrue(result.contains("第1天"));
        assertTrue(result.contains("6:00 PM"));
    }

    @Test
    void formatGameTime_formatCorrect() {
        String result = PlayerContextBuilder.formatGameTime(6000);

        // 格式: 第N天 H:MM AM/PM (tick=N)
        assertTrue(result.matches("第\\d+天 \\d+:\\d{2} [AP]M \\(tick=\\d+\\)"));
    }

    @Test
    void formatGameTime_zeroPaddedMinute() {
        // tick=6001 应该显示 12:00 PM (分钟应该是两位数)
        String result = PlayerContextBuilder.formatGameTime(6000);

        assertTrue(result.contains(":00"));
    }

    @Test
    void formatGameTime_highTickValue() {
        // 大的 tick 值
        String result = PlayerContextBuilder.formatGameTime(100000);

        assertTrue(result.contains("第5天")); // 100000 / 24000 + 1 = 5
    }
}
