package com.minecraftcivilizations.specialization.Listener.Player.Inventories;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.CustomItem.CustomItemManager;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import minecraftcivilizations.com.minecraftCivilizationsCore.Item.ItemUtils;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import minecraftcivilizations.com.minecraftCivilizationsCore.Options.Pair;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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
    public void woolToStringTooCheck(CraftItemEvent event) {
        ItemStack[] matrix = event.getInventory().getMatrix();

        for (int i = 0; i < matrix.length; i++) {
            if (matrix[i] != null && matrix[i].getType() == Material.SHEARS) {
                ItemStack shears = matrix[i].clone();
                ItemMeta itemMeta = shears.getItemMeta();
                if (itemMeta instanceof Damageable damage) {
                    int durability = damage.getDamage()+1;
                    damage.setDamage(durability);
                    shears.setItemMeta(damage);
                    if (!damage.hasMaxDamage() || durability < damage.getMaxDamage()) {
                        int finalI = i;
                        Bukkit.getScheduler().runTaskLater(Specialization.getInstance(), () -> {
                            event.getInventory().setItem(finalI + 1, shears);
                        }, 1L);
                    }

                }
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

        if (event.getRecipe() instanceof ShapelessRecipe recipe) {
            NamespacedKey stringRecipe = new NamespacedKey(Specialization.getInstance(), "wool_to_string_recipe");
            if (recipe.getKey().equals(stringRecipe)) {
                woolToStringTooCheck(event);
            }
        }

        if (COMPLEX_ITEMS.contains(crafted.getType())) {
            CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(player.getUniqueId());

            int amount = getCraftedAmount(event);
            for(int i = 0; i < amount; i++) {
                customPlayer.getAnalyticPlayerData().incrementComplexItemsCrafted(crafted.getType().toString());
            }
            Debug.broadcast("analytics", player.getName() + " crafted complex item: " + crafted.getType() + " x" + amount);
        }
        String key = getRecipeKey(event.getRecipe());
        if (!SpecializationConfig.getXpGainFromCraftingConfig().doesFieldExist(key)) {
            key = crafted.getType().toString();
        }

        Pair<SkillType, Double> xp_gain_pair = SpecializationConfig.getXpGainFromCraftingConfig()
                .get(key, new TypeToken<>() {});

        int craftedAmount = getCraftedAmount(event);

        String amtstring = craftedAmount+"x ";
        if(craftedAmount==1)amtstring = "";




        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance().getCustomPlayerManager().getCustomPlayer(player.getUniqueId());


        int lvl = (int)Math.max((double)customPlayer.getSkillLevel(xp_gain_pair.firstValue()), (double)customPlayer.getSkillLevel(SkillType.BLACKSMITH)*1.5);
        if(lvl>5)lvl = 5;
        double skill_benefit = (5-((double)lvl)/1.5);
        double base_reduction = getFoodReduction(crafted.getType());
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
                String item_name = ItemUtils.getFriendlyName(event.getRecipe().getResult().getType());
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
            player.sendActionBar(MiniMessage.miniMessage().deserialize(hungry_msg));
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

        Material mat = event.getCurrentItem().getType();
        String color = getItemNameFormat(mat);
        boolean rare = true;
        if(color==null){
            color = "gray";
            rare = false;
        }
        String colortag = "<"+color+">"+amtstring+mat.name()+"</"+color+">";

        Component debug_isolated = MiniMessage.miniMessage().deserialize("<gray>crafted</gray> "+colortag+" <red>🍖"+totalReduction+"</red>");
        Component debug_global = Component.text(player.getName()+" ").color(NamedTextColor.WHITE).append(debug_isolated);
        Component hover = MiniMessage.miniMessage().deserialize("<gray>🎬:"+event.getAction().name()+"\n"
                +"<green>Current Item: </green>"+event.getCurrentItem().getType().name()+"\n"
                +"<blue>Cursor Item: </blue>"+event.getCursor().getType().name()+"\n"
                +"<red>🍖 Type Base Reduction: </red>"+base_reduction+"\n"
                +"<red>🍖 Skill Benefit: </red>"+skill_benefit+"\n"
                +"<red>🍖 Food Level: </red>"+foodLevel+" <gold>🍖 Reduction:</gold> "+totalReduction);
        Debug.broadcast("craft", debug_global, hover);
        if(rare){
            Debug.broadcast("craftrare", debug_global, hover);
        }
        String isolated_debug_channel = "craft_"+player.getName().toLowerCase();
        if(Debug.isAnyoneListening(isolated_debug_channel, false)) {
            Debug.broadcast(isolated_debug_channel, debug_isolated
                    .append(Debug.formatLocationClickable(player.getLocation(), true)), hover);
        }
        SpecializationCraftItemEvent new_event = new SpecializationCraftItemEvent(event, player, craftedAmount, totalReduction, xp_gain_pair.firstValue(), lvl);
        Bukkit.getPluginManager().callEvent(new_event);
        if (xp_gain_pair.firstValue() != null && xp_gain_pair.secondValue() != null) {
            double xpToGive = xp_gain_pair.secondValue() * craftedAmount;

            int finalReduction = Math.max(totalReduction, 1);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.setFoodLevel(player.getFoodLevel() - finalReduction);
                    if(!new_event.isXpCancelled()) {
                        customPlayer.addSkillXp(xp_gain_pair.firstValue(), xpToGive);
                    }
                }
            }, 1L);
        }

    }

    private double getFoodReduction(Material type) {
        switch (type) {
            case FERMENTED_SPIDER_EYE:
            case BEETROOT_SOUP:
                return 1.75;
            case STICK:
            case PUMPKIN_PIE:
            case MUSHROOM_STEW:
                return 0.35;
            case TORCH:
            case REDSTONE_TORCH:
            case SOUL_TORCH:
            case REDSTONE_LAMP:
            case BRICKS:
            case BRICK_SLAB:
            case BRICK_STAIRS:
            case BRICK_WALL:
            case BOOK:
            case BOOKSHELF:
            case DRIED_KELP_BLOCK:
            case BREAD: //Bread is a bit more expensive to craft due to it being lo lvl
            case CAKE:
                return 0.5;
            case COOKIE:
                return 0.125;
            case CRAFTING_TABLE:
                return 1.0;
            case FURNACE:
            case SMOKER:
            case BLAST_FURNACE:
            case CHEST:
            case BARREL:
            case ENCHANTING_TABLE:
            case ANVIL:
                return 1.25;
            case WRITABLE_BOOK:
            case FLINT_AND_STEEL:
            case BUCKET:
            case SHEARS:
            case CLAY:
            case BRICK:
            case PACKED_MUD:
            case SNOW_BLOCK:
            case SUGAR:
                return 0.25;
            case GLASS_PANE:
                return 0.33;
            case SANDSTONE:
            case SANDSTONE_SLAB:
            case SANDSTONE_STAIRS:
            case SANDSTONE_WALL:
            case RESIN_BLOCK:
            case RESIN_BRICK:
            case RESIN_BRICK_SLAB:
            case RESIN_BRICK_STAIRS:
            case RESIN_BRICK_WALL:
            case RESIN_BRICKS:
            case RESIN_CLUMP:
            case CHISELED_RESIN_BRICKS:
                return 0.3;
            case TINTED_GLASS:
            case GLASS_BOTTLE:
                return 0.75;
        }
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


        /**
         * Complex values for tools
         */
        double value = 1.0;
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
        if (event.getInventory().getViewers().isEmpty()) return;

        Player player = null;

        for (var viewer : event.getInventory().getViewers()) {
            if (viewer instanceof Player) {
                player = (Player) viewer;
                break;
            }
        }

        if (player == null) return;

        Recipe recipe = event.getRecipe();
        if (recipe == null) return;

        if (!(recipe instanceof Keyed)) {
            return;
        }

        NamespacedKey recipeKey = ((Keyed) recipe).getKey();

        if (shouldBlockRecipe(player, recipeKey)) {
            LOGGER.info("Blocking recipe " + recipeKey + " for player " + player.getName() + " due to insufficient skill level");
            event.getInventory().setResult(null);
            player.undiscoverRecipe(recipeKey);
        } else {
            if (!player.hasDiscoveredRecipe(recipeKey)) {
                player.discoverRecipe(recipeKey);
                LOGGER.fine("Discovered recipe " + recipeKey + " for player " + player.getName() + " on-demand");
            }
        }
    }

    public static boolean shouldBlockRecipe(Player player, NamespacedKey recipeKey) {
        CustomPlayer customPlayer = (CustomPlayer) MinecraftCivilizationsCore.getInstance()
                .getCustomPlayerManager().getCustomPlayer(player.getUniqueId());
        if(customPlayer.getAdditionUnlockedRecipes() != null && customPlayer.getAdditionUnlockedRecipes().contains(recipeKey)) return false;

        // Check if recipe is in any skill-specific unlocked recipes config
        for (SkillType skillType : SkillType.values()) {
            for (SkillLevel skillLevel : SkillLevel.values()) {
                String configKey = skillType + "_" + skillLevel;
                Set<NamespacedKey> skillRecipes = SpecializationConfig.getUnlockedRecipesConfig()
                        .get(configKey, new TypeToken<>() {
                        });

                if (skillRecipes != null && skillRecipes.contains(recipeKey)) {
                    return customPlayer.getSkillLevel(skillType) < skillLevel.ordinal();
                }
            }
        }

        // Recipe is not in any skill config - allow it
        return false;
    }
}