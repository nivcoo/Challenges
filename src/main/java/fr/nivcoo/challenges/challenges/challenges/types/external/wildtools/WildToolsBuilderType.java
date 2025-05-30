package fr.nivcoo.challenges.challenges.challenges.types.external.wildtools;

import com.bgsoftware.wildtools.api.events.BuilderWandUseEvent;
import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.ChallengeType;
import fr.nivcoo.challenges.challenges.challenges.Types;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.List;

public class WildToolsBuilderType extends ChallengeType implements Listener {

    public WildToolsBuilderType() {
        type = Types.BLOCK_BREAK;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onBlockBreakEvent(BuilderWandUseEvent e) {
        List<Location> locs = e.getBlocks();
        for (Location loc : locs)
            loc.getBlock().setMetadata(blacklistMeta, new FixedMetadataValue(challenges, true));
        if (shouldIgnore())
            return;
        Player p = e.getPlayer();
        Block b = p.getTargetBlock(null, 10);
        Challenge selectedChallenge = getSelectedChallenge();

        boolean allow = selectedChallenge.isInMaterialsRequirement(b.getType(), b.getData());
        if (allow) {
            for (Location loc : locs)
                removeScoreToPlayer(p, loc);
        }
    }
}
