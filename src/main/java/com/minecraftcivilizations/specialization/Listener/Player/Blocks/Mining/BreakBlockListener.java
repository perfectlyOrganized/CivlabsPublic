package com.minecraftcivilizations.specialization.Listener.Player.Blocks.Mining;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.CraftEngine.ItemGetterUtil;
import com.minecraftcivilizations.specialization.Data.Pair;
import com.minecraftcivilizations.specialization.player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import com.typesafe.config.Config;
import kotlin.Result;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager.*;

public class BreakBlockListener implements Listener {

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {


        /**
         * This resets the block break progress done by mobs
         */
        Block block = event.getBlock();
        Collection<Player> nearbyPlayers = block.getWorld().getPlayers().stream()
                .filter(player -> player.getGameMode() == GameMode.SURVIVAL)
                .filter(player -> player.getLocation().distanceSquared(block.getLocation()) <= 256) // 16^2 = 256
                .collect(Collectors.toSet());
        if (nearbyPlayers != null && !nearbyPlayers.isEmpty()) {
            nearbyPlayers.forEach(player -> player.sendBlockDamage(block.getLocation(), 0));
        }

        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (tool.getType().name().endsWith("_PICKAXE")) {
            if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
                return;
            }
        }

       // AttributeInstance breakSpeedAttr = event.getPlayer().getAttribute(Attribute.PLAYER_BLOCK_BREAK_SPEED);

//        if (breakSpeedAttr != null) {
//            breakSpeedAttr.setBaseValue(SpecializationConfig.getBlockHardnessConfig().getDouble(event.getBlock().getType().toString()));

            Pair<SkillType, Double> pair = SkillType.getSkillXpFromConfig(SpecializationConfig.getXpGainFromBreakingConfig(),  ItemGetterUtil.getItemId(event));
            CustomPlayer player = CustomPlayerManager.INSTANCE.getCustomPlayer(event.getPlayer().getUniqueId());
            BlockData blockData = event.getBlock().getBlockData();

            if (isReinforced(event.getBlock())) {
                handleReinforcedDrop(event.getBlock(), event.getPlayer());
            }

            if (player != null && pair.key() != null && pair.value() != null) {
                if (blockData instanceof Ageable age) {
                    if (age.getMaximumAge() == age.getAge()) {
                        player.addSkillXp(pair.key(), pair.value(), event.getBlock().getLocation());
                    }
                } else {
                    player.addSkillXp(pair.key(), pair.value(), event.getBlock().getLocation());
                }
            }
      //  }
        minerListener(event);
        farmerListener(event);
    }

    private void handleReinforcedDrop(Block block, org.bukkit.entity.Player player) {
        Location dropLocation = block.getLocation().add(0.5, 0.5, 0.5);

        // 50% chance to give the reward item for iron/copper reinforcement
        if (isHeavilyReinforced(block)) {
            if (Math.random() < 0.5) {
                block.getWorld().dropItemNaturally(dropLocation, new ItemStack(Material.IRON_INGOT));
            }
            PlayerUtil.message(player, "Iron Reinforcement Broke");
        } else if (isLightlyReinforced(block)) {
            if (Math.random() < 0.5) {
                block.getWorld().dropItemNaturally(dropLocation, new ItemStack(Material.COPPER_INGOT));
            }
            PlayerUtil.message(player, "Copper Reinforcement Broke");
        } else if (isWoodenReinforced(block)) {
            // Wooden reinforcement always drops the log type used
            Material logMaterial = getWoodenReinforcementLogMaterial(block);
            if (logMaterial != null) {
                block.getWorld().dropItemNaturally(dropLocation, new ItemStack(logMaterial));
            }
            PlayerUtil.message(player, "Wooden Reinforcement Broke");
        }


        // Always remove reinforcement
        removeReinforcement(block);
//        for (Block b : getMultiBlocks(block)) {
//        }
    }

    public void minerListener(BlockBreakEvent event) {
        CustomPlayer player = CustomPlayerManager.INSTANCE.getCustomPlayer(event.getPlayer());
        Material materialName = event.getBlock().getType();
        Config CanMinerLvlBreak = SpecializationConfig.getCanMinerLvlBreakConfig().getConfig();
        String item = materialName.toString();
        if (!CanMinerLvlBreak.hasPath(item)) {
            return;
        }

        SkillLevel skillRequired = SkillLevel.valueOf(CanMinerLvlBreak.getString(materialName.toString()));

        if (player.getSkillLevel(SkillType.MINER) < skillRequired.getLevel()) {
            event.setDropItems(false);
            if (event.getPlayer().getGameMode() == GameMode.SURVIVAL)
                PlayerUtil.message(event.getPlayer(),"You are unable to mine this ore.");
        }
    }
    public static ItemStack getFarmerDrop(Collection<ItemStack> drops) {
        List<ItemStack> results = drops.stream().filter(
                it -> !SpecializationConfig.getCanFarmerHarvestConfig().getConfig().hasPath(it.getType().toString())
        ).toList();
        if (results.isEmpty()) return null;
        return results.get(0);
    }
    public void farmerListener(BlockBreakEvent event) {
        Player player = event.getPlayer();
        CustomPlayer customPlayer = CustomPlayerManager.INSTANCE.getCustomPlayerOrThrow(player);
        Config CanFarmerBreak = SpecializationConfig.getCanFarmerHarvestConfig().getObject("BREAK");
        ItemStack result = getFarmerDrop(event.getBlock().getDrops());
        if (result == null || !CanFarmerBreak.hasPath(result.getType().toString())) return;

        SkillLevel skillLevel = SkillLevel.valueOf(CanFarmerBreak.getString(result.getType().toString()));
        if (customPlayer.getSkillLevel(SkillType.FARMER) < skillLevel.getLevel()) {
            event.setCancelled(true);
            PlayerUtil.message(player, org.bukkit.ChatColor.RED + "You are unable to farm this");
            return;
        }
        if (event.getBlock().getBlockData() instanceof Ageable ageable) {
            handleHarvest(result, (success) -> {
                event.setDropItems(!success);
            }, player, ageable);
            return;
        }
        handleHarvest(result, (success) -> {
            event.setDropItems(!success);
        }, player);
    }
    public static void handleHarvest(ItemStack item, Consumer<Boolean> success, Player player, Ageable ageable) {
        if (ageable != null && ageable.getAge() != ageable.getMaximumAge()) {
            //success.accept(false);
            return;
        }
        handleHarvest(item,success,player);
    }

    public static void handleHarvest(ItemStack item, Consumer<Boolean> success, Player player) {
        CustomPlayer customPlayer = CustomPlayerManager.INSTANCE.getCustomPlayerOrThrow(player);

        double chance = SpecializationConfig.getFarmerConfig().getDouble("FARMER_GET_DROPS_CHANCE_" + customPlayer.getSkillLevelEnum(SkillType.FARMER));
        double random = ThreadLocalRandom.current().nextDouble();

        success.accept(random < chance);
        if (random <= 0.04) FarmerMinigameManager.INSTANCE.start(player, item.getType());
    }

    private List<Block> getMultiBlocks(Block b) {
        List<Block> l = new ArrayList<>();
        l.add(b);
        BlockData d = b.getBlockData();
        if (d instanceof Door door) {
            l.add(b.getRelative(door.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP));
        } else if (d instanceof Bed bed) {
            l.add(b.getRelative(bed.getPart() == Bed.Part.HEAD ? bed.getFacing().getOppositeFace() : bed.getFacing()));
        } else if (d instanceof Bisected bi) {
            l.add(b.getRelative(bi.getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP));
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
            boolean wooden = isWoodenReinforced(block);

            // Wooden reinforcement provides NO explosion protection - block gets destroyed and drops logs
            if (wooden) {
                Material logMaterial = getWoodenReinforcementLogMaterial(block);
                if (logMaterial != null) {
                    block.getWorld().dropItemNaturally(dropLocation, new ItemStack(logMaterial));
                }
                for (Block b : getMultiBlocks(block)) removeReinforcement(b);
                return; // Let the explosion destroy this block normally
            }

            double factor;
            if(heavy){
                factor = SpecializationConfig.getReinforcementConfig().getDouble("HEAVY_EXPLOSION_RESISTANCE");
            } else{
                factor = SpecializationConfig.getReinforcementConfig().getDouble("LIGHT_EXPLOSION_RESISTANCE");
            }
            if(Math.random() < factor){
                blocks.remove(block); //this removes the block from the event
                block.getWorld().dropItemNaturally(dropLocation, new ItemStack(heavy?Material.IRON_INGOT:Material.COPPER_INGOT));
                for (Block b : getMultiBlocks(block)) removeReinforcement(b);
            }
        });
    }
}
