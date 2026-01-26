package com.minecraftcivilizations.specialization.Cooking;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CookingItemData {
    public ArmorStand stand;
    public Item displayEntity;
    // preview of the recipient in the center — use an ItemDisplay so it's a lying flat visual
    public ItemDisplay recipientDisplay;
    public ItemStack recipient;
    public String recipientId;
    public List<ItemStack> ingredients = new ArrayList<>();
    public ItemStack food;
    public List<ItemStack> seasonings = new ArrayList<>();
    public BukkitTask cookTask; // used as the READY task (marks item ready to collect)
    public BukkitTask burnTask; // scheduled to make the food burnt if not collected
    public BukkitTask progressTask;
    public BukkitTask maintenanceTask;
    public BukkitTask flameTask; // separate periodic task for spawning flame particles while cooking
    // ambient looping sound task while cooking / preview present
    public BukkitTask ambientTask;
    // true when actual cooking (the timed process) is in progress
    public boolean cookingInProgress = false;
    // 'cooked' is true when the food reached "ready to be collected" state
    public boolean cooked = false;
    // true when the food has become burnt
    public boolean burnt = false;
    // XP reward configured for this recipe (from cookingConfig), awarded to the player who started the cook
    public int cookExp = 0;
    // default cook time = 10s
    public int cookTimeSeconds = 10;
    // true when the session was forcefully destroyed (campfire broken)
    public boolean destroyed = false;
    public UUID viewer;
    public boolean wasLit = true;
    public Location campfireLocation;

    public CookingItemData(ArmorStand stand, ItemStack recipient, String recipientId) {
        this.stand = stand;
        this.recipient = recipient;
        this.recipientId = recipientId;
        this.viewer = null;
        this.ambientTask = null;
    }
}