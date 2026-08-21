package fr.nivcoo.challenges.api.model;

import java.util.UUID;

public record ChallengeRunSnapshot(
        UUID runId,
        long generation,
        String challengeId,
        long startsAt,
        long endsAt,
        long effectiveEndsAt,
        ChallengePhase phase,
        String message
) {
}
