package com.minecraftcivilizations.specialization.Combat

import com.minecraftcivilizations.specialization.Config.SpecializationConfig
import com.minecraftcivilizations.specialization.OpenLab
import com.minecraftcivilizations.specialization.Skill.SkillType
import com.minecraftcivilizations.specialization.player.CustomPlayerManager
import org.bukkit.NamespacedKey
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.ln
import kotlin.math.min

class GuardsmanDamage(combatManager: CombatManager) : Listener {
    var plugin: OpenLab
    var MAX_HEALTH_KEY: NamespacedKey? = null
    var combatManager: CombatManager?
    private val LN_2 = ln(2.0)
    init {
        this.combatManager = combatManager
        this.plugin = combatManager.plugin
        this.plugin.getServer().getPluginManager().registerEvents(this, plugin)
    }

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val player = event.entity as? Player ?: return
        val customPlayer = CustomPlayerManager.getCustomPlayerOrThrow(player)
        // Check if the damager is a mob and the victim is a player
        val level = customPlayer.getSkillLevel(SkillType.GUARDSMAN)
        val damageReduction = SpecializationConfig.guardsmanConfig.getDoubleList("damage_reduction")[level] ?: 0.0
        if (event.damager is Monster) {

            val originalDamage = event.damage
            val reducedDamage = originalDamage * (1.0-damageReduction)
            event.damage = reducedDamage
        }
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        val player = event.entity.killer ?: return // Only if killed by a player
        val customPlayer = CustomPlayerManager.getCustomPlayerOrThrow(player)
        val level = customPlayer.getSkillLevel(SkillType.GUARDSMAN)
        if (level < 1) return

        // Calculate bonus looting chance (30% extra)
        val bonusMultiplier = 1 + 0.3 * ln((level + 1).toDouble()) / LN_2


        // Modify each drop

        for (drop in event.drops) {
            val amount = drop.amount
            val exactAmount = amount * bonusMultiplier

            val guaranteedAmount = exactAmount.toInt()

            val fractionalPart = exactAmount - guaranteedAmount

            var finalAmount = guaranteedAmount

            if (fractionalPart > 0 && ThreadLocalRandom.current().nextDouble() < fractionalPart) {
                finalAmount++
            }

            drop.amount = finalAmount
        }
    }
}

