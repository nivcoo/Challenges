package fr.nivcoo.challenges.messaging.action;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.ChallengeRun;
import fr.nivcoo.challenges.challenges.ChallengeRunPhase;
import fr.nivcoo.challenges.challenges.ChallengeScoreLedger;
import fr.nivcoo.challenges.messaging.MessageActionName;
import fr.nivcoo.challenges.messaging.model.ChallengeRankingEntry;
import fr.nivcoo.challenges.messaging.model.ChallengeScoreEntry;
import fr.nivcoo.utilsz.core.messaging.BusAction;
import fr.nivcoo.utilsz.core.messaging.BusMessage;

import java.util.List;
import java.util.HashSet;
import java.util.UUID;

@BusAction(value = MessageActionName.CHALLENGE_STATE, runOnMainThread = true)
public record ChallengeStateAction(Kind kind, String authorityInstanceId,
                                   UUID runId, long generation, ChallengeRun run,
                                   ChallengeRunPhase phase,
                                   long effectiveEndsAt, List<ChallengeScoreEntry> scores,
                                   long baseStateRevision, long stateRevision,
                                   long rankingRevision,
                                   List<ChallengeRankingEntry> ranking) implements BusMessage {
    public ChallengeStateAction {
        authorityInstanceId = authorityInstanceId == null ? "" : authorityInstanceId;
        if (scores != null && scores.size() > ChallengeScoreLedger.MAX_SCORE_ENTRIES) {
            throw new IllegalArgumentException("Challenge state contains too many score entries.");
        }
        scores = scores == null ? List.of() : List.copyOf(scores);
        if (ranking != null && (ranking.size() > ChallengeScoreLedger.MAX_SCORE_ENTRIES
                || ranking.stream().anyMatch(entry -> entry == null || entry.playerId() == null || entry.score() < 0)
                || new HashSet<>(ranking.stream().map(ChallengeRankingEntry::playerId).toList()).size()
                != ranking.size())) {
            throw new IllegalArgumentException("Challenge ranking snapshot is invalid.");
        }
        ranking = ranking == null ? List.of() : List.copyOf(ranking);
    }

    public static ChallengeStateAction coordinatorOnline(String authorityInstanceId, long rankingRevision,
                                                         List<ChallengeRankingEntry> ranking) {
        return frame(Kind.COORDINATOR_ONLINE, authorityInstanceId, null, 0L, null,
                ChallengeRunPhase.IDLE, 0L, List.of(), -1L, 0L, rankingRevision, ranking);
    }

    public static ChallengeStateAction ranking(String authorityInstanceId, long rankingRevision,
                                               List<ChallengeRankingEntry> ranking) {
        return frame(Kind.RANKING, authorityInstanceId, null, 0L, null,
                ChallengeRunPhase.IDLE, 0L, List.of(), -1L, 0L, rankingRevision, ranking);
    }

    public static ChallengeStateAction start(ChallengeRun run, long rankingRevision) {
        return forRun(Kind.START, run, run, ChallengeRunPhase.ACTIVE,
                run.endsAt(), List.of(), -1L, 0L, rankingRevision);
    }

    public static ChallengeStateAction score(ChallengeRun run, List<ChallengeScoreEntry> scores,
                                             long baseRevision, long revision, long rankingRevision) {
        return forRun(Kind.SCORE, run, null, ChallengeRunPhase.ACTIVE, run.endsAt(), scores,
                baseRevision, revision, rankingRevision);
    }

    public static ChallengeStateAction drain(ChallengeRun run, long cutoffAt, long stateRevision,
                                             long rankingRevision) {
        return forRun(Kind.DRAIN, run, null, ChallengeRunPhase.DRAINING, cutoffAt, List.of(),
                stateRevision, stateRevision, rankingRevision);
    }

    public static ChallengeStateAction end(ChallengeRun run, List<ChallengeScoreEntry> scores,
                                           long stateRevision, long rankingRevision) {
        return forRun(Kind.END, run, run, ChallengeRunPhase.FINALIZED, run.endsAt(), scores,
                -1L, stateRevision, rankingRevision);
    }

    public static ChallengeStateAction stop(ChallengeRun run, long rankingRevision) {
        return forRun(Kind.STOP, run, null, ChallengeRunPhase.IDLE, 0L, List.of(),
                -1L, 0L, rankingRevision);
    }

    public static ChallengeStateAction snapshot(String authorityInstanceId, long generation,
                                                ChallengeRun run, ChallengeRunPhase phase,
                                                long effectiveEndsAt, List<ChallengeScoreEntry> scores,
                                                long stateRevision, long rankingRevision) {
        return frame(Kind.SNAPSHOT, authorityInstanceId, run == null ? null : run.runId(), generation,
                run, phase, effectiveEndsAt, scores, -1L, stateRevision, rankingRevision, List.of());
    }

    private static ChallengeStateAction forRun(Kind kind, ChallengeRun descriptor, ChallengeRun includedRun,
                                               ChallengeRunPhase phase, long effectiveEndsAt,
                                               List<ChallengeScoreEntry> scores,
                                               long baseRevision, long revision, long rankingRevision) {
        if (descriptor == null) throw new IllegalArgumentException("run is required");
        return frame(kind, descriptor.authorityInstanceId(), descriptor.runId(), descriptor.generation(),
                includedRun, phase, effectiveEndsAt, scores, baseRevision, revision, rankingRevision, List.of());
    }

    private static ChallengeStateAction frame(Kind kind, String authorityInstanceId,
                                              UUID runId, long generation, ChallengeRun run,
                                              ChallengeRunPhase phase, long effectiveEndsAt,
                                              List<ChallengeScoreEntry> scores, long baseRevision,
                                              long revision, long rankingRevision,
                                              List<ChallengeRankingEntry> ranking) {
        return new ChallengeStateAction(kind, authorityInstanceId, runId, generation, run,
                phase, effectiveEndsAt, scores, baseRevision, revision, rankingRevision, ranking);
    }

    @Override
    public void execute() {
        Challenges.get().getChallengesManager().handleStateAction(this);
    }

    public enum Kind {
        COORDINATOR_ONLINE,
        RANKING,
        SNAPSHOT,
        START,
        SCORE,
        DRAIN,
        END,
        STOP
    }
}
