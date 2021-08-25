package fr.nivcoo.challenges.challenges.challenges.types.internal;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.ChallengeType;
import fr.nivcoo.challenges.challenges.challenges.Types;

public class BlockBreakType extends ChallengeType implements Listener {

	public BlockBreakType() {
		type = Types.BLOCK_BREAK;
	}

	@SuppressWarnings("deprecation")
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockBreakEvent(BlockBreakEvent e) {
		if (e.isCancelled() || !checkRequirements())
			return;
		Challenge selectedChallenge = getSeletedChallenge();
		Block b = e.getBlock();
		Player p = e.getPlayer();

		boolean allow = selectedChallenge.isInMaterialsRequirement(b.getType(), (int) b.getData());
		if (allow)
			addScoreToPlayer(p, b.getLocation());

	}
}