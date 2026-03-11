package com.minecraftcivilizations.specialization.Skill

import com.minecraftcivilizations.specialization.Config.SpecializationConfig


class SkillLevel private constructor(
    val level: Int,
    val name: String,
    val displayName: String,
    val xpRequirement: Double
) {
    companion object {
        private var _values: List<SkillLevel> = listOf()
        val values: List<SkillLevel>
            get() {
                if (_values.isEmpty()) {
                    loadFromConfig()
                }
                return _values
            }

        fun loadFromConfig() {
            val configList = SpecializationConfig.skillsConfig.getConfigList("levels")
            _values = configList.mapIndexed { index, config ->
                SkillLevel(
                    index,
                    config.getString("name").uppercase(),
                    config.getString("display_name"),
                    config.getDouble("percent_requirement")
                )
            }
        }
        fun valueOf(name: String): SkillLevel? {
            val upperName = name.uppercase()
            return values.find { it.name.uppercase() == upperName }
        }
        fun getSkillLevelFromInt(level: Int): SkillLevel {
            if (level < 0) return values.first()
            if (level >= values.size) return values.last()
            return values[level]
        }

        fun getDisplayName(level: Int): String {
            return getSkillLevelFromInt(level).displayName
        }
    }

    override fun toString(): String {
        return this.name
    }
}
