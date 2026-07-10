package fr.nivcoo.challenges.storage;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.messaging.action.RankingUpdateAction;
import fr.nivcoo.challenges.storage.model.ChallengePlayerModel;
import fr.nivcoo.challenges.storage.model.ChallengeRankingModel;
import fr.nivcoo.utilsz.core.database.DatabaseManager;
import fr.nivcoo.utilsz.core.database.ModelRepository;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class Database {
    private final ModelRepository<ChallengeRankingModel> ranking;
    private final ModelRepository<ChallengePlayerModel> players;

    public Database(DatabaseManager manager) {
        this.ranking = manager.model(ChallengeRankingModel.MODEL);
        this.players = manager.model(ChallengePlayerModel.MODEL);
    }

    public void initDB() {
        try {
            ranking.createTable();
            players.createTable();
        } catch (SQLException e) {
            Challenges.get().getLogger().warning("Erreur lors de la création des tables Challenges: " + e.getMessage());
        }
    }

    public void updatePlayerScore(UUID uuid, int score) {
        try {
            if (ranking.exists("uuid = ?", uuid)) {
                ranking.update(Map.of("score", score), "uuid = ?", uuid);
            } else {
                ranking.insert(new ChallengeRankingModel(uuid, score));
            }
            Challenges.get().getBus().publish(new RankingUpdateAction(uuid, score));
        } catch (SQLException e) {
            Challenges.get().getLogger().severe("Failed to update player score: " + e.getMessage());
        }
    }

    public int getPlayerScore(UUID uuid) {
        try {
            return ranking.find().where("uuid", uuid).limit(1).all().stream()
                    .findFirst()
                    .map(ChallengeRankingModel::score)
                    .orElse(0);
        } catch (SQLException e) {
            Challenges.get().getLogger().severe("Failed to get player score: " + e.getMessage());
        }
        return 0;
    }

    public Map<UUID, Integer> getAllPlayersScore() {
        Map<UUID, Integer> scores = new HashMap<>();
        try {
            for (ChallengeRankingModel model : ranking.all()) {
                scores.put(model.uuid(), model.score());
            }
        } catch (SQLException e) {
            Challenges.get().getLogger().severe("Failed to load all challenge scores: " + e.getMessage());
        }
        return scores;
    }

    public void clearDB() {
        try {
            ranking.clear();
        } catch (SQLException e) {
            Challenges.get().getLogger().severe("Failed to clear challenge ranking: " + e.getMessage());
        }
    }

    public void savePlayerName(UUID uuid, String name) {
        if (name == null || name.isBlank()) return;
        try {
            if (players.exists("player_uuid = ?", uuid)) {
                players.update(Map.of("player_name", name), "player_uuid = ?", uuid);
            } else {
                players.insert(new ChallengePlayerModel(uuid, name));
            }
        } catch (SQLException e) {
            Challenges.get().getLogger().warning("Erreur SQL savePlayerName: " + e.getMessage());
        }
    }

    public String getPlayerName(UUID uuid) {
        try {
            return players.find().where("player_uuid", uuid).limit(1).all().stream()
                    .findFirst()
                    .map(ChallengePlayerModel::playerName)
                    .orElse(null);
        } catch (SQLException e) {
            Challenges.get().getLogger().warning("Erreur SQL getPlayerName: " + e.getMessage());
        }
        return null;
    }

    public Map<UUID, String> getAllPlayerNames() {
        Map<UUID, String> all = new HashMap<>();
        try {
            for (ChallengePlayerModel model : players.all()) {
                all.put(model.playerUuid(), model.playerName());
            }
        } catch (SQLException e) {
            Challenges.get().getLogger().warning("Erreur SQL getAllPlayerNames: " + e.getMessage());
        }
        return all;
    }
}
