package fr.nivcoo.challenges.challenges;

import java.util.UUID;

public record ChallengeRun(UUID runId, long generation, String authorityInstanceId,
                           String challengeId, String challengeDigest, long startsAt, long endsAt) {
    public ChallengeRun {
        if (runId == null) throw new IllegalArgumentException("runId must not be null.");
        if (generation <= 0) throw new IllegalArgumentException("generation must be positive.");
        if (authorityInstanceId == null || authorityInstanceId.isBlank() || authorityInstanceId.length() > 128) {
            throw new IllegalArgumentException("authorityInstanceId must not be blank.");
        }
        if (challengeId == null || challengeId.isBlank() || challengeId.length() > 128) {
            throw new IllegalArgumentException("challengeId must not be blank.");
        }
        if (challengeDigest == null || challengeDigest.length() != 64
                || !challengeDigest.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("challengeDigest must not be blank.");
        }
        if (startsAt >= endsAt) throw new IllegalArgumentException("startsAt must be before endsAt.");
    }

    public boolean matches(UUID expectedRunId, long expectedGeneration) {
        return runId.equals(expectedRunId) && generation == expectedGeneration;
    }
}
