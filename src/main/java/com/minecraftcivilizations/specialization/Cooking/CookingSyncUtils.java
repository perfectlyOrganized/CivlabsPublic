package com.minecraftcivilizations.specialization.Cooking;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Campfire;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for syncing session state with campfire contents.
 */
public class CookingSyncUtils {

    /**
     * Resync session data from the actual campfire slot contents.
     */
    public static void resyncSessionFromCampfire(CookingItemData data) {
        if (data == null || data.campfireLocation == null) return;

        Block b = data.campfireLocation.getBlock();
        if (b.getType() != Material.CAMPFIRE) return;

        Campfire cf = (Campfire) b.getState();
        List<ItemStack> newIngredients = new ArrayList<>();
        List<ItemStack> newSeasonings = new ArrayList<>();
        List<ItemStack> newSauces = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            ItemStack it = cf.getItem(i);
            if (it == null || it.getType().isAir()) continue;

            String id = CookingItemUtils.getItemId(it);

            // Classify into sauces vs seasonings vs ingredients
            if (isSauceById(id) || isSauceByType(it)) {
                newSauces.add(it.clone());
            } else if (isSeasoningById(id)) {
                newSeasonings.add(it.clone());
            } else {
                newIngredients.add(it.clone());
            }
        }

        data.ingredients = newIngredients;

        // Build ID lists
        List<String> cIng = new ArrayList<>();
        List<String> cSea = new ArrayList<>();
        List<String> cSau = new ArrayList<>();

        for (ItemStack is : newIngredients) {
            if (is != null) cIng.add(CookingItemUtils.getItemId(is));
        }
        for (ItemStack is : newSeasonings) {
            if (is != null) cSea.add(CookingItemUtils.getItemId(is));
        }
        for (ItemStack is : newSauces) {
            if (is != null) cSau.add(CookingItemUtils.getItemId(is));
        }

        data.ingredientIds = cIng;
        data.seasoningIds = cSea;
        data.sauceIds = cSau;
    }

    private static boolean isSauceById(String id) {
        return SpecializationConfig.getCookingConfig().getStringList("possible_sauces").contains(id);
    }

    private static boolean isSauceByType(ItemStack item) {
        return item.getType() == Material.POTION ||
               item.getType() == Material.INK_SAC ||
               item.getType() == Material.GLOW_INK_SAC ||
               item.getType() == Material.GHAST_TEAR;
    }

    private static boolean isSeasoningById(String id) {
        return SpecializationConfig.getCookingConfig().getStringList("possible_seasonings").contains(id);
    }

    /**
     * Get campfire facing direction as yaw.
     */
    public static float getCampfireFacingYaw(Block block, float fallbackYaw) {
        if (block == null) return fallbackYaw;
        org.bukkit.block.data.BlockData bd = block.getBlockData();
        if (bd instanceof org.bukkit.block.data.Directional d) {
            return switch (d.getFacing()) {
                case NORTH -> 180f;
                case SOUTH -> 0f;
                case WEST -> 90f;
                case EAST -> -90f;
                default -> fallbackYaw;
            };
        }
        return fallbackYaw;
    }
}
