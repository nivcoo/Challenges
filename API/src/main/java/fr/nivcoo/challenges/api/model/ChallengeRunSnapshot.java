package fr.nivcoo.challenges.api.model;

import java.util.UUID;

public record ChallengeRunSnapshot(
        UUID runId,
        long generation,
        String challengeId,
        String displayName,
        long startsAt,
        long endsAt,
        long effectiveEndsAt,
        ChallengePhase phase,
        String message
) {
    public ChallengeRunSnapshot(
            UUID runId,
            long generation,
            String challengeId,
            long startsAt,
            long endsAt,
            long effectiveEndsAt,
            ChallengePhase phase,
            String message
    ) {
        this(runId, generation, challengeId, challengeId, startsAt, endsAt,
                effectiveEndsAt, phase, message);
    }
}
