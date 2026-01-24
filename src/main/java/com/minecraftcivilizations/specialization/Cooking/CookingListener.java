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
                        checkCombination(session, player);
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
                            if (session.recipient != null) giveOrDrop(player, session.recipient.clone());
                            PlayerUtil.message(player, "<green>Returned recipient item from cooking station.");
                            CookingVisuals.cleanupVisuals(session);
                            // remove any armor stand visually tied to this campfire (defensive)
                            removeCookingStandAt(block.getLocation());
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
        } catch (Exception ex) {
            // If unexpected exceptions occur, log them for debugging
            Specialization.getInstance().getLogger().warning("[Cooking] Exception during sneak-interact: " + ex.getMessage());
            throw ex;
        }

        PlayerUtil.message(player, PlayerUtil.buildLogo() + " Interacting with campfire at " + block.getLocation());

        if (cookingSessions.containsKey(block.getLocation())) {
            CookingItemData data = cookingSessions.get(block.getLocation());
            PlayerUtil.message(player, "<gray>Active cooking session present at this campfire.");
            if (handItem.getType() == Material.AIR) {
                if (data.cooked) {
                    // remove only this session's recipient ItemDisplay so other campfires remain untouched
                    if (data.recipientDisplay != null) {
                        data.recipientDisplay.remove();
                        data.recipientDisplay = null;
                    }
                    // Drop the cooked item at the campfire center so it behaves like a normal drop (no inventory teleporting)
                    try {
                        // Drop the cooked item at the campfire center so it behaves like a normal drop (no inventory teleporting)
                        if (data.campfireLocation != null && data.food != null) {
                            Location dropLoc = data.campfireLocation.clone().add(0.5, 0.5, 0.5);
                            data.campfireLocation.getWorld().dropItemNaturally(dropLoc, data.food.clone());
                            // Play pickup/finish sound only for the collector
                            CookingVisuals.playItemGet(player);
                        } else if (data.food != null) {
                            // fallback: give to player if drop point missing
                            giveOrDrop(player, data.food.clone());
                        }
                    } catch (Exception e) {
                        Specialization.getInstance().getLogger().warning("[Cooking] Exception while collecting cooked item: " + e.getMessage());
                        throw e;
                    }

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
            s.setVisible(false);
            s.setInvisible(true);
            s.setGravity(false);
            s.setBasePlate(false);
            s.setArms(false);
            s.setSmall(true);
            s.getPersistentDataContainer().set(STAND_KEY, PersistentDataType.BOOLEAN, true);
        });

        CookingItemData data = new CookingItemData(stand, place.clone(), itemId);
        data.wasLit = originalLit;
        data.campfireLocation = block.getLocation();
        cookingSessions.put(block.getLocation(), data);
        // Spawn a visual ItemDisplay for the recipient (separate from the armor stand)
        CookingVisuals.spawnRecipientDisplay(data);
        // consume one recipient from the player's hand and prevent vanilla placement
        decrementPlayerHandBySlot(player, event.getHand());
        player.updateInventory();
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
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
                    stand.getEquipment().setHelmet(null);
                    stand.getEquipment().setItemInMainHand(null);
                    stand.getEquipment().setItemInOffHand(null);
                }
            }
        } catch (Exception e) {
            Specialization.getInstance().getLogger().warning("[Cooking] Error during stand-interact: " + e.getMessage());
            throw e;
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
                if (data.recipientDisplay != null) {
                    data.recipientDisplay.remove();
                    data.recipientDisplay = null;
                }
                // Drop the cooked item at the campfire center so it behaves like a normal drop (no inventory teleporting)
                try {
                    // Drop the cooked item at the campfire center so it behaves like a normal drop (no inventory teleporting)
                    if (data.campfireLocation != null && data.food != null) {
                        Location dropLoc = data.campfireLocation.clone().add(0.5, 0.5, 0.5);
                        data.campfireLocation.getWorld().dropItemNaturally(dropLoc, data.food.clone());
                        // Play pickup/finish sound only for the collector
                        CookingVisuals.playItemGet(player);
                    } else if (data.food != null) {
                        // fallback: give to player if drop point missing
                        giveOrDrop(player, data.food.clone());
                    }
                } catch (Exception e) {
                    Specialization.getInstance().getLogger().warning("[Cooking] Error while collecting cooked item (right-click): " + e.getMessage());
                    throw e;
                }

                cleanUp(data, stand.getLocation().subtract(0.5, 1, 0.5));
                event.setCancelled(true);
                return;
            }
            // If no ingredients have been placed yet, allow the owner to pick up the recipient preview
            if (data.ingredients.isEmpty()) {
                // No ownership: allow anyone to pick up the recipient before cooking
                giveOrDrop(player, data.recipient.clone());
                PlayerUtil.message(player, "<green>Picked up recipient item.");
                removeCookingStandAt(stand.getLocation().subtract(0.5, 1, 0.5));
                cleanUp(data, stand.getLocation().subtract(0.5, 1, 0.5));
                event.setCancelled(true);
                return;
            }
            // If there is no floating display entity yet, spawn it now (player explicit action)
            if (data.displayEntity == null) {
                // Spawn handled by CookingVisuals (will set data.displayEntity and viewer)
                data.viewer = player.getUniqueId();
                CookingVisuals.spawnOrUpdateDisplay(data);
                savePreviewToDisk(data);
                PlayerUtil.message(player, "<green>Preview spawned above campfire.");
                // do not return here — continue so a valid recipe can be started with a single hit

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
                    event.setCancelled(true);
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
                    event.setCancelled(true);
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
        resyncSessionFromCampfire(data);
        if (data == null) return;

        // if cooking in progress -> cancel cooking but DO NOT return items (player can remove them manually).
        if (isActiveCooking(data)) {
            stopCookingProcesses(data);
            CookingVisuals.playCancelEffects(data);
            PlayerUtil.message(player, "<yellow>Cooking cancelled.");
            event.setCancelled(true);
            return;
        }

        // Recompute recipe matching from authoritative campfire slots so a cancel + re-add cycle is recognized
        try {
            data.viewer = player.getUniqueId();
            // ensure the checker knows who the viewer is (so spawn messages/preview target the hitter)
            checkCombination(data, player);
        } catch (Exception e) {
            Specialization.getInstance().getLogger().warning("[Cooking] Error while recomputing recipe on hit: " + e.getMessage());
            throw e;
        }

        // Defensive re-check: if no resolved food, try once more after forcing viewer to this hitter
        if (data.food == null) {
            try {
                if (data.food == null) {
                    data.viewer = player.getUniqueId();
                    checkCombination(data, player);
                }
            } catch (Exception e) {
                Specialization.getInstance().getLogger().warning("[Cooking] Error during defensive re-check: " + e.getMessage());
                throw e;
            }
        }
        // If a preview display is missing but we have a resolved result item, spawn it so the hit action reflects the visible preview.
        if (data.displayEntity == null && data.food != null) {
            try { data.viewer = player.getUniqueId(); CookingVisuals.spawnOrUpdateDisplay(data); savePreviewToDisk(data); } catch (Exception ex) {
                Specialization.getInstance().getLogger().warning("[Cooking] Failed ensuring preview on hit: " + ex.getMessage());
                throw ex;
            }
        }
        // single hit start: if there's a preview result, start cooking; otherwise notify
        if (data.food == null) {
            PlayerUtil.message(player, PlayerUtil.buildLogo() + " <yellow>No valid recipe to cook yet. Add ingredients first.");
            return;
        }
        PlayerUtil.message(player, "<green>Starting cooking process...");
        startCooking(data, player);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock(); if (block.getType() != Material.CAMPFIRE) return;
        try { event.setDropItems(false); } catch (NoSuchMethodError ignored) {}
        // Prevent vanilla block-item drops (campfire slot items) from also dropping — we handle drops explicitly below.
        CookingItemData data = cookingSessions.get(block.getLocation());
        if (data == null) return;
        // Mark session destroyed first and cancel running processes to prevent finalization
        data.destroyed = true;
        data.cookExp = 0;
        data.viewer = null;
        stopCookingProcesses(data);

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
            if (data.recipient != null && data.recipient.getType() != Material.AIR) toDrop.add(data.recipient.clone());
            // add 2 charcoal as vanilla campfire breaking reward
            toDrop.add(new ItemStack(Material.CHARCOAL, 2));
            // Remove the session mapping first so our drops don't get cancelled by onItemSpawn
            cookingSessions.remove(block.getLocation());
            // Drop each collected stack as-is to preserve custom item data
            for (ItemStack stack : toDrop) {
                Location loc = dropLoc.clone().add((rnd.nextDouble()-0.5)*0.4, 0, (rnd.nextDouble()-0.5)*0.4);
                org.bukkit.entity.Item dropped = block.getWorld().dropItemNaturally(loc, stack);
                // ensure dropped items are immediately pickable after break
                dropped.setPickupDelay(10);
            }
            // remove any preview dropped entity (floating dropped item preview)
            if (data.displayEntity != null) {
                data.displayEntity.remove();
            }
            // ensure any ItemDisplay preview (recipient) near the campfire is removed as well
            NamespacedKey recipKey = new NamespacedKey(Specialization.getInstance(), "cooking_recipient");
            NamespacedKey prevKey = new NamespacedKey(Specialization.getInstance(), "cooking_preview");
            for (org.bukkit.entity.Entity ne : block.getWorld().getNearbyEntities(dropLoc, 0.4, 0.4, 0.4)) {
                if (ne instanceof org.bukkit.entity.ItemDisplay idisp) {
                    try {
                        if (idisp.getPersistentDataContainer().has(recipKey, PersistentDataType.STRING)) {
                            idisp.remove();
                        }
                    } catch (Exception e) {
                        Specialization.getInstance().getLogger().warning("[Cooking] Error while removing nearby ItemDisplay: " + e.getMessage());
                        throw e;
                    }
                }
                if (ne instanceof org.bukkit.entity.Item itemEnt) {
                    try {
                        if (itemEnt.getPersistentDataContainer().has(prevKey, PersistentDataType.BOOLEAN)) {
                            itemEnt.remove();
                        }
                    } catch (Exception e) {
                        Specialization.getInstance().getLogger().warning("[Cooking] Error while removing nearby Item preview: " + e.getMessage());
                        throw e;
                    }
                }
            }
        } catch (Exception e) {
            Specialization.getInstance().getLogger().warning("[Cooking] Error during block-break handling: " + e.getMessage());
            throw e;
        }

        try {
            if (data.stand != null) {
                try {
                    data.stand.getEquipment().setItemInMainHand(null);
                    data.stand.getEquipment().setItemInOffHand(null);
                } catch (Exception e) {
                    Specialization.getInstance().getLogger().warning("[Cooking] Error while clearing stand equipment: " + e.getMessage());
                    throw e;
                }
                CookingVisuals.playBreakEffects(data);
                data.stand.remove();
            }
            // Only remove the special cooking armor stands nearby; do NOT remove Item entities (we want drops to remain).
            Location center = block.getLocation().add(0.5, 0.5, 0.5);
            for (org.bukkit.entity.Entity e : block.getWorld().getNearbyEntities(center, 2.0, 2.0, 2.0)) {
                if (e instanceof ArmorStand as) {
                    if (as.getPersistentDataContainer().has(STAND_KEY, PersistentDataType.BOOLEAN)) {
                        try {
                            as.getEquipment().setItemInMainHand(null);
                            as.remove();
                        } catch (Exception ex) {
                            Specialization.getInstance().getLogger().warning("[Cooking] Error while removing nearby stand: " + ex.getMessage());
                            throw ex;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Specialization.getInstance().getLogger().warning("[Cooking] Error during block-break cleanup: " + e.getMessage());
            throw e;
        }

        try {
            event.setDropItems(false);
        } catch (NoSuchMethodError e) {
            Specialization.getInstance().getLogger().warning("[Cooking] setDropItems not available on this server API build: " + e.getMessage());
        }
        cleanUpKeepDrops(data, block.getLocation());
    }

    private void cleanUp(CookingItemData data, Location loc) {
        if (data == null) return;
        // remove display item if present
        if (data.displayEntity != null) {
            removePreviewFromDisk(data);
            data.displayEntity.remove();
            data.displayEntity = null;
        }

        if (data.recipientDisplay != null) {
            data.recipientDisplay.remove();
            data.recipientDisplay = null;
        }

        if (data.bossBar != null) {
            data.bossBar.removeAll();
            data.bossBar.setVisible(false);
            data.bossBar = null;
        }

        stopCookingProcesses(data);

        if (loc != null) cookingSessions.remove(loc);

        try {
            Block block = loc == null ? null : loc.getBlock();
            if (block != null && block.getType() == Material.CAMPFIRE) {
                org.bukkit.block.data.type.Campfire campfireData = (org.bukkit.block.data.type.Campfire) block.getBlockData();
                if (data.wasLit != campfireData.isLit()) {
                    campfireData.setLit(data.wasLit);
                    block.setBlockData(campfireData);
                }
                Campfire campfire = (Campfire) block.getState();
                for (int i = 0; i < 4; i++) campfire.setItem(i, null);
                campfire.update(true);
            }
        } catch (Exception e) {
            Specialization.getInstance().getLogger().warning("[Cooking] Error during cleanUp: " + e.getMessage());
            throw e;
        }
    }

    /**
     * Clean up session state and visuals but DO NOT remove dropped item entities from the world.
     * Used when a campfire block is broken and we've already dropped the items the player should receive.
     */
    private void cleanUpKeepDrops(CookingItemData data, Location loc) {
        if (data == null) return;
        // remove preview dropped item if present (the preview for the recipe)
        if (data.displayEntity != null) {
            removePreviewFromDisk(data);
            data.displayEntity.remove();
            data.displayEntity = null;
        }

        if (data.recipientDisplay != null) {
            data.recipientDisplay.remove();
            data.recipientDisplay = null;
        }

        if (data.bossBar != null) {
            data.bossBar.removeAll();
            data.bossBar.setVisible(false);
            data.bossBar = null;
        }

        if (data.stand != null) {
            if (data.stand.isValid()) {
                try { data.stand.getEquipment().setHelmet(null); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error clearing stand helmet: " + e.getMessage()); throw e; }
                try { data.stand.getEquipment().setItemInMainHand(null); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error clearing stand mainhand: " + e.getMessage()); throw e; }
            }
            data.stand.remove();
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
            } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error during cleanUpKeepDrops: " + e.getMessage()); throw e; }
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
            Specialization.getInstance().getLogger().warning("[Cooking] Failed to save preview config: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void removePreviewFromDisk(CookingItemData data) {
        if (data == null || data.campfireLocation == null) return;
        String key = data.campfireLocation.getWorld().getName() + ":" + data.campfireLocation.getBlockX() + "," + data.campfireLocation.getBlockY() + "," + data.campfireLocation.getBlockZ();
        previewsConfig.set(key, null);
        try {
            previewsConfig.save(previewsFile);
        } catch (IOException e) {
            Specialization.getInstance().getLogger().warning("[Cooking] Failed to remove preview from disk: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // Safely cancel any running cooking tasks and clear related flags so interaction is re-enabled
    private void stopCookingProcesses(CookingItemData data) {
        if (data == null) return;
        // Stop visuals first (boss bar) to ensure players don't keep a stuck bar
        CookingVisuals.stopProgressBar(data);
        // Cancel scheduled tasks
        if (data.progressTask != null) { data.progressTask.cancel(); }
        data.progressTask = null;
        if (data.cookTask != null) { data.cookTask.cancel(); }
        data.cookTask = null;
        if (data.maintenanceTask != null) { data.maintenanceTask.cancel(); }
        data.maintenanceTask = null;
        // Reset state flags so interactions are allowed again
        data.cookingInProgress = false;
        data.cooked = false;
        data.viewer = null;
        // Force-remove any boss bar reference (defensive)
        if (data.bossBar != null) {
            data.bossBar.removeAll();
            data.bossBar.setVisible(false);
            data.bossBar = null;
        }
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
                    s.setVisible(false);
                    s.setInvisible(true);
                    s.setGravity(false);
                    s.setBasePlate(false);
                    s.setSmall(true);
                    s.getPersistentDataContainer().set(STAND_KEY, PersistentDataType.BOOLEAN, true);
                });
                CookingItemData data = new CookingItemData(stand, stack == null ? null : stack.clone(), recipientId);
                data.campfireLocation = loc;
                data.food = stack == null ? null : stack.clone();
                data.cooked = cooked;
                cookingSessions.put(loc, data);
                // spawn preview dropped item
                if (stack != null) CookingVisuals.spawnOrUpdateDisplay(data);
            } catch (Exception ex) {
                Specialization.getInstance().getLogger().warning("[Cooking] Failed to load persisted preview: " + ex.getMessage());
                throw ex;
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
        } catch (Exception e) {
            Specialization.getInstance().getLogger().warning("[Cooking] Error while reading campfire slots: " + e.getMessage());
            throw e;
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
        } catch (Exception t) {
            Specialization.getInstance().getLogger().warning("[Cooking] Could not resolve CustomPlayer for " + player.getName() + ", defaulting farmer level to 0");
            throw t;
        }
        SkillLevel playerSkill = SkillLevel.getSkillLevelFromInt(playerLevelInt);

        // If player is below Farmer level 2, check whether the current ingredients WOULD match some recipe
        // at a higher tier; if so, inform the player that they need Farmer level 2 to cook.
        try {
            if (playerSkill.getLevel() < 2) {
                List<ConfigObject> allTiers = new ArrayList<>();
                for (SkillLevel t : SkillLevel.values()) {
                    String tk = "FARMER_" + t.name();
                    try { allTiers.addAll(cookingConfig.getObjectList(tk)); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error reading recipes for tier " + tk + ": " + e.getMessage()); throw e; }
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
                    } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error parsing recipe object: " + e.getMessage()); throw e; }
                }
                if (wouldMatch) {
                    try {
                        PlayerUtil.message(player, "<red>You need to be Farmer level 2 or higher to cook this dish!", 1);
                    } catch (Exception e) {
                        try { player.sendMessage("§cYou need to be Farmer level 2 or higher to cook this dish!"); } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[Cooking] Failed to send fallback message: " + ex.getMessage()); throw ex; }
                    }
                    return;
                }
            }
        } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error during level-check: " + e.getMessage()); throw e; }

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
                     CookingVisuals.spawnOrUpdateDisplay(data);
                     if (player.isOnline()) {
                        try {
                            // Play a player-local composter "empty" sound at pitch 0.5 so only the player hears it
                            player.playSound(player.getLocation(), Sound.BLOCK_COMPOSTER_EMPTY, SoundCategory.BLOCKS, 1.0f, 0.5f);
                        } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Failed to play composter sound: " + e.getMessage()); throw e; }
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
                 if (data.displayEntity != null) {
                     data.displayEntity.remove();
                     data.displayEntity = null;
                 }
                 if (data.bossBar != null) {
                     data.bossBar.removeAll();
                     data.bossBar.setVisible(false);
                     data.bossBar = null;
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
         } catch (Exception e) {
             Specialization.getInstance().getLogger().warning("[Cooking] Error resolving CraftEngine custom item: " + e.getMessage());
             throw e;
         }
         if (!made) {
             try {
                 com.minecraftcivilizations.specialization.CustomItem.CustomItem ci = com.minecraftcivilizations.specialization.CustomItem.CustomItemManager.getInstance().getCustomItem(resultId);
                 if (ci != null) {
                     data.food = ci.createItemStack(1);
                     made = true;
                 }
             } catch (Exception e) {
                 Specialization.getInstance().getLogger().warning("[Cooking] Error resolving legacy custom item: " + e.getMessage());
                 throw e;
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
            Specialization.getInstance().getLogger().info("[Cooking] refuse place: activeCooking=" + isActiveCooking(data) + " cooked=" + data.cooked);
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
            Specialization.getInstance().getLogger().info("[Cooking] Placed ingredient " + hand.getType() + " into campfire slot " + slot + " at " + block.getLocation());
            if (isSeasoning) {
                data.seasonings.add(toPlace);
                // spawn a simple particle effect to visualise seasoning
                CookingVisuals.playSeasoningEffect(block, toPlace);
            } else {
                data.ingredients.add(toPlace);
            }
            decrementPlayerHandBySlot(player, handSlot);
            player.updateInventory();
            return true;
        } catch (Exception ex) {
            Specialization.getInstance().getLogger().warning("[Cooking] Failed placing ingredient: " + ex.getMessage());
            throw ex;
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
                } catch (Exception e) {
                    Specialization.getInstance().getLogger().warning("[Cooking] Error removing spawned item: " + e.getMessage());
                    throw e;
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
                } catch (Exception e) {
                    Specialization.getInstance().getLogger().warning("[Cooking] Error removing preview from disk: " + e.getMessage());
                    throw e;
                }
                try {
                    d.displayEntity.remove();
                } catch (Exception e) {
                    Specialization.getInstance().getLogger().warning("[Cooking] Error removing preview entity: " + e.getMessage());
                    throw e;
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
            } catch (Exception e) {
                Specialization.getInstance().getLogger().warning("[Cooking] Error while matching stand to session: " + e.getMessage());
                throw e;
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
        } catch (Exception e) {
            Specialization.getInstance().getLogger().warning("[Cooking] Error resyncing session from campfire: " + e.getMessage());
            throw e;
        }
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
        try { cookSeconds = Math.max(1, session.cookTimeSeconds); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error reading cook time: " + e.getMessage()); throw e; }
        final int DURATION_TICKS = cookSeconds * 20;
        session.cookingInProgress = true;
        session.viewer = (starter == null ? null : starter.getUniqueId());
        // Start visuals (bossbar + preview already present)
        CookingVisuals.startProgressBar(session, starter, DURATION_TICKS);
        CookingVisuals.playStartEffects(session);

        // schedule completion task (cancel any previous)
        if (session.cookTask != null) try { session.cookTask.cancel(); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error cancelling previous cookTask: " + e.getMessage()); throw e; }

        session.cookTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Specialization.getInstance().getLogger().info("[Cooking DEBUG] finalize run for session at " + session.campfireLocation + " viewer=" + session.viewer + " food=" + (session.food==null?"null":session.food.getType().toString()) + " displayEntity=" + (session.displayEntity==null?"null":session.displayEntity.isValid()) + " destroyed=" + session.destroyed);

                    // If the session has been destroyed (campfire broken), abort finalization entirely
                    if (session.destroyed) {
                        Specialization.getInstance().getLogger().info("[Cooking] Session destroyed during cook; aborting finalization for " + session.campfireLocation);
                        return;
                    }

                    // Mark cooked and unset in-progress
                    session.cooked = true;
                    session.cookingInProgress = false;

                    // Try to make any existing preview item pickable
                    if (session.displayEntity != null && session.displayEntity.isValid()) {
                        session.displayEntity.setItemStack(session.food.clone());
                        session.displayEntity.setGravity(true);
                        session.displayEntity.setPickupDelay(0);
                        session.displayEntity.setInvulnerable(false);
                        session.displayEntity.setUnlimitedLifetime(false);
                        removePreviewFromDisk(session);
                        Specialization.getInstance().getLogger().info("[Cooking DEBUG] made existing preview pickable for session at " + session.campfireLocation);
                    }

                    // Drop an explicit item at the campfire center as a guaranteed backup
                    if (session.campfireLocation != null) {
                        Location dropLoc = session.campfireLocation.clone().add(0.5, 0.5, 0.5);
                        org.bukkit.entity.Item dropped = session.campfireLocation.getWorld().dropItem(dropLoc, session.food.clone());
                        try { dropped.getPersistentDataContainer().set(new NamespacedKey(Specialization.getInstance(), "cooking_result"), PersistentDataType.BOOLEAN, true); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Failed to tag dropped item: " + e.getMessage()); throw e; }
                        dropped.setPickupDelay(0);
                        dropped.setInvulnerable(false);
                        dropped.setUnlimitedLifetime(false);
                        Specialization.getInstance().getLogger().info("[Cooking DEBUG] explicitly dropped result item at " + dropLoc + " for session at " + session.campfireLocation);
                    }

                    // Play finish sound and award XP to the starter only (if available)
                    if (session.viewer != null) {
                        org.bukkit.entity.Player starterPlayer = Bukkit.getPlayer(session.viewer);
                        if (starterPlayer != null && starterPlayer.isOnline()) {
                            CookingVisuals.playFinishEffects(session, starterPlayer);
                            PlayerUtil.message(starterPlayer, "<green>Cooking complete! Your meal is ready.", 1);
                            // Award configured farmer XP for this recipe (with debug logging)
                            int xpToAward = Math.max(0, session.cookExp);
                            Specialization.getInstance().getLogger().info("[Cooking] cookExp for session at " + session.campfireLocation + " = " + xpToAward);
                            if (xpToAward > 0) {
                                com.minecraftcivilizations.specialization.Player.CustomPlayer cp = com.minecraftcivilizations.specialization.Player.CustomPlayer.getCustomPlayer(starterPlayer);
                                if (cp == null) {
                                    // fallback: try CoreUtil lookup
                                    cp = com.minecraftcivilizations.specialization.util.CoreUtil.getPlayer(starterPlayer);
                                }
                                if (cp == null) {
                                    Specialization.getInstance().getLogger().warning("[Cooking] Could not find CustomPlayer for " + starterPlayer.getName() + " - XP not awarded.");
                                } else {
                                    cp.addSkillXp(com.minecraftcivilizations.specialization.Skill.SkillType.FARMER, xpToAward, starterPlayer.getLocation());
                                    Specialization.getInstance().getLogger().info("[Cooking] Awarded " + xpToAward + " Farmer XP to " + starterPlayer.getName() + " new_farmer_level=" + cp.getSkillLevel(com.minecraftcivilizations.specialization.Skill.SkillType.FARMER));
                                }
                            }
                        }
                    }

                } catch (Exception ex) {
                    Specialization.getInstance().getLogger().warning("[Cooking] Error finalizing cooking session: " + ex.getMessage());
                    throw ex;
                } finally {
                    // Cleanup visuals and scheduled tasks, clear campfire slots and reset state
                    try { if (session.displayEntity != null && session.displayEntity.isValid()) { session.displayEntity.remove(); session.displayEntity = null; } } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error removing display entity: " + e.getMessage()); throw e; }
                    try { if (session.recipientDisplay != null) { session.recipientDisplay.remove(); session.recipientDisplay = null; } } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error removing recipient display: " + e.getMessage()); throw e; }
                    // Clear the physical campfire slots so station is reset
                    try {
                        if (session.campfireLocation != null) {
                            Block cb = session.campfireLocation.getBlock();
                            if (cb.getType() == Material.CAMPFIRE) {
                                Campfire cstate = (Campfire) cb.getState();
                                for (int si = 0; si < 4; si++) cstate.setItem(si, null);
                                cstate.update(true);
                                // optionally ensure campfire is unlit
                                org.bukkit.block.data.type.Campfire cfdata = (org.bukkit.block.data.type.Campfire) cb.getBlockData();
                                if (cfdata.isLit()) { cfdata.setLit(false); cb.setBlockData(cfdata); }
                            }
                        }
                    } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error clearing campfire slots: " + e.getMessage()); throw e; }

                    try { CookingVisuals.stopProgressBar(session); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error stopping progress bar: " + e.getMessage()); throw e; }
                    try { if (session.progressTask != null) { session.progressTask.cancel(); session.progressTask = null; } } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error cancelling progressTask: " + e.getMessage()); throw e; }
                    try { if (session.maintenanceTask != null) { session.maintenanceTask.cancel(); session.maintenanceTask = null; } } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error cancelling maintenanceTask: " + e.getMessage()); throw e; }
                    try { if (session.cookTask != null) { session.cookTask.cancel(); } } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error cancelling cookTask: " + e.getMessage()); throw e; }
                    session.cookTask = null;
                    session.cookingInProgress = false;

                    // Play a world-level finish sound so nearby players hear it
                    try {
                        CookingVisuals.playWorldFinishSound(session);
                    } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error playing world finish sound: " + e.getMessage()); throw e; }

                    // Reset recipient and food so the station requires a new recipient to be placed for subsequent cooks
                    try {
                        session.recipient = null;
                        session.recipientId = null;
                        session.food = null;
                        session.cookExp = 0;
                    } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error resetting session fields: " + e.getMessage()); throw e; }

                    // ensure cleanUp won't try to restore the previous lit state
                    try { session.wasLit = false; } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error clearing wasLit: " + e.getMessage()); throw e; }
                    // defensively remove any lingering cooking armor stand at this campfire (ensure no invisible stand remains)
                    try { removeCookingStandAt(session.campfireLocation); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error removing cooking stand: " + e.getMessage()); throw e; }
                    // Fully clean up the session (remove displays and remove session from registry)
                    try { cleanUp(session, session.campfireLocation); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error during final cleanUp: " + e.getMessage()); throw e; }
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
                    } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error removing cooking stand entity: " + e.getMessage()); throw e; }
                }
            }
        }
    }
}
