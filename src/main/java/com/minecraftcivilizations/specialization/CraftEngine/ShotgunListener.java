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

public class ShotgunListener implements Listener {

    private static final Key SHOTGUN_ID = ShotgunBehavior.SHOTGUN_ID;
    private static final Key SHOTGUN_AMMO_ID = ShotgunBehavior.SHOTGUN_AMMO_ID;

    public ShotgunListener() {
        Specialization.getInstance().getServer().getPluginManager().registerEvents(this, Specialization.getInstance());
    }

    private boolean isShotgun(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.CROSSBOW) {
            return false;
        }
        Item<ItemStack> wrapped = BukkitItemManager.instance().wrap(itemStack);
        return wrapped.getCustomItem().isPresent() &&
                wrapped.getCustomItem().get().id().equals(SHOTGUN_ID);
    }

    private boolean isShotgunAmmo(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        Item<ItemStack> wrapped = BukkitItemManager.instance().wrap(itemStack);
        return wrapped.getCustomItem().isPresent() &&
                wrapped.getCustomItem().get().id().equals(SHOTGUN_AMMO_ID);
    }

    private int countShotgunAmmo(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && isShotgunAmmo(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void consumeShotgunAmmo(Player player, int amountToConsume) {
        int remainingToConsume = amountToConsume;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && isShotgunAmmo(item)) {
                if (item.getAmount() <= remainingToConsume) {
                    remainingToConsume -= item.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remainingToConsume);
                    remainingToConsume = 0;
                }
                if (remainingToConsume <= 0) {
                    break;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCrossbowLoad(EntityLoadCrossbowEvent event) {
        ItemStack crossbow = event.getCrossbow();
        if (isShotgun(crossbow)) {
            event.setCancelled(true);
            event.setConsumeItem(false);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBowShoot(EntityShootBowEvent event) {
        ItemStack bow = event.getBow();
        if (isShotgun(bow)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onShotgunUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack weapon = event.getItem(); // Dynamically gets the interacted hand (main or off)

        if (!isShotgun(weapon)) {
            return;
        }

        event.setCancelled(true);

        if (isPlayerDowned(player)) {
            player.sendActionBar(Component.text("Cannot use shotgun while downed!").color(NamedTextColor.RED));
            return;
        }

        if (player.hasCooldown(Material.CROSSBOW)) {
            return;
        }

        int currentAmmo = ShotgunBehavior.getShotsLeft(weapon);
        boolean wantsToReload = currentAmmo == 0;

        // --- RELOADING LOGIC ---
        if (wantsToReload && currentAmmo < ShotgunBehavior.MAX_SHOTS) {
            int invRounds = countShotgunAmmo(player);

            if (invRounds == 0) {
                player.sendActionBar(Component.text("Requires Shotgun Shells to reload!").color(NamedTextColor.RED));
                return;
            }

            int needed = ShotgunBehavior.MAX_SHOTS - currentAmmo;
            int toLoad = Math.min(invRounds, needed);

            consumeShotgunAmmo(player, toLoad);

            int newAmmo = currentAmmo + toLoad;
            ShotgunBehavior.setShotsLeft(weapon, newAmmo);

            int quickChargeLevel = weapon.getEnchantmentLevel(Enchantment.QUICK_CHARGE);

            int cooldownPerShell = Math.max(15, 40 - (quickChargeLevel * 5));
            int totalCooldown = toLoad * cooldownPerShell;

            player.setCooldown(Material.CROSSBOW, totalCooldown);
            player.sendActionBar(Component.text("Slugging " + toLoad + " shell(s)... (" + newAmmo + "/2)")
                    .color(NamedTextColor.YELLOW));
            player.getWorld().playSound(player.getLocation(), "specialization:rifle_reload", 0.9f, 0.8f);

            ensureCrossbowVisuallyLoaded(weapon);
            return;
        }

        // --- SHOOTING LOGIC ---
        if (currentAmmo > 0) {
            int piercingLevel = weapon.getEnchantmentLevel(Enchantment.PIERCING);
            Location eyeLocation = player.getEyeLocation();
            Vector direction = eyeLocation.getDirection().normalize();

            ShotgunBehavior.shootProjectile(player, eyeLocation, direction, player.getWorld(), piercingLevel);

            player.setCooldown(Material.CROSSBOW, 8); // Devastatingly fast double-tap pump

            int newShots = currentAmmo - 1;
            ShotgunBehavior.setShotsLeft(weapon, newShots);
            weapon.damage(2, player);

            if (newShots > 0) {
                player.sendActionBar(Component.text("Shotgun: " + newShots + "/" + ShotgunBehavior.MAX_SHOTS)
                        .color(NamedTextColor.WHITE));
            } else {
                player.sendActionBar(Component.text("Shotgun Empty!").color(NamedTextColor.RED));
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
        if (isShotgun(mainHand)) {
            weapon = mainHand;
        } else if (isShotgun(offHand)) {
            weapon = offHand;
        }

        if (player.isSneaking() && weapon != null) {
            event.setCancelled(true);

            if (isPlayerDowned(player)) {
                player.sendActionBar(Component.text("Cannot use shotgun while downed!").color(NamedTextColor.RED));
                return;
            }

            if (player.hasCooldown(Material.CROSSBOW)) {
                return;
            }

            int currentAmmo = ShotgunBehavior.getShotsLeft(weapon);
            if (currentAmmo < ShotgunBehavior.MAX_SHOTS) {
                int invRounds = countShotgunAmmo(player);
                if (invRounds == 0) {
                    player.sendActionBar(
                            Component.text("Requires Shotgun Shells to reload!").color(NamedTextColor.RED));
                    return;
                }

                int needed = ShotgunBehavior.MAX_SHOTS - currentAmmo;
                int toLoad = Math.min(invRounds, needed);
                consumeShotgunAmmo(player, toLoad);

                int newAmmo = currentAmmo + toLoad;
                ShotgunBehavior.setShotsLeft(weapon, newAmmo);

                int quickChargeLevel = weapon.getEnchantmentLevel(Enchantment.QUICK_CHARGE);
                int cooldownPerShell = Math.max(15, 40 - (quickChargeLevel * 5));
                int totalCooldown = toLoad * cooldownPerShell;

                player.setCooldown(Material.CROSSBOW, totalCooldown);
                player.sendActionBar(Component.text("Slugging " + toLoad + " shell(s)... (" + newAmmo + "/2)")
                        .color(NamedTextColor.YELLOW));
                player.getWorld().playSound(player.getLocation(), "specialization:rifle_reload", 0.9f, 0.8f);

                ensureCrossbowVisuallyLoaded(weapon);
            } else {
                player.sendActionBar(Component.text("Shotgun Fully Loaded!").color(NamedTextColor.GREEN));
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onItemHeld(org.bukkit.event.player.PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newWeapon = player.getInventory().getItem(event.getNewSlot());

        if (isShotgun(newWeapon)) {
            int currentAmmo = ShotgunBehavior.getShotsLeft(newWeapon);
            player.sendActionBar(Component.text("Shotgun: " + currentAmmo + "/" + ShotgunBehavior.MAX_SHOTS)
                    .color(NamedTextColor.WHITE));
        }
    }
}
