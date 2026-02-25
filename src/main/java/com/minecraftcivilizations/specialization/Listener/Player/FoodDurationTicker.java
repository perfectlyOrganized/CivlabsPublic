package com.minecraftcivilizations.specialization.Listener.Player;

import com.minecraftcivilizations.specialization.Listener.Player.Interactions.FoodInteractionListener;
import com.minecraftcivilizations.specialization.OpenLab;
import org.bukkit.*;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

public class FoodDurationTicker implements Listener {
    private final boolean enabled = false;;

    public FoodDurationTicker() {

    }

    private int getCurrentTime() {
        World world = Bukkit.getWorlds().get(0);
        return (int) (world.getFullTime());
    }
    private final NamespacedKey CREATED_AT_KEY = new NamespacedKey(OpenLab.getInstance(), "created_at");
    private final NamespacedKey EXPIRATION_KEY = new NamespacedKey(OpenLab.getInstance(), "expiration");
    private boolean isValid(ItemStack item) {
        return item != null && item.getType() != Material.AIR && !FoodInteractionListener.isBlessedFood(item);
    }





    private boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }

}

