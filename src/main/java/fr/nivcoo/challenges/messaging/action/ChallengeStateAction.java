package fr.nivcoo.challenges.messaging.action;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.ChallengeRun;
import fr.nivcoo.challenges.challenges.ChallengeScoreLedger;
import fr.nivcoo.challenges.messaging.MessageActionName;
import fr.nivcoo.challenges.messaging.model.ChallengeScoreEntry;
import fr.nivcoo.utilsz.core.messaging.BusAction;
import fr.nivcoo.utilsz.core.messaging.BusMessage;

import java.util.List;
import java.util.UUID;

@BusAction(value = MessageActionName.CHALLENGE_STATE, runOnMainThread = true)
public record ChallengeStateAction(Kind kind, String authorityInstanceId,
                                   UUID runId, long generation, ChallengeRun run,
                                   long effectiveEndsAt, List<ChallengeScoreEntry> scores,
                                   long baseStateRevision, long stateRevision,
                                   long rankingRevision) implements BusMessage {
    public ChallengeStateAction {
        authorityInstanceId = authorityInstanceId == null ? "" : authorityInstanceId;
        if (scores != null && scores.size() > ChallengeScoreLedger.MAX_SCORE_ENTRIES) {
            throw new IllegalArgumentException("Challenge state contains too many score entries.");
        }
        scores = scores == null ? List.of() : List.copyOf(scores);
    }

    public static ChallengeStateAction coordinatorOnline(String authorityInstanceId, long rankingRevision) {
        return frame(Kind.COORDINATOR_ONLINE, authorityInstanceId, null, 0L, null,
                0L, List.of(), -1L, 0L, rankingRevision);
    }

    public static ChallengeStateAction ranking(String authorityInstanceId, long rankingRevision) {
        return frame(Kind.RANKING, authorityInstanceId, null, 0L, null,
                0L, List.of(), -1L, 0L, rankingRevision);
    }

    public static ChallengeStateAction start(ChallengeRun run, long rankingRevision) {
        return forRun(Kind.START, run, run, run.endsAt(), List.of(), -1L, 0L, rankingRevision);
    }

    public static ChallengeStateAction score(ChallengeRun run, List<ChallengeScoreEntry> scores,
                                             long baseRevision, long revision, long rankingRevision) {
        return forRun(Kind.SCORE, run, null, run.endsAt(), scores,
                baseRevision, revision, rankingRevision);
    }

    public static ChallengeStateAction drain(ChallengeRun run, long cutoffAt, long stateRevision,
                                             long rankingRevision) {
        return forRun(Kind.DRAIN, run, null, cutoffAt, List.of(),
                stateRevision, stateRevision, rankingRevision);
    }

    public static ChallengeStateAction end(ChallengeRun run, List<ChallengeScoreEntry> scores,
                                           long stateRevision, long rankingRevision) {
        return forRun(Kind.END, run, run, run.endsAt(), scores,
                -1L, stateRevision, rankingRevision);
    }

    public static ChallengeStateAction stop(ChallengeRun run, long rankingRevision) {
        return forRun(Kind.STOP, run, null, 0L, List.of(),
                -1L, 0L, rankingRevision);
    }

    private static ChallengeStateAction forRun(Kind kind, ChallengeRun descriptor, ChallengeRun includedRun,
                                               long effectiveEndsAt, List<ChallengeScoreEntry> scores,
                                               long baseRevision, long revision, long rankingRevision) {
        if (descriptor == null) throw new IllegalArgumentException("run is required");
        return frame(kind, descriptor.authorityInstanceId(), descriptor.runId(), descriptor.generation(),
                includedRun, effectiveEndsAt, scores, baseRevision, revision, rankingRevision);
    }

    private static ChallengeStateAction frame(Kind kind, String authorityInstanceId,
                                              UUID runId, long generation, ChallengeRun run,
                                              long effectiveEndsAt, List<ChallengeScoreEntry> scores,
                                              long baseRevision, long revision, long rankingRevision) {
        return new ChallengeStateAction(kind, authorityInstanceId, runId, generation, run,
                effectiveEndsAt, scores, baseRevision, revision, rankingRevision);
    }

    @Override
    public void execute() {
        Challenges.get().getChallengesManager().handleStateAction(this);
    }

    public enum Kind {
        COORDINATOR_ONLINE,
        RANKING,
        START,
        SCORE,
        DRAIN,
        END,
        STOP
    }
}
