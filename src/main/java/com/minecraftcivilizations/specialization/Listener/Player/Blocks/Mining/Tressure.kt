package com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining

import com.minecraftcivilizations.specialization.Config.SpecializationConfig
import com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining.MinerTressureChance.Companion.selectWeightedTier
import com.minecraftcivilizations.specialization.Skill.SkillLevel
import com.minecraftcivilizations.specialization.Skill.SkillType
import com.minecraftcivilizations.specialization.player.CustomPlayerManager
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.loot.LootTable

object Tressure {
    fun getTier(skillType: SkillType, player: Player): SkillLevel {
        val customPlayer = CustomPlayerManager.getCustomPlayerOrThrow(player)
        val level = customPlayer.getSkillLevel(skillType)
        val tierWeights: Map<String, Int> = SpecializationConfig.skillsConfig.getConfigList(skillType.name.uppercase() + "_TREASURE_TIERS").associate { config ->
            config.getString("skill_level") to config.getInt("chance")
        }
        val maxTier = SkillLevel.getSkillLevelFromInt(level)
        val tier = selectWeightedTier(tierWeights, maxTier)
        return tier
    }
    fun getLootTable(skillType: SkillType, tier: SkillLevel): LootTable {
        val skillName = skillType.name.lowercase()

        val lootKey = NamespacedKey("openlabs", "$skillName/tier/${tier.name}".lowercase())
        val lootTable = Bukkit.getLootTable(lootKey) ?: throw Exception("$skillName/tier/${tier.name} not found!".lowercase())
        return lootTable
    }
}