package fr.nivcoo.challenges.challenges.challenges.types.internal;

import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.ChallengeType;
import fr.nivcoo.challenges.challenges.challenges.Types;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.metadata.FixedMetadataValue;

public class BlockPlaceType extends ChallengeType implements Listener {

    public BlockPlaceType() {
        type = Types.BLOCK_PLACE;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlaceEvent(BlockPlaceEvent e) {

        Block newBlock = e.getBlock();
        newBlock.setMetadata(blacklistMeta, new FixedMetadataValue(challenges, true));
        if (!checkRequirements())
            return;
        Challenge selectedChallenge = getSeletedChallenge();

        Player p = e.getPlayer();

        Material oldMaterial = e.getBlockReplacedState().getType();
        Material newMaterial = newBlock.getType();

        boolean allow = selectedChallenge.isInMaterialsRequirement(newMaterial, newBlock.getData());
        if (allow && !oldMaterial.equals(newMaterial))
            addScoreToPlayer(p, newBlock.getLocation());

    }

}
