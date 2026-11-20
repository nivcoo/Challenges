package fr.nivcoo.challenges.challenges;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ChallengeRanking {

    record Entry(UUID playerUuid, BigDecimal score, int place, int row) {
    }

    static List<Entry> entries(Map<UUID, BigDecimal> scores) {
        List<Map.Entry<UUID, BigDecimal>> sorted = new ArrayList<>(scores.entrySet());
        List<Entry> result = new ArrayList<>(sorted.size());
        int scoreStart = 0;
        BigDecimal previousScore = null;
        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<UUID, BigDecimal> entry = sorted.get(i);
            if (previousScore == null || previousScore.compareTo(entry.getValue()) != 0) scoreStart = i;
            int place = scoreStart + ((i - scoreStart) / 2) * 2 + 1;
            result.add(new Entry(entry.getKey(), entry.getValue(), place, i + 1));
            previousScore = entry.getValue();
        }
        return List.copyOf(result);
    }

    static List<Entry> top(List<Entry> entries, int limit) {
        if (limit <= 0) return List.of();
        return entries.subList(0, Math.min(limit, entries.size()));
    }
}
