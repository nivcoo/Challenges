package fr.nivcoo.challenges.hook.core;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.config.MainConfig;
import fr.nivcoo.challenges.service.ChallengeHudBridge;
import fr.nivcoo.challenges.service.tracking.ChallengeTrackingService;
import fr.nivcoo.utilsz.platform.bukkit.hook.BukkitHookContext;

import java.util.Objects;

public final class HookContext extends BukkitHookContext implements AutoCloseable {
    private final Challenges plugin;
    private ChallengeHudBridge hud = ChallengeHudBridge.unavailable();
    private boolean hudBound;

    public HookContext(Challenges plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    @Override
    public Challenges plugin() {
        return plugin;
    }

    public MainConfig cfg() {
        return plugin.cfg();
    }

    public void bindTrackingService(ChallengeTrackingService service) {
        plugin.bindTrackingService(service);
    }

    public void bindHud(ChallengeHudBridge bridge) {
        if (hudBound) throw new IllegalStateException("A challenge HUD bridge is already bound.");
        hud = Objects.requireNonNull(bridge, "bridge");
        hudBound = true;
    }

    public ChallengeHudBridge hud() {
        return hud;
    }

    @Override
    public void close() {
        cancelTasks();
        try {
            hud.close();
        } catch (Exception exception) {
            logHookWarning("Challenge HUD cleanup failed: " + exception.getMessage());
        }
        hud = ChallengeHudBridge.unavailable();
        hudBound = false;
    }
}
