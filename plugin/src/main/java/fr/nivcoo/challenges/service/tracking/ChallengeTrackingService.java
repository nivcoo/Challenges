package fr.nivcoo.challenges.service.tracking;

import fr.nivcoo.challenges.challenges.Challenge;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public interface ChallengeTrackingService extends AutoCloseable {
    boolean available();

    void registerCatalog(Collection<Challenge> challenges);

    ChallengeTrackingSession activate(String definitionId, UUID runId, Instant startsAt, Instant endsAt,
                                      ChallengeTrackingHandler handler);

    @Override
    void close();

    static ChallengeTrackingService unavailable() {
        return UnavailableChallengeTrackingService.INSTANCE;
    }
}
