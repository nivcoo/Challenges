package fr.nivcoo.challenges.cache;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.actions.GlobalResetAction;
import fr.nivcoo.challenges.utils.DatabaseChallenges;

import java.util.HashMap;
import java.util.UUID;

public class CacheManager {

    private final DatabaseChallenges db;
    private HashMap<UUID, Integer> playersRankingCache;

    public CacheManager() {
        Challenges challenges = Challenges.get();
        this.db = challenges.getDatabaseChallenges();
        playersRankingCache = new HashMap<>();
        loadAllScores();

    }

    public void loadAllScores() {
        playersRankingCache = new HashMap<>(db.getAllPlayersScore());
    }


    public void updatePlayerScore(UUID uuid, int addNumber) {
        int newCount = getPlayerScore(uuid) + addNumber;
        db.updatePlayerScore(uuid, newCount);
        playersRankingCache.put(uuid, newCount);
    }

    public int getPlayerScore(UUID uuid) {
        return playersRankingCache.getOrDefault(uuid, 0);
    }

    public void updateFromRedis(UUID uuid, int count) {
        playersRankingCache.put(uuid, count);
    }

    public void resetAllData() {
        resetAllData(true);
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
            plugin.getChallengesManager().disablePlugin();
        }

        db.clearDB();
        playersRankingCache.clear();

        plugin.reload();
    }

}
