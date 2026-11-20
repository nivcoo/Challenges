package fr.nivcoo.challenges.cache;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.edenplayers.api.AEdenPlayers;
import fr.nivcoo.challenges.storage.Database;
import fr.nivcoo.edenplayers.api.EdenPlayersAPI;
import fr.nivcoo.edenplayers.api.model.PlayerProfile;
import org.bukkit.Bukkit;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class CacheManager {

    private final Database db;
    private LinkedHashMap<UUID, Integer> playersRankingCache;
    private final Map<UUID, Long> rankingReachedAt = new HashMap<>();
    private volatile List<Map.Entry<UUID, Integer>> rankingEntries = List.of();

    public CacheManager() throws SQLException {
        Challenges challenges = Challenges.get();
        this.db = challenges.getDatabaseChallenges();
        playersRankingCache = new LinkedHashMap<>();
        loadAllScores();
    }

    private void loadAllScores() throws SQLException {
        replaceRanking(db.getAllPlayersScoreStrict());
    }

    public int getPlayerScore(UUID uuid) {
        return playersRankingCache.getOrDefault(uuid, 0);
    }

    public void applyRankingUpdates(Database.RankingScores ranking) {
        if (ranking == null || ranking.scores().isEmpty()) return;
        playersRankingCache.putAll(ranking.scores());
        ranking.scores().keySet().forEach(uuid -> rankingReachedAt.put(uuid, ranking.reachedAt().getOrDefault(uuid, 0L)));
        sortRanking();
    }

    public void clearRanking() {
        playersRankingCache.clear();
        rankingEntries = List.of();
        rankingReachedAt.clear();
    }

    public void replaceRanking(Database.RankingScores ranking) {
        rankingReachedAt.clear();
        rankingReachedAt.putAll(ranking.reachedAt());
        playersRankingCache.clear();
        playersRankingCache.putAll(sortByValueDescending(ranking.scores()));
        refreshRankingEntries();
    }

    public long getRankingReachedAt(UUID playerUuid) {
        return rankingReachedAt.getOrDefault(playerUuid, 0L);
    }

    public boolean matchesRanking(Database.RankingScores ranking) {
        return ranking != null && playersRankingCache.equals(ranking.scores())
                && rankingReachedAt.equals(ranking.reachedAt());
    }

    public Map<UUID, Integer> getSortedScores() {
        return Collections.unmodifiableMap(playersRankingCache);
    }

    private void sortRanking() {
        playersRankingCache = sortByValueDescending(playersRankingCache);
        refreshRankingEntries();
    }

    public List<Map.Entry<UUID, Integer>> rankingEntries() {
        return rankingEntries;
    }

    private void refreshRankingEntries() {
        rankingEntries = playersRankingCache.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                .toList();
    }

    private LinkedHashMap<UUID, Integer> sortByValueDescending(Map<UUID, Integer> input) {
        return input.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()
                        .thenComparingLong(entry -> rankingReachedAt.getOrDefault(entry.getKey(), 0L))
                        .thenComparing(entry -> UUID.nameUUIDFromBytes((entry.getValue() + ":" + entry.getKey())
                                .getBytes(StandardCharsets.UTF_8))))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public String resolvePlayerName(UUID uuid) {
        Optional<String> authoritative = resolveRewardPlayerName(uuid);
        if (authoritative.isPresent()) return authoritative.get();

        String bukkitName = Bukkit.getOfflinePlayer(uuid).getName();
        if (isSafePlayerName(bukkitName)) return bukkitName;
        return uuid.toString();
    }

    public Optional<String> resolveRewardPlayerName(UUID uuid) {
        if (uuid == null) return Optional.empty();
        try {
            AEdenPlayers api = EdenPlayersAPI.get();
            if (api != null) {
                Optional<PlayerProfile> profile = api.players().profile(uuid);
                if (profile.isPresent() && isRewardPlayerName(profile.get().getUsername())) {
                    return Optional.of(profile.get().getUsername());
                }
            }
        } catch (RuntimeException ignored) {
        }
        return Optional.empty();
    }

    static boolean isSafePlayerName(String name) {
        return name != null && name.length() <= 32 && name.matches("[A-Za-z0-9_.-]+");
    }

    static boolean isRewardPlayerName(String name) {
        return isSafePlayerName(name) && name.length() <= 32;
    }
}
