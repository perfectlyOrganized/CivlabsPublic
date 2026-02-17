package com.minecraftcivilizations.specialization.Recipe;

import com.minecraftcivilizations.specialization.CustomItem.CustomItem;
import com.minecraftcivilizations.specialization.CustomItem.CustomItemRegistry;
import com.minecraftcivilizations.specialization.Specialization;
import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Recipes {

    public static void init() {
        registerCustomItems();
        unregisterRecipes();
        registerRecipes(false);
    }

    private static void registerCustomItems() {
        // Your custom items – unchanged
    }

    public static void unregisterRecipes() {
        Bukkit.removeRecipe(NamespacedKey.minecraft("rail"));
    }

    public static void registerRecipes(boolean reloading) {
        int successCount = 0;
        int skippedMissingCount = 0;
        int skippedDuplicateCount = 0;
        List<String> failedExceptions = new ArrayList<>();

        // ----- CUSTOM ITEM RECIPES -----
        for (NamespacedKey key : CustomItemRegistry.getItems().keySet()) {
            CustomItem customItem = CustomItemRegistry.getItem(key);
            if (customItem == null) {
                skippedMissingCount++;
                continue;
            }

            if (recipeExists(key, customItem.getItem())) {
                skippedDuplicateCount++;
                continue;
            }

            try {
                Bukkit.addRecipe(new ShapelessRecipe(key, customItem.getItem()));
                successCount++;
            } catch (Exception e) {
                failedExceptions.add(key.getKey() + " (" + e.getMessage() + ")");
            }
        }
        // ----- String recipe
        NamespacedKey stringRecipeKEY = new NamespacedKey(Specialization.getInstance(), "wool_to_string_recipe");
        ShapelessRecipe stringRecipe = new ShapelessRecipe(stringRecipeKEY, new ItemStack(Material.STRING,2));
        RecipeChoice wool = new RecipeChoice.MaterialChoice(Tag.WOOL);
        stringRecipe.addIngredient(wool);

        stringRecipe.addIngredient(Material.SHEARS);
        if (!recipeExists(stringRecipeKEY, stringRecipe.getResult())) {
            try {
                Bukkit.addRecipe(stringRecipe);
                successCount++;
            } catch (Exception e) {
                failedExceptions.add("wool_to_string_recipe (" + e.getMessage() + ")");
            }
        } else skippedDuplicateCount++;


        // ----- CUSTOM "RAIL" RECIPE -----
        NamespacedKey railKey = new NamespacedKey(Specialization.getInstance(), "rail_alt");
        ShapedRecipe rail = new ShapedRecipe(railKey, new ItemStack(Material.RAIL, 64));
        rail.shape("I I", "ISI", "I I");
        rail.setIngredient('I', Material.IRON_INGOT);
        rail.setIngredient('S', Material.STICK);
        if (!recipeExists(railKey, rail.getResult())) {
            try {
                Bukkit.addRecipe(rail);
                successCount++;
            } catch (Exception e) {
                failedExceptions.add("rail_alt (" + e.getMessage() + ")");
            }
        } else skippedDuplicateCount++;

        // ----- EXTRA RECIPES -----
        successCount += addNetherRecipes(failedExceptions, skippedDuplicateCount);
        addUnobtainableRecipes(failedExceptions, skippedDuplicateCount);
        addArmorTrims(failedExceptions, skippedDuplicateCount);
        successCount += addWoodcuttingRecipes(failedExceptions, skippedDuplicateCount);

        // ----- FINAL LOG -----
        Bukkit.getLogger().info("[Recipes] Registration complete. Total successes: " + successCount);
        if (skippedMissingCount > 0)
            Bukkit.getLogger().info("[Recipes] Skipped " + skippedMissingCount + " recipes: missing items.");
        if (skippedDuplicateCount > 0)
            Bukkit.getLogger().info("[Recipes] Skipped " + skippedDuplicateCount + " recipes: duplicates.");
        if (!failedExceptions.isEmpty()) {
            Bukkit.getLogger().warning("[Recipes] Failed recipes due to exceptions (" + failedExceptions.size() + "):");
            failedExceptions.forEach(f -> Bukkit.getLogger().warning(" - " + f));
        }
    }

    private static boolean recipeExists(NamespacedKey key, ItemStack result) {
        return Bukkit.getRecipesFor(result).stream()
                .filter(r -> r instanceof Keyed)
                .map(r -> (Keyed) r)
                .anyMatch(r -> r.getKey().equals(key));
    }

    public static int addNetherRecipes(List<String> failedExceptions, int skippedDuplicateCount) {
        int count = 0;

        NamespacedKey netheriteKey = new NamespacedKey(Specialization.getInstance(), "netherite_upgrade");
        ShapelessRecipe netheriteUpgrade = new ShapelessRecipe(netheriteKey,
                new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        netheriteUpgrade.addIngredient(1, Material.NETHERITE_INGOT);
        netheriteUpgrade.addIngredient(6, Material.DIAMOND);
        netheriteUpgrade.addIngredient(1, Material.NETHER_WART_BLOCK);
        if (!recipeExists(netheriteKey, netheriteUpgrade.getResult())) {
            try { Bukkit.addRecipe(netheriteUpgrade); count++; }
            catch (Exception e) { failedExceptions.add("netherite_upgrade (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey blazeRodKey = new NamespacedKey(Specialization.getInstance(), "blaze_rod");
        ShapelessRecipe blazeRod = new ShapelessRecipe(blazeRodKey, new ItemStack(Material.BLAZE_ROD));
        blazeRod.addIngredient(1, Material.GOLD_INGOT);
        blazeRod.addIngredient(3, Material.GUNPOWDER);
        blazeRod.addIngredient(1, Material.CRIMSON_NYLIUM);
        blazeRod.addIngredient(1, Material.WARPED_NYLIUM);
        if (!recipeExists(blazeRodKey, blazeRod.getResult())) {
            try { Bukkit.addRecipe(blazeRod); count++; }
            catch (Exception e) { failedExceptions.add("blaze_rod (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey netherWartKey = new NamespacedKey(Specialization.getInstance(), "nether_wart");
        ShapedRecipe netherWart = new ShapedRecipe(netherWartKey, new ItemStack(Material.NETHER_WART));
        netherWart.shape(" E ", "DDD", " B ");
        netherWart.setIngredient('E', Material.BEETROOT);
        netherWart.setIngredient('D', Material.COARSE_DIRT);
        netherWart.setIngredient('B', Material.BLAZE_POWDER);
        if (!recipeExists(netherWartKey, netherWart.getResult())) {
            try { Bukkit.addRecipe(netherWart); count++; }
            catch (Exception e) { failedExceptions.add("nether_wart (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        return count;
    }

    public static void addUnobtainableRecipes(List<String> failedExceptions, int skippedDuplicateCount) {
        NamespacedKey catEggKey = new NamespacedKey(Specialization.getInstance(), "cat_spawn_egg");
        ShapedRecipe catEgg = new ShapedRecipe(catEggKey, new ItemStack(Material.CAT_SPAWN_EGG));
        catEgg.shape("FFF", " E ", "FDF");
        catEgg.setIngredient('F', Material.TROPICAL_FISH);
        catEgg.setIngredient('E', Material.EGG);
        catEgg.setIngredient('D', Material.DIAMOND);
        if (!recipeExists(catEggKey, catEgg.getResult())) {
            try { Bukkit.addRecipe(catEgg); }
            catch (Exception e) { failedExceptions.add("cat_spawn_egg (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey bellKey = new NamespacedKey(Specialization.getInstance(), "bell");
        ShapedRecipe bell = new ShapedRecipe(bellKey, new ItemStack(Material.BELL));
        bell.shape(" W ", "GGG", "GGG");
        bell.setIngredient('W', new RecipeChoice.MaterialChoice(Tag.PLANKS));
        bell.setIngredient('G', Material.GOLD_INGOT);
        if (!recipeExists(bellKey, bell.getResult())) {
            try { Bukkit.addRecipe(bell); }
            catch (Exception e) { failedExceptions.add("bell (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey cobwebKey = new NamespacedKey(Specialization.getInstance(), "cobweb");
        ShapedRecipe cobweb = new ShapedRecipe(cobwebKey, new ItemStack(Material.COBWEB));
        cobweb.shape("SSS", "SSS", "SSS");
        cobweb.setIngredient('S', Material.STRING);
        if (!recipeExists(cobwebKey, cobweb.getResult())) {
            try { Bukkit.addRecipe(cobweb); }
            catch (Exception e) { failedExceptions.add("cobweb (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;
    }

    public static void addArmorTrims(List<String> failedExceptions, int skippedDuplicateCount) {
        NamespacedKey boltTrim = new NamespacedKey(Specialization.getInstance(), "bolt_trim");
        ShapedRecipe bolt = new ShapedRecipe(boltTrim, new ItemStack(Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE));
        bolt.shape("ABA", "BBB", "AAA");
        bolt.setIngredient('A', Material.LIGHT_BLUE_DYE);
        bolt.setIngredient('B', Material.COPPER_BLOCK);
        if (!recipeExists(boltTrim, bolt.getResult())) {
            try { Bukkit.addRecipe(bolt); }
            catch (Exception e) { failedExceptions.add("bolt_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey flowTrim = new NamespacedKey(Specialization.getInstance(), "flow_trim");
        ShapedRecipe flow = new ShapedRecipe(flowTrim, new ItemStack(Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE));
        flow.shape("ABA", "BBA", "ABB");
        flow.setIngredient('A', Material.LIGHT_BLUE_DYE);
        flow.setIngredient('B', Material.LIGHT_BLUE_TERRACOTTA);
        if (!recipeExists(flowTrim, flow.getResult())) {
            try { Bukkit.addRecipe(flow); }
            catch (Exception e) { failedExceptions.add("flow_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey tideTrim = new NamespacedKey(Specialization.getInstance(), "tide_trim");
        ShapedRecipe tide = new ShapedRecipe(tideTrim, new ItemStack(Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE));
        tide.shape("ABA", "ABA", "BAB");
        tide.setIngredient('A', Material.DEAD_BRAIN_CORAL_BLOCK);
        tide.setIngredient('B', Material.LIGHT_BLUE_TERRACOTTA);
        if (!recipeExists(tideTrim, tide.getResult())) {
            try { Bukkit.addRecipe(tide); }
            catch (Exception e) { failedExceptions.add("tide_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey silenceTrim = new NamespacedKey(Specialization.getInstance(), "silence_trim");
        ShapedRecipe silence = new ShapedRecipe(silenceTrim, new ItemStack(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE));
        silence.shape("ABC", "ABA", "CBA");
        silence.setIngredient('A', Material.DEEPSLATE);
        silence.setIngredient('B', Material.LIGHT_BLUE_DYE);
        silence.setIngredient('C', Material.SCULK);
        if (!recipeExists(silenceTrim, silence.getResult())) {
            try { Bukkit.addRecipe(silence); }
            catch (Exception e) { failedExceptions.add("silence_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey wardTrim = new NamespacedKey(Specialization.getInstance(), "ward_trim");
        ShapedRecipe ward = new ShapedRecipe(wardTrim, new ItemStack(Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE));
        ward.shape("ABA", "BAB", "BBB");
        ward.setIngredient('A', Material.LIGHT_BLUE_DYE);
        ward.setIngredient('B', Material.DEEPSLATE);
        if (!recipeExists(wardTrim, ward.getResult())) {
            try { Bukkit.addRecipe(ward); }
            catch (Exception e) { failedExceptions.add("ward_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey snoutTrim = new NamespacedKey(Specialization.getInstance(), "snout_trim");
        ShapedRecipe snout = new ShapedRecipe(snoutTrim, new ItemStack(Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE));
        snout.shape("AAA", "BAB", "AAA");
        snout.setIngredient('A', Material.BLACKSTONE);
        snout.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(snoutTrim, snout.getResult())) {
            try { Bukkit.addRecipe(snout); }
            catch (Exception e) { failedExceptions.add("snout_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey raiserTrim = new NamespacedKey(Specialization.getInstance(), "raiser_trim");
        ShapedRecipe raiser = new ShapedRecipe(raiserTrim, new ItemStack(Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE));
        raiser.shape("ABB", "BAB", "BBA");
        raiser.setIngredient('A', Material.LIGHT_BLUE_DYE);
        raiser.setIngredient('B', Material.TERRACOTTA);
        if (!recipeExists(raiserTrim, raiser.getResult())) {
            try { Bukkit.addRecipe(raiser); }
            catch (Exception e) { failedExceptions.add("raiser_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey shaperTrim = new NamespacedKey(Specialization.getInstance(), "shaper_trim");
        ShapedRecipe shaper = new ShapedRecipe(shaperTrim, new ItemStack(Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE));
        shaper.shape("AAB", "BBB", "BAA");
        shaper.setIngredient('A', Material.TERRACOTTA);
        shaper.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(shaperTrim, shaper.getResult())) {
            try { Bukkit.addRecipe(shaper); }
            catch (Exception e) { failedExceptions.add("shaper_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey hostTrim = new NamespacedKey(Specialization.getInstance(), "host_trim");
        ShapedRecipe host = new ShapedRecipe(hostTrim, new ItemStack(Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE));
        host.shape("ABA", "ABB", "ABA");
        host.setIngredient('A', Material.TERRACOTTA);
        host.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(hostTrim, host.getResult())) {
            try { Bukkit.addRecipe(host); }
            catch (Exception e) { failedExceptions.add("host_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey wildTrim = new NamespacedKey(Specialization.getInstance(), "wild_trim");
        ShapedRecipe wild = new ShapedRecipe(wildTrim, new ItemStack(Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE));
        wild.shape("AAA", "BBB", "AAA");
        wild.setIngredient('A', Material.MOSSY_COBBLESTONE);
        wild.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(wildTrim, wild.getResult())) {
            try { Bukkit.addRecipe(wild); }
            catch (Exception e) { failedExceptions.add("wild_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey coastTrim = new NamespacedKey(Specialization.getInstance(), "coast_trim");
        ShapedRecipe coast = new ShapedRecipe(coastTrim, new ItemStack(Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE));
        coast.shape("ABA", "CAC", "CCC");
        coast.setIngredient('A', Material.LIGHT_BLUE_DYE);
        coast.setIngredient('B', Material.DEAD_TUBE_CORAL);
        coast.setIngredient('C', Material.COBBLED_DEEPSLATE);
        if (!recipeExists(coastTrim, coast.getResult())) {
            try { Bukkit.addRecipe(coast); }
            catch (Exception e) { failedExceptions.add("coast_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey duneTrim = new NamespacedKey(Specialization.getInstance(), "dune_trim");
        ShapedRecipe dune = new ShapedRecipe(duneTrim, new ItemStack(Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE));
        dune.shape("AAA", "BBB", "AAA");
        dune.setIngredient('A', Material.LIGHT_BLUE_DYE);
        dune.setIngredient('B', Material.SANDSTONE);
        if (!recipeExists(duneTrim, dune.getResult())) {
            try { Bukkit.addRecipe(dune); }
            catch (Exception e) { failedExceptions.add("dune_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey eyeTrim = new NamespacedKey(Specialization.getInstance(), "eye_trim");
        ShapedRecipe eye = new ShapedRecipe(eyeTrim, new ItemStack(Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE));
        eye.shape("ABA", "BAB", "ABA");
        eye.setIngredient('A', Material.SANDSTONE);
        eye.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(eyeTrim, eye.getResult())) {
            try { Bukkit.addRecipe(eye); }
            catch (Exception e) { failedExceptions.add("eye_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey sentryTrim = new NamespacedKey(Specialization.getInstance(), "sentry_trim");
        ShapedRecipe sentry = new ShapedRecipe(sentryTrim, new ItemStack(Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE));
        sentry.shape("ABA", "BAB", "ABA");
        sentry.setIngredient('A', Material.COBBLESTONE);
        sentry.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(sentryTrim, sentry.getResult())) {
            try { Bukkit.addRecipe(sentry); }
            catch (Exception e) { failedExceptions.add("sentry_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey vexTrim = new NamespacedKey(Specialization.getInstance(), "vex_trim");
        ShapedRecipe vex = new ShapedRecipe(vexTrim, new ItemStack(Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE));
        vex.shape("ABB", "BAB", "BBA");
        vex.setIngredient('A', Material.LIGHT_BLUE_DYE);
        vex.setIngredient('B', Material.COBBLESTONE);
        if (!recipeExists(vexTrim, vex.getResult())) {
            try { Bukkit.addRecipe(vex); }
            catch (Exception e) { failedExceptions.add("vex_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey wayfinderTrim = new NamespacedKey(Specialization.getInstance(), "wayfinder_trim");
        ShapedRecipe wayfinder = new ShapedRecipe(wayfinderTrim, new ItemStack(Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE));
        wayfinder.shape("AAA", "ABA", "BAB");
        wayfinder.setIngredient('A', Material.TERRACOTTA);
        wayfinder.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(wayfinderTrim, wayfinder.getResult())) {
            try { Bukkit.addRecipe(wayfinder); }
            catch (Exception e) { failedExceptions.add("wayfinder_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;

        NamespacedKey spireTrim = new NamespacedKey(Specialization.getInstance(), "spire_trim");
        ShapedRecipe spire = new ShapedRecipe(spireTrim, new ItemStack(Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE));
        spire.shape("AAA", "ABA", "AAA");
        spire.setIngredient('A', Material.PINK_CONCRETE);
        spire.setIngredient('B', Material.LIGHT_BLUE_DYE);
        if (!recipeExists(spireTrim, spire.getResult())) {
            try { Bukkit.addRecipe(spire); }
            catch (Exception e) { failedExceptions.add("spire_trim (" + e.getMessage() + ")"); }
        } else skippedDuplicateCount++;
    }

    /**
     * Adds woodcutting recipes for the stonecutter.
     * Allows converting logs into various wood products.
     *
     * LOG input results:
     * - planks = 4
     * - stripped log = 1
     * - stripped wood = 1
     * - stairs = 4
     * - slabs = 8
     * - fences = 2
     * - fence gates = 2 (bamboo = 1)
     *
     * WOOD (bark on all sides) input results:
     * - 4 planks
     * - stripped log = 1
     * - stripped wood = 1
     * - 4 stairs
     * - 8 slabs
     * - 2 fences
     * - 2 fence gates
     *
     * STRIPPED LOG input results (non-bamboo):
     * - 4 planks
     * - 1 stripped wood
     * - 4 stairs
     * - 8 slabs
     *
     * STRIPPED WOOD input results (non-bamboo):
     * - 3 planks
     * - 3 stairs
     * - 6 slabs
     *
     * STRIPPED BAMBOO BLOCK input results:
     * - 4 slabs
     * - 2 stairs
     * - 2 planks
     * - 1 fence
     *
     * PLANKS input results:
     * - 2 slabs
     * - 1 stair
     */
    public static int addWoodcuttingRecipes(List<String> failedExceptions, int skippedDuplicateCount) {
        int count = 0;

        // Define all wood types with their corresponding materials
        // Format: {name, log, planks, strippedLog, strippedWood, wood, stairs, slab, fence, fenceGate}
        String[][] woodTypes = {
            {"oak", "OAK_LOG", "OAK_PLANKS", "STRIPPED_OAK_LOG", "STRIPPED_OAK_WOOD", "OAK_WOOD", "OAK_STAIRS", "OAK_SLAB", "OAK_FENCE", "OAK_FENCE_GATE"},
            {"spruce", "SPRUCE_LOG", "SPRUCE_PLANKS", "STRIPPED_SPRUCE_LOG", "STRIPPED_SPRUCE_WOOD", "SPRUCE_WOOD", "SPRUCE_STAIRS", "SPRUCE_SLAB", "SPRUCE_FENCE", "SPRUCE_FENCE_GATE"},
            {"birch", "BIRCH_LOG", "BIRCH_PLANKS", "STRIPPED_BIRCH_LOG", "STRIPPED_BIRCH_WOOD", "BIRCH_WOOD", "BIRCH_STAIRS", "BIRCH_SLAB", "BIRCH_FENCE", "BIRCH_FENCE_GATE"},
            {"jungle", "JUNGLE_LOG", "JUNGLE_PLANKS", "STRIPPED_JUNGLE_LOG", "STRIPPED_JUNGLE_WOOD", "JUNGLE_WOOD", "JUNGLE_STAIRS", "JUNGLE_SLAB", "JUNGLE_FENCE", "JUNGLE_FENCE_GATE"},
            {"acacia", "ACACIA_LOG", "ACACIA_PLANKS", "STRIPPED_ACACIA_LOG", "STRIPPED_ACACIA_WOOD", "ACACIA_WOOD", "ACACIA_STAIRS", "ACACIA_SLAB", "ACACIA_FENCE", "ACACIA_FENCE_GATE"},
            {"dark_oak", "DARK_OAK_LOG", "DARK_OAK_PLANKS", "STRIPPED_DARK_OAK_LOG", "STRIPPED_DARK_OAK_WOOD", "DARK_OAK_WOOD", "DARK_OAK_STAIRS", "DARK_OAK_SLAB", "DARK_OAK_FENCE", "DARK_OAK_FENCE_GATE"},
            {"mangrove", "MANGROVE_LOG", "MANGROVE_PLANKS", "STRIPPED_MANGROVE_LOG", "STRIPPED_MANGROVE_WOOD", "MANGROVE_WOOD", "MANGROVE_STAIRS", "MANGROVE_SLAB", "MANGROVE_FENCE", "MANGROVE_FENCE_GATE"},
            {"cherry", "CHERRY_LOG", "CHERRY_PLANKS", "STRIPPED_CHERRY_LOG", "STRIPPED_CHERRY_WOOD", "CHERRY_WOOD", "CHERRY_STAIRS", "CHERRY_SLAB", "CHERRY_FENCE", "CHERRY_FENCE_GATE"},
            {"pale_oak", "PALE_OAK_LOG", "PALE_OAK_PLANKS", "STRIPPED_PALE_OAK_LOG", "STRIPPED_PALE_OAK_WOOD", "PALE_OAK_WOOD", "PALE_OAK_STAIRS", "PALE_OAK_SLAB", "PALE_OAK_FENCE", "PALE_OAK_FENCE_GATE"},
            // Crimson and Warped (Nether woods) - use HYPHAE instead of WOOD
            {"crimson", "CRIMSON_STEM", "CRIMSON_PLANKS", "STRIPPED_CRIMSON_STEM", "STRIPPED_CRIMSON_HYPHAE", "CRIMSON_HYPHAE", "CRIMSON_STAIRS", "CRIMSON_SLAB", "CRIMSON_FENCE", "CRIMSON_FENCE_GATE"},
            {"warped", "WARPED_STEM", "WARPED_PLANKS", "STRIPPED_WARPED_STEM", "STRIPPED_WARPED_HYPHAE", "WARPED_HYPHAE", "WARPED_STAIRS", "WARPED_SLAB", "WARPED_FENCE", "WARPED_FENCE_GATE"},
            // Bamboo - no wood block variant
            {"bamboo", "BAMBOO_BLOCK", "BAMBOO_PLANKS", "STRIPPED_BAMBOO_BLOCK", "STRIPPED_BAMBOO_BLOCK", "BAMBOO_BLOCK", "BAMBOO_STAIRS", "BAMBOO_SLAB", "BAMBOO_FENCE", "BAMBOO_FENCE_GATE"}
        };

        for (String[] wood : woodTypes) {
            String woodName = wood[0];
            try {
                Material log = Material.valueOf(wood[1]);
                Material planks = Material.valueOf(wood[2]);
                Material strippedLog = Material.valueOf(wood[3]);
                Material strippedWood = Material.valueOf(wood[4]);
                Material woodBlock = Material.valueOf(wood[5]);
                Material stairs = Material.valueOf(wood[6]);
                Material slab = Material.valueOf(wood[7]);
                Material fence = Material.valueOf(wood[8]);
                Material fenceGate = Material.valueOf(wood[9]);

                // ==================== LOG INPUT RECIPES ====================
                // Log -> Planks (4)
                count += addStonecuttingRecipe("woodcut_log_" + woodName + "_planks", log, planks, 4, failedExceptions);
                // Log -> Stripped Log (1)
                count += addStonecuttingRecipe("woodcut_log_" + woodName + "_stripped_log", log, strippedLog, 1, failedExceptions);
                // Log -> Stripped Wood (1) - only if different from stripped log (not bamboo)
                if (strippedWood != strippedLog) {
                    count += addStonecuttingRecipe("woodcut_log_" + woodName + "_stripped_wood", log, strippedWood, 1, failedExceptions);
                }
                // Log -> Stairs (4)
                count += addStonecuttingRecipe("woodcut_log_" + woodName + "_stairs", log, stairs, 4, failedExceptions);
                // Log -> Slabs (8)
                count += addStonecuttingRecipe("woodcut_log_" + woodName + "_slabs", log, slab, 8, failedExceptions);
                // Log -> Fences (2)
                count += addStonecuttingRecipe("woodcut_log_" + woodName + "_fence", log, fence, 2, failedExceptions);
                // Log -> Fence Gates (2) - bamboo gets 1
                int fenceGateAmount = woodName.equals("bamboo") ? 1 : 2;
                count += addStonecuttingRecipe("woodcut_log_" + woodName + "_fence_gate", log, fenceGate, fenceGateAmount, failedExceptions);

                // ==================== WOOD (bark block) INPUT RECIPES ====================
                // Only add if wood block is different from log (not bamboo)
                if (woodBlock != log) {
                    // Wood -> Planks (4)
                    count += addStonecuttingRecipe("woodcut_wood_" + woodName + "_planks", woodBlock, planks, 4, failedExceptions);
                    // Wood -> Stripped Log (1)
                    count += addStonecuttingRecipe("woodcut_wood_" + woodName + "_stripped_log", woodBlock, strippedLog, 1, failedExceptions);
                    // Wood -> Stripped Wood (1) - only if different from stripped log
                    if (strippedWood != strippedLog) {
                        count += addStonecuttingRecipe("woodcut_wood_" + woodName + "_stripped_wood", woodBlock, strippedWood, 1, failedExceptions);
                    }
                    // Wood -> Stairs (4)
                    count += addStonecuttingRecipe("woodcut_wood_" + woodName + "_stairs", woodBlock, stairs, 4, failedExceptions);
                    // Wood -> Slabs (8)
                    count += addStonecuttingRecipe("woodcut_wood_" + woodName + "_slabs", woodBlock, slab, 8, failedExceptions);
                    // Wood -> Fences (2)
                    count += addStonecuttingRecipe("woodcut_wood_" + woodName + "_fence", woodBlock, fence, 2, failedExceptions);
                    // Wood -> Fence Gates (2)
                    count += addStonecuttingRecipe("woodcut_wood_" + woodName + "_fence_gate", woodBlock, fenceGate, 2, failedExceptions);
                }

                // ==================== STRIPPED LOG INPUT RECIPES ====================
                // Special handling for bamboo (stripped bamboo block)
                if (woodName.equals("bamboo")) {
                    // Stripped Bamboo -> Slabs (4)
                    count += addStonecuttingRecipe("woodcut_stripped_bamboo_slabs", strippedLog, slab, 4, failedExceptions);
                    // Stripped Bamboo -> Stairs (2)
                    count += addStonecuttingRecipe("woodcut_stripped_bamboo_stairs", strippedLog, stairs, 2, failedExceptions);
                    // Stripped Bamboo -> Planks (2)
                    count += addStonecuttingRecipe("woodcut_stripped_bamboo_planks", strippedLog, planks, 2, failedExceptions);
                    // Stripped Bamboo -> Fence (1)
                    count += addStonecuttingRecipe("woodcut_stripped_bamboo_fence", strippedLog, fence, 1, failedExceptions);
                } else {
                    // Regular stripped log recipes for other wood types
                    // Stripped Log -> Planks (4)
                    count += addStonecuttingRecipe("woodcut_stripped_" + woodName + "_planks", strippedLog, planks, 4, failedExceptions);
                    // Stripped Log -> Stripped Wood (1) - only if different from stripped log
                    if (strippedWood != strippedLog) {
                        count += addStonecuttingRecipe("woodcut_stripped_" + woodName + "_stripped_wood", strippedLog, strippedWood, 1, failedExceptions);
                    }
                    // Stripped Log -> Stairs (4)
                    count += addStonecuttingRecipe("woodcut_stripped_" + woodName + "_stairs", strippedLog, stairs, 4, failedExceptions);
                    // Stripped Log -> Slabs (8)
                    count += addStonecuttingRecipe("woodcut_stripped_" + woodName + "_slabs", strippedLog, slab, 8, failedExceptions);
                }

                // ==================== STRIPPED WOOD INPUT RECIPES ====================
                // Only for non-bamboo (bamboo doesn't have a separate stripped wood type)
                if (strippedWood != strippedLog) {
                    // Stripped Wood -> Planks (3)
                    count += addStonecuttingRecipe("woodcut_strippedwood_" + woodName + "_planks", strippedWood, planks, 3, failedExceptions);
                    // Stripped Wood -> Stairs (3)
                    count += addStonecuttingRecipe("woodcut_strippedwood_" + woodName + "_stairs", strippedWood, stairs, 3, failedExceptions);
                    // Stripped Wood -> Slabs (6)
                    count += addStonecuttingRecipe("woodcut_strippedwood_" + woodName + "_slabs", strippedWood, slab, 6, failedExceptions);
                }

                // ==================== PLANKS INPUT RECIPES ====================
                // Planks -> Slabs (2)
                count += addStonecuttingRecipe("woodcut_planks_" + woodName + "_slabs", planks, slab, 2, failedExceptions);
                // Planks -> Stairs (1)
                count += addStonecuttingRecipe("woodcut_planks_" + woodName + "_stairs", planks, stairs, 1, failedExceptions);

            } catch (IllegalArgumentException e) {
                // Material doesn't exist (e.g., PALE_OAK might not be in older versions)
                Bukkit.getLogger().fine("[Recipes] Skipping woodcutting recipes for " + woodName + ": Material not found");
            }
        }

        return count;
    }

    /**
     * Helper method to add a stonecutting recipe
     */
    private static int addStonecuttingRecipe(String keyName, Material input, Material output, int amount, List<String> failedExceptions) {
        NamespacedKey key = new NamespacedKey(Specialization.getInstance(), keyName);
        StonecuttingRecipe recipe = new StonecuttingRecipe(key, new ItemStack(output, amount), input);
        if (!recipeExists(key, recipe.getResult())) {
            try {
                Bukkit.addRecipe(recipe);
                return 1;
            } catch (Exception e) {
                failedExceptions.add(keyName + " (" + e.getMessage() + ")");
            }
        }
        return 0;
    }
}
