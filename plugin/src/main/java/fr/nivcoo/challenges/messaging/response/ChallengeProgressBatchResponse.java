package fr.nivcoo.challenges.messaging.response;

import fr.nivcoo.challenges.messaging.model.ChallengeScoreEntry;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ChallengeProgressBatchResponse(String authorityInstanceId, UUID runId, long generation,
                                             String participantInstanceId, long batchSequence,
                                             UUID batchId, boolean accepted, String reason,
                                             List<UUID> appliedObservationIds,
                                             List<ChallengeScoreEntry> scores,
                                             long baseStateRevision, long stateRevision) {
    public ChallengeProgressBatchResponse {
        authorityInstanceId = authorityInstanceId == null ? "" : authorityInstanceId;
        participantInstanceId = participantInstanceId == null ? "" : participantInstanceId;
        reason = reason == null ? "" : reason;
        if (appliedObservationIds != null && (appliedObservationIds.size() > 256
                || appliedObservationIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(appliedObservationIds).size() != appliedObservationIds.size())) {
            throw new IllegalArgumentException("Challenge batch response has invalid observation IDs.");
        }
        appliedObservationIds = appliedObservationIds == null ? List.of() : List.copyOf(appliedObservationIds);
        if (scores != null && scores.size() > 256) {
            throw new IllegalArgumentException("Challenge batch response is too large.");
        }
        scores = scores == null ? List.of() : List.copyOf(scores);
    }
}
