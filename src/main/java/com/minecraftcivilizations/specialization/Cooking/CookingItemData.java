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
    // Persisted canonical ids for the corresponding lists above. These are populated when an item
    // is placed into the session so we can reliably match recipes even if ItemStack meta is lost.
    public List<String> ingredientIds = new ArrayList<>();
    public ItemStack food;
    public List<ItemStack> seasonings = new ArrayList<>();
    public List<String> seasoningIds = new ArrayList<>();
    // NEW: sauces behave similar to seasonings — persistent visual effects while cooking
    public List<ItemStack> sauces = new ArrayList<>();
    public List<String> sauceIds = new ArrayList<>();
    // Native effects from cookingConfig.json (effect:amplifier:duration format)
    public List<String> nativeEffects = new ArrayList<>();
    // Bonus XP from consuming the food (from cookingConfig.json)
    public int bonusExp = 0;
    public BukkitTask cookTask; // used as the READY task (marks item ready to collect)
    public BukkitTask burnTask; // scheduled to make the food burnt if not collected
    public BukkitTask progressTask;
    public BukkitTask maintenanceTask;
    public BukkitTask flameTask; // separate periodic task for spawning flame particles while cooking
    // ambient looping sound task while cooking / preview present
    public BukkitTask ambientTask;
    // temporary hold task to anchor preview position after finish/cancel
    public BukkitTask holdTask;
    // persistent particle task for seasonings/sauces
    public BukkitTask seasoningTask;
    // NEW: persistent particle task for sauces
    public BukkitTask sauceTask;
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
        // ensure id-lists are initialized
        this.ingredientIds = new ArrayList<>();
        this.seasoningIds = new ArrayList<>();
        this.sauceIds = new ArrayList<>();
    }
}