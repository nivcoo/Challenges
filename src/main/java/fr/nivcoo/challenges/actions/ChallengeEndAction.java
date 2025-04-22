package fr.nivcoo.challenges.actions;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.utilsz.redis.RedisAction;
import fr.nivcoo.utilsz.redis.RedisSerializable;

@RedisAction("challenge_end")
public class ChallengeEndAction implements RedisSerializable {
    @Override
    public void execute() {
        Challenges.get().getChallengesManager().finishChallenge();
    }
}
