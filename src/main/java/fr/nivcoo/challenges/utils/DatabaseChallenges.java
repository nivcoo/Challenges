package fr.nivcoo.challenges.utils;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.actions.RankingUpdateAction;
import fr.nivcoo.utilsz.database.ColumnDefinition;
import fr.nivcoo.utilsz.database.DatabaseManager;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class DatabaseChallenges {

    private final DatabaseManager db;

    public DatabaseChallenges(DatabaseManager db) {
        this.db = db;
    }

    public void initDB() throws SQLException {
        db.createTable("challenge_ranking", List.of(
                new ColumnDefinition("uuid", "TEXT", "PRIMARY KEY"),
                new ColumnDefinition("score", "INTEGER", "DEFAULT 0")
        ));
    }

    public void updatePlayerScore(UUID uuid, int score) {
        try (PreparedStatement ps = db.prepareStatement(
                "REPLACE INTO challenge_ranking (uuid, score) VALUES (?, ?);"
        )) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, score);
            ps.executeUpdate();
        } catch (SQLException e) {
            Challenges.get().getLogger().severe("Failed to update player score: " + e.getMessage());
        }

        if (Challenges.get().getRedisChannelRegistry() != null) {
            Challenges.get().getRedisChannelRegistry().publish(new RankingUpdateAction(uuid, score));
        }
    }

    public int getPlayerScore(UUID uuid) {
        int count = 0;
        try (PreparedStatement ps = db.prepareStatement("SELECT score FROM challenge_ranking WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt("score");
                }
            }
        } catch (SQLException e) {
            Challenges.get().getLogger().severe("Failed to get player count: " + e.getMessage());
        }
        return count;
    }

    public Map<UUID, Integer> getAllPlayersScore() {
        Map<UUID, Integer> counts = new HashMap<>();
        try (PreparedStatement ps = db.prepareStatement("SELECT * FROM challenge_ranking");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                int count = rs.getInt("score");
                counts.put(uuid, count);
            }
        } catch (SQLException e) {
            Challenges.get().getLogger().severe("Failed to load all challenge scores: " + e.getMessage());
        }
        return counts;
    }

    public void clearDB() {
        try (PreparedStatement ps = db.prepareStatement("DELETE FROM challenge_ranking;")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            Challenges.get().getLogger().severe("Failed to clear challenge ranking: " + e.getMessage());
        }
    }

}
