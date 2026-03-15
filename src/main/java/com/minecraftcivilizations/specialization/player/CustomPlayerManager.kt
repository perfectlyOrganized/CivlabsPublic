package com.minecraftcivilizations.specialization.player

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.minecraftcivilizations.specialization.OpenLab
import com.minecraftcivilizations.specialization.Skill.Skill
import com.minecraftcivilizations.specialization.Skill.SkillType
import com.minecraftcivilizations.specialization.Skill.XPReductionTask
import lombok.Setter
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.v1_20_R1.entity.CraftPlayer
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import virtuoel.pehkui.api.ScaleTypes
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.math.min

object CustomPlayerManager : Listener {
    private val customPlayers = ConcurrentHashMap<UUID, CustomPlayer>()
    private val diskPlayers = HashSet<UUID>()
    @Setter
    private val customPlayerClass: Class<out CustomPlayer> = CustomPlayer::class.java

    val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    init {
        val playerFolder = File(OpenLab.getInstance().dataFolder, "players")
        playerFolder.mkdirs()
        playerFolder.listFiles { file -> file.extension == "json" }
            ?.forEach { file ->
                try {
                    val uuid = UUID.fromString(file.nameWithoutExtension)
                    diskPlayers.add(uuid)
                } catch (e: IllegalArgumentException) {
                    // Invalid filename, skip
                }
        }

        XPReductionTask().runTaskTimer(OpenLab.getInstance(), 12000L, 12000L)
    }

    fun getCustomPlayer(source: Any?): CustomPlayer? {
        return when (source) {
            is UUID -> customPlayers[source]
            is Player -> customPlayers[source.uniqueId]
            is HumanEntity -> customPlayers[source.uniqueId]
            is PlayerEvent -> getCustomPlayer(source.player)
            else -> null
        }
    }
    fun getCustomPlayerOrThrow(source: Any?): CustomPlayer {
        val customPlayer = getCustomPlayer(source);
        return customPlayer ?: throw Exception("CustomPlayer not found")
    }


    fun saveAll() {
        customPlayers.keys().asIterator().forEachRemaining(Consumer { uuid: UUID? -> this.save(uuid!!) })
    }
    fun hasCustomPlayer(uuid: UUID): Boolean {
        return diskPlayers.contains(uuid)
    }

    @Synchronized
    fun load(uuid: UUID): CustomPlayer {
        customPlayers[uuid]?.let {
            return it
        }

        val playerFile = File(OpenLab.getInstance().dataFolder, "players/$uuid.json")

        if (!hasCustomPlayer(uuid)) {
            return createNewPlayer(uuid)
        }

        return playerFile.bufferedReader().use { reader ->
            val customPlayer = gson.fromJson(reader, customPlayerClass)
            customPlayer.migrate()
            customPlayers[uuid] = customPlayer
            customPlayer
        }
    }

    private fun createNewPlayer(uuid: UUID): CustomPlayer {
        val customPlayer = CustomPlayer(uuid).apply {
            name = Component.text(OpenLab.localNameGenerator.nextName())
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)
            height = Skill.mapValue(Math.random(), 0.0, 1.0, .85, 1.0)
            skills = SkillType.entries.map {
                Skill(it, 0.0, System.currentTimeMillis())
            }.toMutableList()
            migrate()
        }

        Bukkit.getScheduler().runTaskAsynchronously(OpenLab.getInstance(),Runnable {
            save(customPlayer)
        })
        diskPlayers.add(customPlayer.uuid)

        customPlayers[uuid] = customPlayer
        return customPlayer
    }

    fun save(uuid: UUID) {
        val customPlayer = getCustomPlayerOrThrow(uuid);
        save(customPlayer);
    }

    fun save(customPlayer: CustomPlayer) {
        val playerFile = File(OpenLab.getInstance().dataFolder.toString() + "/players/", "${customPlayer.uuid}.json")
        try {
            playerFile.writeText(gson.toJson(customPlayer, customPlayerClass))
            diskPlayers.add(customPlayer.uuid)
        } catch (e: IOException) {
            OpenLab.logger.severe("Couldn't save custom player ${customPlayer.uuid}: ${e.message}")
        }
    }

    fun removeCustomPlayer(player: UUID) {
        if (getCustomPlayer(player) != null) {
            save(player)
            customPlayers.remove(player)
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        event.joinMessage = null
        val player = event.getPlayer()
        val customPlayer = getCustomPlayer(player.uniqueId)
        if (customPlayer != null) {
            OpenLab.getInstance().applyCustomName(player, customPlayer.name)
            applyPlayerCustomizations(player)
            customPlayer.applyEffects(player)
        } else {
            Bukkit.getScheduler().runTaskLater(OpenLab.getInstance(), Runnable {
                if (player.isOnline) {
                    val retryPlayer = getCustomPlayer(player.uniqueId)
                    if (retryPlayer != null) {
                        OpenLab.getInstance().applyCustomName(player, retryPlayer.name)
                        applyPlayerCustomizations(player)
                        retryPlayer.applyEffects(player)
                    } else {
                        OpenLab.logger.warning("CustomPlayer still not found for ${player.name} after delay")
                        player.kickPlayer("Player data could not be properly loaded.")
                    }
                }
            }, 1L)
        }
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        Bukkit.getScheduler().runTaskLater(OpenLab.getInstance(), Runnable {
            if (player.isOnline) {
                applyPlayerCustomizations(player)
                val customPlayer = getCustomPlayerOrThrow(player.uniqueId)
                customPlayer.applyEffects(player)
            }
        }, 2L)
    }

    private fun applyPlayerCustomizations(player: Player) {
        val customPlayer = getCustomPlayerOrThrow(player.uniqueId)

        val mcEntity = (player as CraftPlayer).handle
        val scaleData = ScaleTypes.HEIGHT.getScaleData(mcEntity)
        scaleData.scale = customPlayer.height.toFloat()
        scaleData.tick()
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        event.quitMessage =  null
        val uuid = event.player.uniqueId
        if (!hasCustomPlayer(uuid)) return

        val customPlayer = getCustomPlayerOrThrow(uuid)
        customPlayer.isWasDownedOnLogout = customPlayer.isDowned
        if (customPlayer.isDowned) customPlayer.isDowned = false

        removeCustomPlayer(uuid)
    }

    @EventHandler
    fun onPreJoin(event: AsyncPlayerPreLoginEvent) {
        val uuid = event.uniqueId
        load(uuid);
    }

    fun getPlayers(): List<CustomPlayer> {
        return Bukkit.getOnlinePlayers().mapNotNull { player ->
            getCustomPlayer(player)
        }
    }
}