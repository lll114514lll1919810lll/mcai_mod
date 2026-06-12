package com.example.mcai.behavior;
public class PenaltyEvent {
    public long timestamp; public final String playerName; public final String description;
    public final int severity; public final int scoreAfter; public final PenaltyAction action;
    public final int approvalId; public final int reviewCycle;
    public enum PenaltyAction { SCORE_ONLY, WARN, KICK, KICK_EXECUTED }
    public PenaltyEvent(String playerName, String description, int severity, int scoreAfter, PenaltyAction action, int approvalId, int reviewCycle) {
        this.timestamp = System.currentTimeMillis(); this.playerName = playerName; this.description = description;
        this.severity = severity; this.scoreAfter = scoreAfter; this.action = action; this.approvalId = approvalId; this.reviewCycle = reviewCycle;
    }
}
