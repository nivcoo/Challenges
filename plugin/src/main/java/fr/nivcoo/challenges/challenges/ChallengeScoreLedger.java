package fr.nivcoo.challenges.challenges;

import fr.nivcoo.challenges.messaging.model.ChallengeScoreEntry;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ChallengeScoreLedger {
    public static final int MAX_SCORE_ENTRIES = 32_768;

    private final Map<UUID, BigDecimal> rawBalances = new LinkedHashMap<>();
    private final Map<UUID, Long> playerRevisions = new HashMap<>();
    private final int capacity;
    private long stateRevision;

    public ChallengeScoreLedger() {
        this(MAX_SCORE_ENTRIES);
    }

    ChallengeScoreLedger(int capacity) {
        if (capacity <= 0 || capacity > MAX_SCORE_ENTRIES) {
            throw new IllegalArgumentException("Invalid challenge score capacity.");
        }
        this.capacity = capacity;
    }

    public Result apply(UUID playerId, BigDecimal signedDelta) {
        return applyBatch(List.of(new Delta(playerId, signedDelta))).get(0);
    }

    public List<Result> applyBatch(List<Delta> deltas) {
        if (deltas == null) throw new IllegalArgumentException("deltas must not be null.");
        if (deltas.isEmpty()) return List.of();

        Map<UUID, BigDecimal> stagedBalances = new LinkedHashMap<>();
        Map<UUID, Long> stagedPlayerRevisions = new HashMap<>();
        Set<UUID> newPlayers = new HashSet<>();
        List<Result> results = new ArrayList<>(deltas.size());
        long nextStateRevision = stateRevision;

        for (Delta delta : deltas) {
            if (delta == null || delta.playerId() == null) {
                throw new IllegalArgumentException("Challenge score delta has no player.");
            }
            BigDecimal signedDelta = ChallengeAmount.parseDelta(ChallengeAmount.canonical(delta.signedDelta()));
            if (!rawBalances.containsKey(delta.playerId()) && newPlayers.add(delta.playerId())
                    && rawBalances.size() + newPlayers.size() > capacity) {
                throw new CapacityExceededException();
            }

            BigDecimal previousBalance = stagedBalances.containsKey(delta.playerId())
                    ? stagedBalances.get(delta.playerId())
                    : rawBalances.getOrDefault(delta.playerId(), BigDecimal.ZERO);
            BigDecimal rawBalance = previousBalance
                    .add(signedDelta).stripTrailingZeros();
            ChallengeAmount.parseBalance(ChallengeAmount.canonical(rawBalance));
            long previousPlayerRevision = stagedPlayerRevisions.containsKey(delta.playerId())
                    ? stagedPlayerRevisions.get(delta.playerId())
                    : playerRevisions.getOrDefault(delta.playerId(), 0L);
            long playerRevision = Math.addExact(previousPlayerRevision, 1L);
            nextStateRevision = Math.addExact(nextStateRevision, 1L);
            stagedBalances.put(delta.playerId(), rawBalance);
            stagedPlayerRevisions.put(delta.playerId(), playerRevision);
            results.add(new Result(delta.playerId(), rawBalance, ChallengeAmount.visible(rawBalance),
                    playerRevision, nextStateRevision));
        }

        rawBalances.putAll(stagedBalances);
        playerRevisions.putAll(stagedPlayerRevisions);
        stateRevision = nextStateRevision;
        return List.copyOf(results);
    }

    public boolean applyRemote(ChallengeScoreEntry entry, long incomingStateRevision) {
        if (entry == null || incomingStateRevision < 0L) return false;
        if (!canAccept(entry.playerId())) return false;
        final BigDecimal rawBalance;
        try {
            rawBalance = entry.rawAmount();
        } catch (IllegalArgumentException exception) {
            return false;
        }
        long currentRevision = playerRevisions.getOrDefault(entry.playerId(), 0L);
        if (entry.playerRevision() <= currentRevision) return false;
        rawBalances.put(entry.playerId(), rawBalance);
        playerRevisions.put(entry.playerId(), entry.playerRevision());
        stateRevision = Math.max(stateRevision, incomingStateRevision);
        return true;
    }

    public boolean restore(List<ChallengeScoreEntry> entries, long revision) {
        if (revision < 0L || entries != null && entries.size() > capacity) return false;
        Map<UUID, BigDecimal> restoredRawBalances = new LinkedHashMap<>();
        Map<UUID, Long> restoredRevisions = new HashMap<>();
        if (entries != null) {
            for (ChallengeScoreEntry entry : entries) {
                if (entry == null || entry.playerRevision() < 0L) return false;
                final BigDecimal balance;
                try {
                    balance = entry.rawAmount();
                } catch (IllegalArgumentException exception) {
                    return false;
                }
                if (restoredRawBalances.putIfAbsent(entry.playerId(), balance) != null) return false;
                restoredRevisions.put(entry.playerId(), entry.playerRevision());
            }
        }

        rawBalances.clear();
        playerRevisions.clear();
        rawBalances.putAll(restoredRawBalances);
        playerRevisions.putAll(restoredRevisions);
        stateRevision = revision;
        return true;
    }

    public void clear() {
        rawBalances.clear();
        playerRevisions.clear();
        stateRevision = 0L;
    }

    public BigDecimal score(UUID playerId) {
        return ChallengeAmount.visible(rawBalances.getOrDefault(playerId, BigDecimal.ZERO));
    }

    public BigDecimal rawBalance(UUID playerId) {
        return rawBalances.getOrDefault(playerId, BigDecimal.ZERO);
    }

    public long playerRevision(UUID playerId) {
        return playerRevisions.getOrDefault(playerId, 0L);
    }

    public boolean canAccept(UUID playerId) {
        return playerId != null && (rawBalances.containsKey(playerId) || rawBalances.size() < capacity);
    }

    public int size() {
        return rawBalances.size();
    }

    public Map<UUID, BigDecimal> scores() {
        Map<UUID, BigDecimal> visible = new LinkedHashMap<>();
        rawBalances.forEach((playerId, rawBalance) -> visible.put(playerId, ChallengeAmount.visible(rawBalance)));
        return Map.copyOf(visible);
    }

    public LinkedHashMap<UUID, BigDecimal> sortedScores() {
        return scores().entrySet().stream()
                .filter(entry -> entry.getValue().signum() > 0)
                .sorted(Map.Entry.<UUID, BigDecimal>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(entry -> entry.getKey().toString()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (left, right) -> left, LinkedHashMap::new));
    }

    public Map<UUID, BigDecimal> rawBalances() {
        return Map.copyOf(rawBalances);
    }

    public List<ChallengeScoreEntry> entries() {
        List<ChallengeScoreEntry> entries = new ArrayList<>();
        rawBalances.forEach((playerId, rawBalance) -> entries.add(new ChallengeScoreEntry(
                playerId, ChallengeAmount.canonical(rawBalance), playerRevisions.getOrDefault(playerId, 0L))));
        entries.sort(Comparator.comparing(entry -> entry.playerId().toString()));
        return List.copyOf(entries);
    }

    public long stateRevision() {
        return stateRevision;
    }

    public record Result(UUID playerId, BigDecimal rawBalance, BigDecimal score,
                         long playerRevision, long stateRevision) {
    }

    public record Delta(UUID playerId, BigDecimal signedDelta) {
    }

    public static final class CapacityExceededException extends IllegalStateException {
        private CapacityExceededException() {
            super("Challenge score ledger capacity reached.");
        }
    }
}
