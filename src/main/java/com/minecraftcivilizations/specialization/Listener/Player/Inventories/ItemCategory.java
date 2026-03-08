package com.minecraftcivilizations.specialization.Listener.Player.Inventories;

import org.bukkit.inventory.ItemStack;

import java.util.*;

enum ItemCategory {
    PICKAXE, SHOVEL, AXE, SWORD, HELMET, CHESTPLATE, LEGGINGS, BOOTS, SHIELD, RANGED;

    static ItemCategory fromItem(ItemStack item) {
        String name = item.getType().name().toLowerCase();
        if (name.contains("_pickaxe")) return PICKAXE;
        if (name.contains("_shovel")) return SHOVEL;
        if (name.contains("_axe") && !name.contains("_pickaxe")) return AXE;
        if (name.contains("_sword")) return SWORD;
        if (name.contains("_helmet")) return HELMET;
        if (name.contains("_chestplate")) return CHESTPLATE;
        if (name.contains("_leggings")) return LEGGINGS;
        if (name.contains("_boots") || name.contains("_shoes")) return BOOTS;
        if (name.contains("shield")) return SHIELD;
        if (name.equals("bow") || name.equals("crossbow")) return RANGED;
        return null;
    }
    static final Map<ItemCategory, Set<String>> CATEGORY_AFFIXES = new EnumMap<>(ItemCategory.class);
    static final HashMap<String, Float> ATTRIBUTE_DEFAULTS = new HashMap<>();
    static {
        CATEGORY_AFFIXES.put(ItemCategory.PICKAXE, Set.of(
             //  "forge:block_reach",
                "attributeslib:mining_speed",
                "attributeslib:experience_gained",
                "minecraft:generic.luck"
        ));

        CATEGORY_AFFIXES.put(ItemCategory.SHOVEL, CATEGORY_AFFIXES.get(ItemCategory.PICKAXE));

        CATEGORY_AFFIXES.put(ItemCategory.AXE, Set.of(
                "attributeslib:mining_speed",
                "attributeslib:experience_gained",
                "minecraft:generic.luck"
        ));

        // Weapons
        CATEGORY_AFFIXES.put(ItemCategory.SWORD, Set.of(
                //"forge:entity_reach",
                "attributeslib:cold_damage",
                //"minecraft:generic.attack_speed",  -- multiply
                "attributeslib:fire_damage",
                "attributeslib:crit_chance", "attributeslib:crit_damage",
                "attributeslib:armor_pierce", "attributeslib:prot_pierce",
                "attributeslib:life_steal", "minecraft:generic.attack_damage"
        ));

        // Armor
        Set<String> ALL_ARMOR_AFFIXES = new HashSet<>(Set.of(
                "minecraft:generic.armor_toughness",
                //"forge:swim_speed",
                "minecraft:generic.luck",
                "minecraft:generic.armor",
                "minecraft:generic.knockback_resistance"
                //"attributeslib:healing_received" - multiplier
                //"minecraft:generic.max_health"
        ));
        CATEGORY_AFFIXES.put(ItemCategory.BOOTS, new HashSet<>(Set.of(
                //"minecraft:generic.movement_speed", -- multiply
                //"forge:step_height_addition"
        )));
        CATEGORY_AFFIXES.put(ItemCategory.RANGED, new HashSet<>(Set.of(
            "attributeslib:arrow_velocity",
            "attributeslib:arrow_damage",
            "attributeslib:draw_speed"
            //"minecraft:generic.movement_speed", -- multiply
        )));
        CATEGORY_AFFIXES.get(ItemCategory.BOOTS).addAll(ALL_ARMOR_AFFIXES);
        CATEGORY_AFFIXES.put(ItemCategory.LEGGINGS, ALL_ARMOR_AFFIXES);
       // CATEGORY_AFFIXES.get(ItemCategory.LEGGINGS).add("minecraft:generic.movement_speed", -- multiply);
        CATEGORY_AFFIXES.put(ItemCategory.CHESTPLATE, new HashSet<>(Set.of())); // "apotheosis:armor/attribute/gravitational"
        CATEGORY_AFFIXES.get(ItemCategory.CHESTPLATE).addAll(ALL_ARMOR_AFFIXES);
        CATEGORY_AFFIXES.put(ItemCategory.HELMET, ALL_ARMOR_AFFIXES);
        // Shields
        CATEGORY_AFFIXES.put(ItemCategory.SHIELD, new HashSet<>(Set.of("minecraft:generic.armor", "minecraft:generic.armor_toughness", "minecraft:generic.knockback_resistance")));
    }
}
