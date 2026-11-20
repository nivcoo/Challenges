package fr.nivcoo.challenges.messaging.model;

import java.util.Objects;
import java.util.UUID;

public record ChallengeRankingEntry(UUID playerId, int score, long reachedAt) {
    public ChallengeRankingEntry {
        Objects.requireNonNull(playerId, "playerId");
        if (reachedAt < 0L) throw new IllegalArgumentException("Ranking timestamp must not be negative.");
        if (score < 0) throw new IllegalArgumentException("Ranking score must not be negative.");
    }
}
