package com.minecraftcivilizations.specialization.util;

import com.minecraftcivilizations.specialization.GUI.GUIItem;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ItemStackUtils {
    private static final Map<String, ItemStack> itemCache = new ConcurrentHashMap<>();
    /**
     * Cache an item for future lookups
     */
    private static void cacheItem(String key, ItemStack item) {
        if (item != null) {
            itemCache.put(key, item.clone());
        }
    }
    public static ItemStack getItemStack(NamespacedKey namespace) {
        if (namespace == null) return null;

        String keyString = namespace.toString();

        // Check cache first
        if (itemCache.containsKey(keyString)) {
            return itemCache.get(keyString).clone();
        }

        ItemStack result = null;

        if (namespace == null) return null;

        Material minecraftMaterial = Registry.MATERIAL.get(namespace);

        if (minecraftMaterial != null && !minecraftMaterial.isAir()) {
            return new ItemStack(minecraftMaterial);
        }

        result = getFromBukkitRecipe(namespace);
        if (result != null) {
            cacheItem(keyString, result);
            return result.clone();
        }

        Material material = Material.matchMaterial(keyString.toUpperCase(Locale.ROOT).replace(":", "_"));

        if (material != null) {
            result = new ItemStack(material);
            cacheItem(keyString, result);
            return result.clone();
        }

        throw new NullPointerException(keyString+ " not found! Maybe typo?");
    }
    private static ItemStack getFromBukkitRecipe(NamespacedKey key) {
        try {
            Recipe recipe = Bukkit.getRecipe(key);
            if (recipe != null && recipe.getResult() != null) {
                return recipe.getResult().clone();
            }
        } catch (Exception e) {
            Debug.broadcast("item", "Bukkit recipe lookup failed for " + key + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Returns true if the item has a non-empty lore line at the given index.
     */
    public static boolean hasLoreLine(@NotNull ItemStack item_stack, int line) {
        ItemMeta meta = item_stack.getItemMeta();
        if (meta == null || !meta.hasLore()) return false;

        List<String> lore = meta.getLore();
        if (lore == null) return false;
        return line < lore.size() && lore.get(line) != null && !lore.get(line).isEmpty();
    }

    /**
     * Sets/overwrites a single lore line on the item.
     * Preserves other existing lines. Expands the lore list with empty strings if necessary.
     */
    public static void setLoreLine(@NotNull ItemStack item_stack, int line, String loreText) {
        ItemMeta meta = item_stack.getItemMeta();
        if (meta == null) return;

        List<String> lore_list = meta.hasLore()
                ? new ArrayList<>(Objects.requireNonNull(meta.getLore()))
                : new ArrayList<>();

        // Ensure list is large enough
        while (lore_list.size() <= line) {
            lore_list.add("");
        }

        lore_list.set(line, loreText == null ? "" : loreText);
        meta.setLore(lore_list);
        item_stack.setItemMeta(meta);
    }

    /**
     * Sets/overwrites a single lore line on the item.
     * Preserves other existing lines. Expands the lore list with empty strings if necessary.
     */
    public static void setLoreLine(ItemMeta meta, int line, String loreText) {
        List<String> lore_list = meta.hasLore()
                ? new ArrayList<>(Objects.requireNonNull(meta.getLore()))
                : new ArrayList<>();

        // Ensure list is large enough
        while (lore_list.size() <= line) {
            lore_list.add("");
        }

        lore_list.set(line, loreText == null ? "" : loreText);
        meta.setLore(lore_list);
    }

    /**
     * Returns true if the item has the given namespaced key flag in its ItemMeta PDC.
     */
    public static boolean hasLoreTag(ItemStack item_stack, NamespacedKey key) {
        if (item_stack == null || key == null) return false;
        if (!item_stack.hasItemMeta()) return false;

        ItemMeta meta = item_stack.getItemMeta();
        if (meta == null) return false;

        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /**
     * Sets/overwrites a single lore line on the item and marks it with the given NamespacedKey in the PDC.
     * If the flag already exists this method does nothing
     */
    public static void setLoreTag(ItemStack item_stack, NamespacedKey key, int line, String lore) {
        if (item_stack == null || key == null || line < 0) return;

        ItemMeta meta = item_stack.getItemMeta();
        if (meta == null) return;

        // If already tagged, don't reapply
        if (meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) return;

        List<String> lore_list = meta.hasLore()
                ? new ArrayList<>(Objects.requireNonNull(meta.getLore()))
                : new ArrayList<>();

        // Expand to required size (fill with empty strings)
        while (lore_list.size() <= line) {
            lore_list.add("");
        }

        lore_list.set(line, lore == null ? "" : lore);

        meta.setLore(lore_list);
        // mark with a byte flag (value 1)
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

        item_stack.setItemMeta(meta);
    }

    public static boolean damageItem(ItemStack item, int amount, Entity breaker) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType().getMaxDurability() == 0) return false; // Unbreakable items

        if (item instanceof Damageable) {
            Damageable damageable = (Damageable) item;

            int currentDamage = damageable.getDamage();
            int maxDurability = item.getType().getMaxDurability();

            // Apply unbreaking enchantment
            int unbreaking = item.getEnchantmentLevel(Enchantment.DURABILITY);
            int damageToApply = amount;

            if (unbreaking > 0) {
                damageToApply = 0;
                for (int i = 0; i < amount; i++) {
                    // Chance to ignore damage: 1/(unbreaking+1)
                    if (Math.random() > 1.0 / (unbreaking + 1.0)) {
                        damageToApply++;
                    }
                }
            }

            if (damageToApply <= 0) return false;

            // Apply damage
            int newDamage = currentDamage + damageToApply;
            damageable.setDamage(newDamage);

            // Check if item broke
            if (newDamage >= maxDurability) {
                // Item breaks
                if (breaker != null) {
                    breaker.getWorld().playSound(breaker.getLocation(),
                            Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                }
                return true; // Item broke
            }
        }

        return false; // Item damaged but didn't break
    }

    // Returns hunger points (food "nutrition") restored by one unit of the given Material.
// Values sourced from the Minecraft Wiki "Food" table (Java Edition values).
    public static int getFoodNutrition(Material food_type) {
        if (food_type == null) return 0;
        switch (food_type) {
            // crops / basic
            case APPLE: return 4;
            case CARROT: return 3;
            case POTATO: return 1;                 // raw potato
            case BAKED_POTATO: return 5;
            case POISONOUS_POTATO: return 2;
            case BEETROOT: return 1;
            case BEETROOT_SOUP: return 6;
            case WHEAT: return 0;                 // wheat is an ingredient, not consumed for hunger
            case MELON_SLICE: return 2;
            case PUMPKIN_PIE: return 8;
            case BREAD: return 5;
            case CAKE: return 2;                  // one slice = 2 hunger points (placed cake is eaten slice-by-slice)
            case COOKIE: return 2;

            // berries / plants
            case SWEET_BERRIES: return 2;
            case GLOW_BERRIES: return 2;
            case HONEY_BOTTLE: return 6;

            // meat (raw / cooked)
            case BEEF: return 3;
            case COOKED_BEEF: return 8;           // steak
            case CHICKEN: return 2;
            case COOKED_CHICKEN: return 6;
            case PORKCHOP: return 3;
            case COOKED_PORKCHOP: return 8;
            case RABBIT: return 3;
            case COOKED_RABBIT: return 5;
            case MUTTON: return 2;
            case COOKED_MUTTON: return 6;

            // fish
            case COD: return 2;                   // raw cod (Material.COD)
            case COOKED_COD: return 5;
            case SALMON: return 2;                // raw salmon
            case COOKED_SALMON: return 6;
            case TROPICAL_FISH: return 1;
            case PUFFERFISH: return 1;

            case ROTTEN_FLESH: return 1;

            // other stackable foods / miscellaneous
            case CHORUS_FRUIT: return 4;
            case SPIDER_EYE: return 2;
            case DRIED_KELP: return 1;
            case SUSPICIOUS_STEW: return 6;       // restores 6 hunger + status effect
            case MUSHROOM_STEW: return 6;
            case RABBIT_STEW: return 10;

            // golden items
            case GOLDEN_APPLE: return 4;
            case ENCHANTED_GOLDEN_APPLE: return 4;
            case GOLDEN_CARROT: return 6;

            // other consumables
            case MILK_BUCKET: return 0;           // clears effects; does not restore hunger
            case HONEYCOMB: return 0;             // not edible
            // (include bucket variants if you want)
            // fall-through default
            default:
                return 0;
        }
    }

    public static boolean isGUIItemWithSpecificName(ItemStack item, String name) {
        if(item.getItemMeta() == null || !item.getItemMeta().hasDisplayName()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getDisplayName().equals(name);
    }

    public static GUIItem makeItemGUIItem(ItemStack item, String name) {
        if(item == null || item.getItemMeta() == null) return null;
        ItemMeta meta = item.getItemMeta();
        if(name!=null){
            LoreUtils.setItemDisplayName(meta, Component.text(name).color(TextColor.fromHexString("#ffffff")).decoration(TextDecoration.ITALIC, false));
        }
        meta.addItemFlags(ItemFlag.values());
        meta.setLore(new ArrayList<>());
        item.setItemMeta(meta);
        return new GUIItem(item, null);
    }

    public static GUIItem makeGUIItemOfType(Material material, String name) {
        return makeItemGUIItem(new ItemStack(material), name);
    }

    public static GUIItem makeGUIItemOfType(Material material) {
        return makeItemGUIItem(new ItemStack(material), getFriendlyName(material));
    }

    public static String getFriendlyName(Material material) {
        if (material == null) return null;
        // Split the enum name by underscores, capitalize each word, and join them
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder friendlyName = new StringBuilder();
        for (String word : words) {
            friendlyName.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
        }
        return friendlyName.toString().trim(); // Remove trailing space
    }
}

