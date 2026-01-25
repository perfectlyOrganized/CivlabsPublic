package com.minecraftcivilizations.specialization.CraftEngine;

import minecraftcivilizations.com.minecraftCivilizationsCore.Item.CustomItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.world.BukkitExistingBlock;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public class CraftEngineUtil {

    public static String getItemId(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (!CraftEngineBlocks.isCustomBlock(block)) {
            return block.getType().toString().toUpperCase(Locale.ROOT);
        }
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
        if (state == null) return  block.getType().toString().toUpperCase(Locale.ROOT);

        return state.owner().value().id().toString().toUpperCase(Locale.ROOT).replace(":","_");
    }
    public static String getItemId(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();

        if (!CraftEngineBlocks.isCustomBlock(block)) {
            return block.getType().toString().toUpperCase(Locale.ROOT);
        }
        ImmutableBlockState state = CraftEngineBlocks.getCustomBlockState(block);
        if (state == null) return block.getType().toString().toUpperCase(Locale.ROOT);

        return state.owner().value().id().toString().toUpperCase(Locale.ROOT).replace(":","_");
    }
    public static String getItemId(CraftItemEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null) return "";
        if (CustomItem.isCustomItem(item)) {
            return event.getRecipe().toString().toUpperCase(Locale.ROOT);
        }
        return getItemId(item);
    }

    public static String getItemId(ItemStack item) {
        if (CraftEngineItems.isCustomItem(item)) {
            var ceItem = CraftEngineItems.getCustomItemId(item);
            if (ceItem != null) return ceItem.toString().toUpperCase(Locale.ROOT).replace(":","_");
        }
        return item.getType().key().value().toUpperCase(Locale.ROOT);
    }
}
