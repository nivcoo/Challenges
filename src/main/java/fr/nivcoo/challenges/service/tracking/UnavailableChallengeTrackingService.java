package fr.nivcoo.challenges.service.tracking;

import fr.nivcoo.challenges.challenges.Challenge;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

final class UnavailableChallengeTrackingService implements ChallengeTrackingService {
    static final UnavailableChallengeTrackingService INSTANCE = new UnavailableChallengeTrackingService();

    private UnavailableChallengeTrackingService() {
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public void registerCatalog(Collection<Challenge> challenges) {
        throw new IllegalStateException("EdenQuests tracking is unavailable.");
    }

    @Override
    public ChallengeTrackingSession activate(String definitionId, UUID runId, Instant startsAt, Instant endsAt,
                                             ChallengeTrackingHandler handler) {
        throw new IllegalStateException("EdenQuests tracking is unavailable.");
    }

    @Override
    public void close() {
    }
}
