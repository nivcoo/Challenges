package fr.nivcoo.challenges.service;

import java.util.UUID;

public interface ChallengeHudBridge extends AutoCloseable {
    ChallengeHudBridge UNAVAILABLE = new ChallengeHudBridge() {
        @Override
        public boolean active(UUID playerUuid) {
            return false;
        }

        @Override
        public void invalidate(UUID playerUuid) {
        }

        @Override
        public void clear(UUID playerUuid) {
        }

        @Override
        public void rankingChanged(
                UUID playerUuid,
                UUID runUuid,
                int previousPlace,
                int currentPlace
        ) {
        }
    };

    boolean active(UUID playerUuid);

    void invalidate(UUID playerUuid);

    void clear(UUID playerUuid);

    void rankingChanged(UUID playerUuid, UUID runUuid, int previousPlace, int currentPlace);

    @Override
    default void close() {
    }

    static ChallengeHudBridge unavailable() {
        return UNAVAILABLE;
    }
}
