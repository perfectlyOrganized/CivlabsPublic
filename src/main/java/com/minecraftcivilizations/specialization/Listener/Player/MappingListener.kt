package com.minecraftcivilizations.specialization.Listener.Player

import com.minecraftcivilizations.specialization.Config.SpecializationConfig
import com.minecraftcivilizations.specialization.OpenLab
import com.minecraftcivilizations.specialization.Skill.SkillType
import com.minecraftcivilizations.specialization.player.CustomPlayerManager
import com.minecraftcivilizations.specialization.util.PlayerUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.scheduler.BukkitTask
import org.joml.Vector2i
import org.joml.Vector3i
import java.util.*
import java.util.function.Function
import kotlin.math.floor
import kotlin.math.sqrt


object MappingListener : Listener {
    private var initialized = false
    private val activePlayers: MutableSet<UUID> = HashSet<UUID>()
    private var scanTask: BukkitTask? = null
    const val drawDistance = 10;

    init {
        if (!initialized) {
            initialized = true
            startScanTask()
        }
    }
    private fun startScanTask() {
        scanTask?.cancel()
        scanTask = Bukkit.getScheduler().runTaskTimer(OpenLab.getInstance(), Runnable {
            for (playerId in activePlayers) {
                val player = Bukkit.getPlayer(playerId) ?: continue
                if (!player.isOnline) continue
                render3x3Grid(player)
            }

        }, 0L, 5L)
    }

    private fun render3x3Grid(player: Player) {
        val y = player.location.y
        val x = player.location.x
        val z = player.location.z

        val playerCellX = floor(x / 10).toInt()
        val playerCellZ = floor(z / 10).toInt()

        for (cellXOffset in -1..1) {
            for (cellZOffset in -1..1) {
                val cellX = playerCellX + cellXOffset
                val cellZ = playerCellZ + cellZOffset

                val cellMinX = cellX * 10
                val cellMaxX = cellMinX + 10
                val cellMinZ = cellZ * 10
                val cellMaxZ = cellMinZ + 10

                val (startX, endX) = if (cellMinX < cellMaxX) cellMinX to cellMaxX else cellMaxX to cellMinX
                val (startZ, endZ) = if (cellMinZ < cellMaxZ) cellMinZ to cellMaxZ else cellMaxZ to cellMinZ

                val northWest = Location(player.world, startX.toDouble(), y, startZ.toDouble())
                val northEast = Location(player.world, endX.toDouble(), y, startZ.toDouble())
                val southWest = Location(player.world, startX.toDouble(), y, endZ.toDouble())
                val southEast = Location(player.world, endX.toDouble(), y, endZ.toDouble())

                val getParticle = Function<Vector3i, Particle>{
                    offset -> if (isCellDiscovered(player, cellX + offset.x, cellZ + offset.z) || isCellDiscovered(player, cellX, cellZ))  Particle.VILLAGER_HAPPY else Particle.WAX_OFF
                }

                drawLine(player, northWest, northEast, getParticle.apply(Vector3i(0,0,-1)))
                drawLine(player, southWest, southEast, getParticle.apply(Vector3i(0,0,1)))
                drawLine(player, northWest, southWest,  getParticle.apply(Vector3i(-1,0,0)))
                drawLine(player, northEast, southEast, getParticle.apply(Vector3i(1,0,0)))
            }
        }
    }

    private fun drawLine(player: Player, start: Location, end: Location, particle: Particle) {
        val world = player.world
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z

        val distance = sqrt(dx * dx + dy * dy + dz * dz)

        val steps = (distance).toInt()
        if (steps <= 0) return

        for (i in 0..steps) {
            val x = start.x + (dx / steps * i)
            val y = start.y + (dy / steps * i)
            val z = start.z + (dz / steps * i)

            val loc = Location(world, x, y, z)
            if (player.location.distance(loc) > drawDistance) continue
            player.spawnParticle(particle, loc, 1, 0.0, 0.0, 0.0, 0.0)
        }
    }

    private fun isCellDiscovered(player: Player, cellX: Int, cellZ: Int): Boolean {
        val customPlayer = CustomPlayerManager.getCustomPlayerOrThrow(player)
        val key = "$cellZ+$cellX"
        return customPlayer.mappedAreas.contains(key)
    }

    @EventHandler
    fun onPlayerItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        Bukkit.getScheduler().runTaskLater(OpenLab.getInstance(), Runnable { checkPlayerItem(player) }, 1L)
    }
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.getPlayer()
        checkPlayerItem(player)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.getPlayer()
        activePlayers.remove(player.uniqueId)
    }

    @EventHandler
    fun useItem(event: PlayerInteractEvent) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;
        val player = event.player
        if (!isHoldingTriggerItem(player)) return
        val paper = if (player.inventory.itemInMainHand.type == Material.PAPER) player.inventory.itemInMainHand else player.inventory.itemInOffHand
        val customPlayer = CustomPlayerManager.getCustomPlayerOrThrow(player)
        val cellX = floor(player.location.x / 10).toInt()
        val cellZ = floor(player.location.z / 10).toInt()
        val key = "$cellZ+$cellX"
        if (customPlayer.mappedAreas.contains(key)) {
            PlayerUtil.sendActionBar(player, Component.text("This cell has already been discovered!", NamedTextColor.DARK_RED))
            return
        }
        customPlayer.mappedAreas.add(key)
        customPlayer.addSkillXp(SkillType.LIBRARIAN, SpecializationConfig.librarianConfig.getDouble("discover_cell_xp"))
        player.playSound(player.location, Sound.ENTITY_VILLAGER_WORK_CARTOGRAPHER, 0.7F, 1.0F)
        paper.amount--
    }


    private fun checkPlayerItem(player: Player) {
        if (!player.isOnline) return

        val playerId = player.uniqueId
        val isHoldingItem = isHoldingTriggerItem(player)
        val wasActive: Boolean = activePlayers.contains(playerId)

        if (isHoldingItem && !wasActive) {
            activePlayers.add(playerId)
        } else if (!isHoldingItem && wasActive) {
            activePlayers.remove(playerId)
        }
    }
    private fun isHoldingTriggerItem(player: Player): Boolean {
        return player.inventory.itemInMainHand.type == Material.PAPER || player.inventory.itemInOffHand.type == Material.PAPER
    }
}


