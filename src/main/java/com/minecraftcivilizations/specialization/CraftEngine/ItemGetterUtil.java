package com.minecraftcivilizations.specialization.CraftEngine;

import com.minecraftcivilizations.specialization.CustomItem.CustomItem;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public class ItemGetterUtil {

    public static String getItemId(BlockBreakEvent event) {
        Block block = event.getBlock();

        return block.getType().toString().toUpperCase(Locale.ROOT).replace(":", "_");
    }
    public static String getItemId(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();

        return block.getType().toString().toUpperCase(Locale.ROOT).replace(":","_");
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
        return item.getType().getKey().getKey().toUpperCase(Locale.ROOT);
    }
}
