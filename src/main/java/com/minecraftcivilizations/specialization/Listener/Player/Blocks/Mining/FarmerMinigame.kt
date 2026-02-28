package com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining

import net.minecraft.world.item.BoneMealItem
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryInteractEvent
import java.util.UUID

class FarmerMinigameManager : Listener {
    companion object {
        val games: HashMap<UUID, FarmerMinigame> = HashMap()
    }
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val game = games[event.whoClicked.uniqueId] ?: return
        event.isCancelled = true
        val item = event.currentItem
    }
}
class FarmerMinigame {
   constructor() {

   }
}