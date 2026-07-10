package fr.nivcoo.challenges.cache;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.messaging.action.GlobalResetAction;
import fr.nivcoo.challenges.storage.Database;
import fr.nivcoo.edenplayers.api.AEdenPlayers;
import fr.nivcoo.edenplayers.api.EdenPlayersAPI;
import fr.nivcoo.edenplayers.api.model.PlayerProfile;

import java.util.*;
import java.util.stream.Collectors;

public class CacheManager {

    private final Database db;
    private LinkedHashMap<UUID, Integer> playersRankingCache;

    public CacheManager() {
        Challenges challenges = Challenges.get();
        this.db = challenges.getDatabaseChallenges();
        playersRankingCache = new LinkedHashMap<>();
        loadAllScores();
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

    public void updateRankingFromBus(UUID uuid, int count) {
        playersRankingCache.put(uuid, count);
        sortRanking();
    }

    public void resetAllData() {
        resetAllData(true);
    }


    public void resetAllData(boolean propagate) {
        performReset();

        if (propagate) Challenges.get().getBus().publish(new GlobalResetAction());

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
        AEdenPlayers api = EdenPlayersAPI.get();
        if (api != null) {
            Optional<PlayerProfile> optProfile = api.players().profile(uuid);
            if (optProfile.isPresent()) {
                String name = optProfile.get().getUsername();
                if (name != null && !name.isBlank()) {
                    return name;
                }
            }
        }

        return uuid.toString();
    }
}
