package com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import minecraftcivilizations.com.minecraftCivilizationsCore.Options.Pair;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager.*;

public class BreakBlockListener implements Listener {

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {


        /**
         * This resets the block break progress done by mobs
         */
        Block block = event.getBlock();
        Collection<Player> nearbyPlayers = block.getLocation().getNearbyPlayers(16);
        nearbyPlayers.stream()
                .filter(player -> player.getGameMode().equals(GameMode.SURVIVAL))
                .collect(Collectors.toSet());
        if (nearbyPlayers != null && !nearbyPlayers.isEmpty()) {
            nearbyPlayers.forEach(player -> player.sendBlockDamage(block.getLocation(), 0));
        }

        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (tool.getType().name().endsWith("_PICKAXE")) { // ensure it's a pickaxe
            if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
                return; // Exit early: do not give miner XP
            }
        }

        AttributeInstance breakSpeedAttr = event.getPlayer().getAttribute(Attribute.BLOCK_BREAK_SPEED);

        if (breakSpeedAttr != null) {
            breakSpeedAttr.setBaseValue(SpecializationConfig.getBlockHardnessConfig().get(event.getBlock().getType(), Double.class));
            Pair<SkillType, Double> pair = SpecializationConfig.getXpGainFromBreakingConfig().get(event.getBlock().getType(), new TypeToken<>() {
            });
            if (pair == null) {
                Specialization.getInstance().getLogger().warning("No XP gain configuration found for broken block: " + event.getBlock().getType());
            }
            CustomPlayer player = CoreUtil.getPlayer(event.getPlayer().getUniqueId());
            BlockData blockData = event.getBlock().getBlockData();

            if (isReinforced(event.getBlock())) {
                handleReinforcedDrop(event.getBlock(), event.getPlayer());
            }

            if (pair != null && pair.firstValue() != null && pair.secondValue() != null) {
                if (blockData instanceof Ageable age) {
                    if (age.getMaximumAge() == age.getAge()) {
                        player.addSkillXp(pair.firstValue(), pair.secondValue(), event.getBlock().getLocation());
                    }
                } else {
                    player.addSkillXp(pair.firstValue(), pair.secondValue(), event.getBlock().getLocation());
                }
            }
        }
        minerListener(event);
        farmerListener(event);
    }

    private void handleReinforcedDrop(Block block, org.bukkit.entity.Player player) {
        Location dropLocation = block.getLocation().add(0.5, 0.5, 0.5);

        // 50% chance to give the reward item
            if (isHeavilyReinforced(block)) {
                if (Math.random() < 0.5) {
                    block.getWorld().dropItemNaturally(dropLocation, new ItemStack(Material.IRON_INGOT));
                }
                PlayerUtil.message(player, "Iron Reinforcement Broke");
            }else if (isLightlyReinforced(block)) {
                if (Math.random() < 0.5) {
                    block.getWorld().dropItemNaturally(dropLocation, new ItemStack(Material.COPPER_INGOT));
                }
                PlayerUtil.message(player, "Copper Reinforcement Broke");
            }


        // Always remove reinforcement
        removeReinforcement(block);
//        for (Block b : getMultiBlocks(block)) {
//        }
    }

    public void minerListener(BlockBreakEvent event) {
        CustomPlayer player = CoreUtil.getPlayer(event.getPlayer());
        Material materialName = event.getBlock().getType();
        SkillLevel skillRequired = SpecializationConfig.getCanMinerLvlBreakConfig().get(materialName.toString(), new TypeToken<>() {
        });
        if (skillRequired != null && player.getSkillLevel(SkillType.MINER) < skillRequired.getLevel()) {
            event.setDropItems(false);
            if (event.getPlayer().getGameMode() == GameMode.SURVIVAL)
                PlayerUtil.message(event.getPlayer(),"You are unable to mine this ore.");
        }
    }

    public void farmerListener(BlockBreakEvent event) {
        CustomPlayer player = CoreUtil.getPlayer(event.getPlayer());
        Material materialName = event.getBlock().getType();
        SkillLevel skillRequired = SpecializationConfig.getCanFarmerBreakConfig().get(materialName.toString(), new TypeToken<>() {
        });

        if (skillRequired != null && player.getSkillLevel(SkillType.FARMER) < skillRequired.getLevel()) {
            event.setDropItems(false);
            PlayerUtil.message(event.getPlayer(), org.bukkit.ChatColor.RED + "You are unable to farm this");
        }

        List<Material> otherFarmables = List.of(Material.COCOA_BEANS, Material.SUGAR_CANE, Material.CACTUS, Material.MELON, Material.PUMPKIN);
        double chance = SpecializationConfig.getFarmerConfig().get("FARMER_GET_DROPS_CHANCE_" + player.getSkillLevelEnum(SkillType.FARMER), Double.class);
        double random = Math.random();

        if (random < chance) {
            event.setDropItems(true);
        } else if (event.getBlock().getBlockData() instanceof Ageable || otherFarmables.contains(materialName)) {
            event.setDropItems(false);
        }
    }

    private List<Block> getMultiBlocks(Block b) {
        List<Block> l = new ArrayList<>();
        l.add(b);
        BlockData d = b.getBlockData();
        switch (d) {
            case Door door -> l.add(b.getRelative(door.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP));
            case Bed bed ->
                    l.add(b.getRelative(bed.getPart() == Bed.Part.HEAD ? bed.getFacing().getOppositeFace() : bed.getFacing()));
            case Bisected bi -> l.add(b.getRelative(bi.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP));
            default -> {
            }
        }
        return l;
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(List<Block> blocks) {
        List<Block> block_list_copy = new ArrayList<>(blocks.size());
        block_list_copy.addAll(blocks);
        block_list_copy.forEach(block -> {
            if (!isReinforced(block)) return;
            Location dropLocation = block.getLocation().add(0.5, 0.5, 0.5);

            boolean heavy = isHeavilyReinforced(block);
            double factor;
            if(heavy){
                factor = SpecializationConfig.getReinforcementConfig().get("HEAVY_EXPLOSION_RESISTANCE", Double.class);
            }else{
                factor = SpecializationConfig.getReinforcementConfig().get("LIGHT_EXPLOSION_RESISTANCE", Double.class);
            }
            if(Math.random() < factor){
                blocks.remove(block); //this removes the block from the event
                block.getWorld().dropItemNaturally(dropLocation, new ItemStack(heavy?Material.IRON_INGOT:Material.COPPER_INGOT));
                for (Block b : getMultiBlocks(block)) removeReinforcement(b);
            }
        });
    }
}
