package fr.nivcoo.challenges.actions;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.utilsz.redis.RedisAction;
import fr.nivcoo.utilsz.redis.RedisSerializable;

@RedisAction("challenge_start")
public record ChallengeStartAction(
        Challenge challenge,
        int timeout,
        int countdown,
        long timestamp
) implements RedisSerializable {

    @Override
    public void execute() {
        Challenges.get().getChallengesManager().startCountdownFromRedis(challenge, timeout, countdown, timestamp, false);
    }
}
