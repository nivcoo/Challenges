package fr.nivcoo.challenges.actions;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.utilsz.redis.RedisAction;
import fr.nivcoo.utilsz.redis.RedisSerializable;

import java.util.UUID;

@RedisAction("player_name_update")
public record PlayerNameUpdateAction(UUID uuid, String name) implements RedisSerializable {
    @Override
    public void execute() {
        if (name == null || name.isBlank()) return;
        Challenges ch = Challenges.get();
        ch.getCacheManager().cacheNameRemote(uuid, name);
    }
}
