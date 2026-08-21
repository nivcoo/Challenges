package fr.nivcoo.challenges.api.model;

import java.util.List;

public record ChallengeStatePage(
        ChallengeRunSnapshot run,
        long stateRevision,
        long rankingRevision,
        int offset,
        int nextOffset,
        int total,
        boolean hasMore,
        boolean resyncRequired,
        List<ChallengeScoreSnapshot> scores
) {
    public ChallengeStatePage {
        scores = scores == null ? List.of() : List.copyOf(scores);
    }
}
