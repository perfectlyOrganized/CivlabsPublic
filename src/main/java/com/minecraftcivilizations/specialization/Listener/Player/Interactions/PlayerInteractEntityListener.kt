package com.minecraftcivilizations.specialization.Listener.Player.Interactions

import com.minecraftcivilizations.specialization.Config.SpecializationConfig
import com.minecraftcivilizations.specialization.Skill.SkillType
import com.minecraftcivilizations.specialization.player.CustomPlayerManager.getCustomPlayer
import com.minecraftcivilizations.specialization.util.PlayerUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Animals
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityTameEvent
import org.bukkit.event.player.PlayerInteractEntityEvent

class PlayerInteractEntityListener : Listener {

    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val entity = event.rightClicked

        if (entity is Animals && entity.canBreed()) {
            val item = player.inventory.itemInMainHand

            if (entity.isBreedItem(item)) {
                val customPlayer = getCustomPlayer(player) ?: return
                val level = SpecializationConfig.farmerConfig
                    .getInt("FARMER_BREED_LEVEL_" + entity.type)
                    .toInt()

                if (customPlayer.getSkillLevel(SkillType.FARMER) < level) {
                    event.isCancelled = true
                    PlayerUtil.sendActionBar(player, Component.text("You need level $level Farmer to breed a ${entity.type}").color(
                        NamedTextColor.RED))
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerTame(event: EntityTameEvent) {
        val player = event.owner as? Player ?: return

        val customPlayer = getCustomPlayer(player)
        val entityKey = "TAME_" + event.entity.type
        var canTame = false
        var requiredLevel = 0
        var requiredSkill = SkillType.FARMER // Default

        for (skill in SkillType.entries) {
            val tames = SpecializationConfig.tameableConfig.getObject(skill.name)
            if (!tames.hasPath(entityKey)) continue
            val requirement = tames.getInt(entityKey)

            if (customPlayer!!.getSkillLevel(skill) >= requirement) {
                canTame = true
                break
            } else {
                requiredLevel = requirement
                requiredSkill = skill
            }
        }

        if (!canTame) {
            player.sendMessage("test")
            PlayerUtil.sendActionBar(player, Component.text("You are unable to use $requiredLevel ${requiredSkill.name} to tame a ${event.entity.type}").color(NamedTextColor.RED))
            event.isCancelled = true
        }
    }
}