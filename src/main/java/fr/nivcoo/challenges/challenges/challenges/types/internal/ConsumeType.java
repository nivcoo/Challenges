package fr.nivcoo.challenges.challenges.challenges.types.internal;

import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.ChallengeType;
import fr.nivcoo.challenges.challenges.challenges.Types;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

public class ConsumeType extends ChallengeType implements Listener {

    public ConsumeType() {
        type = Types.CONSUME;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlayerItemConsumeEvent(PlayerItemConsumeEvent e) {
        if (shouldIgnore())
            return;
        Challenge selectedChallenge = getSelectedChallenge();
        Player p = e.getPlayer();

        ItemStack is = e.getItem();
        if (is.getData() == null)
            return;

        Material type = is.getType();

        boolean allow = selectedChallenge.isInMaterialsRequirement(type, is.getData().getData())
                && type.isEdible();
        if (allow)
            addScoreToPlayer(p);

    }

}
