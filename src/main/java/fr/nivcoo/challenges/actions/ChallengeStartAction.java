package fr.nivcoo.challenges.actions;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.Types;
import fr.nivcoo.utilsz.redis.RedisAction;
import fr.nivcoo.utilsz.redis.RedisSerializable;

import java.util.List;

@RedisAction("challenge_start")
public record ChallengeStartAction(
        String message,
        String challengeType,
        List<String> requirements,
        boolean countPreviousBlocks,
        int timeout,
        int countdown,
        long timestamp
) implements RedisSerializable {

    @Override
    public void execute() {
        Challenge challenge = new Challenge(Types.valueOf(challengeType));
        challenge.setMessage(message);
        challenge.setRequirements(requirements);
        challenge.setCountPreviousBlocks(countPreviousBlocks);

        Challenges.get().getChallengesManager().startCountdownFromRedis(challenge, timeout, countdown, timestamp, false);
    }
}
