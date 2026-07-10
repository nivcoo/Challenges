package fr.nivcoo.challenges.messaging.action;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.messaging.MessageActionName;
import fr.nivcoo.utilsz.core.messaging.BusAction;
import fr.nivcoo.utilsz.core.messaging.BusMessage;

@BusAction(value = MessageActionName.RANKING_GLOBAL_RESET, runOnMainThread = true)
public record GlobalResetAction() implements BusMessage {
    @Override
    public void execute() {
        Challenges.get().getCacheManager().resetAllData(false);
    }
}
