package com.minecraftcivilizations.specialization.Cooking;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Campfire;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import org.bukkit.inventory.EquipmentSlot;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class CookingListener implements Listener {

    private final NamespacedKey STAND_KEY = new NamespacedKey(Specialization.getInstance(), "cooking_stand");

    private final Map<Location, CookingItemData> cookingSessions = new HashMap<>();
    private final File previewsFile = new File(Specialization.getInstance().getDataFolder(), "cooking_previews.yml");
    private final FileConfiguration previewsConfig = YamlConfiguration.loadConfiguration(previewsFile);

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CAMPFIRE) return;
        Player player = event.getPlayer();
        // Determine the actual ItemStack in the interacting hand (event.getItem can be null for off-hand)
        ItemStack handItem = (event.getHand() == EquipmentSlot.OFF_HAND) ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();

        // previous diagnostic logging was removed

        // Sneak-right-click to extract the top-most (last) non-empty campfire slot item and give to player
        try {
            if (player.isSneaking()) {
                // PlayerInteractEvent can fire for both hands (main and off). Only handle extraction on MAIN_HAND
                if (event.getHand() == EquipmentSlot.OFF_HAND) return;
                // do not allow extraction while cooking is actively running (not when already cooked)
                CookingItemData running = cookingSessions.get(block.getLocation());
                if (isActiveCooking(running)) {
                    PlayerUtil.message(player, "<red>Cannot remove items while cooking is in progress.");
                    event.setCancelled(true);
                    return;
                }
                Campfire cfSneak = (Campfire) block.getState();
                int slotToTake = -1;
                for (int i = 3; i >= 0; i--) {
                    ItemStack s = cfSneak.getItem(i);
                    if (s != null && s.getType() != Material.AIR) {
                        slotToTake = i;
                        break;
                    }
                }
                if (slotToTake != -1) {
                    ItemStack slotItem = cfSneak.getItem(slotToTake);
                    ItemStack taken = slotItem == null ? null : slotItem.clone();
                    cfSneak.setItem(slotToTake, null);
                    try {
                        cfSneak.update(true);
                    } catch (Throwable ignored) {
                    }
                    if (taken != null) {
                        giveOrDrop(player, taken);
                        PlayerUtil.message(player, "<green>Returned item from campfire slot.");
                    }
                    // If we have a tracked session, remove the matching tracked ingredient/seasoning and update preview
                    CookingItemData session = cookingSessions.get(block.getLocation());
                    if (session != null) {
                        Iterator<ItemStack> it = session.ingredients.iterator();
                        while (it.hasNext()) {
                            ItemStack ing = it.next();
                            if (ing != null && taken != null && ing.isSimilar(taken)) { it.remove(); break; }
                        }
                        Iterator<ItemStack> sit = session.seasonings.iterator();
                        while (sit.hasNext()) {
                            ItemStack seas = sit.next();
                            if (seas != null && taken != null && seas.isSimilar(taken)) { sit.remove(); break; }
                        }
                        try { checkCombination(session, player); } catch (Throwable ignored) {}
                    }
                    event.setCancelled(true);
                    return;
                } else {
                    // No slot item found: if there's a cooking session and both campfire slots and tracked lists are empty, return the recipient
                    CookingItemData session = cookingSessions.get(block.getLocation());
                    if (session != null) {
                        boolean cfEmpty = true;
                        for (int i = 0; i < 4; i++) {
                            ItemStack it = cfSneak.getItem(i);
                            if (it != null && !it.getType().isAir()) { cfEmpty = false; break; }
                        }
                        if (!isActiveCooking(session) && cfEmpty && session.ingredients.isEmpty() && session.seasonings.isEmpty()) {
                            try {
                                if (session.recipient != null) giveOrDrop(player, session.recipient.clone());
                            } catch (Throwable ignored) {}
                            PlayerUtil.message(player, "<green>Returned recipient item from cooking station.");
                            try { CookingVisuals.cleanupVisuals(session); } catch (Throwable ignored) {}
                            // remove any armor stand visually tied to this campfire (defensive)
                            try { removeCookingStandAt(block.getLocation()); } catch (Throwable ignored) {}
                            cleanUp(session, block.getLocation());
                            event.setCancelled(true);
                            return;
                        } else {
                            event.setCancelled(true);
                            return;
                        }
                    }
                }
            }
        } catch (Throwable ex) {
            // logging removed
        }

        PlayerUtil.message(player, PlayerUtil.buildLogo() + " Interacting with campfire at " + block.getLocation());

        if (cookingSessions.containsKey(block.getLocation())) {
            CookingItemData data = cookingSessions.get(block.getLocation());
            PlayerUtil.message(player, "<gray>Active cooking session present at this campfire.");
            if (handItem.getType() == Material.AIR) {
                if (data.cooked) {
                    // remove only this session's recipient ItemDisplay so other campfires remain untouched
                    try {
                        if (data.recipientDisplay != null) {
                            try { data.recipientDisplay.remove(); } catch (Throwable ignored) {}
                            data.recipientDisplay = null;
                        }
                    } catch (Throwable ignored) {}
                    try {
                        // Drop the cooked item at the campfire center so it behaves like a normal drop (no inventory teleporting)
                        if (data.campfireLocation != null && data.food != null) {
                            Location dropLoc = data.campfireLocation.clone().add(0.5, 0.5, 0.5);
                            data.campfireLocation.getWorld().dropItemNaturally(dropLoc, data.food.clone());
                            // Play pickup/finish sound only for the collector
                            try { CookingVisuals.playItemGet(player); } catch (Throwable ignored) {}
                        } else if (data.food != null) {
                            // fallback: give to player if drop point missing
                            try { giveOrDrop(player, data.food.clone()); } catch (Throwable ignored) {}
                        }
                    } catch (Exception ignored) {
                    }
                    PlayerUtil.message(player, "<green>Collected cooked item.");
                    // Use the campfire block location (we're inside onPlayerInteract, 'stand' isn't available here)
                    cleanUp(data, block.getLocation());
                }
                event.setCancelled(true);
                return;
            }
            String itemId = getItemId(handItem);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);

            if (SpecializationConfig.getCookingConfig().getStringList("possible_ingredients").contains(itemId)) {
                if (tryPlaceItemOnCampfire(block, handItem, data, player, false, event.getHand())) {
                    checkCombination(data, player);
                }
                // No owner concept: multiple players may interact with the same campfire session
                event.setCancelled(true);
                return;
            } else if (SpecializationConfig.getCookingConfig().getStringList("possible_seasonings").contains(itemId)
                    || SpecializationConfig.getCookingConfig().getStringList("possible_sauces").contains(itemId)
                    || handItem.getType() == Material.POTION) {
                tryPlaceItemOnCampfire(block, handItem, data, player, true, event.getHand());
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            return;
        }

        if (handItem.getType() == Material.AIR) return;
        String itemId = getItemId(handItem);
        if (!SpecializationConfig.getCookingConfig().getStringList("possible_recipients").contains(itemId)) return;

        // When placing recipient, extinguish the campfire and enter cooking mode
        org.bukkit.block.data.type.Campfire campfireData = (org.bukkit.block.data.type.Campfire) block.getBlockData();
        boolean originalLit = campfireData.isLit();
        // Do not allow placing a recipient while the campfire is lit. The campfire must be extinguished first (e.g. with a shovel).
        if (originalLit) {
            PlayerUtil.message(player, "<red>You must extinguish the campfire (right-click with a shovel) to enter cooking mode.");
            event.setCancelled(true);
            return;
        }

        ItemStack place = handItem.clone();
        place.setAmount(1);
        PlayerUtil.message(player, "<yellow>Placed recipient on campfire (cooking mode)");
        // Position the armor stand lower so the item floats above the campfire center
        Location loc = block.getLocation().add(0.5, 0.0, 0.5);
        // face player
        float faceYaw = player.getLocation().getYaw();
        loc.setYaw(faceYaw);

        ArmorStand stand = block.getWorld().spawn(loc, ArmorStand.class, s -> {
            try {
                s.setVisible(false);
            } catch (Throwable ignored) {
            }
            try {
                s.setInvisible(true);
            } catch (Throwable ignored) {
            }
            try {
                s.setGravity(false);
            } catch (Throwable ignored) {
            }
            try {
                s.setBasePlate(false);
            } catch (Throwable ignored) {
            }
            try {
                s.setArms(false);
            } catch (Throwable ignored) {
            }
            try {
                s.setSmall(true);
            } catch (Throwable ignored) {
            }
            try {
                s.getPersistentDataContainer().set(STAND_KEY, PersistentDataType.BOOLEAN, true);
            } catch (Throwable ignored) {
            }
        });

        CookingItemData data = new CookingItemData(stand, place.clone(), itemId);
        data.wasLit = originalLit;
        data.campfireLocation = block.getLocation();
        cookingSessions.put(block.getLocation(), data);
        // Spawn a visual ItemDisplay for the recipient (separate from the armor stand)
        try { CookingVisuals.spawnRecipientDisplay(data); } catch (Throwable ignored) {}
        // consume one recipient from the player's hand and prevent vanilla placement
        try {
            decrementPlayerHandBySlot(player, event.getHand());
            player.updateInventory();
        } catch (Throwable ignored) {}
        try { event.setUseInteractedBlock(Event.Result.DENY); } catch (Throwable ignored) {}
        try { event.setUseItemInHand(Event.Result.DENY); } catch (Throwable ignored) {}
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;
        if (!stand.getPersistentDataContainer().has(STAND_KEY, PersistentDataType.BOOLEAN)) return;
        Player player = event.getPlayer();
        EquipmentSlot handSlot = event.getHand();
        ItemStack hand = (handSlot == EquipmentSlot.OFF_HAND) ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();
        // Prevent players from equipping items onto this invisible cooking armor stand.
        // Allow only recipient items (possible_recipients) to bypass this cancellation check — other items (armor, tools) should not be placed on the stand.
        try {
            if (hand.getType() != Material.AIR) {
                String handId = getItemId(hand);
                java.util.List<String> recipients = SpecializationConfig.getCookingConfig().getStringList("possible_recipients");
                if (!recipients.contains(handId)) {
                    // Cancel the default interact (which would equip the item on the stand) but keep processing the event so our ingredient placement still works.
                    event.setCancelled(true);
                    // Also ensure the stand has no equipment applied (defensive) so any client/server race won't show the item.
                    try {
                        stand.getEquipment().setHelmet(null);
                    } catch (Throwable ignored) {
                    }
                    try {
                        stand.getEquipment().setItemInMainHand(null);
                    } catch (Throwable ignored) {
                    }
                    try {
                        stand.getEquipment().setItemInOffHand(null);
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        CookingItemData data = getSessionForStand(stand);
        if (data == null) {
            Location campLoc = stand.getLocation().subtract(0.5, 1, 0.5);
            data = cookingSessions.get(campLoc.getBlock().getLocation());
        }
        if (data == null) {
            PlayerUtil.message(player, "<red>No active cooking session found for this stand.");
            event.setCancelled(true);
            return;
        }

        if (hand.getType() == Material.AIR) {
            // If already cooked -> collect the cooked item
            if (data.cooked) {
                // remove only this session's recipient ItemDisplay so other campfires remain untouched
                try {
                    if (data.recipientDisplay != null) {
                        try { data.recipientDisplay.remove(); } catch (Throwable ignored) {}
                        data.recipientDisplay = null;
                    }
                } catch (Throwable ignored) {}
                try {
                    // Drop the cooked item at the campfire center so it behaves like a normal drop (no inventory teleporting)
                    if (data.campfireLocation != null && data.food != null) {
                        Location dropLoc = data.campfireLocation.clone().add(0.5, 0.5, 0.5);
                        data.campfireLocation.getWorld().dropItemNaturally(dropLoc, data.food.clone());
                        // Play pickup/finish sound only for the collector
                        try { CookingVisuals.playItemGet(player); } catch (Throwable ignored) {}
                    } else if (data.food != null) {
                        // fallback: give to player if drop point missing
                        try { giveOrDrop(player, data.food.clone()); } catch (Throwable ignored) {}
                    }
                } catch (Exception ignored) {
                }
                PlayerUtil.message(player, "<green>Collected cooked item.");
                cleanUp(data, stand.getLocation().subtract(0.5, 1, 0.5));
                event.setCancelled(true);
                return;
            }
            // If no ingredients have been placed yet, allow the owner to pick up the recipient preview
            if (data.ingredients.isEmpty()) {
                // No ownership: allow anyone to pick up the recipient before cooking
                try {
                    giveOrDrop(player, data.recipient.clone());
                } catch (Exception ignored) {
                }
                PlayerUtil.message(player, "<green>Picked up recipient item.");
                try { removeCookingStandAt(stand.getLocation().subtract(0.5, 1, 0.5)); } catch (Throwable ignored) {}
                cleanUp(data, stand.getLocation().subtract(0.5, 1, 0.5));
                event.setCancelled(true);
                return;
            }
            // If there is no floating display entity yet, spawn it now (player explicit action)
            if (data.displayEntity == null) {
                try {
                    // Spawn handled by CookingVisuals (will set data.displayEntity and viewer)
                    data.viewer = player.getUniqueId();
                    CookingVisuals.spawnOrUpdateDisplay(data);
                    try {
                        savePreviewToDisk(data);
                    } catch (Throwable ignored) {}
                    PlayerUtil.message(player, "<green>Preview spawned above campfire.");
                } catch (Throwable ex) {
                    // logging removed
                }
                // do not return here — continue so a valid recipe can be started with a single hit
            }
            if (data.food == null) {
                PlayerUtil.message(player, "<yellow>No valid recipe preview available — add ingredients first.");
                event.setCancelled(true);
                return;
            }
            // If cooking already in progress
            if (isActiveCooking(data)) {
                PlayerUtil.message(player, "<yellow>Cooking already in progress.");
                event.setCancelled(true);
                return;
            }
            // Otherwise start cooking (single-hit start)
            // logging removed
            try {
                // logging removed
                stopCookingProcesses(data);
                // logging removed
            } catch (Throwable ignored) {}
            // logging removed
            startCooking(data, player);
            event.setCancelled(true);
            return;
        }

        Location campLoc2 = stand.getLocation().subtract(0.5, 1, 0.5);
        Block block2 = campLoc2.getBlock();
        if (block2.getType() != Material.CAMPFIRE) return;
        CookingItemData sessionData = cookingSessions.get(block2.getLocation());
        if (sessionData == null) return;
        String itemId = getItemId(hand);
        if (SpecializationConfig.getCookingConfig().getStringList("possible_ingredients").contains(itemId)) {
            CustomPlayer cPlayer = CoreUtil.getPlayer(player);
            if (cPlayer.getSkillLevel(SkillType.FARMER) < 2) {
                PlayerUtil.message(player, "<red>You need to be Farmer level 2 or higher to use this ingredient!");
                return;
            }
            if (isActiveCooking(sessionData)) { PlayerUtil.message(player, "<red>Cannot modify campfire slots while cooking is in progress."); return; }
                if (tryPlaceItemOnCampfire(block2, hand, sessionData, player, false, handSlot)) {
                    // cancel the entity interaction so the server doesn't try to equip the item
                    try { event.setCancelled(true); } catch (Throwable ignored) {}
                    checkCombination(sessionData, player);
                    if (sessionData.food != null) PlayerUtil.message(player, "<aqua>Recipe matched — result shown on campfire stand");
                }
        } else if (SpecializationConfig.getCookingConfig().getStringList("possible_seasonings").contains(itemId)
                || SpecializationConfig.getCookingConfig().getStringList("possible_sauces").contains(itemId)
                || hand.getType() == Material.POTION) {
            CustomPlayer cPlayer = CoreUtil.getPlayer(player);
            if (cPlayer.getSkillLevel(SkillType.FARMER) < 2) { PlayerUtil.message(player, "<red>You need to be Farmer level 2 or higher to use this additive!"); return; }
            if (isActiveCooking(sessionData)) { PlayerUtil.message(player, "<red>Cannot modify campfire slots while cooking is in progress."); return; }
                if (tryPlaceItemOnCampfire(block2, hand, sessionData, player, true, handSlot)) {
                    try { event.setCancelled(true); } catch (Throwable ignored) {}
                }
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) return;
        if (!stand.getPersistentDataContainer().has(STAND_KEY, PersistentDataType.BOOLEAN)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        Location campLoc = stand.getLocation().subtract(0.5, 1, 0.5);
        CookingItemData data = getSessionForStand(stand);
        if (data == null) data = cookingSessions.get(campLoc.getBlock().getLocation());
        // Resync authoritative campfire slot contents into session lists to avoid stale state after removals/adds
        try {
            resyncSessionFromCampfire(data);
        } catch (Throwable ignored) {}
        if (data == null) return;

        // if cooking in progress -> cancel cooking but DO NOT return items (player can remove them manually).
        if (isActiveCooking(data)) {
            stopCookingProcesses(data);
            try {
                CookingVisuals.playCancelEffects(data);
            } catch (Exception ignored) {}
            PlayerUtil.message(player, "<yellow>Cooking cancelled.");
            event.setCancelled(true);
            return;
        }

        // Recompute recipe matching from authoritative campfire slots so a cancel + re-add cycle is recognized
        try {
            // ensure the checker knows who the viewer is (so spawn messages/preview target the hitter)
            data.viewer = player.getUniqueId();
            // logging removed
            checkCombination(data, player);
        } catch (Throwable ignored) {}

        // Defensive re-check: if no resolved food, try once more after forcing viewer to this hitter
        try {
            if (data.food == null) {
                data.viewer = player.getUniqueId();
                checkCombination(data, player);
                // logging removed
            }
        } catch (Throwable ignored) {}

        // If a preview display is missing but we have a resolved result item, spawn it so the hit action reflects the visible preview.
        try {
            if (data.displayEntity == null && data.food != null) {
                try { data.viewer = player.getUniqueId(); CookingVisuals.spawnOrUpdateDisplay(data); savePreviewToDisk(data); } catch (Throwable ex) {
                    // logging removed
                }
            }
        } catch (Throwable ignored) {}
        // single hit start: if there's a preview result, start cooking; otherwise notify
        if (data.food == null) {
            PlayerUtil.message(player, PlayerUtil.buildLogo() + " <yellow>No valid recipe to cook yet. Add ingredients first.");
            return;
        }
        PlayerUtil.message(player, "<green>Starting cooking process...");
        // logging removed
        try {
            stopCookingProcesses(data);
            // logging removed
        } catch (Throwable ignored) {}
        // logging removed
        startCooking(data, player);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock(); if (block.getType() != Material.CAMPFIRE) return;
        // Prevent vanilla block-item drops (campfire slot items) from also dropping — we handle drops explicitly below.
        try { event.setDropItems(false); } catch (NoSuchMethodError ignored) {}
        CookingItemData data = cookingSessions.get(block.getLocation());
        if (data == null) return;
        // Mark session destroyed first and cancel running processes to prevent finalization
        try { data.destroyed = true; } catch (Throwable ignored) {}
        try { data.cookExp = 0; } catch (Throwable ignored) {}
        try { data.viewer = null; } catch (Throwable ignored) {}
        try { stopCookingProcesses(data); } catch (Throwable ignored) {}
        Player breaker = event.getPlayer();
        try {
            Campfire cf = (Campfire) block.getState();
            // Drop campfire slot items and tracked ingredients at the campfire center so the breaker (or nearby players) can pick them up
            Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
            Random rnd = new Random();
            // Collect unique drops from campfire slots and tracked ingredient/recipient lists
            List<ItemStack> toDrop = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                ItemStack s = cf.getItem(i);
                if (s != null && s.getType() != Material.AIR) toDrop.add(s.clone());
            }
            // campfire slot contents are authoritative; do NOT add data.ingredients/data.seasonings here
            // as they are mirrors of the slot contents and would cause duplicate drops.
            // include the recipient item (the item placed on the station)
            try { if (data.recipient != null && data.recipient.getType() != Material.AIR) toDrop.add(data.recipient.clone()); } catch (Throwable ignored) {}
            // add 2 charcoal as vanilla campfire breaking reward
            try { toDrop.add(new ItemStack(Material.CHARCOAL, 2)); } catch (Throwable ignored) {}
            // Remove the session mapping first so our drops don't get cancelled by onItemSpawn
            try { cookingSessions.remove(block.getLocation()); } catch (Throwable ignored) {}
            // Drop each collected stack as-is to preserve custom item data
            for (ItemStack stack : toDrop) {
                try {
                    Location loc = dropLoc.clone().add((rnd.nextDouble()-0.5)*0.4, 0, (rnd.nextDouble()-0.5)*0.4);
                    org.bukkit.entity.Item dropped = block.getWorld().dropItemNaturally(loc, stack);
                    // ensure dropped items are immediately pickable after break
                    try { dropped.setPickupDelay(10); } catch (Throwable ignored) {}
                } catch (Throwable ignored) {}
            }
            // remove any preview dropped entity (floating dropped item preview)
            if (data.displayEntity != null) {
                try { data.displayEntity.remove(); } catch (Throwable ignored) {}
            }
            // ensure any ItemDisplay preview (recipient) near the campfire is removed as well
            try {
                // direct field removal if present
                try { if (data.recipientDisplay != null) { data.recipientDisplay.remove(); data.recipientDisplay = null; } } catch (Throwable ignored) {}
                // remove entities by persistent data keys (set in CookingVisuals)
                NamespacedKey recipKey = new NamespacedKey(Specialization.getInstance(), "cooking_recipient");
                NamespacedKey prevKey = new NamespacedKey(Specialization.getInstance(), "cooking_preview");
                for (org.bukkit.entity.Entity ne : block.getWorld().getNearbyEntities(dropLoc, 0.4, 0.4, 0.4)) {
                    try {
                        if (ne instanceof org.bukkit.entity.ItemDisplay idisp) {
                            try {
                                if (idisp.getPersistentDataContainer().has(recipKey, PersistentDataType.STRING)) {
                                    idisp.remove();
                                }
                            } catch (Throwable ignored) {}
                        }
                        if (ne instanceof org.bukkit.entity.Item itemEnt) {
                            try {
                                if (itemEnt.getPersistentDataContainer().has(prevKey, PersistentDataType.BOOLEAN)) {
                                    itemEnt.remove();
                                }
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        } catch (Exception ignored) {
        }
        try {
            if (data.stand != null) {
                try {
                    data.stand.getEquipment().setItemInMainHand(null);
                    data.stand.getEquipment().setItemInOffHand(null);
                } catch (Exception ignored) {
                }
                try {
                    CookingVisuals.playBreakEffects(data);
                } catch (Exception ignored) {
                }
                try {
                    data.stand.remove();
                } catch (Exception ignored) {
                }
            }
            // Only remove the special cooking armor stands nearby; do NOT remove Item entities (we want drops to remain).
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            for (org.bukkit.entity.Entity e : block.getWorld().getNearbyEntities(center, 2.0, 2.0, 2.0)) {
                if (e instanceof ArmorStand as) {
                    if (as.getPersistentDataContainer().has(STAND_KEY, PersistentDataType.BOOLEAN)) {
                        try {
                            as.getEquipment().setItemInMainHand(null);
                            as.remove();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        try {
            event.setDropItems(false);
        } catch (NoSuchMethodError ignored) {
        }
        // Clean up session without removing the items we just dropped from the world
        cleanUpKeepDrops(data, block.getLocation());
    }

    private void cleanUp(CookingItemData data, Location loc) {
        if (data == null) return;
        // remove display item if present
        try {
            if (data.displayEntity != null) {
                try {
                    removePreviewFromDisk(data);
                } catch (Throwable ignored) {
                }
                data.displayEntity.remove();
                data.displayEntity = null;
            }
        } catch (Throwable ignored) {
        }
        // remove recipient ItemDisplay if present
        try {
            if (data.recipientDisplay != null) {
                try { data.recipientDisplay.remove(); } catch (Throwable ignored) {}
                data.recipientDisplay = null;
            }
        } catch (Throwable ignored) {}
        // remove boss bar if any
        try {
            if (data.bossBar != null) {
                data.bossBar.removeAll();
                data.bossBar.setVisible(false);
                data.bossBar = null;
            }
        } catch (Throwable ignored) {
        }
        // ensure any running cooking tasks are stopped
        stopCookingProcesses(data);
        // remove session from registry using provided loc (may be null in some callers)
        if (loc != null) cookingSessions.remove(loc);
        // Restore campfire lit state (if block still exists) and clear server-side slot state
        try {
            Block block = loc == null ? null : loc.getBlock();
            if (block != null && block.getType() == Material.CAMPFIRE) {
                try {
                    org.bukkit.block.data.type.Campfire campfireData = (org.bukkit.block.data.type.Campfire) block.getBlockData();
                    if (data.wasLit != campfireData.isLit()) {
                        campfireData.setLit(data.wasLit);
                        block.setBlockData(campfireData);
                    }
                    Campfire campfire = (Campfire) block.getState();
                    for (int i = 0; i < 4; i++) campfire.setItem(i, null);
                    campfire.update(true);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Clean up session state and visuals but DO NOT remove dropped item entities from the world.
     * Used when a campfire block is broken and we've already dropped the items the player should receive.
     */
    private void cleanUpKeepDrops(CookingItemData data, Location loc) {
        if (data == null) return;
        // remove preview dropped item if present (the preview for the recipe)
        try {
            if (data.displayEntity != null) {
                try { removePreviewFromDisk(data); } catch (Throwable ignored) {}
                try { data.displayEntity.remove(); } catch (Throwable ignored) {}
                data.displayEntity = null;
            }
        } catch (Throwable ignored) {}
        // remove recipient ItemDisplay if present
        try {
            if (data.recipientDisplay != null) {
                try { data.recipientDisplay.remove(); } catch (Throwable ignored) {}
                data.recipientDisplay = null;
            }
        } catch (Throwable ignored) {}
        // remove boss bar if any
        try {
            if (data.bossBar != null) {
                data.bossBar.removeAll();
                data.bossBar.setVisible(false);
                data.bossBar = null;
            }
        } catch (Throwable ignored) {
        }
        // remove armor stand
        if (data.stand != null) {
            try {
                if (data.stand.isValid()) {
                    try { data.stand.getEquipment().setHelmet(null); } catch (Throwable ignored) {}
                    try { data.stand.getEquipment().setItemInMainHand(null); } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
            try { data.stand.remove(); } catch (Throwable ignored) {}
        }
        // Do not remove Item entities — we want the dropped items to remain for the player.
        // remove session from registry using provided loc (may be null in some callers)
        if (loc != null) cookingSessions.remove(loc);
        Block block = loc == null ? null : loc.getBlock();
        if (block != null && block.getType() == Material.CAMPFIRE) {
            try {
                org.bukkit.block.data.type.Campfire campfireData = (org.bukkit.block.data.type.Campfire) block.getBlockData();
                if (data.wasLit != campfireData.isLit()) {
                    campfireData.setLit(data.wasLit);
                    block.setBlockData(campfireData);
                }
                Campfire campfire = (Campfire) block.getState();
                for (int i = 0; i < 4; i++) campfire.setItem(i, null);
                campfire.update(true);
            } catch (Exception ignored) {
            }
        }
    }

    // Helper to give item to player or drop it if inventory full
    private void giveOrDrop(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType() == Material.AIR) return;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            for (ItemStack rem : leftover.values()) if (rem != null) player.getWorld().dropItemNaturally(player.getLocation().add(0,2,0), rem);
        }
    }

    private String getItemId(ItemStack item) {
        net.momirealms.craftengine.core.item.Item<ItemStack> wrapped = BukkitItemManager.instance().wrap(item);
        return wrapped.getCustomItem().isPresent() ? wrapped.getCustomItem().get().id().value() : "minecraft:" + item.getType().name().toLowerCase();
    }

    private void savePreviewToDisk(CookingItemData data) {
        if (data == null || data.displayEntity == null || !data.displayEntity.isValid() || data.campfireLocation == null)
            return;
        String key = data.campfireLocation.getWorld().getName() + ":" + data.campfireLocation.getBlockX() + "," + data.campfireLocation.getBlockY() + "," + data.campfireLocation.getBlockZ();
        previewsConfig.set(key + ".recipientId", data.recipientId);
        previewsConfig.set(key + ".displayItem", data.displayEntity.getItemStack());
        previewsConfig.set(key + ".cooked", data.cooked);
        try {
            previewsConfig.save(previewsFile);
        } catch (IOException e) {
            // logging removed
        }
    }

    private void removePreviewFromDisk(CookingItemData data) {
        if (data == null || data.campfireLocation == null) return;
        String key = data.campfireLocation.getWorld().getName() + ":" + data.campfireLocation.getBlockX() + "," + data.campfireLocation.getBlockY() + "," + data.campfireLocation.getBlockZ();
        previewsConfig.set(key, null);
        try {
            previewsConfig.save(previewsFile);
        } catch (IOException e) {
            // logging removed
        }
    }

    // Safely cancel any running cooking tasks and clear related flags so interaction is re-enabled
    private void stopCookingProcesses(CookingItemData data) {
        if (data == null) return;
        // Stop visuals first (boss bar) to ensure players don't keep a stuck bar
        try { CookingVisuals.stopProgressBar(data); } catch (Throwable ignored) {}
        // Cancel scheduled tasks
        try { if (data.progressTask != null) { data.progressTask.cancel(); } } catch (Throwable ignored) {}
        try { data.progressTask = null; } catch (Throwable ignored) {}
        try { if (data.cookTask != null) { data.cookTask.cancel(); } } catch (Throwable ignored) {}
        try { data.cookTask = null; } catch (Throwable ignored) {}
        try { if (data.maintenanceTask != null) { data.maintenanceTask.cancel(); } } catch (Throwable ignored) {}
        try { data.maintenanceTask = null; } catch (Throwable ignored) {}
        // Reset state flags so interactions are allowed again
        try { data.cookingInProgress = false; } catch (Throwable ignored) {}
        try { data.cooked = false; } catch (Throwable ignored) {}
        try { data.viewer = null; } catch (Throwable ignored) {}
        // Force-remove any boss bar reference (defensive)
        try {
            if (data.bossBar != null) {
                try { data.bossBar.removeAll(); } catch (Throwable ignored) {}
                try { data.bossBar.setVisible(false); } catch (Throwable ignored) {}
                data.bossBar = null;
            }
        } catch (Throwable ignored) {}
        // Do NOT remove preview/display entities here; keep visuals intact so the player can continue editing
    }

    // Call this on plugin enable to restore previews saved to disk
    @SuppressWarnings("unused")
    public void loadPersistedPreviews() {
        if (!previewsFile.exists()) return;
        for (String k : previewsConfig.getKeys(false)) {
            try {
                String[] parts = k.split(":");
                if (parts.length != 2) continue;
                String worldName = parts[0];
                String[] coords = parts[1].split(",");
                if (coords.length != 3) continue;
                org.bukkit.World w = Bukkit.getWorld(worldName);
                if (w == null) continue;
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                int z = Integer.parseInt(coords[2]);
                Location loc = new Location(w, x, y, z);
                ItemStack stack = previewsConfig.getItemStack(k + ".displayItem");
                String recipientId = previewsConfig.getString(k + ".recipientId");
                boolean cooked = previewsConfig.getBoolean(k + ".cooked", false);
                // spawn a stand placeholder to associate session
                Location standLoc = loc.clone().add(0.5, 0.0, 0.5);
                ArmorStand stand = loc.getWorld().spawn(standLoc, ArmorStand.class, s -> {
                    try {
                        s.setVisible(false);
                    } catch (Throwable ignored) {
                    }
                    try {
                        s.setInvisible(true);
                    } catch (Throwable ignored) {
                    }
                    try {
                        s.setGravity(false);
                    } catch (Throwable ignored) {
                    }
                    try {
                        s.setBasePlate(false);
                    } catch (Throwable ignored) {
                    }
                    try {
                        s.setSmall(true);
                    } catch (Throwable ignored) {
                    }
                    try {
                        s.getPersistentDataContainer().set(STAND_KEY, PersistentDataType.BOOLEAN, true);
                    } catch (Throwable ignored) {
                    }
                });
                CookingItemData data = new CookingItemData(stand, stack == null ? null : stack.clone(), recipientId);
                data.campfireLocation = loc;
                data.food = stack == null ? null : stack.clone();
                data.cooked = cooked;
                cookingSessions.put(loc, data);
                // spawn preview dropped item
                if (stack != null) CookingVisuals.spawnOrUpdateDisplay(data);
            } catch (Throwable ex) {
                // logging removed
            }
        }
    }

    private void checkCombination(CookingItemData data, Player player) {
        if (data == null) return;
        // logging removed

        // Build multiset of current ingredient ids. Prefer authoritative campfire block state when available
        List<String> currentIds = new ArrayList<>();
        try {
            if (data.campfireLocation != null) {
                Block block = data.campfireLocation.getBlock();
                if (block.getType() == Material.CAMPFIRE) {
                    Campfire cf = (Campfire) block.getState();
                    for (int i = 0; i < 4; i++) {
                        ItemStack it = cf.getItem(i);
                        if (it != null && !it.getType().isAir()) {
                            currentIds.add(getItemId(it));
                        }
                    }
                } else {
                    // fallback to tracked list
                    for (ItemStack is : data.ingredients) if (is != null) currentIds.add(getItemId(is));
                }
            } else {
                for (ItemStack is : data.ingredients) if (is != null) currentIds.add(getItemId(is));
            }
        } catch (Throwable ignored) {
            // fallback
            for (ItemStack is : data.ingredients) if (is != null) currentIds.add(getItemId(is));
        }

        Config cookingConfig = SpecializationConfig.getCookingConfig().getConfig();

        // Determine allowed tiers from player's Farmer skill level
        int playerLevelInt = 0;
        try {
            // Prefer the specialization CustomPlayer wrapper if available
            com.minecraftcivilizations.specialization.Player.CustomPlayer specCP = com.minecraftcivilizations.specialization.Player.CustomPlayer.getCustomPlayer(player);
            if (specCP != null) {
                playerLevelInt = specCP.getSkillLevel(SkillType.FARMER);
            } else {
                // fallback to CoreUtil lookup
                CustomPlayer coreCP = CoreUtil.getPlayer(player);
                if (coreCP != null) playerLevelInt = coreCP.getSkillLevel(SkillType.FARMER);
            }
        } catch (Throwable t) {
            try { Specialization.getInstance().getLogger().warning("[Cooking] Could not resolve CustomPlayer for " + player.getName() + ", defaulting farmer level to 0"); } catch (Throwable ignored) {}
            playerLevelInt = 0;
        }
        SkillLevel playerSkill = SkillLevel.getSkillLevelFromInt(playerLevelInt);

        // If player is below Farmer level 2, check whether the current ingredients WOULD match some recipe
        // at a higher tier; if so, inform the player that they need Farmer level 2 to cook.
        try {
            if (playerSkill.getLevel() < 2) {
                List<ConfigObject> allTiers = new ArrayList<>();
                for (SkillLevel t : SkillLevel.values()) {
                    String tk = "FARMER_" + t.name();
                    try { allTiers.addAll(cookingConfig.getObjectList(tk)); } catch (Exception ignored) {}
                }
                boolean wouldMatch = false;
                for (ConfigObject obj : allTiers) {
                    try {
                        Config r = obj.toConfig();
                        String recip = r.getString("recipient");
                        if (!recip.equals(data.recipientId)) continue;
                        List<String> req = r.hasPath("ingredients") ? r.getStringList("ingredients") : List.of();
                        Collections.sort(req);
                        List<String> curSorted = new ArrayList<>(currentIds);
                        Collections.sort(curSorted);
                        if (req.equals(curSorted)) { wouldMatch = true; break; }
                    } catch (Throwable ignored) {}
                }
                if (wouldMatch) {
                    try {
                        PlayerUtil.message(player, "<red>You need to be Farmer level 2 or higher to cook this dish!", 1);
                    } catch (Throwable ignored) {
                        try { player.sendMessage("§cYou need to be Farmer level 2 or higher to cook this dish!"); } catch (Throwable ignored2) {}
                    }
                    return;
                }
            }
        } catch (Throwable ignored) {}

        boolean matchedAny = false;
        // Check tiers from highest to lowest so higher-tier recipes take precedence
        SkillLevel[] levels = SkillLevel.values();
        Arrays.sort(levels, Comparator.comparingInt(SkillLevel::getLevel).reversed());
        for (SkillLevel tier : levels) {
            if (tier.getLevel() > playerSkill.getLevel()) continue; // skip tiers above player's level
            String tierKey = "FARMER_" + tier.name();
            List<? extends ConfigObject> tierRecipes = cookingConfig.getObjectList(tierKey);
            for (ConfigObject configObject : tierRecipes) {
                Config recipe = configObject.toConfig();
                String recipient = recipe.getString("recipient");
                // logging removed
                if (!recipient.equals(data.recipientId)) continue;
                List<String> ingredients = recipe.hasPath("ingredients") ? recipe.getStringList("ingredients") : List.of();
                ingredients.sort(String::compareTo);
                Collections.sort(currentIds);
                if (!ingredients.equals(currentIds)) {
                    // logging removed
                    continue;
                }
                matchedAny = true;

                // logging removed
                 String resultId = recipe.getString("result");
                 int expAmt = recipe.hasPath("exp") ? recipe.getInt("exp") : 0;
                 int cookSeconds = recipe.hasPath("cooking_time") ? recipe.getInt("cooking_time") : 10;
                  boolean made = resolveResultItem(data, resultId);
                 if (made) {
                     data.cookExp = expAmt;
                     data.cookTimeSeconds = cookSeconds;
                 }
                 if (made && data.food != null) {
                     try { CookingVisuals.spawnOrUpdateDisplay(data); } catch (Throwable ignored) {}
                     if (player.isOnline()) {
                        try {
                            // Play a player-local composter "empty" sound at pitch 0.5 so only the player hears it
                            player.playSound(player.getLocation(), Sound.BLOCK_COMPOSTER_EMPTY, SoundCategory.BLOCKS, 1.0f, 0.5f);
                        } catch (Throwable ignored) {}
                     }
                 }
                 break; // stop after first match in this tier
             }
             if (matchedAny) break;
         }

         if (!matchedAny) {
             if (data.food != null) {
                // logging removed
                 data.food = null;
                 // reset configured exp when clearing preview
                 data.cookExp = 0;
                 try {
                     if (data.displayEntity != null) {
                         data.displayEntity.remove();
                         data.displayEntity = null;
                     }
                 } catch (Throwable ignored) {
                 }
                 try {
                     if (data.bossBar != null) {
                         data.bossBar.removeAll();
                         data.bossBar.setVisible(false);
                         data.bossBar = null;
                     }
                 } catch (Throwable ignored) {
                 }
                 if (player.isOnline())
                     PlayerUtil.message(player, "<yellow>Recipe invalid or incomplete for this recipient.");
             }
         }
     }

     private boolean resolveResultItem(CookingItemData data, String resultId) {
         boolean made = false;
         if (data == null || resultId == null) return false;
         try {
             // logging removed
             Optional<net.momirealms.craftengine.core.item.CustomItem<ItemStack>> ce = BukkitItemManager.instance().getCustomItem(net.momirealms.craftengine.core.util.Key.of(resultId));
             if (ce != null && ce.isPresent()) {
                 data.food = ce.get().buildItemStack(1);
                 made = true;
             }
         } catch (Throwable e) {
             // logging removed
         }
         if (!made) {
             try {
                 com.minecraftcivilizations.specialization.CustomItem.CustomItem ci = com.minecraftcivilizations.specialization.CustomItem.CustomItemManager.getInstance().getCustomItem(resultId);
                 if (ci != null) {
                     data.food = ci.createItemStack(1);
                     made = true;
                 }
             } catch (Throwable e) {
                 // logging removed
             }
         }
         return made;
     }


     private boolean tryPlaceItemOnCampfire(Block block, ItemStack hand, CookingItemData data, Player player, boolean isSeasoning, EquipmentSlot handSlot) {
         if (block == null || block.getType() != Material.CAMPFIRE) return false;
         org.bukkit.block.data.type.Campfire blockData = (org.bukkit.block.data.type.Campfire) block.getBlockData();
         if (blockData.isLit()) {
             PlayerUtil.message(player, "<red>You must extinguish the campfire (right-click with a shovel) to enter cooking mode.");
             return false;
         }
         // If the session already started cooking, do not allow modification of slots
         if (isActiveCooking(data)) {
             // logging removed
             PlayerUtil.message(player, "<red>Cannot add or remove items while cooking is in progress.");
             return false;
         }
         try {
             Campfire cf = (Campfire) block.getState();
             int slot = -1;
             for (int i = 0; i < 4; i++) {
                 ItemStack cur = cf.getItem(i);
                 if (cur == null || cur.getType().isAir()) {
                     slot = i;
                     break;
                 }
             }
             if (slot == -1) {
                 PlayerUtil.message(player, isSeasoning ? "<red>No empty seasoning slots available." : "<red>No empty ingredient slots available.");
                 return false;
             }
             if (isSeasoning) {
                 // allow at most 2 seasonings per session
                 if (data.seasonings.size() >= 2) {
                     PlayerUtil.message(player, "<red>Only 2 seasonings/sauces are allowed per cooking session.");
                     return false;
                 }
             }
             ItemStack toPlace = hand.clone();
             toPlace.setAmount(1);
             cf.setItem(slot, toPlace);
             cf.update(true);
             // logging removed
             if (isSeasoning) {
                 data.seasonings.add(toPlace);
                 // spawn a simple particle effect to visualise seasoning
                 try { CookingVisuals.playSeasoningEffect(block, toPlace); } catch (Throwable ignored) {}
             } else {
                 data.ingredients.add(toPlace);
             }
             decrementPlayerHandBySlot(player, handSlot);
             player.updateInventory();
             return true;
         } catch (Exception ex) {
             // logging removed
             return false;
         }
     }

    private void decrementPlayerHandBySlot(Player player, EquipmentSlot handSlot) {
        if (handSlot == EquipmentSlot.OFF_HAND) {
            ItemStack off = player.getInventory().getItemInOffHand();
            int newAmt = Math.max(0, off.getAmount() - 1);
            if (newAmt == 0) player.getInventory().setItemInOffHand(null);
            else off.setAmount(newAmt);
            return;
        }
        ItemStack main = player.getInventory().getItemInMainHand();
        int newAmt = Math.max(0, main.getAmount() - 1);
        if (newAmt == 0) player.getInventory().setItemInMainHand(null);
        else main.setAmount(newAmt);
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        org.bukkit.entity.Item it = event.getEntity();
        ItemStack stack = it.getItemStack();
        Location loc = it.getLocation();
        for (Map.Entry<Location, CookingItemData> entry : cookingSessions.entrySet()) {
            Location campLoc = entry.getKey();
            if (!campLoc.getWorld().equals(loc.getWorld())) continue;
            if (campLoc.distanceSquared(loc) > 4.0) continue;
            CookingItemData d = entry.getValue();
            if (d == null) continue;
            // If the session was destroyed (breaking flow), allow spawns — do not cancel our own drops
            if (d.destroyed) continue;
            String spawnedId = "minecraft:" + stack.getType().name().toLowerCase();
            boolean matches = d.ingredients.stream().anyMatch(i -> ("minecraft:" + i.getType().name().toLowerCase()).equals(spawnedId));
            if (matches) {
                event.setCancelled(true);
                try {
                    it.remove();
                } catch (Exception ignored) {
                }
                return;
            }
        }
    }

    @EventHandler
    public void onEntityPickup(EntityPickupItemEvent event) {
        org.bukkit.entity.Item item = event.getItem();
        // If this item is one of our preview display items and not yet cooked, cancel pickup
        for (CookingItemData d : cookingSessions.values()) {
            if (d == null || d.displayEntity == null) continue;
            if (d.displayEntity.getUniqueId().equals(item.getUniqueId())) {
                if (!d.cooked) {
                    event.setCancelled(true);
                    return;
                }
                // if cooked, allow pickup but cleanup session
                try {
                    removePreviewFromDisk(d);
                } catch (Throwable ignored) {
                }
                try {
                    d.displayEntity.remove();
                } catch (Throwable ignored) {
                }
                d.displayEntity = null;
                return;
            }
        }
    }


    private CookingItemData getSessionForStand(ArmorStand stand) {
        if (stand == null) return null;
        for (CookingItemData d : cookingSessions.values()) {
            if (d == null) continue;
            try {
                if (d.stand != null && d.stand.getUniqueId().equals(stand.getUniqueId())) return d;
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    // Resync session ingredients and seasonings from the physical Campfire block slots (authoritative)
    private void resyncSessionFromCampfire(CookingItemData data) {
        if (data == null || data.campfireLocation == null) return;
        Block b = data.campfireLocation.getBlock();
        if (b.getType() != Material.CAMPFIRE) return;
        try {
            Campfire cf = (Campfire) b.getState();
            List<ItemStack> newIngredients = new ArrayList<>();
            List<ItemStack> newSeasonings = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                ItemStack it = cf.getItem(i);
                if (it == null || it.getType().isAir()) continue;
                String id = getItemId(it);
                if (SpecializationConfig.getCookingConfig().getStringList("possible_seasonings").contains(id)
                        || SpecializationConfig.getCookingConfig().getStringList("possible_sauces").contains(id)
                        || it.getType() == Material.POTION) {
                    newSeasonings.add(it.clone());
                } else {
                    newIngredients.add(it.clone());
                }
            }
            data.ingredients = newIngredients;
            data.seasonings = newSeasonings;
        } catch (Throwable ignored) {}
    }

    private boolean isActiveCooking(CookingItemData d) {
        // Only true when the actual timed cooking process has started.
        return d != null && !d.cooked && (d.cookingInProgress || d.cookTask != null);
    }

    private void startCooking(CookingItemData session, Player starter) {
        if (session == null) return;
        if (session.food == null) {
            if (starter != null) PlayerUtil.message(starter, "<yellow>No valid recipe to cook yet.");
            return;
        }
        // Prevent double-start
        if (session.cookingInProgress || session.cookTask != null) return;

        // Use configured cooking time (seconds) from session; fallback to 10 seconds
        int cookSeconds = 10;
        try { cookSeconds = Math.max(1, session.cookTimeSeconds); } catch (Throwable ignored) {}
        final int DURATION_TICKS = cookSeconds * 20;
        session.cookingInProgress = true;
        session.viewer = (starter == null ? null : starter.getUniqueId());
        // Start visuals (bossbar + preview already present)
        try { CookingVisuals.startProgressBar(session, starter, DURATION_TICKS); } catch (Throwable ignored) {}
        try { CookingVisuals.playStartEffects(session); } catch (Throwable ignored) {}

        // schedule completion task (cancel any previous)
        try { if (session.cookTask != null) session.cookTask.cancel(); } catch (Throwable ignored) {}

        session.cookTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // logging removed

                     // If the session has been destroyed (campfire broken), abort finalization entirely
                     if (session.destroyed) {
                         // logging removed
                         return;
                     }

                     // Mark cooked and unset in-progress
                     session.cooked = true;
                     session.cookingInProgress = false;

                     // Try to make any existing preview item pickable
                     try {
                         if (session.displayEntity != null && session.displayEntity.isValid()) {
                             try { session.displayEntity.setItemStack(session.food.clone()); } catch (Throwable ignored) {}
                             try { session.displayEntity.setGravity(true); } catch (Throwable ignored) {}
                             try { session.displayEntity.setPickupDelay(0); } catch (Throwable ignored) {}
                             try { session.displayEntity.setInvulnerable(false); } catch (Throwable ignored) {}
                             try { session.displayEntity.setUnlimitedLifetime(false); } catch (Throwable ignored) {}
                             try { removePreviewFromDisk(session); } catch (Throwable e) { /* ignore preview disk removal errors */ }
                         }
                     } catch (Throwable exPreview) {
                         // ignore preview exceptions
                     }

                     // Drop an explicit item at the campfire center as a guaranteed backup
                     try {
                         if (session.campfireLocation != null) {
                             Location dropLoc = session.campfireLocation.clone().add(0.5, 0.5, 0.5);
                             org.bukkit.entity.Item dropped = session.campfireLocation.getWorld().dropItem(dropLoc, session.food.clone());
                             try { dropped.getPersistentDataContainer().set(new NamespacedKey(Specialization.getInstance(), "cooking_result"), PersistentDataType.BOOLEAN, true); } catch (Throwable ignored) {}
                             try { dropped.setPickupDelay(0); } catch (Throwable ignored) {}
                             try { dropped.setInvulnerable(false); } catch (Throwable ignored) {}
                             try { dropped.setUnlimitedLifetime(false); } catch (Throwable ignored) {}
                         }
                     } catch (Throwable exDrop) {
                         // ignore drop errors
                     }

                     // Play finish sound and award XP to the starter only (if available)
                     try {
                         if (session.viewer != null) {
                             org.bukkit.entity.Player starterPlayer = Bukkit.getPlayer(session.viewer);
                             if (starterPlayer != null && starterPlayer.isOnline()) {
                                 CookingVisuals.playFinishEffects(session, starterPlayer);
                                 PlayerUtil.message(starterPlayer, "<green>Cooking complete! Your meal is ready.", 1);
                                 // Award configured farmer XP for this recipe
                                 try {
                                     int xpToAward = Math.max(0, session.cookExp);
                                     if (xpToAward > 0) {
                                         com.minecraftcivilizations.specialization.Player.CustomPlayer cp = com.minecraftcivilizations.specialization.Player.CustomPlayer.getCustomPlayer(starterPlayer);
                                         if (cp == null) {
                                             // fallback: try CoreUtil lookup
                                             try {
                                                 cp = com.minecraftcivilizations.specialization.util.CoreUtil.getPlayer(starterPlayer);
                                             } catch (Throwable ignored) {}
                                         }
                                         if (cp != null) {
                                             try {
                                                 cp.addSkillXp(com.minecraftcivilizations.specialization.Skill.SkillType.FARMER, xpToAward, starterPlayer.getLocation());
                                             } catch (Throwable addEx) {
                                                 // ignore XP awarding errors
                                             }
                                         } else {
                                             // no CustomPlayer found; XP cannot be awarded
                                         }
                                     }
                                 } catch (Throwable xpEx) {
                                     // ignore XP lookup errors
                                 }
                             }
                         }
                     } catch (Throwable ignored) {}

                 } catch (Throwable ex) {
                     // ignore finalization errors
                 } finally {
                    // Cleanup visuals and scheduled tasks, clear campfire slots and reset state
                    try { if (session.displayEntity != null && session.displayEntity.isValid()) { try { session.displayEntity.remove(); } catch (Throwable ignored) {} session.displayEntity = null; } } catch (Throwable ignored) {}
                    try { if (session.recipientDisplay != null) { try { session.recipientDisplay.remove(); } catch (Throwable ignored) {} session.recipientDisplay = null; } } catch (Throwable ignored) {}
                    // Clear the physical campfire slots so station is reset
                    try {
                        if (session.campfireLocation != null) {
                            Block cb = session.campfireLocation.getBlock();
                            if (cb.getType() == Material.CAMPFIRE) {
                                Campfire cstate = (Campfire) cb.getState();
                                for (int si = 0; si < 4; si++) cstate.setItem(si, null);
                                cstate.update(true);
                                // optionally ensure campfire is unlit
                                try {
                                    org.bukkit.block.data.type.Campfire cfdata = (org.bukkit.block.data.type.Campfire) cb.getBlockData();
                                    if (cfdata.isLit()) { cfdata.setLit(false); cb.setBlockData(cfdata); }
                                } catch (Throwable ignored) {}
                            }
                        }
                    } catch (Throwable ignored) {}

                    try { CookingVisuals.stopProgressBar(session); } catch (Throwable ignored) {}
                    try { if (session.progressTask != null) { session.progressTask.cancel(); session.progressTask = null; } } catch (Throwable ignored) {}
                    try { if (session.maintenanceTask != null) { session.maintenanceTask.cancel(); session.maintenanceTask = null; } } catch (Throwable ignored) {}
                    try { if (session.cookTask != null) { session.cookTask.cancel(); } } catch (Throwable ignored) {}
                    session.cookTask = null;
                    session.cookingInProgress = false;

                    // Play a world-level finish sound so nearby players hear it
                    try {
                        try { CookingVisuals.playWorldFinishSound(session); } catch (Throwable ignored) {}
                    } catch (Throwable ignored) {}

                    // Reset recipient and food so the station requires a new recipient to be placed for subsequent cooks
                    try {
                        session.recipient = null;
                        session.recipientId = null;
                        session.food = null;
                        session.cookExp = 0;
                    } catch (Throwable ignored) {}

                    // ensure cleanUp won't try to restore the previous lit state
                    try { session.wasLit = false; } catch (Throwable ignored) {}
                    // defensively remove any lingering cooking armor stand at this campfire (ensure no invisible stand remains)
                    try { removeCookingStandAt(session.campfireLocation); } catch (Throwable ignored) {}
                    // Fully clean up the session (remove displays and remove session from registry)
                    try { cleanUp(session, session.campfireLocation); } catch (Throwable ignored) {}
                 }
             }
         }.runTaskLater(Specialization.getInstance(), DURATION_TICKS);
    }

    private void removeCookingStandAt(Location loc) {
        if (loc == null) return;
        for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, 2.0, 2.0, 2.0)) {
            if (e instanceof ArmorStand as) {
                if (as.getPersistentDataContainer().has(STAND_KEY, PersistentDataType.BOOLEAN)) {
                    try {
                        as.getEquipment().setItemInMainHand(null);
                        as.remove();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}
