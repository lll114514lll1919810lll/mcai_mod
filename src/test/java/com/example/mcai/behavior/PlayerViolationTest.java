package com.example.mcai.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerViolationTest {

    @Test
    void constructor_storesAllFields() {
        PlayerViolation violation = new PlayerViolation(
                "Steve", "spam in chat", -20, "warn"
        );

        assertEquals("Steve", violation.playerName);
        assertEquals("spam in chat", violation.description);
        assertEquals(-20, violation.severity);
        assertEquals("warn", violation.suggestedAction);
    }

    // ═══════════════════════════════════════════════════════════════
    // Severity values
    // ═══════════════════════════════════════════════════════════════

    @Test
    void severity_minor() {
        PlayerViolation violation = new PlayerViolation(
                "Steve", "minor offense", -10, "none"
        );

        assertEquals(-10, violation.severity);
    }

    @Test
    void severity_moderate() {
        PlayerViolation violation = new PlayerViolation(
                "Steve", "moderate offense", -20, "warn"
        );

        assertEquals(-20, violation.severity);
    }

    @Test
    void severity_severe() {
        PlayerViolation violation = new PlayerViolation(
                "Steve", "severe offense", -30, "kick"
        );

        assertEquals(-30, violation.severity);
    }

    // ═══════════════════════════════════════════════════════════════
    // Suggested actions
    // ═══════════════════════════════════════════════════════════════

    @Test
    void suggestedAction_none() {
        PlayerViolation violation = new PlayerViolation(
                "Steve", "minor", -10, "none"
        );

        assertEquals("none", violation.suggestedAction);
    }

    @Test
    void suggestedAction_warn() {
        PlayerViolation violation = new PlayerViolation(
                "Steve", "moderate", -20, "warn"
        );

        assertEquals("warn", violation.suggestedAction);
    }

    @Test
    void suggestedAction_kick() {
        PlayerViolation violation = new PlayerViolation(
                "Steve", "severe", -30, "kick"
        );

        assertEquals("kick", violation.suggestedAction);
    }

    @Test
    void suggestedAction_null_defaultsToNone() {
        PlayerViolation violation = new PlayerViolation(
                "Steve", "test", -10, null
        );

        assertEquals("none", violation.suggestedAction);
    }

    // ═══════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════

    @Test
    void playerName_canContainNumbers() {
        PlayerViolation violation = new PlayerViolation(
                "Player123", "test", -10, "none"
        );

        assertEquals("Player123", violation.playerName);
    }

    @Test
    void description_canBeLong() {
        String longDesc = "A".repeat(500);
        PlayerViolation violation = new PlayerViolation(
                "Steve", longDesc, -10, "none"
        );

        assertEquals(longDesc, violation.description);
    }
}
