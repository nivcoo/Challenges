package fr.nivcoo.challenges.hook.integration;

import fr.nivcoo.challenges.hook.core.HookContext;
import fr.nivcoo.edenquests.api.EdenQuestsAPI;
import fr.nivcoo.utilsz.platform.bukkit.hook.BukkitHook;

public final class EdenQuestsHook implements BukkitHook<HookContext> {
    public EdenQuestsHook(HookContext context) {
    }

    @Override
    public String id() {
        return "EdenQuests";
    }

    @Override
    public String requiredPlugin() {
        return "EdenQuests";
    }

    @Override
    public void load(HookContext context) {
        context.bindTrackingService(new EdenQuestsChallengeTrackingService(
                context.plugin(), EdenQuestsAPI.get()));
    }
}
