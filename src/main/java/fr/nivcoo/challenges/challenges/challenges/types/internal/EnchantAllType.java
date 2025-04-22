package fr.nivcoo.challenges.challenges.challenges.types.internal;

import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.challenges.ChallengeType;
import fr.nivcoo.challenges.challenges.challenges.Types;
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

public class EnchantAllType extends ChallengeType implements Listener {

    public EnchantAllType() {
        type = Types.ENCHANT_ALL;
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onEnchantItemEvent(EnchantItemEvent e) {
        if (shouldIgnore())
            return;
        Challenge selectedChallenge = getSelectedChallenge();

        Player p = e.getEnchanter();

        ItemStack is = e.getItem();
        boolean allow = selectedChallenge.isInMaterialsRequirement(is.getType(), is.getData().getData());
        if (allow)
            addScoreToPlayer(p);

    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (shouldIgnore())
            return;
        Challenge selectedChallenge = getSelectedChallenge();
        HumanEntity ent = e.getWhoClicked();
        if (ent instanceof Player) {
            Player p = (Player) ent;
            Inventory inv = e.getInventory();
            if (inv instanceof AnvilInventory) {
                AnvilInventory anvil = (AnvilInventory) inv;
                InventoryView view = e.getView();
                int rawSlot = e.getRawSlot();
                if (rawSlot == view.convertSlot(rawSlot) && rawSlot == 2) {

                    ItemStack[] items = anvil.getContents();
                    ItemStack item1 = items[0];
                    ItemStack item2 = items[1];
                    if (item1 != null && item2 != null && !item1.getType().equals(Material.AIR)
                            && !item2.getType().equals(Material.AIR)) {

                        ItemStack item3 = e.getCurrentItem();
                        if (item3 != null) {
                            ItemMeta meta = item3.getItemMeta();
                            if (meta instanceof Repairable repairable) {
                                int repairCost = repairable.getRepairCost();
                                if (p.getLevel() >= repairCost) {
                                    if(item3.getData() == null)
                                        return;
                                    boolean allow = selectedChallenge.isInMaterialsRequirement(item3.getType(),
                                            item3.getData().getData());
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
