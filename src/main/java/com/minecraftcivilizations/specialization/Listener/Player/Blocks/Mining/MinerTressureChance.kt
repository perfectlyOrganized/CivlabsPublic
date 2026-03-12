package com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining

import com.minecraftcivilizations.specialization.Config.SpecializationConfig
import com.minecraftcivilizations.specialization.OpenLab
import com.minecraftcivilizations.specialization.Skill.SkillLevel
import com.minecraftcivilizations.specialization.Skill.SkillType
import com.minecraftcivilizations.specialization.util.EffectsUtil
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.loot.LootContext
import java.util.*
import java.util.concurrent.ThreadLocalRandom

class MinerTressureChance : Listener {
    companion object {
        fun selectWeightedTier(tierWeights: Map<String, Int>, maxTier: SkillLevel): SkillLevel {
            val allTiers = tierWeights.keys.toList()
            val maxTierLevel = maxTier.level
            if (maxTierLevel == 0) {
                return SkillLevel.values[0]
            }
            // Get tiers up to max level
            val availableTiers = allTiers.take(maxTierLevel)
            val availableWeights = availableTiers.map { tierWeights[it] ?: 1 }

            // Weighted random selection
            val totalWeight = availableWeights.sum()
            val randomWeight = ThreadLocalRandom.current().nextInt(totalWeight)
            var cumulativeWeight = 0

            for (i in availableWeights.indices) {
                cumulativeWeight += availableWeights[i]
                if (randomWeight < cumulativeWeight) {
                    return SkillLevel.valueOf(availableTiers[i]) ?: throw Exception("${availableTiers[i]} is not a valid SkillLevel")
                }
            }

            return SkillLevel.valueOf(availableTiers.last()) ?: throw Exception("${availableTiers.last()} is not a valid SkillLevel")
        }
    }
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
        val tier = Tressure.getTier(SkillType.MINER, player)
        val lootTable = Tressure.getLootTable(SkillType.MINER, tier)

        val lootContext = LootContext.Builder(player.location)
            .killer(player)
            .luck(0f)
            .build()

        val inventory = Bukkit.createInventory(null, 27, "Treasure Chest: ${tier.name.lowercase()}")
        val centerLocation = block.location.clone().add(0.5, 0.5, 0.5)
        EffectsUtil.spawnLootEffect(centerLocation, 100)
        lootTable.fillInventory(inventory, Random(), lootContext);

        Bukkit.getScheduler().runTaskLater(OpenLab.getInstance(), Runnable {
            player.openInventory(inventory)
        }, 30L)
    }

}



