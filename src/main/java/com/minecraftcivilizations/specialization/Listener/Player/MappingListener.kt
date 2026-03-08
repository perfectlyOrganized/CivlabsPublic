package com.minecraftcivilizations.specialization.Listener.Player

import com.minecraftcivilizations.specialization.OpenLab
import com.minecraftcivilizations.specialization.Skill.SkillType
import com.minecraftcivilizations.specialization.player.CustomPlayerManager
import org.bukkit.Material
import org.bukkit.event.Listener
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.roundToInt

object MappingListener : Listener {
    private var initialized = false

    init {
        if (!initialized) {
            initialized = true

        object : BukkitRunnable() {
            override fun run() {
                for (customPlayer in CustomPlayerManager.getPlayers()) {
                    val player = customPlayer.player
                    if (player.inventory.itemInMainHand.type != Material.FILLED_MAP && player.inventory.itemInOffHand.type != Material.FILLED_MAP) return

                    val loc = player.location
                    val gridX = (loc.blockX / 1000.0).roundToInt()
                    val gridZ = (loc.blockZ / 1000.0).roundToInt()
                    val pos = "${gridX}k,${gridZ}k"

                    val discovered = customPlayer.visitedWorldSectors

                    if (discovered.isEmpty()) {
                        discovered.add(pos)
                        continue
                    }

                    if (!discovered.contains(pos)) {
                        if (discovered.size >= 20) {
                            discovered.remove(discovered.first())
                        }
                        discovered.add(pos)
                        customPlayer.addSkillXp(SkillType.LIBRARIAN, 200.0);
                    }
                }
            }
        }.runTaskTimer(OpenLab.getInstance(), 0L, 1200L)
        }
    }
}

