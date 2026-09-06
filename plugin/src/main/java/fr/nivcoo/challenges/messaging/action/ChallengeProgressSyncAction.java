package fr.nivcoo.challenges.messaging.action;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.ChallengeScoreLedger;
import fr.nivcoo.challenges.messaging.MessageActionName;
import fr.nivcoo.challenges.messaging.model.ChallengeProgressContribution;
import fr.nivcoo.utilsz.core.messaging.BusAction;
import fr.nivcoo.utilsz.core.messaging.BusMessage;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@BusAction(value = MessageActionName.CHALLENGE_PROGRESS_SYNC, runOnMainThread = true)
public record ChallengeProgressSyncAction(Kind kind, String authorityInstanceId,
                                          UUID runId, long generation,
                                          String participantInstanceId, UUID streamId,
                                          long revision, long latestObservedAt,
                                          List<ChallengeProgressContribution> contributions,
                                          boolean accepted, String reason) implements BusMessage {
    public ChallengeProgressSyncAction {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(streamId, "streamId");
        authorityInstanceId = authorityInstanceId == null ? "" : authorityInstanceId;
        participantInstanceId = participantInstanceId == null ? "" : participantInstanceId;
        reason = reason == null ? "" : reason;
        if (authorityInstanceId.isBlank() || authorityInstanceId.length() > 128
                || participantInstanceId.isBlank() || participantInstanceId.length() > 128
                || revision < 0L || latestObservedAt < 0L
                || kind == Kind.SNAPSHOT && revision == 0L) {
            throw new IllegalArgumentException("Challenge progress sync envelope is invalid.");
        }
        if (contributions != null && (contributions.size() > ChallengeScoreLedger.MAX_SCORE_ENTRIES
                || contributions.stream().anyMatch(Objects::isNull)
                || new HashSet<>(contributions.stream()
                .map(ChallengeProgressContribution::playerId).toList()).size() != contributions.size())) {
            throw new IllegalArgumentException("Challenge progress snapshot is invalid.");
        }
        contributions = contributions == null ? List.of() : List.copyOf(contributions);
        if (kind == Kind.ACK && !contributions.isEmpty()) {
            throw new IllegalArgumentException("Challenge progress acknowledgement contains contributions.");
        }
    }

    public static ChallengeProgressSyncAction snapshot(String authorityInstanceId,
                                                       UUID runId, long generation,
                                                       String participantInstanceId, UUID streamId,
                                                       long revision, long latestObservedAt,
                                                       List<ChallengeProgressContribution> contributions) {
        return new ChallengeProgressSyncAction(Kind.SNAPSHOT, authorityInstanceId,
                runId, generation, participantInstanceId, streamId, revision,
                latestObservedAt, contributions, false, "");
    }

    public static ChallengeProgressSyncAction ack(String authorityInstanceId,
                                                  UUID runId, long generation,
                                                  String participantInstanceId, UUID streamId,
                                                  long revision, boolean accepted, String reason) {
        return new ChallengeProgressSyncAction(Kind.ACK, authorityInstanceId,
                runId, generation, participantInstanceId, streamId, revision,
                0L, List.of(), accepted, reason);
    }

    @Override
    public void execute() {
        Challenges.get().getChallengesManager().handleProgressSyncAction(this);
    }

    public enum Kind {
        SNAPSHOT,
        ACK
    }
}
