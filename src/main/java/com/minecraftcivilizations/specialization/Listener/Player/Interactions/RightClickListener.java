package com.minecraftcivilizations.specialization.Listener.Player.Interactions;

import com.minecraftcivilizations.specialization.Config.ConfigFile;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.player.CustomPlayer;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager;
import com.minecraftcivilizations.specialization.Skill.Skill;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class RightClickListener implements Listener {

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        CustomPlayer customPlayer = CustomPlayerManager.INSTANCE.getCustomPlayer(player);

        Material mainType = player.getInventory().getItemInMainHand().getType();
        Material offType = player.getInventory().getItemInOffHand().getType();
        String mainKey = mainType.toString();
        String offKey = offType.toString();

        boolean bypass = player.getPotionEffect(PotionEffectType.LUCK) != null && player.isOp();
        ConfigFile config = SpecializationConfig.getCanUseItemConfig();
        List<String> blacklist = SpecializationConfig.getCanUseItemConfig().getStringList("blacklist");
        List<String> modBlacklist = SpecializationConfig.getCanUseItemConfig().getStringList("mod_blacklist");
        if (!bypass && (blacklist.contains(mainKey) || blacklist.contains(offKey))) {
            PlayerUtil.sendActionBar(player, Component.text("You are unable to use "+ mainKey + " or " + offKey).color(NamedTextColor.RED) );
            event.setCancelled(true);
            return;
        }

        boolean itemFoundInConfig = false;
        boolean canUse = false; // Start as false, change to true if ANY requirement is met

        for (Skill skill : customPlayer.getSkills()) {
            for (SkillLevel level : SkillLevel.Companion.getValues()) {
                String key = skill.getSkillType() + "_" + level;
                List<String> allowed = config.getConfig().hasPath(key) ? config.getStringList(key) : List.of();
                if (allowed.isEmpty()) continue;

                boolean mainAllowed = allowed.contains(mainKey);
                boolean offAllowed = allowed.contains(offKey);

                if (mainAllowed || offAllowed) {
                    itemFoundInConfig = true;
                    if (customPlayer.getSkillLevel(skill.getSkillType()) >= level.getLevel()) {
                        canUse = true;
                        break;
                    }
                }
            }
            if (canUse) break; // Break outer loop if we've found a valid combination
        }

        if (!canUse && !bypass && (modBlacklist.stream().anyMatch(mainKey::startsWith) && !player.hasCooldown(mainType)) ||
                (modBlacklist.stream().anyMatch(offKey::startsWith) && !player.hasCooldown(offType))) {
            PlayerUtil.sendActionBar(player, Component.text("You are unable to use "+ mainKey + " or " + offKey).color(NamedTextColor.RED) );
            event.setCancelled(true);
            return;
        }

        if (!canUse && itemFoundInConfig && !bypass) {
            PlayerUtil.sendActionBar(player, Component.text("You are unable to use "+ mainKey + " or " + offKey).color(NamedTextColor.RED) );
            event.setCancelled(true);
        }

        if (!(event.getAction() == Action.RIGHT_CLICK_BLOCK)) return;
        Block clicked = event.getClickedBlock();
        if(clicked == null) return;

        if (clicked.getType().name().equals("FARMERSDELIGHT_CUTTING_BOARD")) return;
        // Handle wooden reinforcement first - prevent placing logs when sneaking with logs in off-hand
        if (ReinforcementManager.isLog(player.getInventory().getItemInOffHand().getType()) && player.isSneaking()) {
            event.setCancelled(true); // Always prevent placing when sneaking with logs in off-hand

            if (!ReinforcementManager.isReinforced(clicked)) {
                // Wooden reinforcement - works at any class level (even classless)
                Material logType = player.getInventory().getItemInOffHand().getType();
                List<Block> blocks = getMultiBlocks(clicked);
                boolean success = false;
                for (Block block : blocks) {
                    if (ReinforcementManager.addWoodenReinforcement(player, block, logType)) {
                        success = true;
                    }
                }
                if (success) {
                    player.getInventory().getItemInOffHand().setAmount(player.getInventory().getItemInOffHand().getAmount() - 1);
                    PlayerUtil.message(player, Component.text("§6Wooden Reinforcement").color(NamedTextColor.GOLD).decorations(Set.of(TextDecoration.BOLD, TextDecoration.ITALIC), false));
                }
            }
            return;
        }

        if (ReinforcementManager.isReinforced(clicked)) return;

        // Light reinforcement (copper ingot) - check main hand first, then off-hand
        if (mainType == Material.COPPER_INGOT || offType == Material.COPPER_INGOT) {
            boolean useOffHand = mainType != Material.COPPER_INGOT;
            if (customPlayer.getSkillLevel(SkillType.BUILDER) >= SpecializationConfig.getReinforcementConfig().getInt("LIGHT_REINFORCEMENT_LEVEL")) {
                List<Block> blocks = getMultiBlocks(clicked);
                boolean success = false;
                for (Block block : blocks) {
                    if (ReinforcementManager.addReinforcement(player, block, false)) {
                        success = true;
                    }
                }
                if (success) {
                    if (useOffHand) {
                        player.getInventory().getItemInOffHand().setAmount(player.getInventory().getItemInOffHand().getAmount() - 1);
                    } else {
                        player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
                    }
                    PlayerUtil.message(player, Component.text("§7Lightly Reinforced").color(NamedTextColor.WHITE).decorations(Set.of(TextDecoration.BOLD, TextDecoration.ITALIC), false));
                }
            }
        }
        // Heavy reinforcement (iron ingot) - check main hand first, then off-hand
        else if (mainType == Material.IRON_INGOT || offType == Material.IRON_INGOT) {
            boolean useOffHand = mainType != Material.IRON_INGOT;

            if (customPlayer.getSkillLevel(SkillType.BUILDER) >= SpecializationConfig.getReinforcementConfig().getInt("HEAVY_REINFORCEMENT_LEVEL")) {
                List<Block> blocks = getMultiBlocks(clicked);
                boolean success = false;
                for (Block block : blocks) {
                    if (ReinforcementManager.addReinforcement(player, block, true)) {
                        success = true;
                    }
                }
                if (success) {
                    if (useOffHand) {
                        player.getInventory().getItemInOffHand().setAmount(player.getInventory().getItemInOffHand().getAmount() - 1);
                    } else {
                        player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
                    }
                    PlayerUtil.message(player, Component.text("§7Heavily Reinforced").color(NamedTextColor.WHITE).decorations(Set.of(TextDecoration.BOLD, TextDecoration.ITALIC), false));
                }
            }
        }

    }


    private List<Block> getMultiBlocks(Block block) {
        List<Block> blocks = new ArrayList<>();
        blocks.add(block);
        if (block.getBlockData() instanceof Door door) {
            blocks.add(block.getRelative(door.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP));
        } else if (block.getBlockData() instanceof Bed bed) {
            blocks.add(block.getRelative(bed.getPart() == Bed.Part.HEAD ? bed.getFacing().getOppositeFace() : bed.getFacing()));
        } else if (block.getBlockData() instanceof Bisected bisected) {
            blocks.add(block.getRelative(bisected.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP));
        }
        return blocks;
    }
}
