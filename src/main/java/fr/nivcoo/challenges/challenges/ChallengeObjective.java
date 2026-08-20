package fr.nivcoo.challenges.challenges;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ChallengeObjective(String id, String type, Map<String, Object> parameters) {
    public ChallengeObjective {
        id = id == null ? "" : id.trim();
        type = type == null ? "" : type.trim().toUpperCase(java.util.Locale.ROOT);
        parameters = parameters == null ? Map.of() : freezeMap(parameters);
    }

    private static Map<String, Object> freezeMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), freeze(value)));
        return Map.copyOf(copy);
    }

    private static Object freeze(Object value) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Enum<?>) {
            return value;
        }
        if (value instanceof Map<?, ?> map) return freezeMap(map);
        if (value instanceof Iterable<?> iterable) {
            List<Object> copy = new ArrayList<>();
            iterable.forEach(element -> copy.add(freeze(element)));
            return List.copyOf(copy);
        }
        if (value.getClass().isArray()) {
            List<Object> copy = new ArrayList<>();
            for (int i = 0; i < Array.getLength(value); i++) {
                copy.add(freeze(Array.get(value, i)));
            }
            return List.copyOf(copy);
        }
        return String.valueOf(value);
    }
}
