package com.minecraftcivilizations.specialization.CraftEngine;

import com.minecraftcivilizations.specialization.Listener.Player.PlayerDownedListener;
import com.minecraftcivilizations.specialization.Specialization;
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Listener to handle musket-related events:
 * - Only allows musket to be loaded with lead_shot (blocks arrows/fireworks completely)
 * - Tracks load count (3 loads required) - consumes 1 lead_shot per full reload cycle
 * - Fires musket projectile instead of arrow when fully loaded
 */
public class MusketListener implements Listener {

    private static final Key MUSKET_ID = Key.of("specialization:musket");
    private static final Key LEAD_SHOT_ID = Key.of("specialization:lead_shot");
    private static final int REQUIRED_LOADS = 3;

    public MusketListener() {
        Specialization.getInstance().getServer().getPluginManager().registerEvents(this, Specialization.getInstance());

        // Start accuracy tracking task (runs every tick)
        Bukkit.getScheduler().runTaskTimer(Specialization.getInstance(), () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                MusketBehavior.updateAccuracyTracking(player);

                // Apply Slowness II while holding musket
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                if (isMusket(mainHand)) {
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SLOWNESS,
                        30, // 1.5 seconds (refreshed every tick, so always active while holding)
                        1,  // Level 2 (0-indexed, so 1 = level 2)
                        false,
                        false, // No particles
                        true   // Show icon
                    ));
                }
            }
        }, 1L, 1L);
    }

    /**
     * Checks if the given ItemStack is a musket custom item
     */
    private boolean isMusket(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.CROSSBOW) {
            return false;
        }
        Item<ItemStack> wrapped = BukkitItemManager.instance().wrap(itemStack);
        return wrapped.getCustomItem().isPresent() &&
               wrapped.getCustomItem().get().id().equals(MUSKET_ID);
    }

    /**
     * Checks if the given ItemStack is lead_shot ammo
     */
    private boolean isLeadShot(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.FIREWORK_STAR) {
            return false;
        }
        Item<ItemStack> wrapped = BukkitItemManager.instance().wrap(itemStack);
        return wrapped.getCustomItem().isPresent() &&
               wrapped.getCustomItem().get().id().equals(LEAD_SHOT_ID);
    }

    /**
     * Checks if player has lead_shot in their inventory
     */
    private boolean hasLeadShot(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isLeadShot(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Consumes one lead_shot from the player's inventory
     */
    private void consumeLeadShot(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isLeadShot(item)) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().remove(item);
                }
                return;
            }
        }
    }

    /**
     * BLOCK all vanilla crossbow loading for musket - arrows, fireworks, everything
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCrossbowLoad(EntityLoadCrossbowEvent event) {
        ItemStack crossbow = event.getCrossbow();
        if (isMusket(crossbow)) {
            // ALWAYS block vanilla loading for musket
            event.setCancelled(true);
            event.setConsumeItem(false);
        }
    }

    /**
     * BLOCK all vanilla crossbow shooting for musket
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBowShoot(EntityShootBowEvent event) {
        ItemStack bow = event.getBow();
        if (isMusket(bow)) {
            // ALWAYS cancel vanilla shooting
            event.setCancelled(true);
        }
    }

    /**
     * Main handler - intercept right-click on musket to handle reload/shoot
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onMusketUse(PlayerInteractEvent event) {
        // Only handle right-click actions
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        if (!isMusket(mainHand)) {
            return;
        }

        // Cancel the event to prevent vanilla crossbow behavior
        event.setCancelled(true);

        // Check if player is downed - downed players cannot use musket
        if (isPlayerDowned(player)) {
            player.sendActionBar(Component.text("Cannot use musket while downed!").color(NamedTextColor.RED));
            return;
        }

        // Check cooldown
        if (player.hasCooldown(Material.CROSSBOW)) {
            return;
        }

        int currentLoads = MusketBehavior.getLoadCount(mainHand);

        // If fully loaded, SHOOT
        if (currentLoads >= REQUIRED_LOADS) {
            // Get enchantment levels
            int multishotLevel = mainHand.getEnchantmentLevel(Enchantment.MULTISHOT);
            int piercingLevel = mainHand.getEnchantmentLevel(Enchantment.PIERCING);

            // Fire the musket projectile with enchantments!
            Location eyeLocation = player.getEyeLocation();
            Vector direction = eyeLocation.getDirection().normalize();
            MusketBehavior.shootProjectile(player, eyeLocation, direction, player.getWorld(), multishotLevel, piercingLevel);

            // Random cooldown between 3-5 seconds (60-100 ticks)
            int randomCooldown = 60 + ThreadLocalRandom.current().nextInt(41);
            player.setCooldown(Material.CROSSBOW, randomCooldown);

            // Reset load count after firing
            MusketBehavior.resetLoadCount(mainHand);

            // Keep crossbow visually loaded (don't clear projectiles)
            ensureCrossbowVisuallyLoaded(mainHand);

            // Damage the musket
            mainHand.damage(1, player);
            return;
        }

        // Not fully loaded - try to RELOAD
        if (!hasLeadShot(player)) {
            player.sendActionBar(Component.text("Requires Lead Shot to reload!").color(NamedTextColor.RED));
            return;
        }

        // Get Quick Charge level for faster reloading
        int quickChargeLevel = mainHand.getEnchantmentLevel(Enchantment.QUICK_CHARGE);

        // Increment load counter
        int newLoads = currentLoads + 1;
        MusketBehavior.incrementLoadCount(mainHand);

        if (newLoads < REQUIRED_LOADS) {
            // Calculate reload cooldown based on Quick Charge
            // Base: 25 ticks (~1.25 seconds)
            // Quick Charge I: 20 ticks (~1.0 second)
            // Quick Charge II: 16 ticks (~0.8 seconds)
            // Quick Charge III: 12 ticks (~0.6 seconds)
            int reloadCooldown = Math.max(12, 25 - (quickChargeLevel * 4));

            player.setCooldown(Material.CROSSBOW, reloadCooldown);
            player.sendActionBar(Component.text("Reloading... " + newLoads + "/" + REQUIRED_LOADS).color(NamedTextColor.YELLOW));
            // Play reload sound for ALL nearby players (not just the shooter)
            playReloadSoundForAll(player.getLocation(), 0.9f + (float)(Math.random() * 0.2));
        } else {
            // Fully loaded! Consume 1 lead_shot - NO cooldown on final reload
            consumeLeadShot(player);
            player.sendActionBar(Component.text("Musket Ready!").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
            // Play final reload sound for ALL nearby players
            playReloadSoundForAll(player.getLocation(), 1.2f);
        }

        // Keep crossbow visually loaded at all times
        ensureCrossbowVisuallyLoaded(mainHand);
    }

    /**
     * Checks if a player is downed using PlayerDownedListener
     */
    private boolean isPlayerDowned(Player player) {
        PlayerDownedListener downedListener = Specialization.getInstance().getPlayerDownedListener();
        return downedListener != null && downedListener.isDowned(player);
    }

    /**
     * Plays reload sound for all nearby players (32 block radius)
     */
    private void playReloadSoundForAll(Location location, float pitch) {
        location.getWorld().playSound(location, "specialization:rifle_reload", 1f, pitch);
    }

    /**
     * Ensures the crossbow appears visually loaded (for aesthetics)
     * but the "projectile" can never be shot via vanilla mechanics
     */
    private void ensureCrossbowVisuallyLoaded(ItemStack musket) {
        if (musket.getItemMeta() instanceof CrossbowMeta meta) {
            if (meta.getChargedProjectiles().isEmpty()) {
                // Add a dummy arrow for visual purposes only
                meta.addChargedProjectile(new ItemStack(Material.ARROW));
                musket.setItemMeta(meta);
            }
        }
    }

    // ============== ACCURACY RESET EVENTS ==============

    /**
     * Reset accuracy when player takes damage
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            MusketBehavior.resetAccuracy(player.getUniqueId());
        }
    }

    /**
     * Reset accuracy when player switches held item
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSwitch(PlayerItemHeldEvent event) {
        MusketBehavior.resetAccuracy(event.getPlayer().getUniqueId());
    }

    /**
     * Reset accuracy when player interacts with blocks (opening chest, etc.)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only reset for block interactions that open inventories
        if (event.getClickedBlock() != null) {
            Material type = event.getClickedBlock().getType();
            if (type.toString().contains("CHEST") ||
                type.toString().contains("BARREL") ||
                type.toString().contains("SHULKER") ||
                type.toString().contains("FURNACE") ||
                type.toString().contains("ANVIL") ||
                type.toString().contains("ENCHANTING") ||
                type.toString().contains("CRAFTING") ||
                type.toString().contains("BREWING") ||
                type.toString().contains("HOPPER") ||
                type.toString().contains("DISPENSER") ||
                type.toString().contains("DROPPER") ||
                type == Material.LEVER ||
                type == Material.COMPARATOR ||
                type == Material.REPEATER ||
                type.toString().contains("BUTTON") ||
                type.toString().contains("DOOR") ||
                type.toString().contains("GATE") ||
                type.toString().contains("TRAPDOOR")) {
                MusketBehavior.resetAccuracy(event.getPlayer().getUniqueId());
            }
        }
    }

    /**
     * Reset accuracy when player interacts with entities (getting in boat, etc.)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        MusketBehavior.resetAccuracy(event.getPlayer().getUniqueId());
    }

    /**
     * Reset accuracy when player enters a vehicle
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player) {
            MusketBehavior.resetAccuracy(player.getUniqueId());
        }
    }

    /**
     * Reset accuracy when player opens any inventory
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            MusketBehavior.resetAccuracy(player.getUniqueId());
        }
    }

    /**
     * Reset accuracy when player starts eating/using an item
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        MusketBehavior.resetAccuracy(event.getPlayer().getUniqueId());
    }

    /**
     * Reset accuracy when player swaps items (F key)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSwap(PlayerSwapHandItemsEvent event) {
        MusketBehavior.resetAccuracy(event.getPlayer().getUniqueId());
    }

    /**
     * Reset accuracy when player drops item
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        MusketBehavior.resetAccuracy(event.getPlayer().getUniqueId());
    }

    /**
     * Clean up when player quits
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        MusketBehavior.resetAccuracy(event.getPlayer().getUniqueId());
    }
}
