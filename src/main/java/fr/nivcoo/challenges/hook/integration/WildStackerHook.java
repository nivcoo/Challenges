package fr.nivcoo.challenges.hook.integration;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import fr.nivcoo.challenges.hook.core.HookContext;
import fr.nivcoo.utilsz.platform.bukkit.hook.BukkitHook;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;

public final class WildStackerHook implements BukkitHook<HookContext> {
    public WildStackerHook(HookContext context) {
    }

    @Override
    public String id() {
        return "WildStacker";
    }

    @Override
    public String requiredPlugin() {
        return "WildStacker";
    }

    @Override
    public boolean enabled(HookContext context) {
        return context.cfg().hooks.wildStacker.enabled;
    }

    @Override
    public void load(HookContext context) {
        context.entityStacks().resolver(WildStackerHook::resolveStackAmount);
    }

    private static int resolveStackAmount(LivingEntity entity) {
        if (entity == null || entity.getType() == EntityType.ARMOR_STAND) {
            return 1;
        }
        return Math.max(1, WildStackerAPI.getEntityAmount(entity));
    }
}
