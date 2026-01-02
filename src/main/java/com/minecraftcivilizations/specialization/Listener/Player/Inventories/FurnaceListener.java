package com.minecraftcivilizations.specialization.Listener.Player.Inventories;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
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
        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        Pair<SkillType, Double> pair = SpecializationConfig.getXpGainFromSmeltingConfig().get(item.getType(), new TypeToken<>() {});
        if (pair == null) {
            Specialization.getInstance().getLogger().warning("No XP gain configuration found for furnace smelted item: " + item.getType());
        }
        customPlayer.addSkillXp(pair.firstValue(), pair.secondValue() * amount);
    }

    private void smokerSmelt(Player player, ItemStack item, int amount) {
        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        Pair<SkillType, Double> pair = SpecializationConfig.getXpGainFromSmokingConfig().get(item.getType(), new TypeToken<>() {});
        if (pair == null) {
            Specialization.getInstance().getLogger().warning("No XP gain configuration found for smoker smelted item: " + item.getType());
        }
        customPlayer.addSkillXp(pair.firstValue(), pair.secondValue() * amount);
    }

    private void blastSmelt(Player player, ItemStack item, int amount) {
        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        Pair<SkillType, Double> pair = SpecializationConfig.getXpGainFromBlastingConfig().get(item.getType().name(), new TypeToken<>() {});
        if (pair == null) {
            Specialization.getInstance().getLogger().warning("No XP gain configuration found for blast smelted item: " + item.getType());
        }
        customPlayer.addSkillXp(pair.firstValue(), pair.secondValue() * amount);
    }
}
