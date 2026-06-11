package com.example.mcai.behavior;

/** Parsed result from AI behavior review. */
public class PlayerViolation {
    public final String playerName;
    public final String description;
    public final int severity;       // negative score delta (e.g. -10, -20, -30)
    public final String suggestedAction; // "none", "warn", "kick"

    public PlayerViolation(String playerName, String description, int severity, String suggestedAction) {
        this.playerName = playerName;
        this.description = description;
        this.severity = severity;
        this.suggestedAction = suggestedAction != null ? suggestedAction : "none";
    }
}
