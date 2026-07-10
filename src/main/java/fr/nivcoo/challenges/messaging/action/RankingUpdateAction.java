package fr.nivcoo.challenges.messaging.action;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.messaging.MessageActionName;
import fr.nivcoo.utilsz.core.messaging.BusAction;
import fr.nivcoo.utilsz.core.messaging.BusMessage;

import java.util.UUID;

@BusAction(MessageActionName.RANKING_UPDATE)
public record RankingUpdateAction(UUID uuid, int count) implements BusMessage {
    @Override
    public void execute() {
        Challenges.get().getCacheManager().updateRankingFromBus(uuid, count);
    }
}
