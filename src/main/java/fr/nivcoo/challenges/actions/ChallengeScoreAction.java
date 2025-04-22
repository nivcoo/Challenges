package fr.nivcoo.challenges.actions;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.utilsz.redis.RedisAction;
import fr.nivcoo.utilsz.redis.RedisSerializable;

import java.util.UUID;

@RedisAction("challenge_score")
public record ChallengeScoreAction(UUID uuid, int score) implements RedisSerializable {
    @Override
    public void execute() {
        if (Challenges.get().getChallengesManager().isChallengeStarted()) {
            Challenges.get().getChallengesManager().setRemoteScore(uuid, score);
        }
    }
}
