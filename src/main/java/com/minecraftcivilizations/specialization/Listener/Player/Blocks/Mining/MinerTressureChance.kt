package com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining

import com.minecraftcivilizations.specialization.Config.SpecializationConfig
import com.minecraftcivilizations.specialization.OpenLab
import com.minecraftcivilizations.specialization.Skill.SkillLevel
import com.minecraftcivilizations.specialization.Skill.SkillType
import com.minecraftcivilizations.specialization.player.CustomPlayerManager
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.loot.LootContext
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

class MinerTressureChance : Listener {
    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.block.y >= 64) return

        val itemInHand = event.player.inventory.itemInMainHand
        if (itemInHand.containsEnchantment(Enchantment.SILK_TOUCH)) return

        val toolType = itemInHand.type
        if (!toolType.name.contains("PICKAXE")) return


        val list = SpecializationConfig.minerConfig.getObject("TRESSURE_TRIGGERING_BLOCKS")

        val id = event.block.type.toString().uppercase()
        val chance = if (list.hasPath(id)) list.getDouble(id) else return

        val random = ThreadLocalRandom.current().nextFloat()
        if (random < chance) {
            triggerTreasure(event.player, event.block)
        }
    }

    private fun triggerTreasure(player: Player, block: Block) {
        val customPlayer = CustomPlayerManager.getCustomPlayer(player) ?: return
        val level = customPlayer.getSkillLevel(SkillType.MINER)

        // get tier between level i.e level = 3 -> random between 1-3
        val tierWeights: Map<String, Int> = SpecializationConfig.minerConfig.getConfigList("MINER_TREASURE_TIERS").associate { config ->
            config.getString("skill_level") to config.getInt("chance")
        }

        val maxTier = SkillLevel.getSkillLevelFromInt(level)
        val tier = selectWeightedTier(tierWeights, maxTier)

        val lootKey = NamespacedKey("openlabs", "treasure/tiers/${tier.lowercase()}-miner-treasure") ?: return  OpenLab.logger.warning { "invalid key" }
        val lootTable = Bukkit.getLootTable(lootKey) ?: return OpenLab.logger.warning { "Missing ${tier}-miner-treasure loot table" }

        val lootContext = LootContext.Builder(block.location)
            .killer(player)
            .luck(0f)
            .lootedEntity(player)
            .build()

        val inventory = Bukkit.createInventory(null, 27, "Treasure Chest: ${tier.lowercase()}")
        val centerLocation = block.location.clone().add(0.5, 0.5, 0.5)
        spawnLootEffect(centerLocation)
        lootTable.fillInventory(inventory, Random(), lootContext);
        Bukkit.getScheduler().runTaskLater(OpenLab.getInstance(), Runnable {
            player.openInventory(inventory)
        }, 30L)
    }


}

fun spawnLootEffect(location: Location) {
    val world: World = location.getWorld() ?: return

    // Play sounds
    world.playSound(location, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f)
    world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.7f, 1.5f)
    world.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.8f)


    // Spawn particles in a circle
    var i = 0
    while (i < 360) {
        val angle = Math.toRadians(i.toDouble())
        val x = cos(angle) * 1.5
        val z = sin(angle) * 1.5

        val particleLoc: Location = location.clone().add(x, 0.5, z)
        world.spawnParticle(Particle.END_ROD, particleLoc, 0, 0.0, 0.1, 0.0, 0.05)
        world.spawnParticle(Particle.VILLAGER_HAPPY, particleLoc, 0, 0.0, 0.1, 0.0, 0.1)
        i += 15
    }


    // Burst above chest
    location.add(0.0, 1.5, 0.0)
    world.spawnParticle(Particle.FIREWORKS_SPARK, location, 30, 0.5, 0.3, 0.5, 0.1)
    world.spawnParticle(Particle.END_ROD, location, 20, 0.3, 0.2, 0.3, 0.05)


    // Final sound
    world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 0.8f)
}

fun selectWeightedTier(tierWeights: Map<String, Int>, maxTier: SkillLevel): String {
    val allTiers = tierWeights.keys.toList()
    val maxTierIndex = maxTier.ordinal

    // Get tiers up to max level
    val availableTiers = allTiers.take(maxTierIndex + 1)
    val availableWeights = availableTiers.map { tierWeights[it] ?: 1 }

    // Weighted random selection
    val totalWeight = availableWeights.sum()
    var randomWeight = ThreadLocalRandom.current().nextInt(totalWeight)
    var cumulativeWeight = 0

    for (i in availableWeights.indices) {
        cumulativeWeight += availableWeights[i]
        if (randomWeight < cumulativeWeight) {
            return availableTiers[i]
        }
    }

    // Fallback (should never reach here)
    return availableTiers.last()
}