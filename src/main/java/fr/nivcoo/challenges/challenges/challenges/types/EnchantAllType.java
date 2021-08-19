package fr.nivcoo.challenges.challenges.challenges.types;

import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;

import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.ChallengeType;
import fr.nivcoo.challenges.challenges.challenges.Types;

public class EnchantAllType extends ChallengeType implements Listener {

	public EnchantAllType() {
		type = Types.ENCHANT_ALL;
	}

	@SuppressWarnings("deprecation")
	@EventHandler
	public void onEnchantItemEvent(EnchantItemEvent e) {
		if (!checkRequirements())
			return;
		Challenge selectedChallenge = getSeletedChallenge();

		Player p = e.getEnchanter();

		ItemStack is = e.getItem();
		boolean allow = selectedChallenge.isInMaterialsRequirement(is.getType(), (int) is.getData().getData());
		if (allow)
			addScoreToPlayer(p);

	}

	@SuppressWarnings("deprecation")
	@EventHandler
	public void onInventoryClick(InventoryClickEvent e) {
		if (!checkRequirements())
			return;
		Challenge selectedChallenge = getSeletedChallenge();
		HumanEntity ent = e.getWhoClicked();
		if (ent instanceof Player) {
			Player p = (Player) ent;
			Inventory inv = e.getInventory();
			if (inv instanceof AnvilInventory) {
				AnvilInventory anvil = (AnvilInventory) inv;
				InventoryView view = e.getView();
				int rawSlot = e.getRawSlot();
				if (rawSlot == view.convertSlot(rawSlot)) {
					if (rawSlot == 2) {
						ItemStack[] items = anvil.getContents();
						ItemStack item1 = items[0];
						ItemStack item2 = items[1];
						if (item1 != null && item2 != null) {
							Material id1 = item1.getType();
							Material id2 = item2.getType();
							if (!id1.equals(Material.AIR) && !id2.equals(Material.AIR)) {
								ItemStack item3 = e.getCurrentItem();
								if (item3 != null) {
									ItemMeta meta = item3.getItemMeta();
									if (meta instanceof Repairable) {
										Repairable repairable = (Repairable) meta;
										int repairCost = repairable.getRepairCost();
										if (p.getLevel() >= repairCost) {
											boolean allow = selectedChallenge.isInMaterialsRequirement(item3.getType(),
													(int) item3.getData().getData());
											if (allow)
												addScoreToPlayer(p);
										}
									}
								}
							}
						}
					}
				}
			}
		}

	}

}
