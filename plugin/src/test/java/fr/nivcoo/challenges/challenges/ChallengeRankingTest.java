package fr.nivcoo.challenges.challenges;

import com.google.gson.Gson;
import fr.nivcoo.challenges.messaging.model.ChallengeScoreEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ChallengeRankingTest {
    @Test
    void pairsEqualScoresByReachOrderAndKeepsTheStrictTopLimit() {
        ChallengeScoreLedger ledger = new ChallengeScoreLedger();
        List<UUID> players = List.of(new UUID(0L, 9L), new UUID(0L, 8L), new UUID(0L, 7L),
                new UUID(0L, 6L), new UUID(0L, 5L));
        for (UUID player : players) ledger.apply(player, BigDecimal.TEN);
        UUID lower = new UUID(0L, 1L);
        ledger.apply(lower, BigDecimal.ONE);

        List<ChallengeRanking.Entry> ranking = ChallengeRanking.entries(ledger.sortedScores());
        assertEquals(List.of(1, 1, 3, 3, 5, 6), ranking.stream().map(ChallengeRanking.Entry::place).toList());
        assertEquals(players, ranking.subList(0, 5).stream().map(ChallengeRanking.Entry::playerUuid).toList());
        assertEquals(players.subList(0, 3), ChallengeRanking.top(ranking, 3).stream()
                .map(ChallengeRanking.Entry::playerUuid).toList());
        assertEquals(List.of(1, 1, 3), ChallengeRanking.top(ranking, 3).stream()
                .map(ChallengeRanking.Entry::place).toList());
    }

    @Test
    void snapshotsAndLiveUpdatesKeepTheSameReachOrder() {
        UUID first = new UUID(0L, 9L);
        UUID second = new UUID(0L, 1L);
        ChallengeScoreLedger authority = new ChallengeScoreLedger();
        authority.apply(first, BigDecimal.TEN);
        authority.apply(second, BigDecimal.TEN);
        ChallengeScoreLedger replica = new ChallengeScoreLedger();
        assertTrue(replica.restore(authority.entries(), authority.stateRevision()));
        assertEquals(List.of(first, second), replica.sortedScores().keySet().stream().toList());

        authority.apply(first, BigDecimal.ONE.negate());
        ChallengeScoreLedger.Result reachedAgain = authority.apply(first, BigDecimal.ONE);
        assertTrue(replica.applyRemote(new ChallengeScoreEntry(first,
                ChallengeAmount.canonical(reachedAgain.rawBalance()), reachedAgain.playerRevision(),
                reachedAgain.reachedOrder()), reachedAgain.stateRevision()));
        assertEquals(List.of(second, first), replica.sortedScores().keySet().stream().toList());
        assertEquals(authority.sortedScores(), replica.sortedScores());
    }

    @Test
    void scoreWireAcceptsOlderPayloadsAndKeepsNewReachOrder() {
        Gson gson = new Gson();
        String oldPayload = "{\"playerId\":\"00000000-0000-0000-0000-000000000001\","
                + "\"rawBalance\":\"10\",\"playerRevision\":2}";
        ChallengeScoreEntry legacy = gson.fromJson(oldPayload, ChallengeScoreEntry.class);
        assertEquals(0L, legacy.reachedOrder());
        ChallengeScoreEntry current = new ChallengeScoreEntry(legacy.playerId(), "10", 2L, 5L);
        assertEquals(current, gson.fromJson(gson.toJson(current), ChallengeScoreEntry.class));
        LegacyScore oldReader = gson.fromJson(gson.toJson(current), LegacyScore.class);
        assertEquals(new LegacyScore(current.playerId(), "10", 2L), oldReader);
    }

    private record LegacyScore(UUID playerId, String rawBalance, long playerRevision) {
    }
}
