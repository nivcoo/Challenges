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
import org.bukkit.metadata.MetadataValue;

import java.util.List;

public class BlockBreakType extends ChallengeType implements Listener {

    public BlockBreakType() {
        type = Types.BLOCK_BREAK;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreakEvent(BlockBreakEvent e) {
        if (shouldIgnore() && !isConflictWithPlace()) return;

        Player p = e.getPlayer();
        Block b = e.getBlock();
        Material type = b.getType();
        byte data = b.getData();
        Location loc = b.getLocation();

        if (isConflictWithPlace()) {
            challenges.getChallengesManager().addLocationToBlacklist(loc, p);
            removeScoreToPlayer(p);
            return;
        }

        List<MetadataValue> metas = b.getMetadata(blacklistMeta);
        boolean isBlacklisted = !metas.isEmpty() && metas.getFirst().asBoolean();
        b.removeMetadata(blacklistMeta, challenges);

        if (!getSelectedChallenge().isCountPreviousBlocks() && isBlacklisted)
            return;

        if (getSelectedChallenge().isInMaterialsRequirement(type, data))
            addScoreToPlayer(p, loc);
    }

    private boolean isConflictWithPlace() {
        Challenge c = getSelectedChallenge();
        return c != null && c.getChallengeType() == Types.BLOCK_PLACE;
    }

}
