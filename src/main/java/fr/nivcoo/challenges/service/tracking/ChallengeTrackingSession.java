package fr.nivcoo.challenges.service.tracking;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public interface ChallengeTrackingSession extends AutoCloseable {
    ChallengeTrackingSession NOOP = new ChallengeTrackingSession() {
        @Override
        public CompletionStage<Void> drainAndClose() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
        }
    };

    CompletionStage<Void> drainAndClose();

    @Override
    void close();
}
