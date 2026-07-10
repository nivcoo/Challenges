package fr.nivcoo.challenges.hook.integration;

import fr.nivcoo.challenges.hook.core.HookContext;
import fr.nivcoo.utilsz.platform.bukkit.hook.BukkitHook;

public final class PlaceholderApiHook implements BukkitHook<HookContext> {
    public PlaceholderApiHook(HookContext context) {
    }

    @Override
    public String id() {
        return "PlaceholderAPI";
    }

    @Override
    public String requiredPlugin() {
        return "PlaceholderAPI";
    }

    @Override
    public boolean enabled(HookContext context) {
        return context.cfg().hooks.placeholderApi.enabled;
    }

    @Override
    public void load(HookContext context) {
        context.plugin().registerPlaceholders();
    }
}
