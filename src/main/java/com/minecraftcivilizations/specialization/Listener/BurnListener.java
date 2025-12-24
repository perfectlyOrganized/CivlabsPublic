package com.minecraftcivilizations.specialization.Listener;

import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;

public class BurnListener implements Listener {
    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        if (ReinforcementManager.isReinforced(event.getBlock())) {
            event.setCancelled(true);
        }
    }
}
