package fr.nivcoo.challenges.api.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ChallengeScoreSnapshot(int rank, UUID playerUuid, BigDecimal score) {
    public ChallengeScoreSnapshot {
        score = score == null ? BigDecimal.ZERO : score;
    }
}
