package fr.nivcoo.challenges.challenges.challenges.types.internal;

import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.ChallengeType;
import fr.nivcoo.challenges.challenges.challenges.Types;
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
        Block b = e.getBlock();
        List<MetadataValue> metas = b.getMetadata(blacklistMeta);
        boolean isBlacklisted = !metas.isEmpty() && metas.get(0).asBoolean();
        b.removeMetadata(blacklistMeta, challenges);
        if (shouldIgnore())
            return;
        Challenge selectedChallenge = getSeletedChallenge();

        if (!selectedChallenge.countPreviousBlocks() && isBlacklisted)
            return;

        Player p = e.getPlayer();


        boolean allow = selectedChallenge.isInMaterialsRequirement(b.getType(), b.getData());
        if (allow)
            addScoreToPlayer(p, b.getLocation());

    }
}
