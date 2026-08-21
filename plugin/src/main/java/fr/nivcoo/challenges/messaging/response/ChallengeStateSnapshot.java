package fr.nivcoo.challenges.messaging.response;

import fr.nivcoo.challenges.challenges.ChallengeRun;
import fr.nivcoo.challenges.challenges.ChallengeRunPhase;
import fr.nivcoo.challenges.challenges.ChallengeScoreLedger;
import fr.nivcoo.challenges.messaging.model.ChallengeScoreEntry;

import java.util.List;

public record ChallengeStateSnapshot(String authorityInstanceId, long generation,
                                     ChallengeRun run, ChallengeRunPhase phase,
                                     long effectiveEndsAt, boolean scoresIncluded,
                                     List<ChallengeScoreEntry> scores, long stateRevision,
                                     long rankingRevision, long nextExpectedBatchSequence) {
    public ChallengeStateSnapshot {
        authorityInstanceId = authorityInstanceId == null ? "" : authorityInstanceId;
        if (scores != null && scores.size() > ChallengeScoreLedger.MAX_SCORE_ENTRIES) {
            throw new IllegalArgumentException("Challenge snapshot contains too many score entries.");
        }
        scores = scores == null ? List.of() : List.copyOf(scores);
        if (nextExpectedBatchSequence <= 0L) {
            throw new IllegalArgumentException("Next expected batch sequence must be positive.");
        }
    }
}
