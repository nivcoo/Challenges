package fr.nivcoo.challenges.challenges.challenges.types.internal;

import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.ChallengeType;
import fr.nivcoo.challenges.challenges.challenges.Types;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;

import java.util.List;

public class BlockPlaceType extends ChallengeType implements Listener {

    public BlockPlaceType() {
        type = Types.BLOCK_PLACE;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlaceEvent(BlockPlaceEvent e) {
        if (shouldIgnore() && !isConflictWithBreak()) return;

        Block block = e.getBlock();
        Player p = e.getPlayer();
        Location loc = block.getLocation();
        Material newMat = block.getType();
        Material oldMat = e.getBlockReplacedState().getType();
        byte data = block.getData();

        block.setMetadata(blacklistMeta, new FixedMetadataValue(challenges, true));

        if (isConflictWithBreak()) {
            if (getSelectedChallenge().isInMaterialsRequirement(newMat, data)) {
                challenges.getChallengesManager().addLocationToBlacklist(loc, p);
                removeScoreToPlayer(p);
            }
            return;
        }

        boolean allow = getSelectedChallenge().isInMaterialsRequirement(newMat, data);
        if (allow && !oldMat.equals(newMat)) {
            addScoreToPlayer(p, loc);
        }
    }

    private boolean isConflictWithBreak() {
        Challenge c = getSelectedChallenge();
        return c != null && c.getChallengeType() == Types.BLOCK_BREAK;
    }



}
