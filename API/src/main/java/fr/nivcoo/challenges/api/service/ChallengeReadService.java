package fr.nivcoo.challenges.api.service;

import fr.nivcoo.challenges.api.model.ChallengeLeaderboardPage;
import fr.nivcoo.challenges.api.model.ChallengeStatePage;

import java.util.concurrent.CompletionStage;

public interface ChallengeReadService {

    CompletionStage<ChallengeStatePage> activePage(int offset, int limit, long expectedStateRevision);

    CompletionStage<ChallengeLeaderboardPage> lifetimePage(int offset, int limit,
                                                            long expectedRankingRevision);

    void addInvalidationListener(Runnable listener);

    void removeInvalidationListener(Runnable listener);
}
