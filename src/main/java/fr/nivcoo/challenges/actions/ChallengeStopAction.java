package fr.nivcoo.challenges.actions;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.utilsz.redis.RedisAction;
import fr.nivcoo.utilsz.redis.RedisSerializable;

@RedisAction("challenge_stop")
public class ChallengeStopAction implements RedisSerializable {
    @Override
    public void execute() {
        Challenges.get().getChallengesManager().stopCurrentChallenge();
    }
}
