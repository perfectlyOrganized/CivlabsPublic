package com.minecraftcivilizations.specialization.Recipe;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Data.Pair;
import com.minecraftcivilizations.specialization.player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.OpenLab;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.Keyed;

import java.util.*;
import java.util.stream.Collectors;

public class RecipeBlocker implements Listener {

    // Add all recipes you want to block here
    private static final Set<NamespacedKey> BLOCKED_RECIPES = new HashSet<>();

    static {
        BLOCKED_RECIPES.add(NamespacedKey.minecraft("rail"));
        // Add more keys easily
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (recipe == null) return;
        if (recipe instanceof Keyed keyedRecipe) {
            if (BLOCKED_RECIPES.contains(keyedRecipe.getKey())) {
                e.getInventory().setResult(null);
                OpenLab.logger.info("blocked recipe");
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        updateRecipes(event.getPlayer());
    }
    public static Set<NamespacedKey> getRecipes(SkillType skillType, SkillLevel level) {
        return getRecipes(skillType,level.getLevel());
    }
    public static Set<NamespacedKey> getRecipes(SkillType skillType, int level) {
        return SpecializationConfig.getUnlockedRecipesConfig()
                .getStringList(skillType + "_" + SkillLevel.getSkillLevelFromInt(level))
                .stream()
                .filter(Objects::nonNull)
                .map(s -> {
                    try {
                        return NamespacedKey.fromString(s);
                    } catch (Exception e) {
                        OpenLab.logger.warning(s + " could not be converted to a namespace");
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static void updateRecipes(Player player) {
        Iterator<Recipe> recipeIterator = Bukkit.recipeIterator();

        while (recipeIterator.hasNext()) {
            Recipe recipe = recipeIterator.next();
            if (recipe instanceof Keyed keyed) {
                toggleRecipeBasedOnCondition(player,  keyed.getKey().toString(), !RecipeBlocker.shouldBlockRecipe(player, keyed.getKey()));
            }
        }
    }

    private static void toggleRecipeBasedOnCondition(Player player, String recipeKey, boolean condition) {
        NamespacedKey key = NamespacedKey.fromString(recipeKey);

        if (key == null) {
            player.sendMessage("§cInvalid recipe key: " + recipeKey);
            return;
        }

        if (condition) {
            player.discoverRecipe(key);
        } else {
            player.undiscoverRecipe(key);
        }
    }

    public static boolean shouldBlockRecipe(Player player, NamespacedKey recipeKey) {
        CustomPlayer customPlayer = CustomPlayerManager.INSTANCE.getCustomPlayer(player.getUniqueId());

        if(customPlayer.getAdditionUnlockedRecipes() != null &&
                customPlayer.getAdditionUnlockedRecipes().contains(recipeKey)) {
            return false;
        }

        List<Pair<SkillType, SkillLevel>> recipeRequirements = new ArrayList<>();
        for (SkillType skillType : SkillType.values()) {
            for (SkillLevel skillLevel : SkillLevel.values()) {
                Set<NamespacedKey> skillRecipes = RecipeBlocker.getRecipes(skillType, skillLevel);

                if (skillRecipes.contains(recipeKey)) {
                    recipeRequirements.add(new Pair<>(skillType, skillLevel));
                }
            }
        }
        if (recipeRequirements.isEmpty() && SpecializationConfig.getUnlockedRecipesConfig()
                .getStringList("mod_blacklist")
                .contains(recipeKey.getNamespace().toUpperCase(Locale.ROOT))) return true;
        if (recipeRequirements.isEmpty() && SpecializationConfig.getUnlockedRecipesConfig()
                .getStringList("blacklist")
                .contains(recipeKey.toString())) return true;

        // If recipe is not in any skill config - allow it (no restrictions)
        if (recipeRequirements.isEmpty()) {
            return false;
        }

        // Check if player meets ANY of the requirements
        for (Pair<SkillType, SkillLevel> requirement : recipeRequirements) {
            SkillType requiredSkill = requirement.key();
            SkillLevel requiredLevel = requirement.value();

            // If player's skill level meets or exceeds the requirement for this skill type
            if (customPlayer.getSkillLevel(requiredSkill) >= requiredLevel.getLevel()) {
                return false; // Player qualifies through at least one skill, don't block
            }
        }

        // Player doesn't meet ANY of the requirements, block the recipe
        return true;
    }
}
