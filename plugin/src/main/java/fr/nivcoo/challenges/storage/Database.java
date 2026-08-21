package fr.nivcoo.challenges.storage;

import fr.nivcoo.challenges.storage.model.ChallengeRankingModel;
import fr.nivcoo.utilsz.core.database.DatabaseManager;
import fr.nivcoo.utilsz.core.database.ModelRepository;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Database {
    private final DatabaseManager manager;
    private final ModelRepository<ChallengeRankingModel> ranking;

    public Database(DatabaseManager manager) {
        this.manager = manager;
        this.ranking = manager.model(ChallengeRankingModel.MODEL);
    }

    public void initDB() {
        try {
            ranking.createTable();
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialize Challenges ranking tables.", e);
        }
    }

    public Map<UUID, Integer> addPlayerScores(Map<UUID, Integer> additions) throws SQLException {
        if (additions == null || additions.isEmpty()) return Map.of();
        return manager.transaction(connection -> {
            Map<UUID, Integer> updated = new HashMap<>();
            for (Map.Entry<UUID, Integer> entry : additions.entrySet()) {
                UUID playerId = entry.getKey();
                int addition = entry.getValue() == null ? 0 : entry.getValue();
                if (playerId == null || addition <= 0) continue;
                ChallengeRankingModel existing = ranking.find(connection).where("uuid", playerId).limit(1).all()
                        .stream().findFirst().orElse(null);
                int newScore = Math.addExact(existing == null ? 0 : existing.score(), addition);
                if (existing == null) {
                    ranking.insert(connection, new ChallengeRankingModel(playerId, newScore));
                } else {
                    ranking.update(connection, Map.of("score", newScore), "uuid = ?", playerId);
                }
                updated.put(playerId, newScore);
            }
            return Map.copyOf(updated);
        });
    }

    public Map<UUID, Integer> getAllPlayersScoreStrict() throws SQLException {
        Map<UUID, Integer> scores = new HashMap<>();
        for (ChallengeRankingModel model : ranking.all()) scores.put(model.uuid(), model.score());
        return scores;
    }

    public void clearDBStrict() throws SQLException {
        ranking.clear();
    }

}
