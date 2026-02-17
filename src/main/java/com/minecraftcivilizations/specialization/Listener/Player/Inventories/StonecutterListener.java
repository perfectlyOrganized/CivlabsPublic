package com.minecraftcivilizations.specialization.Listener.Player.Inventories;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import minecraftcivilizations.com.minecraftCivilizationsCore.MinecraftCivilizationsCore;
import minecraftcivilizations.com.minecraftCivilizationsCore.Options.Pair;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.logging.Logger;

public class StonecutterListener implements Listener {
    private static final Logger LOGGER = Logger.getLogger(StonecutterListener.class.getName());
    private final Plugin plugin;

    public StonecutterListener(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Blocks taking woodcutting results for players below Builder level 2
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onStonecutResult(InventoryClickEvent event) {
        if (event.getView().getType() != InventoryType.STONECUTTER) return;
        if (event.getSlot() != 1) return; // only result slot
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryAction action = event.getAction();
        if (!isValidAction(action)) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType() == Material.AIR) return;

        ItemStack input = event.getView().getItem(0);
        if (input == null || input.getType() == Material.AIR) return;

        // Check if it's a woodcutting recipe (log, wood, stripped log, stripped wood, or planks input)
        if (isWoodcuttingInput(input.getType())) {
            // Check builder level BEFORE allowing the craft
            CustomPlayer customPlayer = Specialization.customPlayerManager.getCustomPlayer(player.getUniqueId());

            int lvl = (customPlayer != null) ? customPlayer.getSkillLevel(SkillType.BUILDER) : 0;
            if (lvl < 2) {
                // Block the craft
                event.setCancelled(true);
                PlayerUtil.message(player, ChatColor.RED + "You need to atleast be Level 2 in Builder to use woodcutting recipes!", 1);
                return;
            }

            InventoryView view = event.getView();
            ItemStack inputBefore = cloneSafe(view.getItem(0));
            int amount = getStonecutAmount(event);
            handleWoodcutting(player, view, inputBefore, result, amount);
        } else {
            InventoryView view = event.getView();
            ItemStack inputBefore = cloneSafe(view.getItem(0));
            int amount = getStonecutAmount(event);
            handleStonecutting(player, view, inputBefore, result, amount);
        }
    }

    private void handleWoodcutting(Player player, InventoryView view,
                                    ItemStack inputBefore, ItemStack result, int amount) {

        // Woodcutting gives no XP as per user request
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            ItemStack inputAfter = cloneSafe(view.getItem(0));
            boolean craftOccurred = didConsumeIngredient(inputBefore, inputAfter);

            if (!craftOccurred) {
                LOGGER.fine("Blocked woodcutting: no actual woodcutting detected for " + player.getName());
                return;
            }

            LOGGER.fine(player.getName() + " woodcut " + amount + "x " + result.getType());
        }, 1L);
    }

    private void handleStonecutting(Player player, InventoryView view,
                                     ItemStack inputBefore, ItemStack result, int amount) {
        Pair<SkillType, Double> pair = SkillType.getSkillXpFromConfig(SpecializationConfig.getXpGainFromStonecuttingConfig(), result.getType().toString());

        double xpToGive = pair.value() * amount;

        // One tick later, check if craft actually happened
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            ItemStack inputAfter = cloneSafe(view.getItem(0));

            // Validate: input item count decreased => craft succeeded
            boolean craftOccurred = didConsumeIngredient(inputBefore, inputAfter);

            if (!craftOccurred) {
                LOGGER.fine("Blocked XP grant: no actual stonecutting detected for " + player.getName());
                return;
            }

            CustomPlayer customPlayer = Specialization.customPlayerManager.getCustomPlayer(player.getUniqueId());
            customPlayer.addSkillXp(pair.key(), xpToGive);
            LOGGER.fine("Gave " + xpToGive + " XP to " + player.getName()
                    + " for stonecutting " + amount + "x " + result.getType());
        }, 1L);
    }

    private boolean isWoodLog(Material material) {
        return material == Material.OAK_LOG || material == Material.SPRUCE_LOG ||
               material == Material.BIRCH_LOG || material == Material.JUNGLE_LOG ||
               material == Material.ACACIA_LOG || material == Material.DARK_OAK_LOG ||
               material == Material.MANGROVE_LOG || material == Material.CHERRY_LOG ||
               material == Material.CRIMSON_STEM || material == Material.WARPED_STEM ||
               material == Material.BAMBOO_BLOCK;
    }

    private boolean isWood(Material material) {
        return material == Material.OAK_WOOD || material == Material.SPRUCE_WOOD ||
               material == Material.BIRCH_WOOD || material == Material.JUNGLE_WOOD ||
               material == Material.ACACIA_WOOD || material == Material.DARK_OAK_WOOD ||
               material == Material.MANGROVE_WOOD || material == Material.CHERRY_WOOD ||
               material == Material.CRIMSON_HYPHAE || material == Material.WARPED_HYPHAE;
    }

    private boolean isStrippedLog(Material material) {
        return material == Material.STRIPPED_OAK_LOG || material == Material.STRIPPED_SPRUCE_LOG ||
               material == Material.STRIPPED_BIRCH_LOG || material == Material.STRIPPED_JUNGLE_LOG ||
               material == Material.STRIPPED_ACACIA_LOG || material == Material.STRIPPED_DARK_OAK_LOG ||
               material == Material.STRIPPED_MANGROVE_LOG || material == Material.STRIPPED_CHERRY_LOG ||
               material == Material.STRIPPED_CRIMSON_STEM || material == Material.STRIPPED_WARPED_STEM ||
               material == Material.STRIPPED_BAMBOO_BLOCK;
    }

    private boolean isStrippedWood(Material material) {
        return material == Material.STRIPPED_OAK_WOOD || material == Material.STRIPPED_SPRUCE_WOOD ||
               material == Material.STRIPPED_BIRCH_WOOD || material == Material.STRIPPED_JUNGLE_WOOD ||
               material == Material.STRIPPED_ACACIA_WOOD || material == Material.STRIPPED_DARK_OAK_WOOD ||
               material == Material.STRIPPED_MANGROVE_WOOD || material == Material.STRIPPED_CHERRY_WOOD ||
               material == Material.STRIPPED_CRIMSON_HYPHAE || material == Material.STRIPPED_WARPED_HYPHAE;
    }

    private boolean isPlanks(Material material) {
        return material == Material.OAK_PLANKS || material == Material.SPRUCE_PLANKS ||
               material == Material.BIRCH_PLANKS || material == Material.JUNGLE_PLANKS ||
               material == Material.ACACIA_PLANKS || material == Material.DARK_OAK_PLANKS ||
               material == Material.MANGROVE_PLANKS || material == Material.CHERRY_PLANKS ||
               material == Material.CRIMSON_PLANKS || material == Material.WARPED_PLANKS ||
               material == Material.BAMBOO_PLANKS;
    }

    private boolean isWoodcuttingInput(Material material) {
        return isWoodLog(material) || isWood(material) || isStrippedLog(material) || isStrippedWood(material) || isPlanks(material);
    }

    private boolean isValidAction(InventoryAction action) {
        return switch (action) {
            case PICKUP_ALL, PICKUP_SOME, PICKUP_HALF, PICKUP_ONE,
                 MOVE_TO_OTHER_INVENTORY, DROP_ALL_CURSOR, DROP_ONE_CURSOR,
                 DROP_ALL_SLOT, DROP_ONE_SLOT, HOTBAR_SWAP -> true;
            default -> false;
        };
    }

    private int getStonecutAmount(InventoryClickEvent event) {
        ItemStack result = event.getCurrentItem();
        if (result == null) return 0;
        InventoryAction action = event.getAction();

        return switch (action) {
            case PICKUP_HALF -> Math.max(1, result.getAmount() / 2);
            case PICKUP_SOME -> {
                ItemStack cursor = event.getCursor();
                if (cursor != null && cursor.isSimilar(result)) {
                    int maxStack = result.getMaxStackSize();
                    int canTake = maxStack - cursor.getAmount();
                    yield Math.min(canTake, result.getAmount());
                }
                yield result.getAmount();
            }
            case MOVE_TO_OTHER_INVENTORY -> event.getView().getItem(0) != null
                    ? result.getAmount() * Objects.requireNonNull(event.getView().getItem(0)).getAmount()
                    : result.getAmount();
            default -> result.getAmount();
        };
    }

    private ItemStack cloneSafe(ItemStack item) {
        return (item == null) ? null : item.clone();
    }

    private boolean didConsumeIngredient(ItemStack before, ItemStack after) {
        if (before == null || before.getType() == Material.AIR) return false;
        if (after == null || after.getType() == Material.AIR) return true;
        if (!before.isSimilar(after)) return true;
        return after.getAmount() < before.getAmount();
    }
}
