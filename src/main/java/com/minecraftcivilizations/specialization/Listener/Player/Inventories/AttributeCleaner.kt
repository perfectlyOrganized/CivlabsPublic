package com.minecraftcivilizations.specialization.Listener.Player.Inventories

import de.tr7zw.changeme.nbtapi.NBT
import de.tr7zw.changeme.nbtapi.iface.ReadWriteItemNBT
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.inventory.ItemStack

object AttributeCleaner : Listener {
//    @EventHandler
//    fun onInventoryClick(event: InventoryClickEvent) {
//        val item = event.getCurrentItem()
//        if (item == null || notAffixable(item)) return
//        NBT.modify(item) { readWriteItemNBT: ReadWriteItemNBT? ->
//            val craftedBy = readWriteItemNBT!!.getString("crafted_by")
//            if (craftedBy == null) {
//                readWriteItemNBT.removeKey("affix_data")
//            }
//        }
//    }
//
//    @EventHandler
//    fun onInventoryClick(event: PlayerDropItemEvent) {
//        val item = event.itemDrop.itemStack
//        if (notAffixable(item)) return
//        NBT.modify(item) { readWriteItemNBT: ReadWriteItemNBT? ->
//            val craftedBy = readWriteItemNBT!!.getString("crafted_by")
//            if (craftedBy == null) {
//                readWriteItemNBT.removeKey("affix_data")
//            }
//        }
//    }


    fun notAffixable(item: ItemStack): Boolean {
        val name = item.type.name.lowercase()
        return !name.contains("_axe") && !name.contains("_sword") && !name.contains("_shovel") && !name.contains("_pickaxe") && !name.contains(
            "_helmet"
        ) && !name.contains("_chestpate") && !name.contains("_leggings") && !name.contains("shield") && !name.contains(
            "_boots"
        )
    }
}