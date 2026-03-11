package com.minecraftcivilizations.specialization.Skill;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.OpenLab;
import com.minecraftcivilizations.specialization.player.CustomPlayer;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class XPReductionTask extends BukkitRunnable {
    private HashMap<UUID, HashMap<SkillType, Double>> lastXpArray = new HashMap<>();
    @Override
    public void run() {
        for (CustomPlayer customPlayer : CustomPlayerManager.INSTANCE.getPlayers()) {
            List<Skill> skills = customPlayer.getSkills();
            HashMap<SkillType, Double> xp_table = lastXpArray.getOrDefault(customPlayer.getUuid(),
                    customPlayer.getSkills().stream()
                    .collect(Collectors.toMap(
                            Skill::getSkillType,
                            Skill::getXp,
                            (a, b) -> a,
                            HashMap::new
                    )) );

            for (Skill skill : skills) {
                double last_xp = xp_table.get(skill.getSkillType());
                double difference = last_xp - skill.getXp();

                String xpGainRequirementKey = skill.getSkillType().name().toUpperCase(Locale.ROOT)+"_XP_GAIN_REQUIREMENT_PER_LEVEL";

                int level = SkillType.getLevelFromXP(skill.getXp());

                if (0 < difference || SpecializationConfig.getSkillsConfig().getDouble(xpGainRequirementKey)*level < difference) return;
                Player player = Bukkit.getPlayer(customPlayer.getUuid());
                String xpDecayKey = skill.getSkillType().name().toUpperCase(Locale.ROOT)+"_XP_DECAY";
                skill.applyXp(player, -skill.getXp()* SpecializationConfig.getSkillsConfig().getDouble(xpDecayKey), true);
            }
        }
    }
}