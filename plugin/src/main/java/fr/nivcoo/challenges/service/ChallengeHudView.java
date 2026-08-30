package fr.nivcoo.challenges.service;

import net.kyori.adventure.text.Component;

import java.util.Objects;
import java.util.UUID;

public record ChallengeHudView(
        UUID runUuid,
        Phase phase,
        String challengeId,
        String challengeName,
        Component objective,
        String score,
        int place,
        long remainingSeconds,
        long totalSeconds
) {
    public ChallengeHudView {
        Objects.requireNonNull(runUuid, "runUuid");
        Objects.requireNonNull(phase, "phase");
        challengeId = challengeId == null ? "" : challengeId;
        challengeName = challengeName == null ? "" : challengeName;
        objective = objective == null ? Component.empty() : objective;
        score = score == null ? "0" : score;
        if (place < 0) throw new IllegalArgumentException("place must not be negative.");
        remainingSeconds = Math.max(0L, remainingSeconds);
        totalSeconds = Math.max(1L, totalSeconds);
    }

    public enum Phase {
        COUNTDOWN,
        ACTIVE,
        DRAINING
    }
}
