package com.minecraftcivilizations.specialization.player

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.minecraftcivilizations.specialization.OpenLab
import com.minecraftcivilizations.specialization.Skill.Skill
import com.minecraftcivilizations.specialization.Skill.SkillType
import com.minecraftcivilizations.specialization.Skill.XPReductionTask
import com.minecraftcivilizations.specialization.util.PlayerUtil
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
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import virtuoel.pehkui.api.ScaleTypes
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer

object CustomPlayerManager : Listener {
    private val customPlayers = ConcurrentHashMap<UUID, CustomPlayer>()
    private val diskPlayers = HashSet<UUID>()
    @Setter
    private val customPlayerClass: Class<out CustomPlayer> = CustomPlayer::class.java

    val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    init {
        val playerFolder = File(OpenLab.getInstance().dataFolder, "players")

        if (playerFolder.exists()) {
            playerFolder.listFiles { file -> file.extension == "json" }
                ?.forEach { file ->
                    try {
                        val uuid = UUID.fromString(file.nameWithoutExtension)
                        diskPlayers.add(uuid)
                    } catch (e: IllegalArgumentException) {
                        // Invalid filename, skip
                    }
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
    fun isCustomPlayerOnline(uuid: UUID): Boolean {
        return customPlayers.contains(uuid)
    }
    fun hasCustomPlayer(uuid: UUID): Boolean {
        return diskPlayers.contains(uuid)
    }

    fun load(uuid: UUID): CustomPlayer {
        val folder = File(OpenLab.getInstance().dataFolder.toString() + "/players/")
        folder.mkdirs()

        if (!hasCustomPlayer(uuid)) {
            val customPlayer = CustomPlayer(uuid);
            customPlayer.name = Component.text(OpenLab.localNameGenerator.nextName()).color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)
            val height = Skill.mapValue(Math.random(), 0.0, 1.0, .85, 1.0)
            customPlayer.height = height
            for (skill in SkillType.entries) {
                val skill1 = Skill(skill, 0.0, System.currentTimeMillis())
                skill1.skillType = skill
                customPlayer.skills.add(skill1)
            }
            return addCustomPlayer(customPlayer);
        }

        val reader = FileReader(OpenLab.getInstance().dataFolder.toString() + "/players/" + uuid + ".json")
        val customPlayer: CustomPlayer = gson.fromJson(reader, customPlayerClass)
        customPlayer.skills = customPlayer.skills
        customPlayer.preferredSkill = customPlayer.preferredSkill
        customPlayer.height = customPlayer.height
        customPlayer.isAdvancedClassesGUIEnabled = customPlayer.isAdvancedClassesGUIEnabled
        customPlayer.isSoundEnabled = customPlayer.isSoundEnabled
        customPlayer.isNewRecipeGUIIteration = customPlayer.isNewRecipeGUIIteration
        customPlayer.analyticPlayerData = customPlayer.analyticPlayerData
        customPlayer.additionUnlockedRecipes.addAll(customPlayer.additionUnlockedRecipes)

        customPlayers[uuid] = customPlayer
        return customPlayer
    }

    fun save(uuid: UUID) {
        val folder = File(OpenLab.getInstance().dataFolder.toString() + "/players/")
        folder.mkdirs()
        try {
            FileWriter(
                OpenLab.getInstance().dataFolder.toString() + "/players/" + uuid.toString() + ".json"
            ).use { writer ->
                val json: String = gson.toJson(getCustomPlayer(uuid), customPlayerClass)
                writer.write(json)
            }
            diskPlayers.add(uuid)
        } catch (e: IOException) {
            OpenLab.logger.severe(String.format("Couldn't save %s custom player", uuid))
        }
    }

    fun addCustomPlayer(player: CustomPlayer): CustomPlayer {
        if (getCustomPlayer(player.uuid) != null) {
            customPlayers.remove(player.uuid)
        }
        customPlayers[player.uuid] = player
        return player
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
        val customPlayer = load(player.uniqueId);
        OpenLab.getInstance().applyCustomName(player, customPlayer.name)
        val mcEntity = (player as CraftPlayer).handle;
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
        if (!OpenLab.playerUtilMap.containsKey(uuid)) {
            OpenLab.playerUtilMap[uuid] = PlayerUtil(uuid)
        }
        load(uuid);
    }

    fun getPlayers(): List<CustomPlayer?> {
        return Bukkit.getOnlinePlayers().map { player ->
            getCustomPlayer(player)
        }
    }
}