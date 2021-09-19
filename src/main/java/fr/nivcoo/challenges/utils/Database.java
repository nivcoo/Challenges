package fr.nivcoo.challenges.utils;

import org.bukkit.entity.Player;

import java.sql.*;
import java.util.Collection;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;

public class Database {
    private String DBPath = "";
    private Connection connection = null;
    private Statement statement = null;

    public Database(String dBPath) {
        DBPath = dBPath;
    }

    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            if (connection == null || connection.isClosed())
                connection = DriverManager.getConnection("jdbc:sqlite:" + DBPath);
            if (statement == null || statement.isClosed())
                statement = connection.createStatement();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (!connection.isClosed())
                connection.close();
            if (!statement.isClosed())
                statement.close();
        } catch (SQLException e) {
        }
    }

    public void initDB() {
        connect();
        query("CREATE TABLE IF NOT EXISTS classement (" + "UUID TEXT PRIMARY KEY, " + "count INTEGER DEFAULT 0 " + ")");

        close();

    }

    public ResultSet query(String requet) {
        ResultSet resultat = null;
        try {
            resultat = statement.executeQuery(requet);
        } catch (SQLException e) {
        }
        return resultat;

    }

    public void updatePlayerCount(UUID uuid, int count) {
        connect();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement("REPLACE INTO classement VALUES (?, ?);");
            preparedStatement.setString(1, uuid.toString());
            preparedStatement.setInt(2, count);
            preparedStatement.executeUpdate();
            preparedStatement.close();

        } catch (SQLException e) {
            e.printStackTrace();
        }
        close();
    }

    public HashMap<UUID, Integer> getAllPlayersCount(Collection<? extends Player> collection) {
        connect();
        HashMap<UUID, Integer> allPlayersCount = new HashMap<>();
        for (Player player : collection)
            allPlayersCount.put(player.getUniqueId(), 0);
        try {
            String sqlIN = collection.stream().map(x -> "'" + x.getUniqueId().toString() + "'")
                    .collect(Collectors.joining(",", "(", ")"));
            PreparedStatement preparedStatement = connection
                    .prepareStatement("SELECT * FROM classement WHERE UUID IN " + sqlIN);

            ResultSet rs = preparedStatement.executeQuery();
            // Array tagIdsInArray = connection.createArrayOf("integer", tagIds.toArray());
            // rs.setArray(1, tagIdsInArray);

            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("UUID"));
                int count = rs.getInt("count");
                allPlayersCount.put(uuid, count);
            }
            preparedStatement.close();
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        close();

        return allPlayersCount;
    }

    public int getPlayerCount(UUID uuid) {
        connect();
        int count = 0;
        try {
            PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM classement WHERE UUID=?");
            preparedStatement.setString(1, uuid.toString());
            ResultSet rs = preparedStatement.executeQuery();
            while (rs.next()) {
                count = rs.getInt("count");
            }
            preparedStatement.close();
            rs.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        close();

        return count;
    }

    public void clearDB() {
        connect();
        try {
            PreparedStatement preparedStatement = connection.prepareStatement("DELETE FROM classement;");
            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        close();

    }

}