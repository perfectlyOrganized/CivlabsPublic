package com.minecraftcivilizations.specialization.CraftEngine;

import com.minecraftcivilizations.specialization.Listener.Player.PlayerDownedListener;
import com.minecraftcivilizations.specialization.Specialization;
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.util.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.util.Vector;

public class RevolverListener implements Listener {

    private static final Key REVOLVER_ID = RevolverBehavior.REVOLVER_ID;
    private static final Key REVOLVER_AMMO_ID = RevolverBehavior.REVOLVER_AMMO_ID;

    public RevolverListener() {
        Specialization.getInstance().getServer().getPluginManager().registerEvents(this, Specialization.getInstance());
    }

    private boolean isRevolver(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.CROSSBOW) {
            return false;
        }
        Item<ItemStack> wrapped = BukkitItemManager.instance().wrap(itemStack);
        return wrapped.getCustomItem().isPresent() &&
                wrapped.getCustomItem().get().id().equals(REVOLVER_ID);
    }

    private boolean isRevolverAmmo(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        Item<ItemStack> wrapped = BukkitItemManager.instance().wrap(itemStack);
        return wrapped.getCustomItem().isPresent() &&
                wrapped.getCustomItem().get().id().equals(REVOLVER_AMMO_ID);
    }

    /**
     * Counts how many revolver rounds the player has in their entire inventory.
     */
    private int countRevolverAmmo(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isRevolverAmmo(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    /**
     * Consumes a specific amount of revolver rounds from the player's inventory.
     */
    private void consumeRevolverAmmo(Player player, int amountToConsume) {
        int remainingToConsume = amountToConsume;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && isRevolverAmmo(item)) {
                if (item.getAmount() <= remainingToConsume) {
                    remainingToConsume -= item.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remainingToConsume);
                    remainingToConsume = 0;
                }
                if (remainingToConsume <= 0) {
                    break; // Finished consuming
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCrossbowLoad(EntityLoadCrossbowEvent event) {
        ItemStack crossbow = event.getCrossbow();
        if (isRevolver(crossbow)) {
            event.setCancelled(true);
            event.setConsumeItem(false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBowShoot(EntityShootBowEvent event) {
        ItemStack bow = event.getBow();
        if (isRevolver(bow)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRevolverUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack weapon = event.getItem();

        if (!isRevolver(weapon)) {
            return;
        }

        event.setCancelled(true);

        if (isPlayerDowned(player)) {
            player.sendActionBar(Component.text("Cannot use revolver while downed!").color(NamedTextColor.RED));
            return;
        }

        if (player.hasCooldown(Material.CROSSBOW)) {
            return;
        }

        int currentAmmo = RevolverBehavior.getShotsLeft(weapon);
        boolean wantsToReload = currentAmmo == 0;

        // --- RELOADING LOGIC ---
        if (wantsToReload && currentAmmo < RevolverBehavior.MAX_SHOTS) {
            int invRounds = countRevolverAmmo(player);

            if (invRounds == 0) {
                player.sendActionBar(Component.text("Requires Revolver Round to reload!").color(NamedTextColor.RED));
                return;
            }

            int needed = RevolverBehavior.MAX_SHOTS - currentAmmo;
            int toLoad = Math.min(invRounds, needed);

            consumeRevolverAmmo(player, toLoad);

            int newAmmo = currentAmmo + toLoad;
            RevolverBehavior.setShotsLeft(weapon, newAmmo);

            int quickChargeLevel = weapon.getEnchantmentLevel(Enchantment.QUICK_CHARGE);

            int cooldownPerBullet = Math.max(8, 20 - (quickChargeLevel * 4));
            int totalCooldown = toLoad * cooldownPerBullet;

            player.setCooldown(Material.CROSSBOW, totalCooldown);
            player.sendActionBar(Component.text("Reloading " + toLoad + " round(s)... (" + newAmmo + "/6)")
                    .color(NamedTextColor.YELLOW));
            player.getWorld().playSound(player.getLocation(), "specialization:revolver_reload", 0.9f, 1.2f);

            ensureCrossbowVisuallyLoaded(weapon);
            return;
        }

        // --- SHOOTING LOGIC ---
        if (currentAmmo > 0) {
            int piercingLevel = weapon.getEnchantmentLevel(Enchantment.PIERCING);
            Location eyeLocation = player.getEyeLocation();
            Vector direction = eyeLocation.getDirection().normalize();

            RevolverBehavior.shootProjectile(player, eyeLocation, direction, player.getWorld(), piercingLevel);

            player.setCooldown(Material.CROSSBOW, 10);

            int newShots = currentAmmo - 1;
            RevolverBehavior.setShotsLeft(weapon, newShots);
            weapon.damage(1, player);

            if (newShots > 0) {
                player.sendActionBar(Component.text("Revolver: " + newShots + "/" + RevolverBehavior.MAX_SHOTS)
                        .color(NamedTextColor.WHITE));
            } else {
                player.sendActionBar(Component.text("Revolver Empty!").color(NamedTextColor.RED));
            }

            ensureCrossbowVisuallyLoaded(weapon);
        }
    }

    private boolean isPlayerDowned(Player player) {
        PlayerDownedListener downedListener = Specialization.getInstance().getPlayerDownedListener();
        return downedListener != null && downedListener.isDowned(player);
    }

    private void ensureCrossbowVisuallyLoaded(ItemStack weapon) {
        if (weapon.getItemMeta() instanceof CrossbowMeta meta) {
            if (meta.getChargedProjectiles().isEmpty()) {
                meta.addChargedProjectile(new ItemStack(Material.ARROW));
                weapon.setItemMeta(meta);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwapHandItems(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        ItemStack weapon = null;
        if (isRevolver(mainHand)) {
            weapon = mainHand;
        } else if (isRevolver(offHand)) {
            weapon = offHand;
        }

        if (player.isSneaking() && weapon != null) {
            event.setCancelled(true);

            if (isPlayerDowned(player)) {
                player.sendActionBar(Component.text("Cannot use revolver while downed!").color(NamedTextColor.RED));
                return;
            }

            if (player.hasCooldown(Material.CROSSBOW)) {
                return;
            }

            int currentAmmo = RevolverBehavior.getShotsLeft(weapon);
            if (currentAmmo < RevolverBehavior.MAX_SHOTS) {
                int invRounds = countRevolverAmmo(player);
                if (invRounds == 0) {
                    player.sendActionBar(
                            Component.text("Requires Revolver Round to reload!").color(NamedTextColor.RED));
                    return;
                }

                int needed = RevolverBehavior.MAX_SHOTS - currentAmmo;
                int toLoad = Math.min(invRounds, needed);
                consumeRevolverAmmo(player, toLoad);

                int newAmmo = currentAmmo + toLoad;
                RevolverBehavior.setShotsLeft(weapon, newAmmo);

                int quickChargeLevel = weapon.getEnchantmentLevel(Enchantment.QUICK_CHARGE);
                int cooldownPerBullet = Math.max(8, 20 - (quickChargeLevel * 4));
                int totalCooldown = toLoad * cooldownPerBullet;

                player.setCooldown(Material.CROSSBOW, totalCooldown);
                player.sendActionBar(Component.text("Reloading " + toLoad + " round(s)... (" + newAmmo + "/6)")
                        .color(NamedTextColor.YELLOW));
                player.getWorld().playSound(player.getLocation(), "specialization:revolver_reload", 0.9f, 1.2f);

                ensureCrossbowVisuallyLoaded(weapon);
            } else {
                player.sendActionBar(Component.text("Revolver Fully Loaded!").color(NamedTextColor.GREEN));
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onItemHeld(org.bukkit.event.player.PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newWeapon = player.getInventory().getItem(event.getNewSlot());

        if (isRevolver(newWeapon)) {
            int currentAmmo = RevolverBehavior.getShotsLeft(newWeapon);
            player.sendActionBar(Component.text("Revolver: " + currentAmmo + "/" + RevolverBehavior.MAX_SHOTS)
                    .color(NamedTextColor.WHITE));
        }
    }
}
