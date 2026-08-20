package fr.nivcoo.challenges.catalog;

import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.ChallengeObjective;
import fr.nivcoo.challenges.challenges.TopReward;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.List;
import java.util.Map;

final class ChallengeDigest {
    private ChallengeDigest() {
    }

    static String of(Challenge challenge, Collection<String> blacklistedWorlds) {
        StringBuilder canonical = new StringBuilder();
        appendString(canonical, challenge.id());
        ChallengeObjective objective = challenge.objective();
        canonical.append('{');
        appendString(canonical, objective.id());
        appendString(canonical, objective.type());
        appendValue(canonical, objective.parameters());
        canonical.append('}');
        appendString(canonical, challenge.message());
        canonical.append('[');
        List<TopReward> rewards = new ArrayList<>(challenge.topRewards());
        rewards.sort(Comparator.comparingInt(TopReward::place));
        for (TopReward reward : rewards) {
            canonical.append('{').append(reward.place()).append(';');
            appendString(canonical, reward.message());
            appendValue(canonical, reward.commands());
            canonical.append('}');
        }
        canonical.append(']');
        appendString(canonical, challenge.forAllMessage());
        appendValue(canonical, challenge.forAllCommands());
        canonical.append(challenge.giveForAllRewardToTop() ? "give-all;" : "exclude-top;");
        canonical.append(challenge.addAllTopIntoDb() ? "rank-all;" : "rank-first;");
        List<String> normalizedWorlds = blacklistedWorlds == null ? new ArrayList<>()
                : blacklistedWorlds.stream()
                .filter(java.util.Objects::nonNull)
                .map(world -> world.trim().toLowerCase(java.util.Locale.ROOT))
                .filter(world -> !world.isBlank())
                .distinct()
                .sorted()
                .toList();
        appendValue(canonical, normalizedWorlds);

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void appendValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null;");
            return;
        }
        if (value instanceof Map<?, ?> map) {
            out.append('{');
            List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
            entries.sort(Comparator.comparing(entry -> String.valueOf(entry.getKey())));
            for (Map.Entry<?, ?> entry : entries) {
                appendString(out, String.valueOf(entry.getKey()));
                appendValue(out, entry.getValue());
            }
            out.append('}');
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            out.append('[');
            for (Object element : iterable) {
                appendValue(out, element);
            }
            out.append(']');
            return;
        }
        if (value.getClass().isArray()) {
            out.append('[');
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                appendValue(out, java.lang.reflect.Array.get(value, i));
            }
            out.append(']');
            return;
        }
        if (value instanceof Number number) {
            try {
                out.append('n').append(new BigDecimal(number.toString()).stripTrailingZeros().toPlainString()).append(';');
            } catch (NumberFormatException ignored) {
                appendString(out, number.toString());
            }
            return;
        }
        if (value instanceof Boolean bool) {
            out.append(bool ? "true;" : "false;");
            return;
        }
        appendString(out, String.valueOf(value));
    }

    private static void appendString(StringBuilder out, String value) {
        String safe = value == null ? "" : value;
        out.append('s').append(safe.length()).append(':').append(safe).append(';');
    }
}
