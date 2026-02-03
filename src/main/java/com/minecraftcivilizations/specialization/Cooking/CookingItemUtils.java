package com.minecraftcivilizations.specialization.Cooking;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Utility class for identifying and classifying cooking items (ingredients, seasonings, sauces).
 */
public class CookingItemUtils {

    /**
     * Get the item ID for an ItemStack, preferring CraftEngine custom item IDs.
     * Returns format "namespace:id" (e.g., "specialization:fried_egg" or "minecraft:bread")
     */
    public static String getItemId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        try {
            var wrapped = BukkitItemManager.instance().wrap(item);
            if (wrapped.getCustomItem().isPresent()) {
                var customItem = wrapped.getCustomItem().get();
                var key = customItem.id();
                // Return full namespace:value format
                String namespace = key.namespace();
                String value = key.value();
                if (namespace != null && !namespace.isEmpty()) {
                    return namespace + ":" + value;
                }
                return value;
            }
        } catch (Exception ignored) {}
        return "minecraft:" + item.getType().name().toLowerCase();
    }

    /**
     * Check if an item is an allowed ingredient for cooking.
     * Only accepts items that are explicitly listed in the possible_ingredients config.
     */
    public static boolean isAllowedIngredient(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        String id = getItemId(item);
        if (id == null) return false;

        // Check against config list
        List<String> configIds = SpecializationConfig.getCookingConfig().getStringList("possible_ingredients");

        // Direct match
        if (configIds.contains(id)) return true;

        // Path-only match (e.g., "fried_egg" matches "specialization:fried_egg")
        String idPath = id.contains(":") ? id.split(":", 2)[1] : id;
        for (String cfgId : configIds) {
            if (cfgId == null) continue;
            String cfgPath = cfgId.contains(":") ? cfgId.split(":", 2)[1] : cfgId;
            if (idPath.equals(cfgPath)) return true;
        }

        return false;
    }

    /**
     * Check if an item is an allowed seasoning or sauce.
     * Only accepts items that are explicitly listed in the possible_seasonings or possible_sauces config.
     */
    public static boolean isAllowedSeasoning(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        String id = getItemId(item);
        if (id == null) return false;

        // Check against config lists
        List<String> seasonings = SpecializationConfig.getCookingConfig().getStringList("possible_seasonings");
        List<String> sauces = SpecializationConfig.getCookingConfig().getStringList("possible_sauces");

        // Direct match
        if (seasonings.contains(id) || sauces.contains(id)) return true;

        // Path-only match
        String idPath = id.contains(":") ? id.split(":", 2)[1] : id;
        for (String cfgId : seasonings) {
            if (cfgId == null) continue;
            String cfgPath = cfgId.contains(":") ? cfgId.split(":", 2)[1] : cfgId;
            if (idPath.equals(cfgPath)) return true;
        }
        for (String cfgId : sauces) {
            if (cfgId == null) continue;
            String cfgPath = cfgId.contains(":") ? cfgId.split(":", 2)[1] : cfgId;
            if (idPath.equals(cfgPath)) return true;
        }

        // Built-in types that are always allowed as seasonings/sauces
        return item.getType() == Material.POTION ||
               item.getType() == Material.SUGAR ||
               item.getType() == Material.COCOA_BEANS ||
               item.getType() == Material.HONEY_BOTTLE ||
               item.getType() == Material.GLOW_LICHEN ||
               item.getType() == Material.BLAZE_POWDER ||
               item.getType() == Material.INK_SAC ||
               item.getType() == Material.GLOW_INK_SAC ||
               item.getType() == Material.GHAST_TEAR;
    }

    /**
     * Check if an item is specifically a sauce (not a dry seasoning).
     * Sauces include: potions, ghast tear, ink sacs, glow ink sacs, and config-defined sauces.
     */
    public static boolean isSauce(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        String id = getItemId(item);
        List<String> sauces = SpecializationConfig.getCookingConfig().getStringList("possible_sauces");

        // Direct match
        if (id != null && sauces.contains(id)) return true;

        // Path-only match
        if (id != null) {
            String idPath = id.contains(":") ? id.split(":", 2)[1] : id;
            for (String cfgId : sauces) {
                if (cfgId == null) continue;
                String cfgPath = cfgId.contains(":") ? cfgId.split(":", 2)[1] : cfgId;
                if (idPath.equals(cfgPath)) return true;
            }
        }

        // Built-in sauce types
        return item.getType() == Material.POTION ||
               item.getType() == Material.INK_SAC ||
               item.getType() == Material.GLOW_INK_SAC ||
               item.getType() == Material.GHAST_TEAR;
    }
}
