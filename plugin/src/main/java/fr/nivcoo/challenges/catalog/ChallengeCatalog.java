package fr.nivcoo.challenges.catalog;

import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.ChallengeObjective;
import fr.nivcoo.challenges.challenges.TopReward;
import fr.nivcoo.challenges.config.ChallengeCatalogFile;
import fr.nivcoo.challenges.config.MainConfig;
import fr.nivcoo.utilsz.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

public final class ChallengeCatalog {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final Map<String, Challenge> challenges;
    private final Map<String, String> digests;

    private ChallengeCatalog(Map<String, Challenge> challenges, Map<String, String> digests) {
        this.challenges = Collections.unmodifiableMap(new LinkedHashMap<>(challenges));
        this.digests = Collections.unmodifiableMap(new LinkedHashMap<>(digests));
    }

    public static ChallengeCatalog load(ConfigManager manager, MainConfig config) {
        ChallengeCatalogFile file = manager.load("challenges.yml", ChallengeCatalogFile.class);
        Map<String, Challenge> challenges = new LinkedHashMap<>();
        Map<String, String> digests = new LinkedHashMap<>();

        List<TopReward> topRewards = new ArrayList<>();
        Set<Integer> rewardPlaces = new HashSet<>();
        for (Map.Entry<String, MainConfig.RewardGroup> entry : config.rewards.top.entrySet()) {
            int place;
            try {
                place = Integer.parseInt(entry.getKey());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Reward place must be an integer: " + entry.getKey(), exception);
            }
            if (place <= 0 || !rewardPlaces.add(place)) {
                throw new IllegalArgumentException("Reward places must be unique positive integers: " + entry.getKey());
            }
            MainConfig.RewardGroup reward = entry.getValue();
            if (reward == null || reward.commands == null) {
                throw new IllegalArgumentException("Reward place " + place + " must define a reward group.");
            }
            topRewards.add(new TopReward(place, legacy(reward.message), List.copyOf(reward.commands)));
        }
        for (int place = 1; place <= rewardPlaces.size(); place++) {
            if (!rewardPlaces.contains(place)) {
                throw new IllegalArgumentException("Reward places must be contiguous from 1 to "
                        + rewardPlaces.size() + ".");
            }
        }
        topRewards.sort(Comparator.comparingInt(TopReward::place));

        for (ChallengeCatalogFile.Entry entry : file.challenges) {
            if (!entry.enabled) continue;
            String id = normalizeId(entry.id);
            if (challenges.containsKey(id)) {
                throw new IllegalArgumentException("Duplicate challenge id: " + id);
            }

            ChallengeObjective objective = new ChallengeObjective(
                    "objective", entry.objective.type, entry.objective.parameters);
            if (objective.type().isBlank() || objective.type().length() > 128
                    || objective.parameters().toString().length() > 32_768) {
                throw new IllegalArgumentException("Challenge '" + id + "' has an oversized objective.");
            }

            String message = legacy(entry.message);
            if (message.isBlank() || message.length() > 4_096) {
                throw new IllegalArgumentException("Challenge '" + id + "' has an invalid message.");
            }
            validateCommands("challenge '" + id + "' participation", config.rewards.forAll.commands);
            for (TopReward reward : topRewards) {
                validateCommands("challenge '" + id + "' top " + reward.place(), reward.commands());
            }

            Challenge challenge = new Challenge(
                    id,
                    objective,
                    message,
                    PLAIN.serialize(entry.message == null ? Component.empty() : entry.message),
                    List.copyOf(topRewards),
                    legacy(config.rewards.forAll.message),
                    List.copyOf(config.rewards.forAll.commands),
                    config.rewards.giveForAllRewardToTop,
                    config.rewards.addAllTopIntoDb
            );
            challenges.put(id, challenge);
            digests.put(id, ChallengeDigest.of(challenge, config.blacklistedWorld));
        }

        if (challenges.isEmpty()) {
            throw new IllegalArgumentException("No enabled challenges found in plugins/Challenges/challenges.yml.");
        }
        return new ChallengeCatalog(challenges, digests);
    }

    public Collection<Challenge> all() {
        return challenges.values();
    }

    public Optional<Challenge> find(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(challenges.get(normalizeId(id)));
    }

    public String digest(String id) {
        return digests.get(normalizeId(id));
    }

    public boolean matches(String id, String expectedDigest) {
        String digest = digest(id);
        return digest != null && digest.equalsIgnoreCase(expectedDigest == null ? "" : expectedDigest);
    }

    private static String normalizeId(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 128 || !normalized.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException("Invalid challenge id: " + id);
        }
        return normalized;
    }

    private static void validateCommands(String source, List<String> commands) {
        if (commands == null) throw new IllegalArgumentException(source + " commands must not be null.");
        for (String command : commands) {
            if (command == null || command.isBlank() || command.length() > 4_096) {
                throw new IllegalArgumentException(source + " contains an invalid command.");
            }
        }
    }

    private static String legacy(Component component) {
        return LEGACY.serialize(component == null ? Component.empty() : component);
    }
}
