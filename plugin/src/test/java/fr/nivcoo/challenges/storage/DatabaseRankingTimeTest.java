package fr.nivcoo.challenges.storage;

import fr.nivcoo.challenges.storage.model.ChallengeRankingModel;
import fr.nivcoo.utilsz.core.database.DatabaseManager;
import fr.nivcoo.utilsz.core.database.DatabaseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DatabaseRankingTimeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsManuallyUpdatedRankingAndPersistsReachTimesWithoutCreatingAnotherTable() throws Exception {
        String file = temporaryDirectory.resolve("ranking.db").toString();
        UUID legacy = new UUID(0L, 1L);
        UUID current = new UUID(0L, 2L);
        DatabaseManager manager = open(file);
        Database.RankingScores saved;
        try {
            manager.execute("CREATE TABLE challenge_ranking (uuid VARCHAR(36) PRIMARY KEY, score INTEGER DEFAULT 0)");
            manager.execute("INSERT INTO challenge_ranking (uuid, score) VALUES (?, ?)", legacy, 7);
            manager.execute("ALTER TABLE challenge_ranking ADD COLUMN reached_at BIGINT NOT NULL DEFAULT 0");
            Database database = new Database(manager);
            database.initDB();
            assertFalse(tableExists(manager, "challenge_ranking_times"));
            assertEquals(Map.of(legacy, 7), database.getAllPlayersScoreStrict().scores());
            assertEquals(Map.of(), database.getAllPlayersScoreStrict().reachedAt());
            saved = database.addPlayerScores(Map.of(current, 7));
            assertTrue(saved.reachedAt().get(current) > 0L);
            assertEquals(new Database.RankingScores(Map.of(), Map.of()), database.addPlayerScores(Map.of(current, 0)));
            assertEquals(saved.reachedAt(), database.getAllPlayersScoreStrict().reachedAt());
        } finally {
            manager.closeConnection();
        }

        manager = open(file);
        try {
            Database database = new Database(manager);
            database.initDB();
            Database.RankingScores loaded = database.getAllPlayersScoreStrict();
            assertEquals(Map.of(legacy, 7, current, 7), loaded.scores());
            assertEquals(saved.reachedAt(), loaded.reachedAt());
            assertFalse(tableExists(manager, "challenge_ranking_times"));
            database.clearDBStrict();
            assertEquals(new Database.RankingScores(Map.of(), Map.of()), database.getAllPlayersScoreStrict());
        } finally {
            manager.closeConnection();
        }
    }

    @Test
    void rollsBackTheScoreAndReachTimeWhenAnotherScoreOverflowsInTheSameBatch() throws Exception {
        DatabaseManager manager = open(temporaryDirectory.resolve("rollback.db").toString());
        UUID first = new UUID(0L, 1L);
        UUID overflowing = new UUID(0L, 2L);
        try {
            Database database = new Database(manager);
            database.initDB();
            var ranking = manager.model(ChallengeRankingModel.MODEL);
            ranking.insert(new ChallengeRankingModel(first, 4, 100L));
            ranking.insert(new ChallengeRankingModel(overflowing, Integer.MAX_VALUE, 200L));
            Database.RankingScores before = database.getAllPlayersScoreStrict();
            Map<UUID, Integer> additions = new LinkedHashMap<>();
            additions.put(first, 1);
            additions.put(overflowing, 1);
            assertThrows(ArithmeticException.class, () -> database.addPlayerScores(additions));
            assertEquals(before, database.getAllPlayersScoreStrict());
        } finally {
            manager.closeConnection();
        }
    }

    private static boolean tableExists(DatabaseManager manager, String name) throws SQLException {
        return !manager.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                row -> row.getString("name"), name).isEmpty();
    }

    private static DatabaseManager open(String file) {
        return new DatabaseManager(DatabaseType.SQLITE, null, 0, null, null, null, file);
    }
}
