package fr.nivcoo.challenges.challenges.challenges.types;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.ChallengeType;
import fr.nivcoo.challenges.challenges.challenges.Types;

public class FishingType extends ChallengeType implements Listener {

	public FishingType() {
		type = Types.FISHING;
	}

	@SuppressWarnings("deprecation")
	@EventHandler
	public void onPlayerFishEvent(PlayerFishEvent e) {
		if (!checkRequirements())
			return;
		Challenge selectedChallenge = getSeletedChallenge();
		Player p = e.getPlayer();

		Entity caught = e.getCaught();
		if (caught instanceof Item) {
			Item itemCaught = (Item) caught;
			ItemStack is = itemCaught.getItemStack();
			boolean allow = selectedChallenge.isInMaterialsRequirement(is.getType(), (int) is.getData().getData());
			if (allow)
				addScoreToPlayer(p);
		}

	}

}
