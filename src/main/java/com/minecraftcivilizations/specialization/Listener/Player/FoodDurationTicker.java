package com.minecraftcivilizations.specialization.Listener.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.CustomItem.CustomItem;
import com.minecraftcivilizations.specialization.Specialization;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static net.minecraft.server.MinecraftServer.currentTick;

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
                if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
                ItemStack[] contents = player.getInventory().getContents();
                for (int index = 0; index < contents.length; index++) {
                    if (contents[index] == null) continue;
                    player.getInventory().setItem(index, updateExpirationLore(contents[index]));
                }
            }
        }, 0L, 10L);
    }

    private int getCurrentHour() {
        World world = Bukkit.getWorlds().getFirst();
        return (int) (world.getFullTime() / 1000);
    }
    private int getSnappedCreationTime() {
        int currentHour = getCurrentHour();
        return (currentHour / 6) * 6;
    }
    private final NamespacedKey CREATED_AT_KEY = new NamespacedKey(Specialization.getInstance(), "created_at");
    private final NamespacedKey EXPIRATION_KEY = new NamespacedKey(Specialization.getInstance(), "expiration");
    private boolean isValid(ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }


    public void registerPacketListener() {
        ProtocolManager manager = ProtocolLibrary.getProtocolManager();

        manager.addPacketListener(new PacketAdapter(Specialization.getInstance(), ListenerPriority.LOW,
                PacketType.Play.Server.SET_SLOT, PacketType.Play.Server.WINDOW_ITEMS) {

            @Override
            public void onPacketSending(PacketEvent event) {
                if (event.getPlayer().getGameMode() == org.bukkit.GameMode.CREATIVE) return;
                PacketContainer packet = event.getPacket();

                if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
                    ItemStack item = packet.getItemModifier().read(0);
                    if (isValid(item)) {
                        ItemStack clone = item.clone();
                        packet.getItemModifier().write(0,  updateExpirationLore(clone));
                    }
                } else {
                    List<ItemStack> items = packet.getItemListModifier().read(0);
                    for (int i = 0; i < items.size(); i++) {
                        ItemStack item = items.get(i);
                        if (isValid(item)) {
                            ItemStack clone = item.clone();
                            items.set(i, updateExpirationLore(clone));
                        }
                    }
                    packet.getItemListModifier().write(0, items);
                }
            }
        });
    }
    private ItemStack updateExpirationLore(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || item.getType() == Material.AIR) return item;
        String id = getItemId(item);
        if (!SpecializationConfig.getFoodExpirationConfig().getConfig().hasPath(id)) return item;
        int expiration = SpecializationConfig.getFoodExpirationConfig().getInteger(id);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(CREATED_AT_KEY)) {
            Specialization.logger.info("3");
            pdc.set(CREATED_AT_KEY, PersistentDataType.INTEGER, getSnappedCreationTime());
            if (meta instanceof Damageable damageable) {
                damageable.setMaxDamage(100);
            }
        }
        int now = getCurrentHour();
        int creation = pdc.getOrDefault(CREATED_AT_KEY, PersistentDataType.INTEGER, getSnappedCreationTime());

        int spoilsIn = creation+expiration - now;
        int days = spoilsIn/24;
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
            if (days > 1) {
                lore.add(mm.deserialize("<gray>Expires in: <yellow>" + days + " days"));
            } else {
                lore.add(mm.deserialize("<gray>Expires in: <yellow>" + spoilsIn + "h"));
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}

