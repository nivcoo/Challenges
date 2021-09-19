package fr.nivcoo.challenges.cache;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.utils.Database;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashMap;
import java.util.UUID;

public class CacheManager implements Listener {

    private Challenges challenges;

    private Database db;

    private HashMap<UUID, Integer> playersClassementCache;

    public CacheManager() {
        challenges = Challenges.get();
        db = challenges.getDatabase();
        playersClassementCache = new HashMap<>();
        getAllPlayersCount();
    }

    public void getAllPlayersCount() {
        playersClassementCache = db.getAllPlayersCount(Bukkit.getServer().getOnlinePlayers());
    }

    public void updatePlayerCount(UUID uuid, int addNumber) {
        int newCount = getPlayerCount(uuid) + addNumber;
        db.updatePlayerCount(uuid, newCount);
        playersClassementCache.put(uuid, newCount);
    }

    public int getPlayerCount(UUID uuid) {
        Integer count = playersClassementCache.get(uuid);
        if (count == null) {
            count = db.getPlayerCount(uuid);
            playersClassementCache.put(uuid, count);
        }
        return count;
    }

    @EventHandler
    public void onPlayerJoinEvent(PlayerJoinEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        int count = db.getPlayerCount(uuid);
        playersClassementCache.put(uuid, count);

    }

}
