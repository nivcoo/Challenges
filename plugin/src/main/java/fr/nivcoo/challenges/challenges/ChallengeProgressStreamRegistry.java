package fr.nivcoo.challenges.challenges;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

final class ChallengeProgressStreamRegistry {
    static final int MAX_STREAMS = 1_024;

    private final Map<UUID, StreamState> streams = new HashMap<>();

    Preparation prepare(UUID streamId, String participantId, long revision,
                        Map<UUID, BigDecimal> contributions) {
        Objects.requireNonNull(streamId, "streamId");
        if (participantId == null || participantId.isBlank() || participantId.length() > 128) {
            throw new IllegalArgumentException("Participant id is invalid.");
        }
        if (revision <= 0L) throw new IllegalArgumentException("Revision must be positive.");

        Map<UUID, BigDecimal> normalized = normalize(contributions);
        StreamState current = streams.get(streamId);
        if (current == null) {
            if (streams.size() >= MAX_STREAMS) {
                return new Preparation(this, Status.CAPACITY, 0L, List.of(), streamId,
                        participantId, revision, normalized, -1L);
            }
            return new Preparation(this, Status.NEW, 0L, deltas(Map.of(), normalized), streamId,
                    participantId, revision, normalized, -1L);
        }
        if (!current.participantId().equals(participantId)) {
            return new Preparation(this, Status.CONFLICT, current.revision(), List.of(), streamId,
                    participantId, revision, normalized, current.revision());
        }
        if (revision < current.revision()) {
            return new Preparation(this, Status.STALE, current.revision(), List.of(), streamId,
                    participantId, revision, normalized, current.revision());
        }
        if (revision == current.revision()) {
            Status status = current.contributions().equals(normalized) ? Status.DUPLICATE : Status.CONFLICT;
            return new Preparation(this, status, current.revision(), List.of(), streamId,
                    participantId, revision, normalized, current.revision());
        }
        return new Preparation(this, Status.NEW, current.revision(),
                deltas(current.contributions(), normalized), streamId,
                participantId, revision, normalized, current.revision());
    }

    long commit(Preparation preparation) {
        if (preparation == null || preparation.registry != this || preparation.status != Status.NEW) {
            throw new IllegalArgumentException("Preparation cannot be committed.");
        }
        StreamState current = streams.get(preparation.streamId);
        if (preparation.expectedRevision < 0L) {
            if (current != null) throw new IllegalStateException("Preparation is no longer current.");
        } else if (current == null
                || current.revision() != preparation.expectedRevision
                || !current.participantId().equals(preparation.participantId)) {
            throw new IllegalStateException("Preparation is no longer current.");
        }
        streams.put(preparation.streamId, new StreamState(
                preparation.participantId, preparation.revision, preparation.contributions));
        return preparation.revision;
    }

    long acknowledgedRevision(UUID streamId, String participantId) {
        if (streamId == null || participantId == null) return 0L;
        StreamState state = streams.get(streamId);
        return state != null && state.participantId().equals(participantId) ? state.revision() : 0L;
    }

    int size() {
        return streams.size();
    }

    void clear() {
        streams.clear();
    }

    private static Map<UUID, BigDecimal> normalize(Map<UUID, BigDecimal> contributions) {
        if (contributions == null) throw new IllegalArgumentException("Contributions are required.");
        if (contributions.size() > ChallengeScoreLedger.MAX_SCORE_ENTRIES) {
            throw new IllegalArgumentException("Too many contributions.");
        }
        Map<UUID, BigDecimal> normalized = new TreeMap<>();
        for (Map.Entry<UUID, BigDecimal> entry : contributions.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new IllegalArgumentException("Contribution is invalid.");
            }
            BigDecimal value = ChallengeAmount.parseBalance(ChallengeAmount.canonical(entry.getValue()));
            if (value.signum() != 0) normalized.put(entry.getKey(), value);
        }
        return Map.copyOf(normalized);
    }

    private static List<ChallengeScoreLedger.Delta> deltas(Map<UUID, BigDecimal> previous,
                                                            Map<UUID, BigDecimal> next) {
        Set<UUID> players = new TreeSet<>();
        players.addAll(previous.keySet());
        players.addAll(next.keySet());
        List<ChallengeScoreLedger.Delta> deltas = new ArrayList<>();
        for (UUID playerId : players) {
            BigDecimal delta = next.getOrDefault(playerId, BigDecimal.ZERO)
                    .subtract(previous.getOrDefault(playerId, BigDecimal.ZERO))
                    .stripTrailingZeros();
            if (delta.signum() != 0) deltas.add(new ChallengeScoreLedger.Delta(playerId, delta));
        }
        return List.copyOf(deltas);
    }

    enum Status {
        NEW,
        DUPLICATE,
        STALE,
        CONFLICT,
        CAPACITY
    }

    static final class Preparation {
        private final ChallengeProgressStreamRegistry registry;
        private final Status status;
        private final long acknowledgedRevision;
        private final List<ChallengeScoreLedger.Delta> deltas;
        private final UUID streamId;
        private final String participantId;
        private final long revision;
        private final Map<UUID, BigDecimal> contributions;
        private final long expectedRevision;

        private Preparation(ChallengeProgressStreamRegistry registry, Status status,
                            long acknowledgedRevision, List<ChallengeScoreLedger.Delta> deltas,
                            UUID streamId, String participantId, long revision,
                            Map<UUID, BigDecimal> contributions, long expectedRevision) {
            this.registry = registry;
            this.status = status;
            this.acknowledgedRevision = acknowledgedRevision;
            this.deltas = List.copyOf(deltas);
            this.streamId = streamId;
            this.participantId = participantId;
            this.revision = revision;
            this.contributions = Map.copyOf(contributions);
            this.expectedRevision = expectedRevision;
        }

        Status status() {
            return status;
        }

        long acknowledgedRevision() {
            return acknowledgedRevision;
        }

        List<ChallengeScoreLedger.Delta> deltas() {
            return deltas;
        }
    }

    private record StreamState(String participantId, long revision,
                               Map<UUID, BigDecimal> contributions) {
        private StreamState {
            contributions = Map.copyOf(contributions);
        }
    }
}
