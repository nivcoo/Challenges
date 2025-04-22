package fr.nivcoo.challenges.actions;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.utilsz.redis.RedisAction;
import fr.nivcoo.utilsz.redis.RedisSerializable;

@RedisAction("ranking_global_reset")
public record GlobalResetAction() implements RedisSerializable {

    @Override
    public void execute() {
        Challenges.get().getCacheManager().resetAllData(false);
    }
}
