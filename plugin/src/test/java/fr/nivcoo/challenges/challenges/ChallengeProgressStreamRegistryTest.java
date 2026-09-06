package fr.nivcoo.challenges.challenges;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ChallengeProgressStreamRegistryTest {
    private static final String PARTICIPANT = "skyblock-01";

    @Test
    void acceptsNewStreamsAndRevisionGaps() {
        ChallengeProgressStreamRegistry registry = new ChallengeProgressStreamRegistry();
        ChallengeScoreLedger ledger = new ChallengeScoreLedger();
        UUID streamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        ChallengeProgressStreamRegistry.Preparation first = registry.prepare(
                streamId, PARTICIPANT, 1L, Map.of(playerId, amount("10")));

        assertEquals(ChallengeProgressStreamRegistry.Status.NEW, first.status());
        assertEquals(0L, first.acknowledgedRevision());
        ledger.applyBatch(first.deltas());
        assertEquals(1L, registry.commit(first));

        ChallengeProgressStreamRegistry.Preparation fifth = registry.prepare(
                streamId, PARTICIPANT, 5L, Map.of(playerId, amount("16")));

        assertEquals(ChallengeProgressStreamRegistry.Status.NEW, fifth.status());
        assertEquals(1L, fifth.acknowledgedRevision());
        assertDelta(fifth, playerId, "6");
        ledger.applyBatch(fifth.deltas());
        assertEquals(5L, registry.commit(fifth));
        assertAmount("16", ledger.rawBalance(playerId));
        assertEquals(5L, registry.acknowledgedRevision(streamId, PARTICIPANT));
    }

    @Test
    void classifiesDuplicateStaleAndConflictingSnapshots() {
        ChallengeProgressStreamRegistry registry = new ChallengeProgressStreamRegistry();
        UUID streamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        commit(registry, streamId, PARTICIPANT, 5L, Map.of(playerId, amount("10")));

        ChallengeProgressStreamRegistry.Preparation duplicate = registry.prepare(
                streamId, PARTICIPANT, 5L, Map.of(playerId, amount("10.0")));
        ChallengeProgressStreamRegistry.Preparation stale = registry.prepare(
                streamId, PARTICIPANT, 3L, Map.of(playerId, amount("999")));
        ChallengeProgressStreamRegistry.Preparation changed = registry.prepare(
                streamId, PARTICIPANT, 5L, Map.of(playerId, amount("11")));
        ChallengeProgressStreamRegistry.Preparation wrongOwner = registry.prepare(
                streamId, "skyblock-02", 6L, Map.of(playerId, amount("11")));

        assertStatus(duplicate, ChallengeProgressStreamRegistry.Status.DUPLICATE, 5L);
        assertStatus(stale, ChallengeProgressStreamRegistry.Status.STALE, 5L);
        assertStatus(changed, ChallengeProgressStreamRegistry.Status.CONFLICT, 5L);
        assertStatus(wrongOwner, ChallengeProgressStreamRegistry.Status.CONFLICT, 5L);
    }

    @Test
    void removalsProduceNegativeDeltas() {
        ChallengeProgressStreamRegistry registry = new ChallengeProgressStreamRegistry();
        ChallengeScoreLedger ledger = new ChallengeScoreLedger();
        UUID streamId = UUID.randomUUID();
        UUID retainedPlayer = UUID.randomUUID();
        UUID removedPlayer = UUID.randomUUID();

        apply(registry, ledger, streamId, PARTICIPANT, 1L,
                Map.of(retainedPlayer, amount("10"), removedPlayer, amount("4")));
        ChallengeProgressStreamRegistry.Preparation removal = registry.prepare(
                streamId, PARTICIPANT, 2L, Map.of(retainedPlayer, amount("10")));

        assertDelta(removal, removedPlayer, "-4");
        ledger.applyBatch(removal.deltas());
        registry.commit(removal);
        assertAmount("10", ledger.rawBalance(retainedPlayer));
        assertAmount("0", ledger.rawBalance(removedPlayer));
    }

    @Test
    void keepsStreamsIndependentAndAdditive() {
        ChallengeProgressStreamRegistry registry = new ChallengeProgressStreamRegistry();
        ChallengeScoreLedger ledger = new ChallengeScoreLedger();
        UUID firstStream = UUID.randomUUID();
        UUID secondStream = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();

        apply(registry, ledger, firstStream, PARTICIPANT, 1L, Map.of(playerId, amount("10")));
        apply(registry, ledger, secondStream, PARTICIPANT, 1L, Map.of(playerId, amount("7")));
        assertAmount("17", ledger.rawBalance(playerId));

        apply(registry, ledger, firstStream, PARTICIPANT, 2L, Map.of(playerId, amount("9")));
        assertAmount("16", ledger.rawBalance(playerId));
        assertEquals(2, registry.size());
    }

    @Test
    void appliesAnAbsoluteJumpLargerThanOneMutation() {
        ChallengeProgressStreamRegistry registry = new ChallengeProgressStreamRegistry();
        ChallengeScoreLedger ledger = new ChallengeScoreLedger();
        UUID streamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        ChallengeProgressStreamRegistry.Preparation preparation = registry.prepare(
                streamId, PARTICIPANT, 2L, Map.of(playerId, amount("2000000000000")));

        ledger.applyAdjustments(preparation.deltas());
        registry.commit(preparation);

        assertAmount("2000000000000", ledger.rawBalance(playerId));
    }

    @Test
    void rejectsNewStreamsAtCapacityWithoutBlockingExistingStreams() {
        ChallengeProgressStreamRegistry registry = new ChallengeProgressStreamRegistry();
        for (int index = 0; index < ChallengeProgressStreamRegistry.MAX_STREAMS; index++) {
            UUID streamId = new UUID(0L, index + 1L);
            commit(registry, streamId, "participant-" + index, 1L, Map.of());
        }

        ChallengeProgressStreamRegistry.Preparation capacity = registry.prepare(
                new UUID(1L, 1L), PARTICIPANT, 1L, Map.of());
        ChallengeProgressStreamRegistry.Preparation existing = registry.prepare(
                new UUID(0L, 1L), "participant-0", 2L, Map.of());

        assertStatus(capacity, ChallengeProgressStreamRegistry.Status.CAPACITY, 0L);
        assertEquals(ChallengeProgressStreamRegistry.Status.NEW, existing.status());
        assertEquals(2L, registry.commit(existing));
    }

    @Test
    void snapshotsAndPreparationsAreImmutableAndValidated() {
        ChallengeProgressStreamRegistry registry = new ChallengeProgressStreamRegistry();
        UUID streamId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        Map<UUID, BigDecimal> mutable = new LinkedHashMap<>();
        mutable.put(playerId, amount("3"));

        ChallengeProgressStreamRegistry.Preparation prepared = registry.prepare(
                streamId, PARTICIPANT, 1L, mutable);
        mutable.put(playerId, amount("99"));

        assertDelta(prepared, playerId, "3");
        assertThrows(UnsupportedOperationException.class,
                () -> prepared.deltas().add(new ChallengeScoreLedger.Delta(playerId, amount("1"))));
        registry.commit(prepared);
        assertEquals(ChallengeProgressStreamRegistry.Status.DUPLICATE,
                registry.prepare(streamId, PARTICIPANT, 1L, Map.of(playerId, amount("3"))).status());

        assertThrows(NullPointerException.class,
                () -> registry.prepare(null, PARTICIPANT, 1L, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> registry.prepare(UUID.randomUUID(), " ", 1L, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> registry.prepare(UUID.randomUUID(), PARTICIPANT, 0L, Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> registry.prepare(UUID.randomUUID(), PARTICIPANT, 1L, null));
    }

    private static void apply(ChallengeProgressStreamRegistry registry, ChallengeScoreLedger ledger,
                              UUID streamId, String participantId, long revision,
                              Map<UUID, BigDecimal> contributions) {
        ChallengeProgressStreamRegistry.Preparation preparation = registry.prepare(
                streamId, participantId, revision, contributions);
        assertEquals(ChallengeProgressStreamRegistry.Status.NEW, preparation.status());
        ledger.applyBatch(preparation.deltas());
        registry.commit(preparation);
    }

    private static void commit(ChallengeProgressStreamRegistry registry, UUID streamId,
                               String participantId, long revision,
                               Map<UUID, BigDecimal> contributions) {
        ChallengeProgressStreamRegistry.Preparation preparation = registry.prepare(
                streamId, participantId, revision, contributions);
        assertEquals(ChallengeProgressStreamRegistry.Status.NEW, preparation.status());
        registry.commit(preparation);
    }

    private static void assertStatus(ChallengeProgressStreamRegistry.Preparation preparation,
                                     ChallengeProgressStreamRegistry.Status status,
                                     long acknowledgedRevision) {
        assertEquals(status, preparation.status());
        assertEquals(acknowledgedRevision, preparation.acknowledgedRevision());
        assertEquals(0, preparation.deltas().size());
    }

    private static void assertDelta(ChallengeProgressStreamRegistry.Preparation preparation,
                                    UUID playerId, String amount) {
        assertEquals(1, preparation.deltas().size());
        ChallengeScoreLedger.Delta delta = preparation.deltas().getFirst();
        assertEquals(playerId, delta.playerId());
        assertAmount(amount, delta.signedDelta());
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, amount(expected).compareTo(actual));
    }
}
