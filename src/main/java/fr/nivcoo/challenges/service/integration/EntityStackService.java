package fr.nivcoo.challenges.service.integration;

import org.bukkit.entity.LivingEntity;

import java.util.function.ToIntFunction;

public final class EntityStackService {
    private ToIntFunction<LivingEntity> resolver = ignored -> 1;

    public int amount(LivingEntity entity) {
        try {
            return Math.max(1, resolver.applyAsInt(entity));
        } catch (Throwable ignored) {
            return 1;
        }
    }

    public void resolver(ToIntFunction<LivingEntity> resolver) {
        this.resolver = resolver == null ? ignored -> 1 : resolver;
    }

    public void reset() {
        resolver = ignored -> 1;
    }
}
