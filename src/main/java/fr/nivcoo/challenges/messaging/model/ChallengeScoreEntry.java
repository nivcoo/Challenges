package fr.nivcoo.challenges.messaging.model;

import fr.nivcoo.challenges.challenges.ChallengeAmount;

import java.math.BigDecimal;
import java.util.UUID;

public record ChallengeScoreEntry(UUID playerId, String rawBalance, long playerRevision) {
    public ChallengeScoreEntry {
        if (playerId == null) throw new IllegalArgumentException("playerId must not be null.");
        ChallengeAmount.parseBalance(rawBalance);
        if (playerRevision < 0L) throw new IllegalArgumentException("playerRevision must not be negative.");
    }

    public BigDecimal rawAmount() {
        return ChallengeAmount.parseBalance(rawBalance);
    }
}
