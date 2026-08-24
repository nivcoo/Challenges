package fr.nivcoo.challenges.service;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.api.model.ChallengeLeaderboardPage;
import fr.nivcoo.challenges.api.model.ChallengePhase;
import fr.nivcoo.challenges.api.model.ChallengeRunSnapshot;
import fr.nivcoo.challenges.api.model.ChallengeScoreSnapshot;
import fr.nivcoo.challenges.api.model.ChallengeStatePage;
import fr.nivcoo.challenges.api.service.ChallengeReadService;
import fr.nivcoo.challenges.challenges.ChallengeRun;
import fr.nivcoo.challenges.challenges.ChallengesManager;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class CachedChallengeReadService implements ChallengeReadService {

    private static final int MAX_PAGE_SIZE = 250;

    private final Challenges plugin;
    private final ChallengeReadInvalidationPublisher invalidations;

    public CachedChallengeReadService(Challenges plugin) {
        this.plugin = plugin;
        this.invalidations = new ChallengeReadInvalidationPublisher(
                task -> Bukkit.getScheduler().runTask(plugin, task)
        );
    }

    @Override
    public CompletionStage<ChallengeStatePage> activePage(int offset, int limit,
                                                          long expectedStateRevision) {
        int safeOffset = Math.max(0, offset);
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, limit));
        return onMain(() -> {
            ChallengesManager.ActiveReadPage page = plugin.getChallengesManager()
                    .activeReadPage(safeOffset, pageSize, expectedStateRevision);
            ChallengeRunSnapshot run = convertRun(page);
            List<ChallengeScoreSnapshot> scores = convertScores(page.scores(), safeOffset);
            int responseOffset = page.resyncRequired() ? 0 : safeOffset;
            int nextOffset = responseOffset + scores.size();
            return new ChallengeStatePage(
                    run,
                    page.stateRevision(),
                    page.rankingRevision(),
                    responseOffset,
                    nextOffset,
                    page.total(),
                    !page.resyncRequired() && nextOffset < page.total(),
                    page.resyncRequired(),
                    scores
            );
        });
    }

    @Override
    public CompletionStage<ChallengeLeaderboardPage> lifetimePage(int offset, int limit,
                                                                  long expectedRankingRevision) {
        int safeOffset = Math.max(0, offset);
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, limit));
        return onMain(() -> {
            ChallengesManager.LifetimeReadPage page = plugin.getChallengesManager()
                    .lifetimeReadPage(safeOffset, pageSize, expectedRankingRevision);
            List<ChallengeScoreSnapshot> scores = convertScores(page.scores(), safeOffset);
            int responseOffset = page.resyncRequired() ? 0 : safeOffset;
            int nextOffset = responseOffset + scores.size();
            return new ChallengeLeaderboardPage(
                    page.rankingRevision(),
                    responseOffset,
                    nextOffset,
                    page.total(),
                    !page.resyncRequired() && nextOffset < page.total(),
                    page.resyncRequired(),
                    scores
            );
        });
    }

    @Override
    public void addInvalidationListener(Runnable listener) {
        invalidations.addListener(listener);
    }

    @Override
    public void removeInvalidationListener(Runnable listener) {
        invalidations.removeListener(listener);
    }

    public void invalidate() {
        try {
            invalidations.invalidate();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Unable to schedule a challenge read-model invalidation: "
                    + exception.getMessage());
        }
    }

    public void close() {
        invalidations.close();
    }

    private <T> CompletionStage<T> onMain(Supplier<T> supplier) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(supplier.get());
            } catch (Throwable error) {
                return CompletableFuture.failedFuture(error);
            }
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                result.complete(supplier.get());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        });
        return result;
    }

    private static ChallengeRunSnapshot convertRun(ChallengesManager.ActiveReadPage page) {
        ChallengeRun run = page.run();
        if (run == null) return null;
        return new ChallengeRunSnapshot(
                run.runId(),
                run.generation(),
                run.challengeId(),
                page.displayName(),
                run.startsAt(),
                run.endsAt(),
                page.effectiveEndsAt(),
                ChallengePhase.valueOf(page.phase().name()),
                page.message()
        );
    }

    private static List<ChallengeScoreSnapshot> convertScores(
            List<ChallengesManager.ReadScore> source, int offset) {
        List<ChallengeScoreSnapshot> scores = new ArrayList<>(source.size());
        for (int i = 0; i < source.size(); i++) {
            ChallengesManager.ReadScore score = source.get(i);
            scores.add(new ChallengeScoreSnapshot(
                    offset + i + 1, score.playerUuid(), score.score()));
        }
        return scores;
    }
}
