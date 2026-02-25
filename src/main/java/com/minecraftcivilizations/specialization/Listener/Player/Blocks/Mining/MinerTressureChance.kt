package com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining

import com.minecraftcivilizations.specialization.Config.SpecializationConfig
import com.minecraftcivilizations.specialization.OpenLab
import com.minecraftcivilizations.specialization.Skill.SkillLevel
import com.minecraftcivilizations.specialization.Skill.SkillType
import com.minecraftcivilizations.specialization.player.CustomPlayerManager
import net.kyori.adventure.key.Key
import net.minecraft.world.item.Tier
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.loot.LootContext
import java.util.Random
import java.util.concurrent.ThreadLocalRandom

class MinerTressureChance : Listener {
    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val list = SpecializationConfig.minerConfig.getObject("TRESSURE_TRIGGERING_BLOCKS")

        val id = event.block.type.toString().uppercase()
        val chance = if (list.hasPath(id)) list.getDouble(id) else return

        val random = ThreadLocalRandom.current().nextFloat()
        if (random < chance) {
            val player = event.player
            triggerTreasure(player)
        }
    }

    private fun triggerTreasure(player: Player) {
            // Create a 27-slot inventory
        val inventory = Bukkit.createInventory(null, 27, "Treasure Chest")
        val customPlayer = CustomPlayerManager.getCustomPlayer(player) ?: return
        val level = customPlayer.getSkillLevel(SkillType.MINER)

        // get tier between level i.e level = 3 -> random between 1-3

        val tierWeights = mapOf(
            SkillLevel.NOVICE.name to 100,
            SkillLevel.APPRENTICE.name to 50,
            SkillLevel.JOURNEYMAN.name to 25,
            SkillLevel.EXPERT.name to 12,
            SkillLevel.MASTER.name to 6,
            SkillLevel.GRANDMASTER.name to 3,
        )

        val maxTier = SkillLevel.getSkillLevelFromInt(level)
        val tier = selectWeightedTier(tierWeights, maxTier)


        val lootKey = NamespacedKey(OpenLab.getInstance(), "treasure/tiers/${tier}-miner-treasure")
        val lootTable = Bukkit.getLootTable(lootKey) ?: return OpenLab.logger.warning { "Missing ${tier}-miner-treasure loot table" }

        val lootContext = LootContext.Builder(player.location)
            .lootedEntity(player)
            .killer(player)
            .luck(0.0f) // #TODO actually use luck?
            .build()

        val loot = lootTable.populateLoot(Random(), lootContext)
        // Fill every slot with gold ingots
        var slotIndex = 0
        for (item in loot) {
            if (slotIndex < inventory.size) {
                inventory.setItem(slotIndex, item)
                slotIndex++
            } else {
                break // Inventory full
            }
        }

        player.openInventory(inventory)
    }


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