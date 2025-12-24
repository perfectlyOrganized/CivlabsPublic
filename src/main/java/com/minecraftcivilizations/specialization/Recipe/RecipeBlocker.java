package com.minecraftcivilizations.specialization.Recipe;

import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Recipe;
import org.bukkit.Keyed;

import java.util.HashSet;
import java.util.Set;

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
            }
        }
    }
}
