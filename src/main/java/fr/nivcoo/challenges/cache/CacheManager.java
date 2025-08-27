package fr.nivcoo.challenges.cache;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.actions.GlobalResetAction;
import fr.nivcoo.challenges.actions.PlayerNameUpdateAction;
import fr.nivcoo.challenges.utils.DatabaseChallenges;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class CacheManager {

    private final Challenges challenges;
    private final DatabaseChallenges db;
    private LinkedHashMap<UUID, Integer> playersRankingCache;
    private final Map<UUID, String> nameCache = new ConcurrentHashMap<>();

    public CacheManager() {
        challenges = Challenges.get();
        this.db = challenges.getDatabaseChallenges();
        playersRankingCache = new LinkedHashMap<>();
        loadAllScores();
        preloadNamesFromDB();
    }

    public void loadAllScores() {
        Map<UUID, Integer> loaded = db.getAllPlayersScore();
        playersRankingCache.clear();
        playersRankingCache.putAll(sortByValueDescending(loaded));
    }

    public void updatePlayerScore(UUID uuid, int addNumber) {
        int newCount = getPlayerScore(uuid) + addNumber;
        db.updatePlayerScore(uuid, newCount);
        playersRankingCache.put(uuid, newCount);
        sortRanking();
    }

    public int getPlayerScore(UUID uuid) {
        return playersRankingCache.getOrDefault(uuid, 0);
    }

    public void updateRankingFromRedis(UUID uuid, int count) {
        playersRankingCache.put(uuid, count);
        sortRanking();
    }

    public void resetAllData() {
        resetAllData(true);
    }

    public void preloadNamesFromDB() {
        Map<UUID, String> all = Challenges.get().getDatabaseChallenges().getAllPlayerNames();
        all.forEach((u, n) -> {
            if (n != null && !n.isBlank()) nameCache.put(u, n);
        });
        Challenges.get().getLogger().info("[Challenges] Loaded " + nameCache.size() + " player names into cache.");
    }

    public void resetAllData(boolean propagate) {
        performReset();

        if (propagate && Challenges.get().getRedisChannelRegistry() != null) {
            Challenges.get().getRedisChannelRegistry().publish(new GlobalResetAction());
        }

        Challenges.get().getLogger().info("[Challenges] Réinitialisation " + (propagate ? "globale" : "locale") + " effectuée.");
    }

    private void performReset() {
        Challenges plugin = Challenges.get();

        if (plugin.getChallengesManager() != null) {
            plugin.getChallengesManager().stopCurrentChallenge();
        }

        db.clearDB();
        playersRankingCache.clear();

        plugin.reload();
    }

    public Map<UUID, Integer> getSortedScores() {
        return Collections.unmodifiableMap(playersRankingCache);
    }

    private void sortRanking() {
        playersRankingCache = sortByValueDescending(playersRankingCache);
    }

    private LinkedHashMap<UUID, Integer> sortByValueDescending(Map<UUID, Integer> input) {
        return input.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public String resolvePlayerName(UUID uuid) {
        String cached = nameCache.get(uuid);
        if (cached != null) return cached;

        String dbName = challenges.getDatabaseChallenges().getPlayerName(uuid);
        if (dbName != null && !dbName.isBlank()) {
            nameCache.put(uuid, dbName);
            return dbName;
        }

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            String n = online.getName();
            cacheName(uuid, n);
            return n;
        }

        String off = Bukkit.getOfflinePlayer(uuid).getName();
        if (off != null && !off.isBlank()) {
            cacheName(uuid, off);
            return off;
        }

        return uuid.toString();
    }

    public void cacheNameRemote(UUID uuid, String name) {
        if (name == null || name.isBlank()) return;
        String cached = nameCache.get(uuid);
        if (name.equals(cached)) return;
        nameCache.put(uuid, name);
    }

    public void cacheName(UUID uuid, String name) {
        if (name == null || name.isBlank()) return;

        String cached = nameCache.get(uuid);
        if (name.equals(cached)) return;

        nameCache.put(uuid, name);
        Challenges.get().getDatabaseChallenges().savePlayerName(uuid, name);

        if (Challenges.get().getRedisChannelRegistry() != null) {
            Challenges.get().getRedisChannelRegistry().publish(new PlayerNameUpdateAction(uuid, name));
        }
    }
}
