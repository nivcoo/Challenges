package fr.nivcoo.challenges.actions;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.utilsz.redis.RedisAction;
import fr.nivcoo.utilsz.redis.RedisSerializable;

import java.util.UUID;

@RedisAction("ranking_update")
public record RankingUpdateAction(UUID uuid, int count) implements RedisSerializable {

    public void execute() {
        Challenges.get().getCacheManager().updateFromRedis(uuid, count);
    }
}
