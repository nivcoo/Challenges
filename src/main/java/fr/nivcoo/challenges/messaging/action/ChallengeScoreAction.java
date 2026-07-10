package fr.nivcoo.challenges.messaging.action;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.messaging.MessageActionName;
import fr.nivcoo.utilsz.core.messaging.BusAction;
import fr.nivcoo.utilsz.core.messaging.BusMessage;

import java.util.UUID;

@BusAction(value = MessageActionName.CHALLENGE_SCORE, runOnMainThread = true)
public record ChallengeScoreAction(UUID uuid, int score) implements BusMessage {
    @Override
    public void execute() {
        if (Challenges.get().getChallengesManager().isChallengeStarted()) {
            Challenges.get().getChallengesManager().setRemoteScore(uuid, score);
        }
    }
}
