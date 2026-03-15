package com.minecraftcivilizations.specialization.Skill;

import com.minecraftcivilizations.specialization.Config.ConfigFile;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Data.Pair;
import com.typesafe.config.ConfigException;
import org.bukkit.Material;

public enum SkillType {

    FARMER,
    BUILDER,
    MINER,
    HEALER,
    LIBRARIAN,
    GUARDSMAN,
    BLACKSMITH;

    public String getSkillDescription() {
        return SpecializationConfig.getSkillsConfig().getString(this + "_DESCRIPTION");
    }

    public Material getSkillWorkstation() {
        return Material.getMaterial(SpecializationConfig.getSkillsConfig().getString(this + "_WORKSTATION"));
    }

    /**
     * Optimized Level from XP ⚡
     */
    public static int getLevelFromXP(double xp) {
        if (xp <= 0) return 0;

        if (Skill.CACHED_LEVELS == null) Skill.Companion.initCacheXPLevelFormula();
        double[] cached_levels = Skill.CACHED_LEVELS;
        int last_level = cached_levels.length - 1;

        for (int lvl = 0; lvl < last_level; lvl++) {
            if (xp < cached_levels[lvl + 1]) return lvl;
        }

        return last_level;
    }
    public String getDisplayName() {
        return SkillType.getDisplayName(this);
    }
    public static String getDisplayName(SkillType skillType) {
        return capitalize(skillType.name().toLowerCase());
    }
    public static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
    public static Pair<SkillType, Double> getSkillXpFromConfig(ConfigFile config, String key) {
        Double xp = 0.0;
        SkillType skillType = SkillType.BUILDER;
        for (SkillType skill : SkillType.values()) {
            try {
                xp = config.getDouble(skill.name() + "." + key);
            } catch(ConfigException.Missing _e) {}
            if (xp != 0) {
                skillType = skill;
                break;
            }
        }
        return new Pair<>(skillType,xp);

    }

}
