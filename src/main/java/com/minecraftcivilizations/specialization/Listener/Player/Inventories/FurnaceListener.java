package com.minecraftcivilizations.specialization.Listener.Player.Inventories;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.typesafe.config.ConfigException;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import minecraftcivilizations.com.minecraftCivilizationsCore.Options.Pair;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.inventory.ItemStack;

public class FurnaceListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onSmelt(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        Material extracted = event.getItemType();
        int amount = event.getItemAmount();

        if (event.getBlock().getType() == Material.FURNACE) {
            furnaceSmelt(player, new ItemStack(extracted, amount), amount);
        } else if (event.getBlock().getType() == Material.FURNACE_MINECART) {
            furnaceSmelt(player, new ItemStack(extracted, amount), amount);
        } else if (event.getBlock().getType() == Material.SMOKER) {
            smokerSmelt(player, new ItemStack(extracted, amount), amount);
        } else if (event.getBlock().getType() == Material.BLAST_FURNACE) {
            blastSmelt(player, new ItemStack(extracted, amount), amount);
        }

    }

    private void furnaceSmelt(Player player, ItemStack item, int amount) {
        CustomPlayer customPlayer = Specialization.customPlayerManager.getCustomPlayer(player.getUniqueId());
        Double xp = 0.0;
        SkillType skillType = SkillType.MINER;
        for (SkillType skill : SkillType.values()) {
            try {
                xp = SpecializationConfig.getXpGainFromSmeltingConfig().getDouble(skill.name() + "." + item.getType());
            } catch(ConfigException.Missing _e) {}
            if (xp != 0) {
                skillType = skill;
                break;
            }
        }
        customPlayer.addSkillXp(skillType, xp * amount);
    }

    private void smokerSmelt(Player player, ItemStack item, int amount) {
        CustomPlayer customPlayer = Specialization.customPlayerManager.getCustomPlayer(player.getUniqueId());
        Double xp = 0.0;
        SkillType skillType = SkillType.MINER;
        for (SkillType skill : SkillType.values()) {
            try {
                xp = SpecializationConfig.getXpGainFromSmokingConfig().getDouble(skill.name() + "." + item.getType());
            } catch(ConfigException.Missing _e) {}
            if (xp != 0) {
                skillType = skill;
                break;
            }
        }
        customPlayer.addSkillXp(skillType, xp * amount);
    }

    private void blastSmelt(Player player, ItemStack item, int amount) {
        CustomPlayer customPlayer = Specialization.customPlayerManager.getCustomPlayer(player.getUniqueId());
        Double xp = 0.0;
        SkillType skillType = SkillType.MINER;
        for (SkillType skill : SkillType.values()) {
            try {
                xp = SpecializationConfig.getXpGainFromBlastingConfig().getDouble(skill.name() + "." + item.getType());
            } catch(ConfigException.Missing _e) {}
            if (xp != 0) {
                skillType = skill;
                break;
            }
        }
        customPlayer.addSkillXp(skillType, xp * amount);
    }
}
