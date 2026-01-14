package com.minecraftcivilizations.specialization.Listener.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Listener.Player.Interactions.FoodInteractionListener;
import com.minecraftcivilizations.specialization.Specialization;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class FoodDurationTicker implements Listener {
    private String getItemId(ItemStack item) {
        //if (CustomItem.isCustomItem(item)) {
            //return event.getRecipe().toString().toUpperCase(Locale.ROOT);
        //} else
        if (CraftEngineItems.isCustomItem(item)) {
            return CraftEngineItems.getCustomItemId(item).toString().toUpperCase(Locale.ROOT).replace(":","_");
        } else {
            return item.getType().key().value().toUpperCase(Locale.ROOT);
        }
    }
    public FoodDurationTicker() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(Specialization.getInstance(), () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) continue;
                ItemStack[] contents = player.getInventory().getContents();
                for (int index = 0; index < contents.length; index++) {
                    if (contents[index] == null || FoodInteractionListener.isBlessedFood(contents[index])) continue;
                    player.getInventory().setItem(index, updateExpirationLore(contents[index]));
                }
            }
        }, 0L, 10L);
    }

    private int getCurrentTime() {
        World world = Bukkit.getWorlds().getFirst();
        return (int) (world.getFullTime());
    }
    private final NamespacedKey CREATED_AT_KEY = new NamespacedKey(Specialization.getInstance(), "created_at");
    private final NamespacedKey EXPIRATION_KEY = new NamespacedKey(Specialization.getInstance(), "expiration");
    private boolean isValid(ItemStack item) {
        return item != null && item.getType() != Material.AIR && !FoodInteractionListener.isBlessedFood(item);
    }


    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;

        Inventory inventory = event.getInventory();

        // Check if it's a player's own inventory or a container
        if (inventory.getType() == InventoryType.PLAYER ||
                inventory.getType() == InventoryType.CHEST ||
                inventory.getType() == InventoryType.ENDER_CHEST ||
                inventory.getType() == InventoryType.SHULKER_BOX ||
                inventory.getType() == InventoryType.BARREL ||
                inventory.getType() == InventoryType.DISPENSER ||
                inventory.getType() == InventoryType.DROPPER ||
                inventory.getType() == InventoryType.HOPPER ||
                inventory.getType() == InventoryType.BREWING) {

            // Update all items in the inventory
            for (int i = 0; i < inventory.getSize(); i++) {
                ItemStack item = inventory.getItem(i);
                if (item != null && isValid(item)) {
                    ItemStack updatedItem = updateExpirationLore(item.clone());
                    inventory.setItem(i, updatedItem);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();
        ClickType clickType = event.getClick();

        if (!isAir(cursor) && !isAir(clicked) && event.getAction() == InventoryAction.SWAP_WITH_CURSOR && Objects.equals(getItemId(cursor), getItemId(clicked))) {
            InventoryType.SlotType slotType = event.getSlotType();
            int slot = event.getSlot();

            ItemMeta metaA = cursor.getItemMeta();
            ItemMeta metaB = clicked.getItemMeta();

            if (metaA == null || metaB == null) return;

            PersistentDataContainer pdcA = metaA.getPersistentDataContainer();
            PersistentDataContainer pdcB = metaB.getPersistentDataContainer();

            Integer timeA = pdcA.get(CREATED_AT_KEY, PersistentDataType.INTEGER);
            Integer timeB = pdcB.get(CREATED_AT_KEY, PersistentDataType.INTEGER);

            if (timeA == null || timeB == null) return;


            int weightA = cursor.getAmount();
            int weightB = clicked.getAmount();
            int totalWeight = weightA + weightB;


            double averageTime = (timeA * weightA + timeB * weightB) / (double) totalWeight;
            int roundedAverage = (int) Math.round(averageTime);
            pdcA.set(CREATED_AT_KEY, PersistentDataType.INTEGER, roundedAverage);
            cursor.setAmount(totalWeight);
            cursor.setItemMeta(metaA);
            event.setCurrentItem(null);
        }
    }

    private boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }
    private ItemStack updateExpirationLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || item.getType() == Material.AIR) return item;
        String id = getItemId(item);
        if (!SpecializationConfig.getFoodExpirationConfig().getConfig().hasPath(id)) return item;
        int expiration = SpecializationConfig.getFoodExpirationConfig().getInteger(id)*20*50;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(CREATED_AT_KEY)) {
            pdc.set(CREATED_AT_KEY, PersistentDataType.INTEGER, getCurrentTime());
            if (meta instanceof Damageable damageable) {
                damageable.setMaxDamage(100);
            }
        }
        int now = getCurrentTime();
        int creation = pdc.getOrDefault(CREATED_AT_KEY, PersistentDataType.INTEGER, getCurrentTime());

        int spoilsIn = creation+expiration - now;
        int hours = spoilsIn/20/60/60;
        int minutes = (spoilsIn-hours*20*60*60)/20/60;
        boolean isSpoiled = spoilsIn < 0;

        List<Component> lore = new ArrayList<>();
        MiniMessage mm = MiniMessage.miniMessage();
        if (isSpoiled) {
            ItemStack itemStack = CraftEngineItems.byId(new Key(Specialization.getInstance().namespace(), "spoiled_food")).buildItemStack(item.getAmount());
            return itemStack;
        } else {
            if (meta instanceof Damageable damageable) {
                damageable.setDamage(100*(now-creation)/expiration);
            }
            if (hours >= 1) {
                lore.add(mm.deserialize("<gray>Expires in: <yellow>" + hours + "h " + minutes + "m"));
            } else {
                lore.add(mm.deserialize("<gray>Expires in: <yellow>" + minutes + " minutes"));
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}

