package fr.nivcoo.challenges.challenges.challenges.types;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.ChallengeType;
import fr.nivcoo.challenges.challenges.challenges.Types;

public class BlockPlaceType extends ChallengeType implements Listener {

	public BlockPlaceType() {
		type = Types.BLOCK_PLACE;
	}

	@SuppressWarnings("deprecation")
	@EventHandler
	public void onBlockPlaceEvent(BlockPlaceEvent e) {
		if (!checkRequirements())
			return;
		Challenge selectedChallenge = getSeletedChallenge();
		Block newBlock = e.getBlock();
		Player p = e.getPlayer();

		Material oldMaterial = e.getBlockReplacedState().getType();
		Material newMaterial = newBlock.getType();

		if (oldMaterial.equals(newMaterial))
			return;

		boolean allow = selectedChallenge.isInMaterialsRequirement(newMaterial, (int) newBlock.getData());
		if (allow)
			addScoreToPlayer(p, newBlock.getLocation());

	}

}
