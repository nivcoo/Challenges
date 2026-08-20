package fr.nivcoo.challenges.messaging.model;

import java.util.UUID;

public record ChallengeProgressMutation(UUID observationId, UUID playerId, String signedDelta,
                                        long observedAt, String world) {
    public ChallengeProgressMutation {
        signedDelta = signedDelta == null ? "" : signedDelta;
        world = world == null ? "" : world;
    }
}
