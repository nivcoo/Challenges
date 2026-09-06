package fr.nivcoo.challenges.messaging.model;

import java.util.Objects;
import java.util.UUID;

public record ChallengeRankingEntry(UUID playerId, int score) {
    public ChallengeRankingEntry {
        Objects.requireNonNull(playerId, "playerId");
        if (score < 0) throw new IllegalArgumentException("Ranking score must not be negative.");
    }
}
