package fr.nivcoo.challenges.messaging.model;

import fr.nivcoo.challenges.challenges.ChallengeAmount;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public record ChallengeProgressContribution(UUID playerId, String rawAmount) {
    public ChallengeProgressContribution {
        Objects.requireNonNull(playerId, "playerId");
        BigDecimal parsed = ChallengeAmount.parseBalance(rawAmount);
        rawAmount = ChallengeAmount.canonical(parsed);
    }

    public BigDecimal amount() {
        return ChallengeAmount.parseBalance(rawAmount);
    }
}
