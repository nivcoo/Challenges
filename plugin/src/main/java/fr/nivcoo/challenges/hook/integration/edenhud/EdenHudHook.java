package fr.nivcoo.challenges.hook.integration.edenhud;

import fr.nivcoo.challenges.hook.core.HookContext;
import fr.nivcoo.utilsz.platform.bukkit.hook.BukkitHook;

public final class EdenHudHook implements BukkitHook<HookContext> {
    public EdenHudHook(HookContext context) {
    }

    @Override
    public String id() {
        return "EdenHUD";
    }

    @Override
    public String requiredPlugin() {
        return "EdenHUD";
    }

    @Override
    public boolean enabled(HookContext context) {
        return context.cfg().hud.enabled;
    }

    @Override
    public void load(HookContext context) {
        context.bindHud(EdenHudIntegration.create(context.plugin()));
    }
}
