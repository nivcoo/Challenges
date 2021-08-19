package fr.nivcoo.challenges.challenges.challenges.types;

import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.ChallengeType;
import fr.nivcoo.challenges.challenges.challenges.Types;

public class EntityDeathType extends ChallengeType implements Listener {

	public EntityDeathType() {
		type = Types.ENTITY_DEATH;
	}

	@EventHandler
	public void onEntityDeathEvent(EntityDamageByEntityEvent e) {
		if (!checkRequirements())
			return;

		Entity entity = e.getEntity();

		if (entity instanceof LivingEntity
				&& (entity instanceof Monster || entity instanceof Animals || entity instanceof Slime) && e.getDamager() instanceof Player) {
			LivingEntity killed = (LivingEntity) e.getEntity();
			Player killer = (Player) e.getDamager();
			if (e.getFinalDamage() >= killed.getHealth()) {
				Challenge selectedChallenge = getSeletedChallenge();
				boolean allow = selectedChallenge.isInRequirements(killed.getType().name());
				if (allow)
					addScoreToPlayer(killer);
			}

		}

	}

}
