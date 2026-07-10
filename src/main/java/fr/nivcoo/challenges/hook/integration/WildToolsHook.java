package fr.nivcoo.challenges.hook.integration;

import com.bgsoftware.wildtools.api.events.BuilderWandUseEvent;
import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.Types;
import fr.nivcoo.challenges.hook.core.HookContext;
import fr.nivcoo.utilsz.platform.bukkit.hook.ListenerBukkitHook;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.metadata.FixedMetadataValue;

public final class WildToolsHook extends ListenerBukkitHook<HookContext> {
    private static final String BLACKLIST_META = "challenges_blacklist";

    private final Challenges plugin;

    public WildToolsHook(HookContext context) {
        this.plugin = context.plugin();
    }

    @Override
    public String id() {
        return "WildTools";
    }

    @Override
    public String requiredPlugin() {
        return "WildTools";
    }

    @Override
    public boolean enabled(HookContext context) {
        return context.cfg().hooks.wildTools.enabled;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onBuilderWandUse(BuilderWandUseEvent event) {
        for (Location location : event.getBlocks()) {
            location.getBlock().setMetadata(BLACKLIST_META, new FixedMetadataValue(plugin, true));
        }

        Challenge selectedChallenge = plugin.getChallengesManager().getSelectedChallenge();
        if (selectedChallenge == null || selectedChallenge.challengeType() != Types.BLOCK_BREAK) {
            return;
        }

        Player player = event.getPlayer();
        Block target = player.getTargetBlock(null, 10);
        if (!selectedChallenge.isInMaterialsRequirement(target.getType(), target.getData())) {
            return;
        }

        for (Location location : event.getBlocks()) {
            plugin.getChallengesManager().editScoreToPlayer(Types.BLOCK_BREAK, player, location, true, 1);
        }
    }
}
