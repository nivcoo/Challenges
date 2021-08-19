package fr.nivcoo.challenges.challenges.challenges.types;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.ChallengeType;
import fr.nivcoo.challenges.challenges.challenges.Types;

public class ConsumeType extends ChallengeType implements Listener {

	public ConsumeType() {
		type = Types.CONSUME;
	}

	@SuppressWarnings("deprecation")
	@EventHandler
	public void onPlayerItemConsumeEvent(PlayerItemConsumeEvent e) {
		if (!checkRequirements())
			return;
		Challenge selectedChallenge = getSeletedChallenge();
		Player p = e.getPlayer();

		ItemStack is = e.getItem();
		if (is == null)
			return;

		Material type = is.getType();

		boolean allow = selectedChallenge.isInMaterialsRequirement(type, (int) is.getData().getData())
				&& type.isEdible();
		if (allow)
			addScoreToPlayer(p);

	}

}
