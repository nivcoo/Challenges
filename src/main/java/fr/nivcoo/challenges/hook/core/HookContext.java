package fr.nivcoo.challenges.hook.core;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.config.MainConfig;
import fr.nivcoo.challenges.service.tracking.ChallengeTrackingService;
import fr.nivcoo.utilsz.platform.bukkit.hook.BukkitHookContext;

public final class HookContext extends BukkitHookContext {
    private final Challenges plugin;

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
}
