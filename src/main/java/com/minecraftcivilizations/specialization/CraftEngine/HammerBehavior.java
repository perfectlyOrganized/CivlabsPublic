package com.minecraftcivilizations.specialization.CraftEngine;

import com.minecraftcivilizations.specialization.Specialization;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptors;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.item.CustomItem;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.nio.file.Path;
import java.util.Map;

public class HammerBehavior extends ItemBehavior implements Listener {
    private static final Key HAMMER_KEY = Key.of("specialization:hammer");
    public static final Factory FACTORY = new Factory();

    public static class Factory implements ItemBehaviorFactory {
        @Override
        public ItemBehavior create(Pack pack, Path path, String node, Key key, Map<String, Object> arguments) {
            return new HammerBehavior();
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        Item<ItemStack> wrapped = BukkitItemManager.instance().wrap(item);
        CustomItem<ItemStack> customItem = wrapped.getCustomItem().orElse(null);
        if (customItem == null || !customItem.id().equals(HAMMER_KEY)) return;

        // Apply mace hitting effect: knockback the target
        Vector direction = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
        direction.setY(0.5);
        target.setVelocity(direction.multiply(2));

        // Set damage to 6 (3 hearts)
        event.setDamage(6.0);

        // Play hit sound
        org.bukkit.World bukkitWorld = player.getWorld();
        World ceWorld = BukkitAdaptors.adapt(bukkitWorld);
        ceWorld.playSound(LocationUtils.toVec3d(player.getLocation()), Key.of("specialization:hammer_hit"), 1f, 1.0f, SoundSource.PLAYER);
    }
}
