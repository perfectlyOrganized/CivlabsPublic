package com.minecraftcivilizations.specialization.CustomItem;

import com.minecraftcivilizations.specialization.Specialization;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 *
 * @author  alectriciti, jfrogy
 */
public class DefineCustomItems implements Listener {
    public Bandage bandage;
    public DefineCustomItems(Specialization plugin) {
        this.bandage = new Bandage(1301, "Bandage");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }


    // intercept the take from the result slot and replace with your modified item
    @EventHandler(priority = EventPriority.HIGHEST) //Highest will run AFTER the Xp is given
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getSlotType() != InventoryType.SlotType.RESULT) return;
        if (event.isCancelled()) return;
        if (event.getResult() != Event.Result.ALLOW) return;

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;

        ItemStack modified = current.clone();
        ItemMeta meta = modified.getItemMeta();

        if (current.getType().name().contains("_SWORD")) {
            // final tweak applied when player actually takes the item
            masterwork_sword.wrapItemStack(modified, (Player) event.getWhoClicked());
            // setCurrentItem changes what the player receives from the result slot
            event.setCurrentItem(modified);
        }
    }

    CustomItemBase masterwork_sword = new CustomWeapon(1320);


//
//        // === Generic Blessed Food (light regen) ===
//        BlessedFood blessed_food = new BlessedFood(
//                "blessed_food",
//                "§eBlessed Food",
//                Material.COOKED_BEEF,
//                PotionEffectType.REGENERATION,
//                20 * 6, 1, 200,
//                List.of(Material.COOKED_BEEF, Material.SUGAR)
//        );

        // === Hearty Soup (stronger regen, shapeless) ===
        BlessedFood hearty_soup = new BlessedFood(
                1300,
                "§6Hearty Soup",
                Material.BEETROOT_SOUP,
                PotionEffectType.REGENERATION,
                20 * 5, 1, 50,
                List.of(Material.FERMENTED_SPIDER_EYE, Material.BOWL)
        ) {
            @Override
            public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
                meta.setLore(List.of(
                        "§7A warm soup imbued with divine vitality.",
                        "§eRestores health and grants some regeneration."
                ));
                itemStack.setItemMeta(meta);
            }
        };
//
//        // === Radiant Bread (speed boost, shapeless) ===
//        BlessedFood radiant_bread = new BlessedFood(
//                "radiant_bread",
//                "§fRadiant Bread",
//                Material.BREAD,
//                PotionEffectType.SPEED,
//                20 * 15, 1, 300,
//                List.of(Material.WHEAT, Material.HONEY_BOTTLE)
//        ) {
//            @Override
//            public void onCreateItem(ItemStack itemStack, ItemMeta meta, Player player) {
//                meta.setLore(List.of(
//                        "§7A loaf infused with radiant energy.",
//                        "§eGrants a burst of speed when eaten."
//                ));
//                itemStack.setItemMeta(meta);
//            }
//        };
}
