package com.minecraftcivilizations.specialization.util

import org.bukkit.entity.Player
import java.util.*

object CooldownManager {
    private val playerCooldowns = HashMap<UUID?, HashMap<String?, Long?>>()

    fun setCooldown(player: Player, ability: String?, seconds: Int) {
        val uuid = player.uniqueId
        playerCooldowns.computeIfAbsent(uuid) { k: UUID? -> HashMap<String?, Long?>() }[ability] = System.currentTimeMillis() + (seconds * 1000L)
    }

    fun isOnCooldown(player: Player, key: String?): Boolean {
        val uuid = player.uniqueId
        if (!playerCooldowns.containsKey(uuid)) return false

        val abilities: HashMap<String?, Long?> = playerCooldowns.get(uuid)!!
        if (!abilities.containsKey(key)) return false

        val expiryTime: Long = abilities.get(key)!!
        return System.currentTimeMillis() < expiryTime
    }

    fun getTimeLeft(player: Player, ability: String?): Int {
        if (!isOnCooldown(player, ability)) return 0

        val expiryTime: Long = playerCooldowns.get(player.uniqueId)!!.get(ability)!!
        val timeLeft = expiryTime - System.currentTimeMillis()
        return (timeLeft / 1000).toInt()
    }
}