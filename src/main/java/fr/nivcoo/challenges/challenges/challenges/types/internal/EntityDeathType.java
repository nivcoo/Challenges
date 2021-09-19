package fr.nivcoo.challenges.challenges.challenges.types.internal;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.ChallengeType;
import fr.nivcoo.challenges.challenges.challenges.Types;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class EntityDeathType extends ChallengeType implements Listener {

    public EntityDeathType() {
        type = Types.ENTITY_DEATH;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!checkRequirements())
            return;
        LivingEntity entity = event.getEntity();
        Player p = entity.getKiller();
        if (!(p instanceof Player) || entity instanceof Player) {
            return;
        }
        String stringEntity = entity.getType().toString().replace("Craft", "");
        int entityAmount = 1;
        if (event.getEntity().getType() != EntityType.ARMOR_STAND
                && Bukkit.getPluginManager().isPluginEnabled("WildStacker")) {
            entityAmount = WildStackerAPI.getEntityAmount(event.getEntity());
        }

        Challenge selectedChallenge = getSeletedChallenge();
        boolean allow = selectedChallenge.isInRequirements(stringEntity);
        if (allow)
            addScoreToPlayer(p, entityAmount);

    }

}
