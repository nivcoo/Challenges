package fr.nivcoo.challenges.api.model;

import java.util.List;

public record ChallengeLeaderboardPage(
        long revision,
        int offset,
        int nextOffset,
        int total,
        boolean hasMore,
        boolean resyncRequired,
        List<ChallengeScoreSnapshot> scores
) {
    public ChallengeLeaderboardPage {
        scores = scores == null ? List.of() : List.copyOf(scores);
    }
}
