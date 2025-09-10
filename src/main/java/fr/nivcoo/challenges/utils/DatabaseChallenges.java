package fr.nivcoo.challenges.utils;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.actions.RankingUpdateAction;
import fr.nivcoo.utilsz.database.ColumnDefinition;
import fr.nivcoo.utilsz.database.DatabaseManager;
import fr.nivcoo.utilsz.database.DatabaseType;
import fr.nivcoo.utilsz.database.TableConstraintDefinition;

import java.sql.Connection;
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
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
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
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT score FROM challenge_ranking WHERE uuid = ?")) {
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
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM challenge_ranking");
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
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM challenge_ranking;")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            Challenges.get().getLogger().severe("Failed to clear challenge ranking: " + e.getMessage());
        }
    }

    public void savePlayerName(UUID uuid, String name) {
        if (name == null || name.isBlank()) return;
        String sql = (db.getType() == DatabaseType.SQLITE)
                ? "INSERT INTO challenge_players (player_uuid, player_name) VALUES (?, ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET player_name = excluded.player_name"
                : "INSERT INTO challenge_players (player_uuid, player_name) VALUES (?, ?) " +
                "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name)";

        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            Challenges.get().getLogger().warning("Erreur SQL savePlayerName: " + e.getMessage());
        }
    }

    public String getPlayerName(UUID uuid) {
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT player_name FROM challenge_players WHERE player_uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("player_name");
            }
        } catch (SQLException e) {
            Challenges.get().getLogger().warning("Erreur SQL getPlayerName: " + e.getMessage());
        }
        return null;
    }

    public Map<UUID, String> getAllPlayerNames() {
        Map<UUID, String> all = new HashMap<>();
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT player_uuid, player_name FROM challenge_players");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UUID u = UUID.fromString(rs.getString("player_uuid"));
                String n = rs.getString("player_name");
                all.put(u, n);
            }
        } catch (SQLException e) {
            Challenges.get().getLogger().warning("Erreur SQL getAllPlayerNames: " + e.getMessage());
        }
        return all;
    }



}
