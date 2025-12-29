package com.minecraftcivilizations.specialization.Listener.Player;

import com.minecraftcivilizations.specialization.Command.EmoteManager;
import com.minecraftcivilizations.specialization.Specialization;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public class HammerListener implements Listener {
    public HammerListener() {
    }

    NamespacedKey MACE_KEY = new NamespacedKey("specialization", "is_mace");
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (meta.getPersistentDataContainer().has(MACE_KEY)) {
            Vector playerVel = player.getVelocity();
            playerVel.setY(-5);
            player.setVelocity(playerVel);
            player.setFallDistance(5);
            event.setDamage(Math.min(event.getFinalDamage(), 5));
        }
    }
}
