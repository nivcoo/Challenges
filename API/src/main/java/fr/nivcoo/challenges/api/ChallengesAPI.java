package fr.nivcoo.challenges.api;

import java.util.Optional;

public final class ChallengesAPI {

    private static volatile AChallenges instance;

    private ChallengesAPI() {
    }

    public static AChallenges get() {
        return instance;
    }

    public static Optional<AChallenges> find() {
        return Optional.ofNullable(instance);
    }

    public static synchronized void register(AChallenges api) {
        if (api == null) throw new IllegalArgumentException("api");
        if (instance != null && instance != api) {
            throw new IllegalStateException("Challenges API is already registered.");
        }
        instance = api;
    }

    public static synchronized void unregister(AChallenges api) {
        if (instance == api) instance = null;
    }
}
