package com.minecraftcivilizations.specialization.Listener.Player.Interactions;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.minecraftcivilizations.specialization.Specialization;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class HeroOfTheVillageListener implements Listener {

    private static final String HOTV_ITEM_ID = "specialization:hero_of_the_village";
    private static final Set<UUID> blockTotemSound = new HashSet<>();

    public static boolean shouldBlockTotemSound(UUID playerId) {
        return blockTotemSound.contains(playerId);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerUseHOTV(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() != Material.TOTEM_OF_UNDYING) return;

        String itemId = getItemId(item);
        if (!HOTV_ITEM_ID.equals(itemId)) return;

        event.setCancelled(true);
        playTotemAnimation(player);

        EquipmentSlot hand = event.getHand();
        if (hand != null) {
            boolean isMainHand = hand == EquipmentSlot.HAND;
            ItemStack handItem = isMainHand
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

            if (handItem.getAmount() > 1) {
                handItem.setAmount(handItem.getAmount() - 1);
            } else {
                if (isMainHand) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    player.getInventory().setItemInOffHand(null);
                }
            }
        }
    }

    private void playTotemAnimation(Player player) {
        blockTotemSound.add(player.getUniqueId());
        player.stopSound(Sound.ITEM_TOTEM_USE);

        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.ENTITY_STATUS);
            packet.getIntegers().write(0, player.getEntityId());
            packet.getBytes().write(0, (byte) 35);
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            Specialization.getInstance().getLogger().warning("Failed to send HOTV animation packet: " + e.getMessage());
        }

        // Stop default totem sound repeatedly
        for (int i = 0; i <= 10; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    player.stopSound(Sound.ITEM_TOTEM_USE);
                }
            }.runTaskLater(Specialization.getInstance(), i);
        }

        // Play custom sound
        new BukkitRunnable() {
            @Override
            public void run() {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
            }
        }.runTaskLater(Specialization.getInstance(), 2L);

        // Cleanup
        new BukkitRunnable() {
            @Override
            public void run() {
                blockTotemSound.remove(player.getUniqueId());
            }
        }.runTaskLater(Specialization.getInstance(), 20L);
    }

    private String getItemId(ItemStack stack) {
        if (stack == null) return null;
        var wrapped = BukkitItemManager.instance().wrap(stack);
        if (wrapped.getCustomItem().isPresent()) {
            return wrapped.getCustomItem().get().id().toString();
        }
        return "minecraft:" + stack.getType().name().toLowerCase();
    }
}
