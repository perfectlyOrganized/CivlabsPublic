package com.minecraftcivilizations.specialization.Listener.Player.Inventories;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Data.Pair;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.ItemStackUtils;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import com.typesafe.config.ConfigException;
import minecraftcivilizations.com.minecraftCivilizationsCore.Config.ConfigFile;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CraftingListener implements Listener {

    private static final Logger LOGGER = Logger.getLogger(CraftingListener.class.getName());
    private final Plugin plugin;

    private static final Set<Material> COMPLEX_ITEMS = Arrays.stream(Material.values())
            .filter(material -> {
                String name = material.name();
                if (
                        (
                                name.startsWith("IRON_") || name.startsWith("GOLDEN_") ||
                                        name.startsWith("DIAMOND_") || name.startsWith("NETHERITE_")
                        )
                                &&
                                (
                                        name.endsWith("_PICKAXE") || name.endsWith("_AXE") ||
                                                name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.endsWith("_SWORD")
                                )
                ) {
                    return true;
                }
                return (name.startsWith("CHAINMAIL_") || name.startsWith("IRON_") ||
                        name.startsWith("GOLDEN_") || name.startsWith("DIAMOND_") ||
                        name.startsWith("NETHERITE_")) &&
                        (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") ||
                                name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS"));
            })
            .collect(Collectors.collectingAndThen(
                    Collectors.toSet(),
                    set -> {
                        set.addAll(Set.of(
                                Material.ANVIL, Material.SMITHING_TABLE, Material.BLAST_FURNACE, Material.GRINDSTONE,
                                Material.PISTON, Material.STICKY_PISTON, Material.DISPENSER, Material.DROPPER,
                                Material.OBSERVER, Material.HOPPER, Material.COMPARATOR, Material.REPEATER,
                                Material.DAYLIGHT_DETECTOR, Material.SCAFFOLDING, Material.JUKEBOX, Material.CAMPFIRE,
                                Material.ENCHANTING_TABLE, Material.BOOKSHELF, Material.LECTERN,
                                Material.BREWING_STAND, Material.GLISTERING_MELON_SLICE, Material.GOLDEN_CARROT, Material.GOLDEN_APPLE,
                                Material.BEACON, Material.ENDER_CHEST, Material.SHIELD, Material.CROSSBOW, Material.TNT, Material.TARGET,
                                Material.CAKE, Material.PUMPKIN_PIE, Material.RABBIT_STEW
                        ));
                        return Set.copyOf(set);
                    }
            ));

    public CraftingListener(Plugin plugin) {
        this.plugin = plugin;
    }



    public void keepGenericItem(CraftItemEvent event, Material material, ItemStack newItem) {
        ItemStack[] matrix = event.getInventory().getMatrix();

        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i] != null && matrix[i].getType() == material) {
                int finalI = i;
                Bukkit.getScheduler().runTaskLater(Specialization.getInstance(), () -> {
                    event.getInventory().setItem(finalI + 1, newItem);
                }, 1L);
            }
        }
    }

    private String getRecipeKey(Recipe recipe) {
        // for custom recipes like "bandage_recipe"
        if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            return String.valueOf(shapelessRecipe.getKey());
        } else if (recipe instanceof ShapedRecipe shapedRecipe) {
            return String.valueOf(shapedRecipe.getKey());
        }
        return "";
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) return;

        if (!isCraftingActionValid(event)) {
            event.setResult(Event.Result.DENY);
            event.setCancelled(true);
            return;
        }

        ItemStack crafted = event.getCurrentItem();


        if (event.getRecipe() instanceof ShapedRecipe recipe) {
            NamespacedKey recipeKey = new NamespacedKey(Specialization.getInstance(), "wheat_dough");
            if (recipe.getKey().equals(recipeKey)) {
                keepGenericItem(event, Material.WATER_BUCKET, new ItemStack(Material.BUCKET, 1));
            }
        }

        if (COMPLEX_ITEMS.contains(crafted.getType())) {
            CustomPlayer customPlayer = Specialization.customPlayerManager.getCustomPlayer(player.getUniqueId());

            int amount = getCraftedAmount(event);
            for(int i = 0; i < amount; i++) {
                customPlayer.getAnalyticPlayerData().incrementComplexItemsCrafted(crafted.getType().toString());
            }
            Debug.broadcast("analytics", player.getName() + " crafted complex item: " + crafted.getType() + " x" + amount);
        }

        Double xp = 0.0;
        SkillType skillType = SkillType.BLACKSMITH;
        String itemName = getCraftId(event);

        for (SkillType skill : SkillType.values()) {
            try {
                xp = SpecializationConfig.getXpGainFromCraftingConfig().getDouble(skill.name() + "." + itemName);
            } catch(ConfigException.Missing _e) {}
            if (xp != 0) {
                skillType = skill;
                break;
            }
        }

        int craftedAmount = getCraftedAmount(event);

        CustomPlayer customPlayer = Specialization.customPlayerManager.getCustomPlayer(player.getUniqueId());

        int lvl = (int) Math.max(customPlayer.getSkillLevel(skillType), customPlayer.getSkillLevel(SkillType.BLACKSMITH)*1.5);
        if(lvl>5)lvl = 5;
        double skill_benefit = (5-((double)lvl)/1.5);
        double base_reduction = getFoodReduction(event);
        // Reduction based on Skill Level and Amount Crafted
        double food_reduction_formula = base_reduction * (skill_benefit * craftedAmount);

        double divider = event.getRecipe().getResult().getAmount();

        int totalReduction = (int) Math.max(1.0, food_reduction_formula / divider); //Math.max(0, totalReduction - (int) (Math.random() * 3));

        int foodLevel = player.getFoodLevel();
        if(player.getGameMode()==GameMode.CREATIVE){
            foodLevel=220; //for testing etc
        }

        double anti_starvation_threshold = 2; //increase this to prevent causing a plyer to starve upon crafting

        if(foodLevel - totalReduction < anti_starvation_threshold){
            event.setResult(Event.Result.DENY);
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.BLOCK_CHORUS_FLOWER_GROW, 0.5f, 1.25f);


            String hungry_msg = "<red>You're too hungry to craft</red>";
            if(craftedAmount>1){
                hungry_msg = "<red>You're too hungry to craft that many</red>";
            }
            if(ThreadLocalRandom.current().nextDouble()<0.0125){
                // Fun Messages
                String item_name = ItemStackUtils.getFriendlyName(event.getRecipe().getResult().getType());
                switch(ThreadLocalRandom.current().nextInt(6)){
                    case 0:
                        hungry_msg = "<red>You're too craft to hungry</red>"; break;
                    case 1:
                        hungry_msg = "<red>Some food would be nice right about now</red>"; break;
                    case 2:
                        hungry_msg = "<red>"+item_name+" does sound nice, but so does food.</red>"; break;
                    case 3:
                        hungry_msg = "<red>You're hungry, go eat!</red>"; break;
                    case 4:
                        hungry_msg = "<red>You try to craft the "+ item_name+", but you're too hungry!</red>"; break;
                    case 5:
                        hungry_msg = "<red>"+item_name+" demands that you eat!</red>"; break;
                }
            }
            PlayerUtil.sendActionBar(player,MiniMessage.miniMessage().deserialize(hungry_msg));
            return;
        }


        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack testItem = crafted.clone();
            testItem.setAmount(craftedAmount * crafted.getAmount());
            if (!canFitInInventory(player, testItem)) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                return;
            }
        }

        SpecializationCraftItemEvent new_event = new SpecializationCraftItemEvent(event, player, craftedAmount, totalReduction, skillType, lvl);
        Bukkit.getPluginManager().callEvent(new_event);
        double xpToGive = xp * craftedAmount;

        int finalReduction = Math.max(totalReduction, 1);
        SkillType finalSkillType = skillType;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.setFoodLevel(player.getFoodLevel() - finalReduction);
                if(!new_event.isXpCancelled()) {
                    customPlayer.addSkillXp(finalSkillType, xpToGive);
                }
            }
        }, 1L);

    }

    private String getCraftId(CraftItemEvent event) {
        ItemStack item = event.getCurrentItem();
        if (Specialization.getInstance().customItemManager.isCustomItem(item)) {
            return event.getRecipe().toString().toUpperCase(Locale.ROOT);
        } else {
            if (!item.getType().getKey().getNamespace().equals("minecraft"))
                return item.getType().getKey().toString().toUpperCase(Locale.ROOT).replace(":","_");
            return item.getType().getKey().getKey().toUpperCase(Locale.ROOT);
        }
    }

    private double getFoodReduction(CraftItemEvent event) {
        String itemName = getCraftId(event);
        ItemStack item = event.getCurrentItem();
        if (!SpecializationConfig.getHungerCostConfig().getConfig().hasPath(itemName)) return 0;
        double value = SpecializationConfig.getHungerCostConfig().getDouble(itemName);
        if (value == 1.0) {
            Material type = item.getType();
            String name = type.name();
            if(Tag.STAIRS.isTagged(type)
                    || Tag.FENCES.isTagged(type)
                    || Tag.FENCE_GATES.isTagged(type)
                    || Tag.SLABS.isTagged(type)
                    || Tag.WALLS.isTagged(type)
                    || Tag.BUTTONS.isTagged(type)
                    || Tag.ALL_SIGNS.isTagged(type)
                    || Tag.ALL_HANGING_SIGNS.isTagged(type)
                    || Tag.TERRACOTTA.isTagged(type)
            ){
                return 0.5;
            }
            if (Tag.PLANKS.isTagged(type)){
                return 0.25;
            }
            if(Tag.TRAPDOORS.isTagged(type)
                    || Tag.PRESSURE_PLATES.isTagged(type)
                    || name.contains("_GLASS")){
                return 0.33;
            }

            if(name.contains("_HELMET") || name.contains("_BOOTS")){
                value += 0.5;
            }else if(name.contains("_LEGGINGS") || name.contains("_CHESTPLATE")){
                value += 1.5;
            }else if(name.contains("_AXE") || name.contains("_SWORD")){
                value += 1.0;
            }else if(name.contains("_PICKAXE") || name.contains("_SHOVEL") || name.contains("_HOE")){
                value += 1.0;
            }
            if(name.contains("WOODEN_")) {
                value *= 0.35;
            }else if(name.contains("LEATHER_")){
                value *= 0.5;
            }else if(name.contains("STONE_")){
                value *= 0.75;
            }else if(name.contains("IRON_")){
                value *= 1.25;
            }else if(name.contains("DIAMOND_")){
                value *= 2.0;
            }
            return value;
        }
        return value;
    }

    private String getItemNameFormat(Material type) {
        String color = null;
        if(type.name().contains("IRON_")){
            color = "green";
        }else if(type.name().contains("DIAMOND_")){
            color = "aqua";
        }else if(type.name().contains("NETHERITE_")) {
            color = "light_purple";
        }else{
            switch(type){
                case TNT:
                case RESPAWN_ANCHOR:
                case END_CRYSTAL:
                    color = "dark_red";
                    break;
                case BEACON:
                case ANVIL:
                case ENCHANTING_TABLE:
                    color = "yellow";
                    break;

            }
        }
        return color;
    }

    /**
     * Checks if the player's inventory has space for the given item stack
     */
    private boolean canFitInInventory(Player player, ItemStack item) {
        int amountToAdd = item.getAmount();
        int maxStackSize = item.getMaxStackSize();

        for (ItemStack invItem : player.getInventory().getStorageContents()) {
            if (amountToAdd <= 0) break;

            if (invItem == null || invItem.getType().isAir()) {
                // Empty slot can fit a full stack
                amountToAdd -= maxStackSize;
            } else if (invItem.isSimilar(item)) {
                // Existing stack can fit more
                int spaceLeft = maxStackSize - invItem.getAmount();
                amountToAdd -= spaceLeft;
            }
        }

        return amountToAdd <= 0;
    }

    /**
     * Determines if the crafting action will actually consume ingredients
     * and produce items in the player's inventory.
     */
    private boolean isCraftingActionValid(CraftItemEvent event) {
        InventoryAction action = event.getAction();
        if(action==InventoryAction.MOVE_TO_OTHER_INVENTORY)return true;

        return switch (action) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE, PLACE_ALL, PLACE_SOME,
                 PLACE_ONE, SWAP_WITH_CURSOR, HOTBAR_SWAP, DROP_ALL_CURSOR, DROP_ALL_SLOT, DROP_ONE_CURSOR -> true;
            case DROP_ONE_SLOT -> (event.getCursor().getType().isAir());
                 default -> false;
        };
    }

    /**
     * Calculates how many items are actually being crafted based on the event action
     */
    private int getCraftedAmount(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null) return 0;
        if(event.getResult().equals(Event.Result.DENY)){
            return 0;
        }

        InventoryAction action = event.getAction();

        switch (action) {
            case PICKUP_HALF:
                return Math.max(1, result.getAmount() / 2);
            case PICKUP_SOME:
                ItemStack cursor = event.getCursor();
                if (cursor.isSimilar(result)) {
                    int maxStack = result.getMaxStackSize();
                    int canTake = maxStack - cursor.getAmount();
                    return Math.min(canTake, result.getAmount());
                }
                return result.getAmount();
            case MOVE_TO_OTHER_INVENTORY:
                return calculateBulkCraftAmount(event);
            case DROP_ONE_CURSOR, DROP_ALL_SLOT, DROP_ALL_CURSOR, DROP_ONE_SLOT:
                if(!event.getWhoClicked().getItemOnCursor().getType().equals(Material.AIR)) return 0;
            default:
                return result.getAmount();
        }
    }

    /**
     * Calculates the actual number of crafting operations for bulk crafting (Shift+Click)
     */
    private int calculateBulkCraftAmount(CraftItemEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null) return 0;

        // Get the recipe and check ingredient availability
        Recipe recipe = event.getRecipe();
        if (recipe == null) return 0;

        // For bulk crafting, we need to determine how many times the recipe can be executed
        // based on available ingredients in the crafting matrix
        org.bukkit.inventory.CraftingInventory craftingInventory = event.getInventory();
        ItemStack[] matrix = craftingInventory.getMatrix();
        
        int maxCrafts = Integer.MAX_VALUE;
        
        // Check each ingredient slot to find the limiting factor
        for (ItemStack ingredient : matrix) {
            if (ingredient != null && ingredient.getAmount() > 0) {
                // Each crafting operation consumes 1 of this ingredient
                maxCrafts = Math.min(maxCrafts, ingredient.getAmount());
            }
        }
        
        // If no ingredients found or unlimited, default to result amount divided by recipe yield
        if (maxCrafts == Integer.MAX_VALUE) {
            return result.getAmount();
        }
        
        return maxCrafts * event.getRecipe().getResult().getAmount();
    }/**
     * Returns the exact ItemStacks that will be added to the player's inventory
     * when doing a bulk craft (shift+click / MOVE_TO_OTHER_INVENTORY).
     * The returned stacks are clones (safe to mutate).
     */
    private List<ItemStack> getStacksAddedByBulkCraft(Player player, ItemStack result, int totalProduced) {
        List<ItemStack> added = new ArrayList<>();
        if (result == null || totalProduced <= 0) return added;

        PlayerInventory inv = player.getInventory();
        int maxStack = result.getMaxStackSize();
        int remaining = totalProduced;

        // First try to fill existing similar stacks
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot == null || slot.getType().isAir()) continue;
            if (!slot.isSimilar(result)) continue;

            int space = maxStack - slot.getAmount();
            if (space <= 0) continue;

            int toAdd = Math.min(space, remaining);
            ItemStack addedStack = result.clone();
            addedStack.setAmount(toAdd);
            added.add(addedStack);
            remaining -= toAdd;
        }

        // Then fill empty slots
        for (int i = 0; i < inv.getSize() && remaining > 0; i++) {
            ItemStack slot = inv.getItem(i);
            if (slot != null && !slot.getType().isAir()) continue;

            int toAdd = Math.min(maxStack, remaining);
            ItemStack addedStack = result.clone();
            addedStack.setAmount(toAdd);
            added.add(addedStack);
            remaining -= toAdd;
        }

        // remaining > 0 means not all produced items fit; those remain in grid.
        return added;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        HumanEntity viewer = event.getViewers().stream()
                .findFirst()
                .orElse(null);

        if (!(viewer instanceof Player player)) return;

        Recipe recipe = event.getRecipe();
        if (recipe == null) return;

        // Get recipe key
        NamespacedKey recipeKey = null;
        if (recipe instanceof Keyed keyed) {
            recipeKey = keyed.getKey();
        }

        if (shouldBlockRecipe(player, recipeKey)) {
            LOGGER.info("Blocking recipe " + recipeKey + " for player " + player.getName());
            event.getInventory().setResult(null);

            // Try to undiscover, but don't rely on it
            player.undiscoverRecipe(recipeKey);
            player.updateInventory();
        }
    }

    public static boolean shouldBlockRecipe(Player player, NamespacedKey recipeKey) {
        CustomPlayer customPlayer = Specialization.customPlayerManager.getCustomPlayer(player.getUniqueId());

        if(customPlayer.getAdditionUnlockedRecipes() != null &&
                customPlayer.getAdditionUnlockedRecipes().contains(recipeKey)) {
            return false;
        }

        List<Pair<SkillType, SkillLevel>> recipeRequirements = new ArrayList<>();
        for (SkillType skillType : SkillType.values()) {
            for (SkillLevel skillLevel : SkillLevel.values()) {
                String configKey = skillType + "_" + skillLevel;
                Set<NamespacedKey> skillRecipes = new HashSet<>(SpecializationConfig.getUnlockedRecipesConfig()
                        .getStringList(configKey).stream().map(NamespacedKey::fromString).toList());

                if (skillRecipes.contains(recipeKey)) {
                    recipeRequirements.add(new Pair<>(skillType, skillLevel));
                }
            }
        }

        // If recipe is not in any skill config - allow it (no restrictions)
        if (recipeRequirements.isEmpty()) {
            return false;
        }

        // Check if player meets ANY of the requirements
        for (Pair<SkillType, SkillLevel> requirement : recipeRequirements) {
            SkillType requiredSkill = requirement.key();
            SkillLevel requiredLevel = requirement.value();

            // If player's skill level meets or exceeds the requirement for this skill type
            if (customPlayer.getSkillLevel(requiredSkill) >= requiredLevel.ordinal()) {
                return false; // Player qualifies through at least one skill, don't block
            }
        }

        // Player doesn't meet ANY of the requirements, block the recipe
        return true;
    }
}