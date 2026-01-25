package com.minecraftcivilizations.specialization.CraftEngine;

import com.minecraftcivilizations.specialization.Specialization;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.entity.projectile.BukkitProjectileManager;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.plugin.BukkitCraftEngine;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.entity.projectile.ProjectileMeta;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.item.context.UseOnContext;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.plugin.locale.LocalizedResourceConfigException;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.util.MiscUtils;
import net.momirealms.craftengine.core.util.ResourceConfigUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Path;
import java.util.*;

public class ThrowableExplosiveBehavior extends ItemBehavior implements Listener {
    private Set<Material> destroyableMaterials = Set.of();
    private double radius = 0;
    private double strength = 0;
    private int delay = 0;
    private int cooldown = 0;
    static private final HashSet<Key> items = new HashSet<Key>();
    public static final Factory FACTORY = new Factory();
    private static final Map<Key, ThrowableExplosiveBehavior> behaviorInstances = new HashMap<>();
    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        ItemStack item = ((ThrowableProjectile) event.getEntity()).getItem();
        if (CraftEngineItems.isCustomItem(item)) {
            Key id = CraftEngineItems.getCustomItemId(item);
            if (ThrowableExplosiveBehavior.items.contains(id)) {
                ThrowableExplosiveBehavior behavior = behaviorInstances.get(id);
                behavior.explode(event.getEntity().getLocation());
            }
        }
    }
    public static class Factory implements ItemBehaviorFactory {
        @Override
        public ItemBehavior create(Pack pack, Path path, String node, Key key, Map<String, Object> arguments) {
            List<String> materialNames = MiscUtils.getAsStringList(arguments.get("materials"));
            Set<Material> materials = new HashSet<>();
            for (String materialName : materialNames) {
                try {
                    materials.add(Material.valueOf(materialName.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new LocalizedResourceConfigException("warning.config.item.behavior.explosive_snowball.invalid_material", materialName);
                }
            }
            if (key == null) {
                throw new IllegalStateException("Key is null in behavior factory");
            }
            // Parse other parameters
            double radius = ResourceConfigUtils.getAsDouble(arguments.getOrDefault("radius", 3.0), "radius");
            double strength = ResourceConfigUtils.getAsDouble(arguments.getOrDefault("strength", 2.0), "strength");
            int delay = ResourceConfigUtils.getAsInt(arguments.getOrDefault("delay", 20), "delay");
            int cooldown = ResourceConfigUtils.getAsInt(arguments.getOrDefault("cooldown", 60), "cooldown");
            return new ThrowableExplosiveBehavior(materials, radius, strength, delay, cooldown, key);          }
    }
    public ThrowableExplosiveBehavior() {

    }
    public ThrowableExplosiveBehavior(Set<Material> destroyableMaterials, double radius,
                                      double strength, int delay, int cooldown, Key item) {
        ThrowableExplosiveBehavior.items.add(item);
        this.destroyableMaterials = destroyableMaterials;
            this.radius = radius;
            this.strength = strength;
            this.delay = delay;
            this.cooldown = cooldown;
        behaviorInstances.put(item, this);
        }

    public InteractionResult use(World world, Player CEplayer, InteractionHand hand) {
        if (CEplayer == null) return InteractionResult.PASS;
        Item<?> item = CEplayer.getItemInHand(hand);
        if (!(item.getItem() instanceof ItemStack itemStack)) return InteractionResult.PASS;
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) CEplayer.platformPlayer();
        if (bukkitPlayer.hasCooldown(itemStack)) return InteractionResult.PASS;
        bukkitPlayer.setCooldown(Material.SNOWBALL, cooldown);
        return InteractionResult.SUCCESS;
    }

    private void explode(Location center) {
        World world = center.getWorld();
        // Create explosion effect
        world.createExplosion(center, (float) strength, false, false);

        int radiusInt = (int) Math.ceil(radius);
        System.out.println("=== SPHERE DEBUG ===");
        System.out.println("Center: " + center);
        System.out.println("Center block coords: X=" + center.getBlockX() + " Y=" + center.getBlockY() + " Z=" + center.getBlockZ());
        System.out.println("Radius: " + radius + ", RadiusInt: " + radiusInt);
        System.out.println("World: " + center.getWorld().getName());

        int blocksChanged = 0;
        for (int x = -radiusInt; x <= radiusInt; x++) {
            for (int y = -radiusInt; y <= radiusInt; y++) {
                for (int z = -radiusInt; z <= radiusInt; z++) {
                    double distanceSquared = x*x + y*y + z*z;

                    if (distanceSquared <= radius * radius) {
                        Location blockLoc = center.clone().add(x, y, z);

                        Block block = blockLoc.getBlock();

                        block.breakNaturally();
                        blocksChanged++;
                    }
                }
            }
        }
        System.out.println("Total blocks changed: " + blocksChanged);
    }
}