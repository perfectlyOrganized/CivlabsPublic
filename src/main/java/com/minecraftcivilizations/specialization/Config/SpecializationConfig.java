package com.minecraftcivilizations.specialization.Config;

import com.minecraftcivilizations.specialization.OpenLab;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.*;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.function.Supplier;

@Getter
public class SpecializationConfig {
    @Getter
    private static ConfigFile xpMonitorConfig;
    @Getter
    private static ConfigFile playerConfig;
    @Getter
    private static ConfigFile skillsConfig;
    @Getter
    private static ConfigFile blockHardnessConfig;
    @Getter
    private static ConfigFile unlockedRecipesConfig;
    @Getter
    private static ConfigFile allRecipeBank;
    @Getter
    private static ConfigFile xpGainFromStonecuttingConfig;
    @Getter
    private static ConfigFile xpGainFromSmeltingConfig;
    @Getter
    private static ConfigFile xpGainFromCraftingConfig;
    @Getter
    private static ConfigFile xpGainFromBlastingConfig;
    @Getter
    private static ConfigFile xpGainFromSmokingConfig;
    @Getter
    private static ConfigFile xpGainFromBreakingConfig;
    @Getter
    private static ConfigFile xpGainFromPlacingConfig;
    @Getter
    private static ConfigFile xpGainFromEnchantingConfig;
    @Getter
    private static ConfigFile xpGainFromCartographyConfig;
    @Getter
    private static ConfigFile classSkillEffectsConfig;
    @Getter
    private static ConfigFile librarianConfig;
    @Getter
    public static ConfigFile farmerConfig;
    @Getter
    public static ConfigFile minerConfig;
    @Getter
    private static ConfigFile cookingConfig;
    @Getter
    public static ConfigFile tameableConfig;
    @Getter
    private static ConfigFile canUseBlockConfig;
    @Getter
    private static ConfigFile canUseItemConfig;
    @Getter
    private static ConfigFile xpGainFromRepairingConfig;
    @Getter
    private static ConfigFile combatConfig;
    @Getter
    private static ConfigFile chatConfig;
    @Getter
    private static ConfigFile mobConfig;
    @Getter
    private static ConfigFile mobDropsConfig;
    @Getter
    private static ConfigFile guardsmanConfig;
    @Getter
    private static ConfigFile berserkConfig;
    @Getter
    private static ConfigFile reinforcementConfig;
    @Getter
    private static ConfigFile hungerConfig;
    @Getter
    private static ConfigFile hungerCostConfig;
    @Getter
    private static ConfigFile foodExpirationConfig;
    @Getter
    private static ConfigFile downedConfig;
    @Getter
    private static ConfigFile canMinerLvlBreakConfig;
    @Getter
    private static ConfigFile canFarmerBreakConfig;
    @Getter
    private static ConfigFile armorDamageReductionConfig;
    @Getter
    private static ConfigFile healthConfig;
    @Getter
    private static ConfigFile bedOwnershipConfig;
    @Getter
    private static ConfigFile serverConfig;
    @Getter
    private static ConfigFile instinctConfig;
    @Getter
    private static ConfigFile locatorBarConfig;
    @Getter
    private static ConfigFile grindConfig;
    private static final List<EntityType> BREEDABLE =  List.of(EntityType.AXOLOTL, EntityType.CAMEL, EntityType.CAT, EntityType.CHICKEN, EntityType.COD, EntityType.COW, EntityType.DONKEY, EntityType.FOX, EntityType.FROG, EntityType.GOAT, EntityType.HOGLIN, EntityType.HORSE, EntityType.LLAMA, EntityType.MUSHROOM_COW, EntityType.OCELOT, EntityType.PANDA, EntityType.PARROT, EntityType.PIG, EntityType.RABBIT, EntityType.SHEEP, EntityType.STRIDER, EntityType.TADPOLE, EntityType.TURTLE, EntityType.WOLF);
    public static final List<EntityType> TAMEABLE = List.of(EntityType.WOLF, EntityType.OCELOT, EntityType.CAT, EntityType.PARROT, EntityType.HORSE, EntityType.DONKEY, EntityType.MULE, EntityType.LLAMA, EntityType.TRADER_LLAMA);


    public static void initialize() {
        Supplier<Map<String, Object>> playerDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("SPECIALIZATION_BONUS", 0.3);
            data.put("MULTI_CLASS_PENALTY", 0.15);
            data.put("LINEAR_DECAY_RATE", 0.02);
            data.put("CROSS_SKILL_PENALTY", 0.25);
            return data;
        };
        playerConfig = new ConfigFile(OpenLab.getInstance(), "playerConfig", playerDefaults);

        Supplier<Map<String, Object>> locatorBarDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("LOCATOR_BAR_ENABLED", true);
            data.put("DEFAULT_RECEIVE_RANGE", 0.0);
            data.put("DEFAULT_TRANSMIT_RANGE", 64.0);
            data.put("TEMPORARY_VISIBILITY_RANGE", 128.0);
            data.put("OBSERVER_RECEIVE_RANGE", 128.0);
            return data;
        };
        locatorBarConfig = new ConfigFile(OpenLab.getInstance(), "locatorBarConfig", locatorBarDefaults);

        Supplier<Map<String, Double>> reinforcementDefaults = () -> {
            Map<String, Double> data = new HashMap<>();
            data.put("LIGHT_REINFORCEMENT_MULTIPLIER", 0.2);
            data.put("HEAVY_REINFORCEMENT_MULTIPLIER", 0.1);
            data.put("LIGHT_REINFORCEMENT_LEVEL", 1.0);
            data.put("HEAVY_REINFORCEMENT_LEVEL", 2.0);
            data.put("LIGHT_EXPLOSION_RESISTANCE", 0.75);
            data.put("HEAVY_EXPLOSION_RESISTANCE", 0.95);
            return data;
        };
        reinforcementConfig = new ConfigFile(OpenLab.getInstance(), "reinforcementConfig", reinforcementDefaults);

        Supplier<Map<String, Object>> unlockedRecipesDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            for (SkillType skillType : SkillType.values()) {
                for (SkillLevel skillLevel : SkillLevel.values()) {
                    data.put(skillType + "_" + skillLevel, new HashSet<NamespacedKey>());
                }
            }
            data.put("blacklist", new ArrayList());
            data.put("mod_blacklist", new ArrayList());
            return data;
        };
        unlockedRecipesConfig = new ConfigFile(OpenLab.getInstance(), "unlockedRecipesConfig", unlockedRecipesDefaults);

        Supplier<Map<String, List<Object>>> cookingConfigDefaults = () -> {
            Map<String, List<Object>> data = new HashMap<>();
            data.put("possible_recipients", List.of("minecraft:bread"));
            data.put("possible_ingredients", List.of("minecraft:apple", "minecraft:golden_apple", "minecraft:enchanted_golden_apple",
                    "minecraft:melon_slice", "minecraft:glistering_melon_slice", "minecraft:sweet_berries",
                    "minecraft:glow_berries", "minecraft:sea_pickle", "minecraft:carrot", "minecraft:golden_carrot",
                    "minecraft:potato", "minecraft:baked_potato", "minecraft:beetroot", "minecraft:nether_wart",
                    "minecraft:pitcher_pod", "minecraft:brown_mushroom", "minecraft:red_mushroom",
                    "minecraft:warped_fungus", "minecraft:crimson_fungus", "minecraft:wheat", "minecraft:egg",
                    "minecraft:turtle_egg", "minecraft:sniffer_egg", "minecraft:milk_bucket", "minecraft:chicken",
                    "minecraft:cooked_chicken", "minecraft:rabbit", "minecraft:cooked_rabbit", "minecraft:mutton",
                    "minecraft:cooked_mutton", "minecraft:beef", "minecraft:cooked_beef", "minecraft:cod",
                    "minecraft:cooked_cod", "minecraft:salmon", "minecraft:cooked_salmon", "minecraft:tropical_fish",
                    "minecraft:pufferfish", "minecraft:dried_kelp"));
            data.put("possible_seasonings", List.of("specialization:salt", "minecraft:sugar", "minecraft:cocoa_beans", "minecraft:honey_bottle",
                    "minecraft:glow_lichen", "minecraft:blaze_powder"));
            data.put("possible_sauces", List.of("minecraft:glow_ink_sac", "minecraft:ink_sac", "minecraft:ghast_tear"));
            // Default sound id to play when cooking finishes (can be overridden in cookingConfig.json)
            data.put("finish_sound", Collections.singletonList("specialization:cooking_success"));
            for (SkillType skillType : SkillType.values()) {
                for (SkillLevel skillLevel : SkillLevel.values()) {
                    data.put(skillType + "_" + skillLevel, new ArrayList<>());
                }
            }
            List<Object> farmer = data.get(SkillType.FARMER.name() + "_" + SkillLevel.JOURNEYMAN.name());
            Map<String, Object> recipeData = new HashMap<>();
            recipeData.put("exp", 25);
            recipeData.put("cooking_time", 25);
            recipeData.put("ingredients", List.of("minecraft:apple"));
            recipeData.put("recipient", "minecraft:bread");
            recipeData.put("result", "specialization:apple_bread");
            farmer.add(recipeData);
            return data;
        };
        cookingConfig = new ConfigFile(OpenLab.getInstance(), "cookingConfig", cookingConfigDefaults);

        Supplier<Map<String, Map<String, Object>>> xpGainFromCraftingDefaults = () -> {
            Map<String, Map<String, Object>> data = new HashMap<>();
            for (SkillType skillType : SkillType.values()) {
                data.put(skillType.name(), new HashMap<>());
            }
            Map<String, Object> skillType = data.get(SkillType.BLACKSMITH.name());
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isItem() && inputMaterial != Material.AIR) {
                    skillType.put(inputMaterial.getKey().getKey().toUpperCase(Locale.ROOT), 1D);
                }
            }
            return data;
        };
        xpGainFromCraftingConfig = new ConfigFile(OpenLab.getInstance(), "xpGainFromCrafting", xpGainFromCraftingDefaults);

        Supplier<Map<String, Map<String, Object>>> grindDefaults = () -> {
            Map<String, Map<String, Object>> data = new HashMap<>();
            for (SkillType skillType : SkillType.values()) {
                data.put(skillType.name(), new HashMap<>());
            }
            Map<String, Object> skillType = data.get(SkillType.MINER.name());
            List<Map<String, Object>> conversions = new ArrayList<>();
            Map<String, Object> conversion = new HashMap<>();
            conversion.put("level", 1);
            conversion.put("foodCost", 1);
            conversion.put("amount", 1);
            conversion.put("chance", 0.5);
            conversion.put("xp", 1);
            conversion.put("item", "SAND");
            conversions.add(conversion);
            skillType.put("STONE", conversions);
            return data;
        };

        grindConfig = new ConfigFile(OpenLab.getInstance(), "grind", grindDefaults);

        Supplier<Map<String, Map<String, Object>>> xpGainFromStonecuttingDefaults = () -> {
            Map<String, Map<String, Object>> data = new HashMap<>();
            for (SkillType skillType : SkillType.values()) {
                data.put(skillType.name(), new HashMap<>());
            }
            Map<String, Object> skillType = data.get(SkillType.BUILDER.name());
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isItem() && inputMaterial != Material.AIR) {
                    Bukkit.recipeIterator().forEachRemaining((recipe) -> {
                        if (recipe instanceof StonecuttingRecipe stonecuttingRecipe) {
                            if (stonecuttingRecipe.getResult().equals(new ItemStack(inputMaterial))) {
                                skillType.put(inputMaterial.name(), 1);
                            }
                        }
                    });
                }
            }
            return data;
        };
        xpGainFromStonecuttingConfig = new ConfigFile(OpenLab.getInstance(), "xpGainFromStonecutting", xpGainFromStonecuttingDefaults);

        Supplier<Map<String, Double>> combatDefaults = () -> {
            Map<String, Double> data = new HashMap<>();
            data.put("CROSSBOW_BASE_VELOCITY", 1.2);
            data.put("CROSSBOW_BASE_PIERCING_VELOCITY", 1.1);
            data.put("CROSSBOW_BASE_MULTISHOT_VELOCITY", 1.3);
            data.put("CROSSBOW_BASE_QUICKCHARGE_VELOCITY", 1.15);
            return data;
        };
        combatConfig = new ConfigFile(OpenLab.getInstance(), "combatConfig", combatDefaults);

        Supplier<Map<String, String>> serverDefaults = () -> {
            Map<String, String> data = new HashMap<>();
            data.put("SERVER_ANALYTIC", "server_1");
            return data;
        };
        serverConfig = new ConfigFile(OpenLab.getInstance(), "serverConfig", serverDefaults);

        Supplier<Map<String, Double>> hungerDefaults = () -> {
            Map<String, Double> data = new HashMap<>();
            data.put("SPRINTING_DRAIN", 2.0);
            data.put("WALKING_DRAIN", 0.5);
            data.put("CROUCHING_DRAIN", 0.2);
            data.put("SWIMMING_DRAIN", 4.0);
            data.put("IDLE_DRAIN", 0.1);
            data.put("DRAIN_INTERVAL_IN_TICKS", 100.0);
            data.put("IDLE_CHECK_TIME_IN_TICKS", 100.0);
            data.put("HUNGER_REDUCTION_ON_NON_UNIQUE_CONSECUTIVE_FOOD", 1.0);
            return data;
        };
        hungerConfig = new ConfigFile(OpenLab.getInstance(), "hungerConfig", hungerDefaults);

        Supplier<Map<String, Double>> hungerCostDefaults = () -> {
            Map<String, Double> data = new HashMap<>();
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isItem() && inputMaterial != Material.AIR) {
                    data.put(inputMaterial.getKey().getKey().toUpperCase(Locale.ROOT), 1D);
                }
            }
            return data;
        };
        hungerCostConfig = new ConfigFile(OpenLab.getInstance(), "hungerCostConfig", hungerCostDefaults);

        Supplier< Map<String, Integer>> foodExpirationDefaults = () -> {
            Map<String, Integer> data = new HashMap<>();
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isEdible() && inputMaterial != Material.AIR) {
                    data.put(inputMaterial.getKey().getKey().toUpperCase(Locale.ROOT), 24);
                }
            }
            return data;
        };
        foodExpirationConfig = new ConfigFile(OpenLab.getInstance(), "foodExpirationConfig", foodExpirationDefaults);
        Supplier<Map<String, Object>> mobDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("DAYTIME_MOB_DAMAGE_MULTIPLIER", 4.0);
            data.put("NIGHTTIME_MOB_DAMAGE_MULTIPLIER", 10.0);
            data.put("NIGHT_GUARDSMAN_MOB_DAMAGE_PERCENT_REDUCTION", 30);
            data.put("DAYTIME_SPEED_BUFF", .03);
            data.put("NIGHTTIME_SPEED_BUFF", .2);
            data.put("MOB_RULE_TARGET_RANGE", 48);
            data.put("MOB_RULE_VERTICAL_FOLLOW_RANGE", 16);
            data.put("BLOCK_BREAK_CHANCE_PERCENTAGE", 30);
            data.put("BLOCK_BREAK_IGNORE_LIST_REGEX", List.of(".*BRICK.*", "OBSIDIAN"));
            data.put("VISUAL_BREAKING_INCREASE_PER_TICK_PERCENTAGE", 1.0);
            return data;
        };
        mobConfig = new ConfigFile(OpenLab.getInstance(), "mobConfig", mobDefaults);

        Supplier<Map<String, Object>> mobDropsDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            for (EntityType entityType : EntityType.values()) {
                if (entityType.isAlive()) {
                    data.put(entityType.name(), List.of());
                }
            }
            return data;
        };
        mobDropsConfig = new ConfigFile(OpenLab.getInstance(), "mobDrops", mobDropsDefaults);

        Supplier<Map<String, Map<String, Object>>> xpGainFromRepairingDefaults = () -> {
            Map<String, Map<String, Object>> data = new HashMap<>();
            for (SkillType skillType : SkillType.values()) {
                data.put(skillType.name(), new HashMap<>());
            }
            Map<String, Object> skillType = data.get(SkillType.BLACKSMITH.name());
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isItem() && inputMaterial != Material.AIR && inputMaterial.getMaxDurability() > 0) {
                    skillType.put(inputMaterial.name(), 0.0);
                }
            }
            return data;
        };
        xpGainFromRepairingConfig = new ConfigFile(OpenLab.getInstance(), "xpGainFromRepairing", xpGainFromRepairingDefaults);

        Supplier<Map<String, Double>> guardsmanDefaults = () -> {
            Map<String, Double> data = new HashMap<>();
            for (EntityType entityType : EntityType.values()) {
                data.put(entityType.name(), 1.0);
            }
            data.put("NON_GUARDSMAN_DAMAGE_REDUCTION", 0.25);
            return data;
        };
        guardsmanConfig = new ConfigFile(OpenLab.getInstance(), "guardsmanConfig", guardsmanDefaults);

        Supplier<Map<String, Object>> downedDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("TIME_TO_DEATH_IN_TICKS", 2400.0);
            data.put("OFFSET_TO_GROUND", 1.9);
            data.put("revive_items", new ArrayList<String>());
            return data;
        };
        downedConfig = new ConfigFile(OpenLab.getInstance(), "downedConfig", downedDefaults);

        Supplier<Map<String, String>> canFarmerBreakDefaults = () -> {
            Map<String, String> data = new HashMap<>();
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isBlock()) {
                    data.put(inputMaterial.toString(), SkillLevel.NOVICE.name());
                }
            }
            return data;
        };
        canFarmerBreakConfig = new ConfigFile(OpenLab.getInstance(), "canFarmerBreakConfig", canFarmerBreakDefaults);

        Supplier<Map<String, String>> canMinerLvlBreakDefaults = () -> {
            Map<String, String> data = new HashMap<>();
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isBlock()) {
                    data.put(inputMaterial.toString(), SkillLevel.NOVICE.name());
                }
            }
            return data;
        };
        canMinerLvlBreakConfig = new ConfigFile(OpenLab.getInstance(), "canMinerLvlBreakConfig", canMinerLvlBreakDefaults);

        Supplier<Map<String,Object>> berserkDefaults = () -> {
            List<Map<String,Object>> list = new ArrayList<>();
            list.add(Map.of(PotionEffectType.ABSORPTION.getKey().getKey().toUpperCase(Locale.ROOT), new PotionEffectData(0, 0).toMap()));
            return Map.of("berserk_effect",list);
        };
        berserkConfig = new ConfigFile(OpenLab.getInstance(), "berserkConfig", berserkDefaults);

        Supplier<Map<String, Map<String, Object>>> xpGainFromBlastingDefaults = () -> {
            Map<String, Map<String, Object>> data = new HashMap<>();
            for (SkillType skillType : SkillType.values()) {
                data.put(skillType.name(), new HashMap<>());
            }
            Map<String, Object> skillType = data.get(SkillType.MINER.name());
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isItem() && inputMaterial != Material.AIR) {
                    Bukkit.recipeIterator().forEachRemaining((recipe) -> {
                        if (recipe instanceof BlastingRecipe blastingRecipe) {
                            if (blastingRecipe.getResult().equals(new ItemStack(inputMaterial))) {
                                skillType.put(inputMaterial.name(), 0);
                            }
                        }
                    });
                }
            }
            return data;
        };
        xpGainFromBlastingConfig = new ConfigFile(OpenLab.getInstance(), "xpGainFromBlasting", xpGainFromBlastingDefaults);



        Supplier<Map<String, Map<String, Object>>> xpGainFromSmeltingDefaults = () -> {
            Map<String, Map<String, Object>> data = new HashMap<>();
            for (SkillType skillType : SkillType.values()) {
                data.put(skillType.name(), new HashMap<>());
            }
            Map<String, Object> skillType = data.get(SkillType.FARMER.name());
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isItem() && inputMaterial != Material.AIR) {
                    Bukkit.recipeIterator().forEachRemaining((recipe) -> {
                        if (recipe instanceof FurnaceRecipe furnaceRecipe) {
                            if (furnaceRecipe.getResult().equals(new ItemStack(inputMaterial))) {
                                skillType.put(inputMaterial.name(), 0);
                            }
                        }
                    });
                }
            }
            return data;
        };
        xpGainFromSmeltingConfig = new ConfigFile(OpenLab.getInstance(), "xpGainFromSmelting", xpGainFromSmeltingDefaults);

        Supplier<Map<String, Map<String, Object>>> xpGainFromSmokingDefaults = () -> {
            Map<String, Map<String, Object>> data = new HashMap<>();
            for (SkillType skillType : SkillType.values()) {
                data.put(skillType.name(), new HashMap<>());
            }
            Map<String, Object> skillType = data.get(SkillType.FARMER.name());
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isItem() && inputMaterial != Material.AIR) {
                    Bukkit.recipeIterator().forEachRemaining((recipe) -> {
                        if (recipe instanceof SmokingRecipe smokingRecipe) {
                            if (smokingRecipe.getResult().equals(new ItemStack(inputMaterial))) {
                                skillType.put(inputMaterial.name(), 0);
                            }
                        }
                    });
                }
            }
            return data;
        };
        xpGainFromSmokingConfig = new ConfigFile(OpenLab.getInstance(), "xpGainFromSmoking", xpGainFromSmokingDefaults);

        Supplier<Map<String, Map<String, Double>>> xpGainFromBreakingDefaults = () -> {
            Map<String, Map<String, Double>> data = new HashMap<>();
            for (SkillType skillType : SkillType.values()) {
                data.put(skillType.name(), new HashMap<>());
            }
            Map<String, Double> skillType = data.get(SkillType.FARMER.name());
            for (Material inputMaterial : Material.values()) {
                if (inputMaterial.isBlock()) {
                    skillType.put(inputMaterial.getKey().getKey().toUpperCase(Locale.ROOT), 0.0);
                }
            }
            return data;
        };
        xpGainFromBreakingConfig = new ConfigFile(
                OpenLab.getInstance(),
                "xpGainFromBreaking",
                xpGainFromBreakingDefaults
        );

        Supplier<Map<String, Map<String, Object>>> xpGainFromPlacingDefaults = () -> {
            Map<String, Map<String, Object>> data = new HashMap<>();
            for (SkillType skillType : SkillType.values()) {
                data.put(skillType.name(), new HashMap<>());
            }
            Map<String, Object> skillType = data.get(SkillType.BUILDER.name());
            for (Material material : Material.values()) {
                if (material.isBlock()) {
                    skillType.put(material.getKey().getKey().toUpperCase(Locale.ROOT), 0.0);
                }
            }
            return data;
        };
        xpGainFromPlacingConfig = new ConfigFile(
                OpenLab.getInstance(),
                "xpGainFromPlacing",
                xpGainFromPlacingDefaults
        );
        Supplier<Map<String, List<String>>> canUseItemDefaults = () -> {
            Map<String, List<String>> data = new HashMap<>();
            data.put("blacklist", List.of());
            data.put("mod_blacklist", List.of());
            for (SkillType skillType : SkillType.values()) {
                for (SkillLevel skillLevel : SkillLevel.values()) {
                    data.put(skillType + "_" + skillLevel, new ArrayList<>());
                }
            }
            return data;
        };

        canUseItemConfig = new ConfigFile(
                OpenLab.getInstance(),
                "canUseItem",
                canUseItemDefaults
        );

        Supplier<Map<String, List<String>>> canUseBlockDefaults = () -> {
            Map<String, List<String>> data = new HashMap<>();
            data.put("default", Arrays.asList(InventoryType.CRAFTING.toString(), InventoryType.FURNACE.toString()));
            for (SkillType skillType : SkillType.values()) {
                for (SkillLevel skillLevel : SkillLevel.values()) {
                    data.put(skillType + "_" + skillLevel, new ArrayList<>());
                }
            }
            return data;
        };

        canUseBlockConfig = new ConfigFile(
                OpenLab.getInstance(),
                "canUseBlock",
                canUseBlockDefaults
        );

        Supplier<Map<String, List<Map<String, Object>>>> classSkillEffectsDefaults = () -> {
            Map<String, List<Map<String, Object>>> data = new HashMap<>();
            for (SkillType skillType : SkillType.values()) {
                for (SkillLevel skillLevel : SkillLevel.values()) {
                    Map<String, Object> effectMap = new HashMap<>();
                    effectMap.put("effect", "minecraft:haste");
                    effectMap.put("amplifier", 0);
                    data.put(skillType + "_" + skillLevel, List.of(effectMap));
                }
            }
            return data;
        };
        classSkillEffectsConfig = new ConfigFile(
                OpenLab.getInstance(),
                "classSkillEffectsConfig",
                classSkillEffectsDefaults
        );

        Supplier<Map<String, List<String>>> allRecipeBankDefaults = () -> {
            Map<String, List<String>> data = new HashMap<>();
            Set<NamespacedKey> allRecipes = new HashSet<>();
            Bukkit.recipeIterator().forEachRemaining((recipe) -> {
                if (recipe instanceof Keyed keyed) {
                    allRecipes.add(keyed.getKey());
                }
            });
            data.put("ALL_RECIPES", allRecipes.stream().map(NamespacedKey::toString).toList());
            return data;
        };
        allRecipeBank = new ConfigFile(
                OpenLab.getInstance(),
                "allRecipeBank",
                allRecipeBankDefaults
        );

        Supplier<Map<String, Double>> blockHardnessDefaults = () -> {
            Map<String, Double> data = new HashMap<>();
            for (Material material : Material.values()) {
                if (material.isBlock() && !material.isAir()) {
                    data.put(material.getKey().getKey().toUpperCase(Locale.ROOT), 1.0);
                }
            }
            return data;
        };
        blockHardnessConfig = new ConfigFile(
                OpenLab.getInstance(),
                "blockHardnessConfig",
                blockHardnessDefaults
        );

        Supplier<Map<String, Object>> armorDamageReductionDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("HELMET_BASE_REDUCTION", 0.05);
            data.put("CHESTPLATE_BASE_REDUCTION", 0.15);
            data.put("LEGGINGS_BASE_REDUCTION", 0.10);
            data.put("BOOTS_BASE_REDUCTION", 0.05);
            data.put("LEATHER_MULTIPLIER", 0.5);
            data.put("CHAINMAIL_MULTIPLIER", 0.75);
            data.put("IRON_MULTIPLIER", 1.0);
            data.put("DIAMOND_MULTIPLIER", 1.5);
            data.put("GOLDEN_MULTIPLIER", 0.8);
            data.put("NETHERITE_MULTIPLIER", 2.0);
            data.put("MAX_TOTAL_REDUCTION", 0.8);
            data.put("ENABLED", true);
            return data;
        };
        armorDamageReductionConfig = new ConfigFile(
                OpenLab.getInstance(),
                "armorDamageReductionConfig",
                armorDamageReductionDefaults
        );

        Supplier<Map<String, Object>> healthDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("MAX_HEALTH", 20D);
            data.put("DEATH_REDUCED_MAX_HEALTH", 8D);
            data.put("BLESSED_FOOD_HEALTH_RESTORE_AMOUNT", 2D);
            data.put("HEALTH_ENABLED", true);
            data.put("LIMIT_SLEEP_REGEN_PER_DAY", true);
            data.put("SLEEP_REGEN_CAP", 5);
            data.put("SLEEP_REGEN_TICK_SPEED", 900);
            return data;
        };
        healthConfig = new ConfigFile(
                OpenLab.getInstance(),
                "healthConfig",
                healthDefaults
        );

        Supplier<Map<String, Object>> bedOwnershipDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("BED_OWNERSHIP_ENABLED", true);
            data.put("ALLOW_BED_SHARING", false);
            data.put("BED_OWNERSHIP_MESSAGE", "§cThis bed is already claimed by another player!");
            data.put("BED_CLAIM_MESSAGE", "§aYou have claimed this bed as your spawn point!");
            data.put("BED_UNCLAIM_MESSAGE", "§7Your previous bed has been unclaimed.");
            data.put("BED_RESPAWN_HUNGER_REDUCTION_ENABLED", true);
            data.put("BED_RESPAWN_HUNGER_DIVISOR", 3);
            data.put("BED_RESPAWN_MINIMUM_HUNGER", 1);
            data.put("BED_RESPAWN_SHOW_MESSAGE", true);
            return data;
        };
        bedOwnershipConfig = new ConfigFile(
                OpenLab.getInstance(),
                "bedOwnershipConfig",
                bedOwnershipDefaults
        );

        Supplier<Map<String, Object>> skillsDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            for (SkillType skillType : SkillType.values()) {
                data.put(skillType + "_WORKSTATION", Material.COMPOSTER.getKey().getKey().toUpperCase(Locale.ROOT));
                data.put(skillType + "_DESCRIPTION", "Description");
                data.put(skillType + "_XP_MULTIPLIER", 1.0);
                data.put(skillType + "_XP_GAIN_REQUIREMENT_PER_LEVEL", 100D);
                data.put(skillType + "_XP_DECAY", 0.05D);
                for (SkillLevel skillLevel : SkillLevel.values()) {
                    data.put(skillType + "_" + skillLevel + "_REQUIREMENT", 0D);
                }

            }
            data.put("XP_MULTIPLIER", 1.0);
            data.put("XP_LOSS", 1.0);
            return data;
        };
        skillsConfig = new ConfigFile(
                OpenLab.getInstance(),
                "skillsConfig",
                skillsDefaults
        );


        Supplier<Map<String, Object>> librarianDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("ENCHANTABLE_TOOL_REGEX", "^(?i)(?:(wooden|stone|iron|diamond|golden|netherite)_(?:(pickaxe|axe|shovel|sword|hoe))|(leather|chainmail|iron|diamond|golden|netherite)_(?:(helmet|chestplate|leggings|boots))|fishing_rod|shears|flint_and_steel|bow|crossbow|trident|mace|elytra|book|shield)");
            data.put("BLUEPRINT_ITEM_RECIPES", "^(?i)(?:(wooden|stone|iron|diamond|golden|netherite)_(?:(pickaxe|axe|shovel|sword|hoe))|(leather|chainmail|iron|diamond|golden|netherite)_(?:(helmet|chestplate|leggings|boots))|fishing_rod|shears|flint_and_steel|bow|crossbow|trident|mace|elytra|book|shield)");
            data.put("BANNED_BLESS_ENCHANTS", List.of(Enchantment.MENDING.getKey().getKey()));
            data.put("BLESS_ITEM_LIBRARIAN_LEVEL", 2);
            data.put("BLESS_ITEM_XP_LEVEL_REQUIREMENT", 3);
            data.put("ITEM_LORE_LIBRARIAN_LEVEL", 3);
            return data;
        };
        librarianConfig = new ConfigFile(
                OpenLab.getInstance(),
                "librarianConfig",
                librarianDefaults
        );
        Supplier<Map<String, Object>> minerDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("TRESSURE_TRIGGERING_BLOCKS", new HashMap<>());
            return data;
        };
        minerConfig = new ConfigFile(
                OpenLab.getInstance(),
                "minerConfig",
                minerDefaults
        );

        Supplier<Map<String, Object>> farmerDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            for (EntityType animal : BREEDABLE) {
                data.put("FARMER_BREED_LEVEL_" + animal, SkillLevel.JOURNEYMAN.getLevel());
            }
            for (SkillLevel skillLevel : SkillLevel.values()) {
                data.put("FARMER_GET_DROPS_CHANCE_" + skillLevel, 0.5);
            }
            return data;
        };
        farmerConfig = new ConfigFile(
                OpenLab.getInstance(),
                "farmerConfig",
                farmerDefaults
        );

        Supplier<Map<String, Map<String, Object>>> tameableDefaults = () -> {
            Map<String, Map<String, Object>> data = new HashMap<>();
            for (SkillType skillType : SkillType.values()) {
                data.put(skillType.name(), new HashMap<>());
            }
            Map<String, Object> skillType = data.get(SkillType.FARMER.name());
            for (EntityType tameable : TAMEABLE) {
                skillType.put("TAME_" + tameable, SkillLevel.NOVICE.getLevel());
            }
            return data;
        };
        tameableConfig = new ConfigFile(
                OpenLab.getInstance(),
                "tamingConfig",
                tameableDefaults
        );

        Supplier<Map<String, Object>> chatDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("CHAT_RADIUS", 32.0);
            data.put("DEFAULT_FORMAT", "%s > %s");
            data.put("ANNOUNCEMENT_FORMAT", "<aqua>[Announcement]<gray> %s");
            data.put("ANNOUNCEMENT_PREFIX", "#");
            return data;
        };
        chatConfig = new ConfigFile(
                OpenLab.getInstance(),
                "chatConfig",
                chatDefaults
        );

        Supplier<Map<String, Object>> instinctDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("INSTINCT_ENABLED", true);
            data.put("INSTINCT_DETECTION_RADIUS_LEVEL_1", 8.0);
            data.put("INSTINCT_DETECTION_RADIUS_LEVEL_2", 12.0);
            data.put("INSTINCT_DETECTION_RADIUS_LEVEL_3", 16.0);
            data.put("INSTINCT_GLOW_DURATION_TICKS", 300);
            return data;
        };
        instinctConfig = new ConfigFile(
                OpenLab.getInstance(),
                "instinctConfig",
                instinctDefaults
        );

        Supplier<Map<String, Object>> xpMonitorDefaults = () -> {
            Map<String, Object> data = new HashMap<>();
            data.put("FARMER.threshold", 600.0);
            data.put("FARMER.cooldown-seconds", 30);
            data.put("BUILDER.threshold", 600.0);
            data.put("BUILDER.cooldown-seconds", 30);
            data.put("MINER.threshold", 600.0);
            data.put("MINER.cooldown-seconds", 30);
            data.put("HEALER.threshold", 600.0);
            data.put("HEALER.cooldown-seconds", 30);
            data.put("LIBRARIAN.threshold", 550.0);
            data.put("LIBRARIAN.cooldown-seconds", 30);
            data.put("GUARDSMAN.threshold", 550.0);
            data.put("GUARDSMAN.cooldown-seconds", 30);
            data.put("BLACKSMITH.threshold", 600.0);
            data.put("BLACKSMITH.cooldown-seconds", 30);
            return data;
        };
        xpMonitorConfig = new ConfigFile(
                OpenLab.getInstance(),
                "XpMonitorAlertThresholds",
                xpMonitorDefaults
        );
    }//change

}
