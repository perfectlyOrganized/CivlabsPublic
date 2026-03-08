package com.minecraftcivilizations.specialization.util

import com.minecraftcivilizations.specialization.OpenLab
import com.minecraftcivilizations.specialization.Skill.SkillType
import com.minecraftcivilizations.specialization.player.CustomPlayerManager.getCustomPlayer
import de.tr7zw.changeme.nbtapi.NBT
import de.tr7zw.changeme.nbtapi.iface.ReadWriteNBT
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT
import de.tr7zw.changeme.nbtapi.iface.ReadableNBTList
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import java.util.*

object OvergearedUtils {
    fun isOvergearedAnvilConversion(player: Player, block: Block): Boolean {
        if (!player.isSneaking) return false
        val item = player.inventory.itemInMainHand
        return isOvergearedHammer(item) && block.type == Material.ANVIL
    }

    fun isOvergearedHammer(item: ItemStack): Boolean {
        // #TODO move to config
        return mutableListOf<String?>("OVERGEARED_COPPER_SMITHING_HAMMER", "OVERGEARED_SMITHING_HAMMER").contains(
            item.type.toString())
    }

    fun handleOvergearedAnvilEvent(event: PlayerInteractEvent, block: Block, player: Player) {
        if (!isOvergearedHammer(player.inventory.itemInMainHand)) return
        if (!block.state.type.toString().startsWith("OVERGEARED")) return
        val customPlayer = getCustomPlayer(player) ?: return
        val level = customPlayer.getSkillLevel(SkillType.BLACKSMITH)
        val canUseIron = level >= 2
        val canUseGold = level >= 3
        val canUseSteel = level >= 3

        try {
            NBT.get(block.state) { nbt: ReadableNBT ->
                val inventory = nbt.getCompound("inventory") ?: return@get

                val items: ReadableNBTList<ReadWriteNBT> = inventory.getCompoundList("Items")
                if (items.isEmpty) return@get

                for (item in items) {
                    val id = item.getString("id").lowercase(Locale.getDefault())
                    if ((id.contains("iron") && !canUseIron) ||
                        (id.contains("gold") && !canUseGold) ||
                        (id.contains("steel") && !canUseSteel)
                    ) {
                        val material =
                            if (id.contains("iron")) "iron" else if (id.contains("gold")) "gold" else "steel"

                        PlayerUtil.sendActionBar(
                            player,
                            Component.text("Can't use $material").color(NamedTextColor.RED)
                        )
                        event.isCancelled = true
                        return@get
                    }
                }
            }
        } catch (e: Exception) {
        }

    }
}