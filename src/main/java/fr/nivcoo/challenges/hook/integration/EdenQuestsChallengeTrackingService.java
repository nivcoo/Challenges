package fr.nivcoo.challenges.hook.integration;

import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.service.tracking.ChallengeTrackingHandler;
import fr.nivcoo.challenges.service.tracking.ChallengeTrackingDecision;
import fr.nivcoo.challenges.service.tracking.ChallengeTrackingDirection;
import fr.nivcoo.challenges.service.tracking.ChallengeTrackingService;
import fr.nivcoo.challenges.service.tracking.ChallengeTrackingSession;
import fr.nivcoo.edenquests.api.AEdenQuests;
import fr.nivcoo.edenquests.api.tracking.TrackingCatalogRegistration;
import fr.nivcoo.edenquests.api.tracking.TrackingDefinition;
import fr.nivcoo.edenquests.api.tracking.TrackingObjective;
import fr.nivcoo.edenquests.api.tracking.TrackingScope;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class EdenQuestsChallengeTrackingService implements ChallengeTrackingService {
    private final TrackingScope scope;
    private TrackingCatalogRegistration catalog;

    EdenQuestsChallengeTrackingService(Plugin owner, AEdenQuests edenQuests) {
        this.scope = edenQuests.tracking().forPlugin(owner);
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void registerCatalog(Collection<Challenge> challenges) {
        if (catalog != null) throw new IllegalStateException("Challenge catalogue is already registered.");
        Collection<TrackingDefinition> definitions = challenges.stream()
                .map(challenge -> new TrackingDefinition(
                        challenge.id(),
                        java.util.List.of(new TrackingObjective(
                                challenge.objective().id(), challenge.objective().type(),
                                challenge.objective().parameters()))))
                .toList();
        catalog = scope.registerCatalog(definitions);
    }

    @Override
    public ChallengeTrackingSession activate(String definitionId, UUID runId, Instant startsAt, Instant endsAt,
                                             ChallengeTrackingHandler handler) {
        if (catalog == null) throw new IllegalStateException("Challenge catalogue is not registered.");
        String edenDigest = catalog.digest(definitionId);
        fr.nivcoo.edenquests.api.tracking.TrackingSession session = catalog.activate(
                definitionId,
                runId,
                edenDigest,
                startsAt,
                endsAt,
                progress -> {
                    final java.math.BigDecimal signedDelta;
                    try {
                        signedDelta = progress.signedDelta();
                        fr.nivcoo.challenges.challenges.ChallengeAmount.parseDelta(
                                fr.nivcoo.challenges.challenges.ChallengeAmount.canonical(signedDelta));
                    } catch (IllegalArgumentException exception) {
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("Invalid tracking delta.", exception));
                    }
                    return handler.handle(new ChallengeTrackingDecision(
                            progress.observationId(),
                            progress.runId(),
                            progress.definitionId(),
                            progress.objectiveId(),
                            progress.playerId(),
                            ChallengeTrackingDirection.valueOf(progress.decision().name()),
                            signedDelta,
                            progress.observedAt(),
                            progress.observation().world()
                    ));
                }
        );
        return new ChallengeTrackingSession() {
            @Override
            public java.util.concurrent.CompletionStage<Void> drainAndClose() {
                return session.drainAndClose();
            }

            @Override
            public void close() {
                session.close();
            }
        };
    }

    @Override
    public void close() {
        if (catalog != null) {
            catalog.close();
            catalog = null;
        }
        scope.close();
    }
}
