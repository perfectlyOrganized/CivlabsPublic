package com.minecraftcivilizations.specialization.CraftEngine;

import com.minecraftcivilizations.specialization.Specialization;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.item.CustomItem;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class HammerBehavior extends ItemBehavior implements Listener {
    private static final Key HAMMER_KEY = Key.of("specialization:hammer");
    public static final Factory FACTORY = new Factory();

    // Light hit sounds
    private static final String[] LIGHT_HIT_SOUNDS = {
        "specialization:hammer_hit_light1",
        "specialization:hammer_hit_light2",
        "specialization:hammer_hit_light3",
        "specialization:hammer_hit_light4"
    };

    // Swing sounds
    private static final String[] SWING_SOUNDS = {
        "specialization:hammer_swing1",
        "specialization:hammer_swing2",
        "specialization:hammer_swing3"
    };

    public static class Factory implements ItemBehaviorFactory {
        @Override
        public ItemBehavior create(Pack pack, Path path, String node, Key key, Map<String, Object> arguments) {
            return new HammerBehavior();
        }
    }

    /**
     * Checks if an ItemStack is the hammer
     */
    private boolean isHammer(ItemStack item) {
        if (item == null || item.getType() != Material.MACE) return false;
        Item<ItemStack> wrapped = BukkitItemManager.instance().wrap(item);
        CustomItem<ItemStack> customItem = wrapped.getCustomItem().orElse(null);
        return customItem != null && customItem.id().equals(HAMMER_KEY);
    }

    /**
     * Play swing sound on left click (air swing)
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSwing(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isHammer(item)) return;

        // Play random swing sound
        String swingSound = SWING_SOUNDS[ThreadLocalRandom.current().nextInt(SWING_SOUNDS.length)];
        player.getWorld().playSound(player.getLocation(), swingSound, 1.0f, 1.0f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isHammer(item)) return;

        // === SLAM ATTACK - Always act like falling down (like old code) ===
        // Push player down and set fall distance to simulate slam attack
        Vector playerVel = player.getVelocity();
        playerVel.setY(-5); // Push player down hard
        player.setVelocity(playerVel);
        player.setFallDistance(5); // Set fall distance for mace bonus damage

        // Cap the damage to 5 (like old code)
        event.setDamage(Math.min(event.getFinalDamage(), 5));

        // Play hit sound - heavy if actually falling, light otherwise
        org.bukkit.World world = target.getWorld();
        float actualFallDistance = player.getFallDistance();

        if (actualFallDistance > 3) {
            // Heavy hit sound
            world.playSound(target.getLocation(), "specialization:hammer_hit_heavy", 1.5f, 0.8f);
        } else {
            // Random light hit sound
            String lightSound = LIGHT_HIT_SOUNDS[ThreadLocalRandom.current().nextInt(LIGHT_HIT_SOUNDS.length)];
            world.playSound(target.getLocation(), lightSound, 1.5f, 0.9f);
        }

        // === PARTICLE EFFECTS (no shockwave) ===
        Location hitLoc = target.getLocation().add(0, 1, 0);

        // Explosion burst
        world.spawnParticle(Particle.EXPLOSION, hitLoc, 1, 0, 0, 0, 0);

        // Critical hit particles
        world.spawnParticle(Particle.CRIT, hitLoc, 25, 0.5, 0.5, 0.5, 0.3);

        // Enchantment sparkles
        world.spawnParticle(Particle.ENCHANT, hitLoc, 30, 0.5, 0.5, 0.5, 1.0);

        // Dust cloud at feet
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, target.getLocation().add(0, 0.2, 0), 15, 0.4, 0.1, 0.4, 0.02);
        world.spawnParticle(Particle.SMOKE, hitLoc, 20, 0.3, 0.3, 0.3, 0.1);

        // Metallic sparks
        world.spawnParticle(Particle.ELECTRIC_SPARK, hitLoc, 15, 0.3, 0.3, 0.3, 0.2);

        // Iron/grey dust particles
        world.spawnParticle(Particle.DUST, hitLoc, 20, 0.4, 0.4, 0.4, 0,
                new Particle.DustOptions(Color.fromRGB(120, 120, 120), 1.5f));

        // Damage indicators
        world.spawnParticle(Particle.DAMAGE_INDICATOR, hitLoc, 8, 0.3, 0.5, 0.3, 0.1);
    }
}
