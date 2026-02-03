package com.minecraftcivilizations.specialization.Cooking;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.Listener.Player.Interactions.HeroOfTheVillageListener;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Listener that applies cooking effects when a player consumes cooked food.
 */
public class CookingConsumeListener implements Listener {

    // Track players who should have totem sound blocked
    private static final Set<UUID> blockTotemSound = new HashSet<>();

    // Track players currently in HOTV animation (to protect against inventory exploits)
    private static final Set<UUID> playersInAnimation = new HashSet<>();

    // The custom ID that marks our HOTV totem
    private static final String HOTV_TOTEM_ID = "specialization:hero_of_the_village";

    private static final PotionEffectType[] HARMFUL_EFFECTS = {
        PotionEffectType.POISON,
        PotionEffectType.WITHER,
        PotionEffectType.SLOWNESS,
        PotionEffectType.MINING_FATIGUE,
        PotionEffectType.INSTANT_DAMAGE,
        PotionEffectType.NAUSEA,
        PotionEffectType.BLINDNESS,
        PotionEffectType.HUNGER,
        PotionEffectType.WEAKNESS,
        PotionEffectType.LEVITATION,
        PotionEffectType.UNLUCK,
        PotionEffectType.DARKNESS
    };

    /**
     * Static initializer to register the packet listener for blocking totem sounds.
     */
    public static void registerPacketListener() {
        // Listen to NAMED_SOUND_EFFECT to catch totem sounds
        ProtocolLibrary.getProtocolManager().addPacketListener(
            new PacketAdapter(Specialization.getInstance(), ListenerPriority.HIGHEST,
                PacketType.Play.Server.NAMED_SOUND_EFFECT) {
                @Override
                public void onPacketSending(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (player == null) return;

                    // Check both block lists (from CookingConsumeListener and HeroOfTheVillageListener)
                    boolean shouldBlock = blockTotemSound.contains(player.getUniqueId()) ||
                        HeroOfTheVillageListener.shouldBlockTotemSound(player.getUniqueId());

                    if (!shouldBlock) return;

                    // Check if this is the totem sound
                    try {
                        String soundName = "";
                        PacketContainer packet = event.getPacket();

                        // Try to get sound from packet
                        try {
                            var sounds = packet.getSoundEffects();
                            if (sounds.size() > 0) {
                                var sound = sounds.read(0);
                                if (sound != null) soundName = sound.toString().toLowerCase();
                            }
                        } catch (Exception ignored) {}

                        // Fallback: check entire packet string
                        if (soundName.isEmpty()) soundName = packet.toString().toLowerCase();

                        // Block if it's a totem sound
                        if (soundName.contains("totem") || soundName.contains("item.totem")) {
                            event.setCancelled(true);
                        }
                    } catch (Exception e) {
                        try {
                            if (event.getPacket().toString().toLowerCase().contains("totem")) {
                                event.setCancelled(true);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        );
    }

    /**
     * Checks if an ItemStack is the HOTV totem (either CraftEngine or vanilla fallback).
     */
    private boolean isHOTVTotem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        // Check if it's a CraftEngine HOTV totem
        try {
            var wrapped = BukkitItemManager.instance().wrap(item);
            if (wrapped != null && wrapped.getCustomItem().isPresent()) {
                String id = wrapped.getCustomItem().get().id().toString();
                if (HOTV_TOTEM_ID.equals(id)) return true;
            }
        } catch (Exception ignored) {}

        // Check if it's a vanilla totem that was marked by us (via custom model data or similar)
        if (item.getType() == Material.TOTEM_OF_UNDYING) {
            var meta = item.getItemMeta();
            if (meta != null && meta.hasCustomModelData()) return true;
        }

        return false;
    }

    /**
     * Removes all HOTV totems from a player's inventory.
     */
    private void removeHOTVTotemsFromInventory(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (isHOTVTotem(inventory.getItem(i))) inventory.setItem(i, null);
        }
        if (isHOTVTotem(inventory.getItemInOffHand())) inventory.setItemInOffHand(null);
        if (player.getOpenInventory() != null && isHOTVTotem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    /**
     * Periodic task to clean up any HOTV totems that might have appeared in inventories.
     */
    public static void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    // Only check players not in animation (those in animation are handled separately)
                    if (!playersInAnimation.contains(player.getUniqueId())) {
                        cleanupPlayerInventory(player);
                    }
                }
            }
        }.runTaskTimer(Specialization.getInstance(), 20L, 20L); // Every second
    }

    private static void cleanupPlayerInventory(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                try {
                    var wrapped = BukkitItemManager.instance().wrap(item);
                    if (wrapped != null && wrapped.getCustomItem().isPresent()) {
                        if (HOTV_TOTEM_ID.equals(wrapped.getCustomItem().get().id().toString())) {
                            inventory.setItem(i, null);
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // ============ INVENTORY PROTECTION EVENTS ============

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // If player is in animation, block all inventory interactions
        if (playersInAnimation.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // Check if they're trying to interact with HOTV totem
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (isHOTVTotem(current) || isHOTVTotem(cursor)) {
            event.setCancelled(true);
            // Schedule removal
            Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> removeHOTVTotemsFromInventory(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (playersInAnimation.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (isHOTVTotem(event.getOldCursor())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> removeHOTVTotemsFromInventory(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (playersInAnimation.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (isHOTVTotem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getItemDrop().remove();
            Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> removeHOTVTotemsFromInventory(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();

        if (playersInAnimation.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (isHOTVTotem(event.getMainHandItem()) || isHOTVTotem(event.getOffHandItem())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> removeHOTVTotemsFromInventory(player));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;

        Player player = event.getPlayer();
        List<PotionEffect> effects = CookingEffectManager.getStoredEffects(item);
        boolean clearHarmful = CookingEffectManager.shouldClearHarmful(item);
        int bonusExp = CookingEffectManager.getBonusExp(item);

        // Check if item has HOTV from cooking effects and get its level
        int hotvLevelFromCooking = effects.stream()
            .filter(e -> e.getType().equals(PotionEffectType.HERO_OF_THE_VILLAGE))
            .mapToInt(e -> e.getAmplifier() + 1) // Amplifier is 0-indexed, so +1 for level
            .findFirst()
            .orElse(0);

        // Check if CraftEngine item has HOTV in its consume events and get level
        int hotvLevelFromCraftEngine = getHOTVLevelFromCraftEngine(item);

        // Use the higher level between cooking and CraftEngine
        int hotvLevel = Math.max(hotvLevelFromCooking, hotvLevelFromCraftEngine);
        boolean shouldPlayAnimation = hotvLevel > 0;

        // Apply bonus XP if present
        if (bonusExp > 0) {
            player.giveExp(bonusExp);
        }

        // Only process cooking effects if there are any
        if (!effects.isEmpty() || clearHarmful) {
            if (clearHarmful) {
                for (PotionEffectType harmful : HARMFUL_EFFECTS) {
                    if (player.hasPotionEffect(harmful)) {
                        player.removePotionEffect(harmful);
                    }
                }
            }

            for (PotionEffect effect : effects) {
                PotionEffect existing = player.getPotionEffect(effect.getType());
                if (existing != null) {
                    if (effect.getAmplifier() > existing.getAmplifier()) {
                        // New effect is stronger - replace
                        player.removePotionEffect(effect.getType());
                        player.addPotionEffect(effect);
                    } else if (effect.getAmplifier() == existing.getAmplifier()) {
                        // Same amplifier - reset to new duration (don't stack)
                        player.removePotionEffect(effect.getType());
                        player.addPotionEffect(effect);
                    }
                    // If existing amplifier is higher, do nothing (keep stronger effect)
                } else {
                    player.addPotionEffect(effect);
                }
            }
        }

        // Play Hero of the Village totem animation if HOTV effect was applied from any source
        if (shouldPlayAnimation) {
            final int finalHotvLevel = hotvLevel;
            // Wait a bit longer to ensure the food consumption is fully processed server-side
            // This prevents inventory desync issues
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Force sync inventory state before animation
                    player.updateInventory();
                    playHeroOfTheVillageAnimation(player, finalHotvLevel);
                }
            }.runTaskLater(Specialization.getInstance(), 5L);
        }
    }

    /**
     * Gets the HOTV level from a CraftEngine item's lore or effects.
     * Returns 0 if no HOTV effect found.
     */
    private int getHOTVLevelFromCraftEngine(ItemStack item) {
        if (item == null) return 0;

        try {
            var wrapped = BukkitItemManager.instance().wrap(item);
            if (wrapped == null || wrapped.getCustomItem().isEmpty()) return 0;

            var meta = item.getItemMeta();
            if (meta != null && meta.hasLore()) {
                var lore = meta.lore();
                if (lore != null) {
                    for (var line : lore) {
                        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                            .plainText().serialize(line).toLowerCase();
                        if (text.contains("hero") && text.contains("village")) {
                            if (text.contains(" v") || text.contains(" 5")) return 5;
                            if (text.contains(" iv") || text.contains(" 4")) return 4;
                            if (text.contains(" iii") || text.contains(" 3")) return 3;
                            if (text.contains(" ii") || text.contains(" 2")) return 2;
                            return 1;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return 0;
    }

    /**
     * Plays the Hero of the Village totem-style animation for a player.
     * This is done entirely CLIENT-SIDE - we don't actually change the player's inventory.
     * @param player The player to play the animation for
     * @param hotvLevel The HOTV level (1-5) to determine sound pitch
     */
    private void playHeroOfTheVillageAnimation(Player player, int hotvLevel) {
        ItemStack hotvTotem = getHOTVTotemItem();
        UUID playerId = player.getUniqueId();

        // Calculate pitch based on HOTV level
        // HOTV 1 = 2.0, HOTV 2 = 1.8, HOTV 3 = 1.6, HOTV 4 = 1.4, HOTV 5 = 1.2
        float pitch = switch (hotvLevel) {
            case 1 -> 2.0f;
            case 2 -> 1.8f;
            case 3 -> 1.6f;
            case 4 -> 1.4f;
            default -> 1.2f; // Default to lowest pitch for levels > 5
        };

        // Add player to sound block list and animation protection
        blockTotemSound.add(playerId);
        playersInAnimation.add(playerId);

        try {
            // Stop totem sound immediately BEFORE animation
            player.stopSound(Sound.ITEM_TOTEM_USE, SoundCategory.PLAYERS);
            player.stopSound(Sound.ITEM_TOTEM_USE, SoundCategory.MASTER);
            player.stopSound(Sound.ITEM_TOTEM_USE);

            // Send FAKE equipment packet to client showing HOTV totem in offhand
            sendClientEquipmentPacket(player, EnumWrappers.ItemSlot.OFFHAND, hotvTotem);

            new BukkitRunnable() {
                @Override
                public void run() {
                    // Stop sound again right before animation packet
                    player.stopSound(Sound.ITEM_TOTEM_USE, SoundCategory.PLAYERS);
                    player.stopSound(Sound.ITEM_TOTEM_USE, SoundCategory.MASTER);
                    player.stopSound(Sound.ITEM_TOTEM_USE);

                    // Send Entity Status packet (status 35) to play totem animation
                    try {
                        PacketContainer packet = ProtocolLibrary.getProtocolManager()
                            .createPacket(PacketType.Play.Server.ENTITY_STATUS);
                        packet.getIntegers().write(0, player.getEntityId());
                        packet.getBytes().write(0, (byte) 35);
                        ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
                    } catch (Exception e) {
                        Specialization.getInstance().getLogger().warning("Failed to send HOTV animation: " + e.getMessage());
                    }

                    // Stop default totem sound repeatedly with all categories
                    for (int i = 0; i <= 15; i++) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                player.stopSound(Sound.ITEM_TOTEM_USE, SoundCategory.PLAYERS);
                                player.stopSound(Sound.ITEM_TOTEM_USE, SoundCategory.MASTER);
                                player.stopSound(Sound.ITEM_TOTEM_USE);
                            }
                        }.runTaskLater(Specialization.getInstance(), i);
                    }

                    // Play custom sound with pitch based on HOTV level
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, pitch);
                        }
                    }.runTaskLater(Specialization.getInstance(), 2L);

                    // Immediately restore actual offhand visual and sync inventory
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // Cleanup any totems that might have synced to inventory FIRST
                            removeHOTVTotemsFromInventory(player);

                            // Now get the real server-side offhand and send it to client
                            ItemStack actualServerOffhand = player.getInventory().getItemInOffHand();
                            sendClientEquipmentPacket(player, EnumWrappers.ItemSlot.OFFHAND, actualServerOffhand);

                            // Force full inventory sync
                            player.updateInventory();
                        }
                    }.runTaskLater(Specialization.getInstance(), 1L);

                    // Remove player from protection lists and final cleanup
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            blockTotemSound.remove(playerId);
                            playersInAnimation.remove(playerId);

                            // Final cleanup to ensure no totems remain
                            removeHOTVTotemsFromInventory(player);

                            // Final inventory sync
                            player.updateInventory();
                        }
                    }.runTaskLater(Specialization.getInstance(), 20L);
                }
            }.runTaskLater(Specialization.getInstance(), 1L);

        } catch (Exception e) {
            blockTotemSound.remove(playerId);
            playersInAnimation.remove(playerId);
            Specialization.getInstance().getLogger().warning("Failed to play HOTV animation: " + e.getMessage());
        }
    }

    /**
     * Sends a CLIENT-SIDE ONLY equipment update packet.
     */
    private void sendClientEquipmentPacket(Player player, EnumWrappers.ItemSlot slot, ItemStack item) {
        try {
            PacketContainer packet = ProtocolLibrary.getProtocolManager()
                .createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
            packet.getIntegers().write(0, player.getEntityId());
            packet.getSlotStackPairLists().write(0, Collections.singletonList(new Pair<>(slot, item)));
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, packet);
        } catch (Exception e) {
            Specialization.getInstance().getLogger().warning("Failed to send equipment packet: " + e.getMessage());
        }
    }

    /**
     * Gets the custom Hero of the Village totem item from CraftEngine.
     */
    private ItemStack getHOTVTotemItem() {
        try {
            var ceItem = BukkitItemManager.instance().getCustomItem(Key.of("specialization:hero_of_the_village"));
            if (ceItem != null && ceItem.isPresent()) return ceItem.get().buildItemStack(1);
        } catch (Exception ignored) {}
        return new ItemStack(Material.TOTEM_OF_UNDYING);
    }
}
