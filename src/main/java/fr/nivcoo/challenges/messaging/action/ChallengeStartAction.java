package fr.nivcoo.challenges.messaging.action;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.messaging.MessageActionName;
import fr.nivcoo.utilsz.core.messaging.BusAction;
import fr.nivcoo.utilsz.core.messaging.BusMessage;

@BusAction(value = MessageActionName.CHALLENGE_START, runOnMainThread = true)
public record ChallengeStartAction(Challenge challenge, int timeout, int countdown, long timestamp) implements BusMessage {
    @Override
    public void execute() {
        Challenges.get().getChallengesManager().startCountdownFromBus(challenge, timeout, countdown, timestamp, false);
    }
}
