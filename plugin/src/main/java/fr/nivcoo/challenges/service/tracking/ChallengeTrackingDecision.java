package fr.nivcoo.challenges.service.tracking;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;

public record ChallengeTrackingDecision(UUID observationId, UUID runId, String definitionId, String objectiveId,
                                        UUID playerId, ChallengeTrackingDirection direction, BigDecimal signedDelta,
                                        Instant observedAt, String world) {
}
