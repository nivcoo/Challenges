package fr.nivcoo.challenges.challenges;

import java.util.List;

public record Challenge(String id, ChallengeObjective objective, String message, String displayName,
                        List<TopReward> topRewards, String forAllMessage, List<String> forAllCommands,
                        boolean giveForAllRewardToTop, boolean addAllTopIntoDb) {
    public Challenge {
        id = id == null ? "" : id;
        if (objective == null) throw new IllegalArgumentException("objective must not be null.");
        message = message == null ? "" : message;
        displayName = displayName == null ? "" : displayName;
        topRewards = topRewards == null ? List.of() : List.copyOf(topRewards);
        forAllMessage = forAllMessage == null ? "" : forAllMessage;
        forAllCommands = forAllCommands == null ? List.of() : List.copyOf(forAllCommands);
    }
}
