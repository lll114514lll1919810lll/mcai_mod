package com.example.mcai.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PenaltyEventTest {

    @Test
    void constructor_storesAllFields() {
        PenaltyEvent event = new PenaltyEvent(
                "Steve", "spam", -20, -30, PenaltyEvent.PenaltyAction.WARN, 0, 1
        );

        assertEquals("Steve", event.playerName);
        assertEquals("spam", event.description);
        assertEquals(-20, event.severity);
        assertEquals(-30, event.scoreAfter);
        assertEquals(PenaltyEvent.PenaltyAction.WARN, event.action);
        assertEquals(0, event.approvalId);
        assertEquals(1, event.reviewCycle);
    }

    @Test
    void constructor_setsTimestamp() {
        long before = System.currentTimeMillis();
        PenaltyEvent event = new PenaltyEvent(
                "Steve", "spam", -20, -30, PenaltyEvent.PenaltyAction.WARN, 0, 1
        );
        long after = System.currentTimeMillis();

        assertTrue(event.timestamp >= before);
        assertTrue(event.timestamp <= after);
    }

    // ═══════════════════════════════════════════════════════════════
    // PenaltyAction enum
    // ═══════════════════════════════════════════════════════════════

    @Test
    void penaltyAction_hasCorrectValues() {
        PenaltyEvent.PenaltyAction[] actions = PenaltyEvent.PenaltyAction.values();

        assertEquals(4, actions.length);
        assertEquals(PenaltyEvent.PenaltyAction.SCORE_ONLY, actions[0]);
        assertEquals(PenaltyEvent.PenaltyAction.WARN, actions[1]);
        assertEquals(PenaltyEvent.PenaltyAction.KICK, actions[2]);
        assertEquals(PenaltyEvent.PenaltyAction.KICK_EXECUTED, actions[3]);
    }

    @Test
    void penaltyAction_valueOf_works() {
        assertEquals(PenaltyEvent.PenaltyAction.SCORE_ONLY, PenaltyEvent.PenaltyAction.valueOf("SCORE_ONLY"));
        assertEquals(PenaltyEvent.PenaltyAction.WARN, PenaltyEvent.PenaltyAction.valueOf("WARN"));
        assertEquals(PenaltyEvent.PenaltyAction.KICK, PenaltyEvent.PenaltyAction.valueOf("KICK"));
        assertEquals(PenaltyEvent.PenaltyAction.KICK_EXECUTED, PenaltyEvent.PenaltyAction.valueOf("KICK_EXECUTED"));
    }

    @Test
    void penaltyAction_valueOf_invalid_throws() {
        assertThrows(IllegalArgumentException.class, () -> PenaltyEvent.PenaltyAction.valueOf("INVALID"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Different action types
    // ═══════════════════════════════════════════════════════════════

    @Test
    void scoreOnlyAction() {
        PenaltyEvent event = new PenaltyEvent(
                "Steve", "minor offense", -10, -10, PenaltyEvent.PenaltyAction.SCORE_ONLY, 0, 1
        );

        assertEquals(PenaltyEvent.PenaltyAction.SCORE_ONLY, event.action);
    }

    @Test
    void kickAction_withApprovalId() {
        PenaltyEvent event = new PenaltyEvent(
                "Steve", "severe offense", -30, -60, PenaltyEvent.PenaltyAction.KICK, 42, 1
        );

        assertEquals(PenaltyEvent.PenaltyAction.KICK, event.action);
        assertEquals(42, event.approvalId);
    }

    @Test
    void kickExecutedAction() {
        PenaltyEvent event = new PenaltyEvent(
                "Steve", "confirmed violation", -30, -90, PenaltyEvent.PenaltyAction.KICK_EXECUTED, 42, 2
        );

        assertEquals(PenaltyEvent.PenaltyAction.KICK_EXECUTED, event.action);
    }
}
