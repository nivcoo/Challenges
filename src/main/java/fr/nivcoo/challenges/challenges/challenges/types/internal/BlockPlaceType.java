package fr.nivcoo.challenges.challenges.challenges.types.internal;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockPlaceEvent(BlockPlaceEvent e) {
		if (e.isCancelled() || !checkRequirements())
			return;
		Challenge selectedChallenge = getSeletedChallenge();
		Block newBlock = e.getBlock();
		Player p = e.getPlayer();

		Material oldMaterial = e.getBlockReplacedState().getType();
		Material newMaterial = newBlock.getType();

		boolean allow = selectedChallenge.isInMaterialsRequirement(newMaterial, (int) newBlock.getData());
		if (allow && !oldMaterial.equals(newMaterial))
			addScoreToPlayer(p, newBlock.getLocation());

	}

}
