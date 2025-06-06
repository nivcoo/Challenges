package fr.nivcoo.challenges.challenges.challenges.types.internal;

import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.ChallengeType;
import fr.nivcoo.challenges.challenges.challenges.Types;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

public class FishingType extends ChallengeType implements Listener {

    public FishingType() {
        type = Types.FISHING;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerFishEvent(PlayerFishEvent e) {
        if (shouldIgnore())
            return;
        Challenge selectedChallenge = getSelectedChallenge();
        Player p = e.getPlayer();

        Entity caught = e.getCaught();
        PlayerFishEvent.State state = e.getState();
        if (state.equals(PlayerFishEvent.State.CAUGHT_FISH) && caught instanceof Item itemCaught) {
            ItemStack is = itemCaught.getItemStack();
            boolean allow = selectedChallenge.isInMaterialsRequirement(is.getType(), is.getData().getData());
            if (allow)
                addScoreToPlayer(p);
        }

    }

}
