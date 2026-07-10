package fr.nivcoo.challenges.messaging.action;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.messaging.MessageActionName;
import fr.nivcoo.utilsz.core.messaging.BusAction;
import fr.nivcoo.utilsz.core.messaging.BusMessage;

@BusAction(value = MessageActionName.CHALLENGE_STOP, runOnMainThread = true)
public record ChallengeStopAction() implements BusMessage {
    @Override
    public void execute() {
        Challenges.get().getChallengesManager().stopCurrentChallenge();
    }
}
