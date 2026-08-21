package fr.nivcoo.challenges.service;

import fr.nivcoo.challenges.Challenges;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class ChallengeStateWakeupListener implements Listener {
    private final Challenges plugin;

    public ChallengeStateWakeupListener(Challenges plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getChallengesManager().wakeupStateSynchronization();
    }
}
