package fr.nivcoo.challenges.service.tracking;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface ChallengeTrackingHandler {
    CompletionStage<Void> handle(ChallengeTrackingDecision decision);
}
