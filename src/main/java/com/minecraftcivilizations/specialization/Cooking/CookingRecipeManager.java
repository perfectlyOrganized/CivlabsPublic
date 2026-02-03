package com.minecraftcivilizations.specialization.Cooking;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Campfire;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Handles recipe matching and result resolution for cooking.
 */
public class CookingRecipeManager {

    /**
     * Check and resolve the recipe combination for a cooking session.
     * Updates data.food if a valid recipe is found.
     */
    public static void checkCombination(CookingItemData data, Player player) {
        if (data == null) return;

        List<String> currentIds = buildCurrentIngredientIds(data);

        Config cookingConfig = SpecializationConfig.getCookingConfig().getConfig();
        int playerLevelInt = getPlayerFarmerLevel(player);
        SkillLevel playerSkill = SkillLevel.getSkillLevelFromInt(playerLevelInt);

        // Check if player level is too low
        if (playerSkill.getLevel() < 2) {
            if (wouldMatchAnyRecipe(data, currentIds, cookingConfig)) {
                return; // Player can't cook this yet
            }
        }

        boolean matchedAny = findAndApplyRecipe(data, currentIds, cookingConfig, playerSkill, player);

        if (!matchedAny && data.food != null) {
            data.food = null;
            data.cookExp = 0;
            if (data.displayEntity != null) {
                data.displayEntity.remove();
                data.displayEntity = null;
            }
        }
    }

    private static List<String> buildCurrentIngredientIds(CookingItemData data) {
        List<String> currentIds = new ArrayList<>();

        if (data.ingredientIds != null && !data.ingredientIds.isEmpty()) {
            for (String id : data.ingredientIds) {
                if (id != null && !id.isEmpty()) {
                    currentIds.add(id);
                }
            }
        } else {
            if (data.ingredients != null) {
                for (ItemStack is : data.ingredients) {
                    if (is != null && is.getType() != Material.AIR) {
                        String id = CookingItemUtils.getItemId(is);
                        if (id != null) currentIds.add(id);
                    }
                }
            }
            if (data.campfireLocation != null) {
                Block block = data.campfireLocation.getBlock();
                if (block.getType() == Material.CAMPFIRE) {
                    Campfire cf = (Campfire) block.getState();
                    for (int i = 0; i < 4; i++) {
                        ItemStack it = cf.getItem(i);
                        if (it != null && it.getType() != Material.AIR) {
                            String id = CookingItemUtils.getItemId(it);
                            if (id != null) currentIds.add(id);
                        }
                    }
                }
            }
        }
        return currentIds;
    }

    private static int getPlayerFarmerLevel(Player player) {
        CustomPlayer specCP = CustomPlayer.getCustomPlayer(player);
        if (specCP != null) return specCP.getSkillLevel(SkillType.FARMER);
        CustomPlayer coreCP = CoreUtil.getPlayer(player);
        if (coreCP != null) return coreCP.getSkillLevel(SkillType.FARMER);
        return 0;
    }

    private static boolean wouldMatchAnyRecipe(CookingItemData data, List<String> currentIds, Config cookingConfig) {
        List<ConfigObject> allTiers = new ArrayList<>();
        for (SkillLevel t : SkillLevel.values()) {
            String tk = "FARMER_" + t.name();
            allTiers.addAll(cookingConfig.getObjectList(tk));
        }

        for (ConfigObject obj : allTiers) {
            Config r = obj.toConfig();
            String recip = r.getString("recipient");
            if (!recip.equals(data.recipientId)) continue;
            List<String> req = r.hasPath("ingredients") ? r.getStringList("ingredients") : List.of();
            Collections.sort(req);
            List<String> curSorted = new ArrayList<>(currentIds);
            Collections.sort(curSorted);
            if (req.equals(curSorted)) {
                return true;
            }
        }
        return false;
    }

    private static boolean findAndApplyRecipe(CookingItemData data, List<String> currentIds,
                                               Config cookingConfig, SkillLevel playerSkill, Player player) {
        // Track if food already existed before this check (to avoid re-playing sound on seasoning/sauce addition)
        boolean hadFoodBefore = data.food != null;

        SkillLevel[] levels = SkillLevel.values();
        Arrays.sort(levels, Comparator.comparingInt(SkillLevel::getLevel).reversed());

        for (SkillLevel tier : levels) {
            if (tier.getLevel() > playerSkill.getLevel()) continue;

            String tierKey = "FARMER_" + tier.name();
            List<? extends ConfigObject> tierRecipes = cookingConfig.getObjectList(tierKey);

            for (ConfigObject configObject : tierRecipes) {
                Config recipe = configObject.toConfig();
                String recipient = recipe.getString("recipient");
                if (!recipient.equals(data.recipientId)) continue;

                List<String> ingredients = recipe.hasPath("ingredients") ? recipe.getStringList("ingredients") : List.of();
                if (!recipeMatches(currentIds, ingredients)) continue;

                // Recipe matched!
                String resultId = recipe.getString("result");
                int expAmt = recipe.hasPath("exp") ? recipe.getInt("exp") : 0;
                int cookSeconds = recipe.hasPath("cooking_time") ? recipe.getInt("cooking_time") : 10;
                int bonusExp = recipe.hasPath("bonus_exp") ? recipe.getInt("bonus_exp") : 0;

                // Parse native effects from config
                List<String> nativeEffects = new ArrayList<>();
                if (recipe.hasPath("effects")) {
                    List<? extends ConfigObject> effectsList = recipe.getObjectList("effects");
                    for (ConfigObject effectObj : effectsList) {
                        Config effectConfig = effectObj.toConfig();
                        String effectName = effectConfig.getString("effect");
                        int duration = effectConfig.getInt("duration");
                        int amplifier = effectConfig.hasPath("amplifier") ? effectConfig.getInt("amplifier") : 0;
                        // Store as "effect_name:amplifier:duration" format
                        String effectKey = effectName.replace("minecraft:", "");
                        nativeEffects.add(effectKey + ":" + amplifier + ":" + duration);
                    }
                }

                boolean made = resolveResultItem(data, resultId);
                if (made) {
                    data.cookExp = expAmt;
                    data.cookTimeSeconds = cookSeconds;
                    data.nativeEffects = nativeEffects;
                    data.bonusExp = bonusExp;
                }
                if (made && data.food != null) {
                    CookingVisuals.spawnOrUpdateDisplay(data);
                    // Only play sound when recipe is NEWLY discovered (not when adding seasonings/sauces)
                    // Play for all nearby players at the campfire location
                    if (!hadFoodBefore && data.campfireLocation != null) {
                        Location soundLoc = data.campfireLocation.clone().add(0.5, 0.5, 0.5);
                        soundLoc.getWorld().playSound(soundLoc, Sound.BLOCK_COMPOSTER_EMPTY, SoundCategory.BLOCKS, 1.0f, 0.5f);
                    }
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Resolve a result item by ID (CraftEngine or legacy custom item).
     */
    public static boolean resolveResultItem(CookingItemData data, String resultId) {
        if (data == null || resultId == null || resultId.isEmpty()) return false;

        // Parse namespace and path from resultId
        String namespace;
        String path;
        if (resultId.contains(":")) {
            String[] parts = resultId.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        } else {
            namespace = "specialization";
            path = resultId;
        }

        String fullId = namespace + ":" + path;

        // Try CraftEngine with full namespace:path format
        try {
            var ce = BukkitItemManager.instance().getCustomItem(net.momirealms.craftengine.core.util.Key.of(fullId));
            if (ce != null && ce.isPresent()) {
                data.food = ce.get().buildItemStack(1);
                return true;
            }
        } catch (Exception e) {
            // Silently continue to next attempt
        }

        // Try with original resultId as-is
        if (!fullId.equals(resultId)) {
            try {
                var ce = BukkitItemManager.instance().getCustomItem(net.momirealms.craftengine.core.util.Key.of(resultId));
                if (ce != null && ce.isPresent()) {
                    data.food = ce.get().buildItemStack(1);
                    return true;
                }
            } catch (Exception e) {
                // Silently continue to next attempt
            }
        }

        // Try with specialization: prefix if not already present
        if (!resultId.startsWith("specialization:")) {
            try {
                String specId = "specialization:" + path;
                var ce = BukkitItemManager.instance().getCustomItem(net.momirealms.craftengine.core.util.Key.of(specId));
                if (ce != null && ce.isPresent()) {
                    data.food = ce.get().buildItemStack(1);
                    return true;
                }
            } catch (Exception e) {
                // Silently continue to next attempt
            }
        }

        // Try legacy custom item system
        try {
            com.minecraftcivilizations.specialization.CustomItem.CustomItem ci =
                com.minecraftcivilizations.specialization.CustomItem.CustomItemManager.getInstance().getCustomItem(resultId);
            if (ci != null) {
                data.food = ci.createItemStack(1);
                return true;
            }
        } catch (Exception e) {
            // Continue
        }

        return false;
    }

    /**
     * Check if current ingredients match a recipe (multiset comparison with namespace flexibility).
     */
    public static boolean recipeMatches(List<String> currentIds, List<String> recipeIngredients) {
        if (recipeIngredients == null || recipeIngredients.isEmpty()) {
            return (currentIds == null || currentIds.isEmpty());
        }
        if (currentIds == null || currentIds.isEmpty()) return false;

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String cid : currentIds) {
            if (cid == null) continue;
            counts.put(cid, counts.getOrDefault(cid, 0) + 1);
        }

        for (String rid : recipeIngredients) {
            if (rid == null) return false;

            // Try exact match first
            if (counts.getOrDefault(rid, 0) > 0) {
                counts.put(rid, counts.get(rid) - 1);
                continue;
            }

            // Try path-only match
            String rpath = rid.contains(":") ? rid.split(":", 2)[1] : rid;
            boolean matched = false;
            for (Map.Entry<String, Integer> e : new ArrayList<>(counts.entrySet())) {
                String cand = e.getKey();
                int num = e.getValue();
                if (num <= 0) continue;
                String cpath = cand.contains(":") ? cand.split(":", 2)[1] : cand;
                if (rpath.equals(cpath)) {
                    counts.put(cand, num - 1);
                    matched = true;
                    break;
                }
            }
            if (!matched) return false;
        }

        // Ensure no leftover ingredients
        for (int v : counts.values()) {
            if (v > 0) return false;
        }
        return true;
    }
}
