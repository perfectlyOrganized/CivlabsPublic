package com.minecraftcivilizations.specialization.Listener.Player.Interactions;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Listener.Player.LocalChat;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RightClickListener implements Listener {

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (!(event.getAction() == Action.RIGHT_CLICK_BLOCK)) return;
        Block clicked = event.getClickedBlock();
        if(clicked == null) return;

        if(clicked.getType().equals(Material.SPAWNER)){
            event.setCancelled(true);
        }

        Player player = event.getPlayer();

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
                    player.swingHand(EquipmentSlot.OFF_HAND);
                    player.getInventory().getItemInOffHand().setAmount(player.getInventory().getItemInOffHand().getAmount() - 1);
                    PlayerUtil.message(player, Component.text("§6Wooden Reinforcement").color(NamedTextColor.GOLD).decorations(Set.of(TextDecoration.BOLD, TextDecoration.ITALIC), false));
                }
            }
            return;
        }

        if (ReinforcementManager.isReinforced(clicked)) return;

        CustomPlayer cPlayer = CoreUtil.getPlayer(player);
        if(clicked.getBlockData() instanceof Ageable bush && cPlayer.getSkillLevel(SkillType.FARMER) < 2){
            if(bush.getAge() >= 3 && Math.random() < 0.2){
                player.damage(1);
            }
        }

        Material mainHand = player.getInventory().getItemInMainHand().getType();
        Material offHand = player.getInventory().getItemInOffHand().getType();

        // Light reinforcement (copper ingot) - check main hand first, then off-hand
        if (mainHand == Material.COPPER_INGOT || offHand == Material.COPPER_INGOT) {
            boolean useOffHand = mainHand != Material.COPPER_INGOT;
            CustomPlayer customPlayer = CoreUtil.getPlayer(player.getUniqueId());
            if (customPlayer.getSkillLevel(SkillType.BUILDER) >= SpecializationConfig.getReinforcementConfig().getInteger("LIGHT_REINFORCEMENT_LEVEL")) {
                List<Block> blocks = getMultiBlocks(clicked);
                boolean success = false;
                for (Block block : blocks) {
                    if (ReinforcementManager.addReinforcement(player, block, false)) {
                        success = true;
                    }
                }
                if (success) {
                    if (useOffHand) {
                        player.swingHand(EquipmentSlot.OFF_HAND);
                        player.getInventory().getItemInOffHand().setAmount(player.getInventory().getItemInOffHand().getAmount() - 1);
                    } else {
                        player.swingHand(EquipmentSlot.HAND);
                        player.getInventory().getItemInMainHand().setAmount(player.getInventory().getItemInMainHand().getAmount() - 1);
                    }
                    PlayerUtil.message(player, Component.text("§7Lightly Reinforced").color(NamedTextColor.WHITE).decorations(Set.of(TextDecoration.BOLD, TextDecoration.ITALIC), false));
                }
            }
        }
        // Heavy reinforcement (iron ingot) - check main hand first, then off-hand
        else if (mainHand == Material.IRON_INGOT || offHand == Material.IRON_INGOT) {
            boolean useOffHand = mainHand != Material.IRON_INGOT;
            CustomPlayer customPlayer = CoreUtil.getPlayer(player.getUniqueId());

            if (customPlayer.getSkillLevel(SkillType.BUILDER) >= SpecializationConfig.getReinforcementConfig().getInteger("HEAVY_REINFORCEMENT_LEVEL")) {
                List<Block> blocks = getMultiBlocks(clicked);
                boolean success = false;
                for (Block block : blocks) {
                    if (ReinforcementManager.addReinforcement(player, block, true)) {
                        success = true;
                    }
                }
                if (success) {
                    if (useOffHand) {
                        player.swingHand(EquipmentSlot.OFF_HAND);
                        player.getInventory().getItemInOffHand().setAmount(player.getInventory().getItemInOffHand().getAmount() - 1);
                    } else {
                        player.swingHand(EquipmentSlot.HAND);
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
