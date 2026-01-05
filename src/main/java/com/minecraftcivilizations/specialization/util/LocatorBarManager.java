package com.minecraftcivilizations.specialization.util;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the locator bar system for entity visibility
 * By default, all players have zero receive range (can't see anyone)
 * This system allows specific features to grant temporary visibility
 */
public class LocatorBarManager implements Listener {

    private final Plugin plugin;
    @Getter
    private static LocatorBarManager instance;

    private final Map<UUID, Map<UUID, Long>> temporaryVisibility = new HashMap<>();

    public LocatorBarManager(Plugin plugin) {
        this.plugin = plugin;
        instance = this;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        initializePlayerLocatorBar(player);
    }

    public void initializePlayerLocatorBar(Player player) {
        double defaultReceiveRange = SpecializationConfig.getLocatorBarConfig().getDouble("DEFAULT_RECEIVE_RANGE");
        double defaultTransmitRange = SpecializationConfig.getLocatorBarConfig().getDouble("DEFAULT_TRANSMIT_RANGE");
        
        try {
            player.getAttribute(Attribute.WAYPOINT_RECEIVE_RANGE).setBaseValue(defaultReceiveRange);
            player.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE).setBaseValue(defaultTransmitRange);
        } catch (Exception e) {
            Bukkit.getLogger().severe("[LOCATOR_BAR] Error setting attributes for player " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Grant temporary visibility of an entity to a player
     * @param observer The player who will see the entity
     * @param target The entity to be made visible
     * @param durationTicks How long the visibility lasts
     */
    public void grantTemporaryVisibility(Player observer, Entity target, int durationTicks) {
        if (!SpecializationConfig.getLocatorBarConfig().getBoolean("LOCATOR_BAR_ENABLED")) {
            return;
        }

        UUID observerId = observer.getUniqueId();
        UUID targetId = target.getUniqueId();

        temporaryVisibility.computeIfAbsent(observerId, k -> new HashMap<>())
                          .put(targetId, System.currentTimeMillis() + (durationTicks * 50L));
        
        if (target instanceof LivingEntity livingTarget) {
            double visibilityRange = SpecializationConfig.getLocatorBarConfig().getDouble("TEMPORARY_VISIBILITY_RANGE");
            
            try {
                livingTarget.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE).setBaseValue(visibilityRange);
            } catch (Exception e) {
                Bukkit.getLogger().severe("[LOCATOR_BAR] Error setting target transmit range: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        double observerRange = SpecializationConfig.getLocatorBarConfig().getDouble("OBSERVER_RECEIVE_RANGE");
        
        try {
            observer.getAttribute(Attribute.WAYPOINT_RECEIVE_RANGE).setBaseValue(observerRange);
        } catch (Exception e) {
            Bukkit.getLogger().severe("[LOCATOR_BAR] Error setting observer receive range: " + e.getMessage());
            e.printStackTrace();
        }
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            cleanupTemporaryVisibility(observer, target);
        }, durationTicks);
    }

    public void grantMultipleTemporaryVisibility(Player observer, Entity[] targets, int durationTicks) {
        for (Entity target : targets) {
            grantTemporaryVisibility(observer, target, durationTicks);
        }
    }

    public void setPlayerReceiveRange(Player player, double range) {
        if (!SpecializationConfig.getLocatorBarConfig().getBoolean("LOCATOR_BAR_ENABLED")) {
            return;
        }

        player.getAttribute(Attribute.WAYPOINT_RECEIVE_RANGE).setBaseValue(range);
    }

    public void setEntityTransmitRange(LivingEntity entity, double range) {
        if (!SpecializationConfig.getLocatorBarConfig().getBoolean("LOCATOR_BAR_ENABLED")) {
            return;
        }

        entity.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE).setBaseValue(range);
    }

    public void resetPlayerLocatorBar(Player player) {
        initializePlayerLocatorBar(player);
        temporaryVisibility.remove(player.getUniqueId());
    }

    private void cleanupTemporaryVisibility(Player observer, Entity target) {
        UUID observerId = observer.getUniqueId();
        UUID targetId = target.getUniqueId();

        Map<UUID, Long> observerGrants = temporaryVisibility.get(observerId);
        if (observerGrants != null) {
            observerGrants.remove(targetId);
            if (observerGrants.isEmpty()) {
                double defaultReceiveRange = SpecializationConfig.getLocatorBarConfig().getDouble("DEFAULT_RECEIVE_RANGE");
                observer.getAttribute(Attribute.WAYPOINT_RECEIVE_RANGE).setBaseValue(defaultReceiveRange);
                temporaryVisibility.remove(observerId);
            }
        }

        // Reset target's transmit range to default
        if (target instanceof LivingEntity livingTarget) {
            double defaultTransmitRange = SpecializationConfig.getLocatorBarConfig().getDouble("DEFAULT_TRANSMIT_RANGE");
            livingTarget.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE).setBaseValue(defaultTransmitRange);
        }
    }

    public boolean hasTemporaryVisibility(Player observer, Entity target) {
        UUID observerId = observer.getUniqueId();
        UUID targetId = target.getUniqueId();

        Map<UUID, Long> observerGrants = temporaryVisibility.get(observerId);
        if (observerGrants == null) return false;

        Long expireTime = observerGrants.get(targetId);
        if (expireTime == null) return false;

        return System.currentTimeMillis() < expireTime;
    }

    public double getPlayerReceiveRange(Player player) {
        return player.getAttribute(Attribute.WAYPOINT_RECEIVE_RANGE).getValue();
    }

    public double getEntityTransmitRange(LivingEntity entity) {
        return entity.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE).getValue();
    }
}
