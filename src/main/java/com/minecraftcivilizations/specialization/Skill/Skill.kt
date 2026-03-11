package com.minecraftcivilizations.specialization.Skill

import com.minecraftcivilizations.specialization.Config.SpecializationConfig
import com.minecraftcivilizations.specialization.StaffTools.Debug
import com.minecraftcivilizations.specialization.player.CustomPlayerManager.getCustomPlayerOrThrow
import lombok.Getter
import lombok.Setter
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.properties.Delegates

class Skill {
    @Getter
    @Setter
    var skillType: SkillType

    var xp = 0.0
        private set
    @Getter
    private var lastUpdate: Long = 0
    constructor(skillType: SkillType, xp: Double, lastUpdate: Long) {
        this.skillType = skillType
        this.xp = xp
        this.lastUpdate = lastUpdate
    }
    fun applyXp(player: Player, appliedXp: Double, allowNegative: Boolean) {
        var appliedXp = appliedXp
        val customPlayer = getCustomPlayerOrThrow(player)
        val positiveXP = appliedXp > 0
        if (!customPlayer.classXPToggles[this.skillType]!! && positiveXP) return
        if (appliedXp == 0.0) return

        if (!allowNegative) appliedXp = max(appliedXp, 0.0)
        this.xp += appliedXp
        if (this.xp < 0) this.xp = 0.0

        this.xp = (this.xp * 10.0).roundToInt() / 10.0
        val component =
            Component.text("+$appliedXp ").color(if (positiveXP) NamedTextColor.GREEN else NamedTextColor.RED)
        val comp = Component.text(" " + skillType.name + " ").color(NamedTextColor.WHITE)
            .append(component)
            .append(Component.text("(" + this.xp + ")").color(NamedTextColor.GRAY))
        Debug.broadcast("xp_" + player.name, comp)
        this.lastUpdate = System.currentTimeMillis()
    }

    companion object {
        var MAX_LEVEL by Delegates.notNull<Int>()
        lateinit var CACHED_LEVELS: DoubleArray

        fun initCacheXPLevelFormula() {
            val xp = SpecializationConfig.skillsConfig.getConfigList("levels").map { a -> a.getDouble("xp")}
            CACHED_LEVELS = DoubleArray(xp.size)
            for ((index, xp) in xp.withIndex()) {
                CACHED_LEVELS[index] = xp 
            }
            MAX_LEVEL = xp.size
        }

        /**
         * Optimized AF ⚡
         * Uses a pre-cached lookup, you're welcome.
         */
        fun getXPNeededForLevel(level: Int): Double {
            return CACHED_LEVELS[max(0, min(MAX_LEVEL-1, level))]
        }

        fun mapValue(x: Double, in_min: Double, in_max: Double, out_min: Double, out_max: Double): Double {
            // Handle division by zero case when in_max equals in_min
            if (in_max == in_min) {
                return out_min // Return minimum output value when input range is zero
            }
            return out_min + (x - in_min) * (out_max - out_min) / (in_max - in_min)
        }
    }
}