package com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.UUID;

public class PlayerMineListener implements Listener {

    private static HashMap<UUID, Double> originalBreakSpeed = new HashMap<>();






    @EventHandler
    public void onBlockBreakAbort(BlockDamageAbortEvent event) {
        resetPlayerBreakSpeed(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            resetPlayerBreakSpeed(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        originalBreakSpeed.remove(event.getPlayer().getUniqueId());
    }

    private double getBlockBreakMultiplier(org.bukkit.block.Block block) {
        double multiplier = SpecializationConfig.getBlockHardnessConfig().getDouble(block.getType().toString());
        

        if (ReinforcementManager.isWoodenReinforced(block)) {
            // Wooden reinforcement is 0.5x as strong as light reinforcement
            double lightMultiplier = SpecializationConfig.getReinforcementConfig().getDouble("LIGHT_REINFORCEMENT_MULTIPLIER");
            multiplier = lightMultiplier * 0.5;
        }
        if (ReinforcementManager.isLightlyReinforced(block)) {
            multiplier = SpecializationConfig.getReinforcementConfig().getDouble("LIGHT_REINFORCEMENT_MULTIPLIER");
        }
        if (ReinforcementManager.isHeavilyReinforced(block)) {
            multiplier = SpecializationConfig.getReinforcementConfig().getDouble("HEAVY_REINFORCEMENT_MULTIPLIER");;
        }
        
        return multiplier;
    }

    private void resetPlayerBreakSpeed(UUID playerId) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(playerId);
//        if (player != null && originalBreakSpeed.containsKey(playerId)) {
//            AttributeInstance breakSpeedAttr = player.getAttribute(Attribute.PLAYER_BLOCK_BREAK_SPEED);
//            if (breakSpeedAttr != null) {
//                breakSpeedAttr.setBaseValue(1.0); // Reset to normal speed
//            }
//            originalBreakSpeed.remove(playerId);
//        }
    }
}
