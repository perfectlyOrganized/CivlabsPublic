package com.minecraftcivilizations.specialization.Cooking;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.CoreUtil;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class CookingListener implements Listener {

    private final NamespacedKey COOKED_RESULT_KEY = new NamespacedKey(Specialization.getInstance(), "cooked_result");
    private final CookingSessionManager sessionManager = CookingSessionManager.getInstance();


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CAMPFIRE) return;

        sessionManager.ensureSessionFresh(block);
        Player player = event.getPlayer();
        ItemStack handItem = (event.getHand() == EquipmentSlot.OFF_HAND)
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();

        // Sneak-right-click: extract items
        if (player.isSneaking()) {
            if (event.getHand() == EquipmentSlot.OFF_HAND) return;
            handleSneakRightClick(event, block, player);
            return;
        }

        // Handle existing session
        if (sessionManager.hasSession(block.getLocation())) {
            handleExistingSession(event, block, player, handItem);
            return;
        }

        // Place new recipient
        handleNewRecipient(event, block, player, handItem);
    }

    private void handleSneakRightClick(PlayerInteractEvent event, Block block, Player player) {
        CookingItemData running = sessionManager.getSession(block.getLocation());

        // If food is cooked, sneak-right-click should collect the cooked item (same as normal pickup)
        if (running != null && running.cooked) {
            collectCookedItem(running, player, block.getLocation());
            event.setCancelled(true);
            return;
        }

        // Remove seasoning/sauce only BEFORE cooking starts (not after cooking is done)
        if (running != null && !CookingProcessManager.isActiveCooking(running) && !running.cooked &&
            ((running.seasonings != null && !running.seasonings.isEmpty()) ||
             (running.sauces != null && !running.sauces.isEmpty()))) {

            ItemStack removed = null;
            boolean isSauceRemoval = false;
            if (running.seasonings != null && !running.seasonings.isEmpty()) {
                removed = running.seasonings.get(0); // Don't remove yet, check first
            } else if (running.sauces != null && !running.sauces.isEmpty()) {
                removed = running.sauces.get(0); // Don't remove yet, check first
                isSauceRemoval = true;
            }

            if (removed != null && removed.getType() != Material.AIR) {
                // Check if sauce requires glass bottle to retrieve
                boolean requiresBottle = removed.getType() == Material.POTION || removed.getType() == Material.HONEY_BOTTLE;

                if (requiresBottle) {
                    // Check if player has glass bottle
                    if (!player.getInventory().contains(Material.GLASS_BOTTLE)) {
                        event.setCancelled(true);
                        return; // Can't take out without glass bottle
                    }
                    // Remove one glass bottle from inventory BEFORE giving sauce back
                    player.getInventory().removeItem(new ItemStack(Material.GLASS_BOTTLE, 1));
                    player.updateInventory();
                }

                // Now actually remove from the list
                if (isSauceRemoval) {
                    running.sauces.remove(0);
                    CookingVisuals.stopSauceParticles(running);
                } else {
                    running.seasonings.remove(0);
                    CookingVisuals.stopSeasoningParticles(running);
                }

                giveOrDrop(player, removed.clone());
                removeIdFromSession(running, removed);
                // Play sound for taking seasoning/sauce out (audible to all nearby players)
                block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.BLOCKS, 1.0f, 1.2f);
            }

            CookingSyncUtils.resyncSessionFromCampfire(running);
            CookingRecipeManager.checkCombination(running, player);
            if (running.food != null) {
                CookingVisuals.spawnOrUpdateDisplay(running);
                sessionManager.savePreviewToDisk(running);
            }
            event.setCancelled(true);
            return;
        }

        // Block extraction while meal inside
        if (CookingProcessManager.hasMealInside(running)) {
            event.setCancelled(true);
            return;
        }

        // Extract from campfire slots
        Campfire cf = (Campfire) block.getState();
        int slotToTake = -1;
        for (int i = 3; i >= 0; i--) {
            ItemStack s = cf.getItem(i);
            if (s != null && s.getType() != Material.AIR) {
                slotToTake = i;
                break;
            }
        }

        if (slotToTake != -1) {
            ItemStack taken = cf.getItem(slotToTake).clone();
            cf.setItem(slotToTake, null);
            cf.update(true);
            giveOrDrop(player, taken);

            // Play sound for taking item out (audible to all nearby players)
            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.BLOCKS, 1.0f, 1.2f);

            CookingItemData session = sessionManager.getSession(block.getLocation());
            if (session != null) {
                removeFromSessionLists(session, taken);
                CookingSyncUtils.resyncSessionFromCampfire(session);
                CookingRecipeManager.checkCombination(session, player);
                if (session.food != null) {
                    CookingVisuals.spawnOrUpdateDisplay(session);
                    sessionManager.savePreviewToDisk(session);
                }
            }
            event.setCancelled(true);
            return;
        }

        // Return recipient if empty
        CookingItemData session = sessionManager.getSession(block.getLocation());
        if (session != null) {
            boolean cfEmpty = isCampfireEmpty(cf);
            if (!CookingProcessManager.isActiveCooking(session) && cfEmpty &&
                session.ingredients.isEmpty() && session.seasonings.isEmpty()) {
                if (session.recipient != null) {
                    giveOrDrop(player, session.recipient.clone());
                    // Play sound for taking recipient out (audible to all nearby players)
                    block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.BLOCKS, 1.0f, 1.2f);
                }
                cf.update(true);
                CookingVisuals.cleanupVisuals(session);
                sessionManager.removeCookingStandAt(block.getLocation());
                sessionManager.cleanUp(session, block.getLocation());
            }
            event.setCancelled(true);
        }
    }

    private void handleExistingSession(PlayerInteractEvent event, Block block, Player player, ItemStack handItem) {
        CookingItemData data = sessionManager.getSession(block.getLocation());

        // Empty hand interactions
        if (handItem.getType() == Material.AIR) {
            handleEmptyHandInteraction(event, block, player, data);
            return;
        }

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);

        // Check seasoning/sauce FIRST (before ingredient) to properly handle items like specialization:salt
        if (CookingItemUtils.isAllowedSeasoning(handItem)) {
            handleSeasoningPlacement(event, block, player, handItem, data);
            return;
        }

        // Add ingredient (checked after seasoning)
        if (CookingItemUtils.isAllowedIngredient(handItem)) {
            if (CookingProcessManager.hasMealInside(data)) {
                event.setCancelled(true);
                return;
            }
            if (tryPlaceIngredient(block, handItem, data, player, event.getHand())) {
                CookingRecipeManager.checkCombination(data, player);
            }
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
    }

    private void handleEmptyHandInteraction(PlayerInteractEvent event, Block block, Player player, CookingItemData data) {
        // Check if other hand has useful item
        ItemStack other = (event.getHand() == EquipmentSlot.OFF_HAND)
            ? player.getInventory().getItemInMainHand()
            : player.getInventory().getItemInOffHand();
        if (other != null && other.getType() != Material.AIR) {
            if (CookingItemUtils.isAllowedSeasoning(other) || CookingItemUtils.isAllowedIngredient(other)) {
                return; // Let other hand handle it
            }
        }

        // Cancel cooking
        if (data.cookingInProgress) {
            handleCookingCancellation(event, player, data);
            return;
        }

        // Collect cooked item
        if (data.cooked) {
            collectCookedItem(data, player, block.getLocation());
        }
        event.setCancelled(true);
    }

    private void handleCookingCancellation(PlayerInteractEvent event, Player player, CookingItemData data) {
        // Check Farmer level - must be level 2+ to cancel cooking
        CustomPlayer cPlayer = CoreUtil.getPlayer(player);
        int farmerLevel = cPlayer != null ? cPlayer.getSkillLevel(SkillType.FARMER) : 0;
        if (farmerLevel < 2) {
            event.setCancelled(true);
            return;
        }

        boolean canCancel = data.viewer == null ? player.isOp() :
            data.viewer.equals(player.getUniqueId()) || player.isOp() ||
            player.hasPermission("specialization.cooking.cancel");

        if (canCancel) {
            List<ItemStack> savedSeasonings = cloneList(data.seasonings);
            List<ItemStack> savedSauces = cloneList(data.sauces);

            CookingProcessManager.stopCookingProcesses(data);

            data.seasonings = savedSeasonings;
            data.sauces = savedSauces;

            CookingVisuals.playCancelEffects(data);
            if (!data.seasonings.isEmpty()) CookingVisuals.startSeasoningParticles(data);
            if (!data.sauces.isEmpty()) CookingVisuals.startSauceParticles(data);

            CookingRecipeManager.checkCombination(data, player);
            if (data.displayEntity != null) CookingVisuals.spawnOrUpdateDisplay(data);
        }
        event.setCancelled(true);
    }

    private void handleSeasoningPlacement(PlayerInteractEvent event, Block block, Player player, ItemStack handItem, CookingItemData data) {
        CustomPlayer cPlayer = CoreUtil.getPlayer(player);
        boolean sauce = CookingItemUtils.isSauce(handItem);

        int requiredLevel = sauce ? 4 : 3;
        if (cPlayer.getSkillLevel(SkillType.FARMER) < requiredLevel) {
            event.setCancelled(true);
            return;
        }

        tryPlaceSeasoning(block, handItem, data, player, sauce, event.getHand());
        event.setCancelled(true);
    }

    private void handleNewRecipient(PlayerInteractEvent event, Block block, Player player, ItemStack handItem) {
        if (handItem.getType() == Material.AIR) return;

        String itemId = CookingItemUtils.getItemId(handItem);
        if (!SpecializationConfig.getCookingConfig().getStringList("possible_recipients").contains(itemId)) return;

        org.bukkit.block.data.type.Campfire campfireData = (org.bukkit.block.data.type.Campfire) block.getBlockData();
        if (campfireData.isLit()) {
            event.setCancelled(true);
            return;
        }

        ItemStack place = handItem.clone();
        place.setAmount(1);

        Location loc = block.getLocation().add(0.5, 0.0, 0.5);
        loc.setYaw(CookingSyncUtils.getCampfireFacingYaw(block, player.getLocation().getYaw()));

        ArmorStand stand = block.getWorld().spawn(loc, ArmorStand.class, s -> {
            s.setVisible(false);
            s.setInvisible(true);
            s.setGravity(false);
            s.setBasePlate(false);
            s.setArms(false);
            s.setSmall(true);
            s.setPersistent(false); // Don't persist - we manage persistence via YAML
            s.getPersistentDataContainer().set(sessionManager.getStandKey(), PersistentDataType.BOOLEAN, true);
        });

        CookingItemData data = new CookingItemData(stand, place.clone(), itemId);
        data.wasLit = campfireData.isLit();
        data.campfireLocation = block.getLocation();
        data.viewer = player.getUniqueId();
        data.recipient = place.clone(); // Store recipient for persistence
        sessionManager.putSession(block.getLocation(), data);
        sessionManager.saveSessionToDisk(data); // Save immediately so it persists across restarts

        CookingVisuals.spawnRecipientDisplay(data);

        // Play sound for placing recipient item (audible to all nearby players)
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.BLOCKS, 1.0f, 0.8f);

        decrementHand(player, event.getHand());
        player.updateInventory();

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) return;
        if (!stand.getPersistentDataContainer().has(sessionManager.getStandKey(), PersistentDataType.BOOLEAN)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        Location campLoc = stand.getLocation().subtract(0.5, 1, 0.5);
        CookingItemData data = sessionManager.getSessionForStand(stand);
        if (data == null) data = sessionManager.getSession(campLoc.getBlock().getLocation());

        CookingSyncUtils.resyncSessionFromCampfire(data);
        if (data == null) return;

        // Check Farmer level - must be level 2+ to interact with cooking
        CustomPlayer cPlayer = CoreUtil.getPlayer(player);
        int farmerLevel = cPlayer != null ? cPlayer.getSkillLevel(SkillType.FARMER) : 0;

        // Cancel cooking via left-click
        if (data.cookingInProgress) {
            // Only Farmer level 2+ can cancel cooking
            if (farmerLevel < 2) {
                event.setCancelled(true);
                return;
            }
            handleLeftClickCookingCancel(event, player, data);
            return;
        }

        // Start cooking - only Farmer level 2+ can start cooking
        if (!data.cooked && !CookingProcessManager.isActiveCooking(data) && data.food != null) {
            if (farmerLevel < 2) {
                event.setCancelled(true);
                return;
            }
            CookingRecipeManager.checkCombination(data, player);
            if (data.food != null) {
                CookingProcessManager.startCooking(data, player);
            }
            event.setCancelled(true);
            return;
        }

        // Collect cooked item via left-click
        if (data.cooked) {
            Location useLoc = data.campfireLocation != null ? data.campfireLocation : campLoc.getBlock().getLocation();
            collectCookedItem(data, player, useLoc);
        }
        event.setCancelled(true);
    }

    private void handleLeftClickCookingCancel(EntityDamageByEntityEvent event, Player player, CookingItemData data) {
        boolean canCancel = data.viewer == null ? player.isOp() :
            data.viewer.equals(player.getUniqueId()) || player.isOp() ||
            player.hasPermission("specialization.cooking.cancel");

        if (canCancel) {
            List<ItemStack> savedSeasonings = cloneList(data.seasonings);
            List<ItemStack> savedSauces = cloneList(data.sauces);

            CookingProcessManager.stopCookingProcesses(data);

            data.seasonings = savedSeasonings;
            data.sauces = savedSauces;

            CookingVisuals.playCancelEffects(data);
            if (!data.seasonings.isEmpty()) CookingVisuals.startSeasoningParticles(data);
            if (!data.sauces.isEmpty()) CookingVisuals.startSauceParticles(data);

            CookingRecipeManager.checkCombination(data, player);
            if (data.displayEntity != null) CookingVisuals.spawnOrUpdateDisplay(data);
        }
        event.setCancelled(true);
    }



    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.CAMPFIRE) return;

        CookingItemData data = sessionManager.getSession(block.getLocation());
        if (data == null) return;

        event.setDropItems(false);
        data.destroyed = true;
        data.cookExp = 0;
        data.viewer = null;
        CookingProcessManager.stopCookingProcesses(data);

        dropAllItems(block, data);

        CookingVisuals.stopSeasoningParticles(data);
        CookingVisuals.stopSauceParticles(data);

        if (data.displayEntity != null) {
            sessionManager.removePreviewFromDisk(data);
            data.displayEntity.remove();
        }

        if (data.stand != null) {
            data.stand.getEquipment().setItemInMainHand(null);
            data.stand.getEquipment().setItemInOffHand(null);
            CookingVisuals.playBreakEffects(data);
            data.stand.remove();
        }

        sessionManager.removeCookingStandAt(block.getLocation());
        sessionManager.cleanUpKeepDrops(data, block.getLocation());
    }



    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        org.bukkit.entity.Item it = event.getEntity();
        ItemStack stack = it.getItemStack();
        Location loc = it.getLocation();

        for (Map.Entry<Location, CookingItemData> entry : sessionManager.getSessions().entrySet()) {
            Location campLoc = entry.getKey();
            if (!campLoc.getWorld().equals(loc.getWorld())) continue;
            if (campLoc.distanceSquared(loc) > 4.0) continue;

            CookingItemData d = entry.getValue();
            if (d == null || d.destroyed) continue;

            String spawnedId = "minecraft:" + stack.getType().name().toLowerCase();
            boolean matches = d.ingredients.stream()
                .anyMatch(i -> ("minecraft:" + i.getType().name().toLowerCase()).equals(spawnedId));

            if (matches) {
                event.setCancelled(true);
                it.remove();
                return;
            }
        }
    }

    @EventHandler
    public void onEntityPickup(EntityPickupItemEvent event) {
        org.bukkit.entity.Item item = event.getItem();

        // Check preview pickups
        for (CookingItemData d : sessionManager.getSessions().values()) {
            if (d == null || d.displayEntity == null) continue;
            if (d.displayEntity.getUniqueId().equals(item.getUniqueId())) {
                if (!d.cooked) {
                    event.setCancelled(true);
                    return;
                }
                handlePreviewPickup(d);
                return;
            }
        }

        // Check cooked result pickup
        if (item.getPersistentDataContainer().has(COOKED_RESULT_KEY, PersistentDataType.STRING)) {
            handleCookedResultPickup(item);
        }
    }



    private void collectCookedItem(CookingItemData data, Player player, Location loc) {
        if (data.recipientDisplay != null) {
            data.recipientDisplay.remove();
            data.recipientDisplay = null;
        }
        if (data.displayEntity != null) {
            CookingVisuals.stopSeasoningParticles(data);
            CookingVisuals.stopSauceParticles(data);
            sessionManager.removePreviewFromDisk(data);
            data.displayEntity.remove();
            data.displayEntity = null;
        }

        if (data.campfireLocation != null && data.food != null) {
            dropCookedResult(data);
        } else if (data.food != null) {
            // Apply seasoning/sauce effects to the food first (stored in PDC)
            ItemStack foodWithEffects = CookingEffectManager.applyEffects(
                data.food.clone(),
                data.seasonings,
                data.sauces,
                data.burnt,
                data.nativeEffects,
                data.bonusExp
            );
            // Apply lore after effects so it can read stored effects
            ItemStack foodWithLore = CookingLoreUtils.applyLore(
                foodWithEffects,
                data.viewer,
                data.seasonings,
                data.sauces,
                data.burnt
            );
            giveOrDrop(player, foodWithLore);
        }

        CookingVisuals.playWorldFinishSound(data, !data.burnt);

        // Play fire extinguish sound for burnt food
        if (data.burnt && data.campfireLocation != null) {
            data.campfireLocation.getWorld().playSound(
                data.campfireLocation.clone().add(0.5, 0.5, 0.5),
                Sound.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.BLOCKS,
                1.0f,
                0.8f
            );
        }

        awardXp(data);

        if (data.burnTask != null) {
            data.burnTask.cancel();
            data.burnTask = null;
        }

        sessionManager.removeCookingStandAt(loc);
        sessionManager.cleanUp(data, loc);
    }

    private org.bukkit.entity.Item dropCookedResult(CookingItemData data) {
        if (data == null || data.food == null) return null;

        Location dropLoc = data.campfireLocation != null
            ? data.campfireLocation.clone().add(0.5, 0.5, 0.5)
            : (data.stand != null ? data.stand.getLocation().clone().add(0, 0.5, 0) : null);
        if (dropLoc == null) return null;

        // Apply seasoning/sauce effects to the food first (stored in PDC)
        ItemStack foodWithEffects = CookingEffectManager.applyEffects(
            data.food.clone(),
            data.seasonings,
            data.sauces,
            data.burnt,
            data.nativeEffects,
            data.bonusExp
        );

        // Apply lore after effects so it can read stored effects
        ItemStack foodWithLore = CookingLoreUtils.applyLore(
            foodWithEffects,
            data.viewer,
            data.seasonings,
            data.sauces,
            data.burnt
        );

        org.bukkit.entity.Item dropped = dropLoc.getWorld().dropItem(dropLoc, foodWithLore);
        dropped.setVelocity(new Vector(0, 0, 0));
        dropped.setGravity(true);
        dropped.setPickupDelay(10);

        if (data.campfireLocation != null) {
            dropped.getPersistentDataContainer().set(COOKED_RESULT_KEY, PersistentDataType.STRING,
                data.campfireLocation.getWorld().getName() + ":" +
                data.campfireLocation.getBlockX() + "," +
                data.campfireLocation.getBlockY() + "," +
                data.campfireLocation.getBlockZ());
        }

        // Anchor item
        final org.bukkit.entity.Item anchor = dropped;
        final Location anchorLoc = dropLoc.clone();
        new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (anchor.isDead() || !anchor.isValid()) { cancel(); return; }
                anchor.teleport(anchorLoc);
                anchor.setVelocity(new Vector(0, 0, 0));
                if (++t > 8) cancel();
            }
        }.runTaskTimer(Specialization.getInstance(), 0L, 1L);

        return dropped;
    }

    private boolean tryPlaceIngredient(Block block, ItemStack hand, CookingItemData data, Player player, EquipmentSlot handSlot) {
        Campfire cf = (Campfire) block.getState();
        int slot = -1;
        for (int i = 0; i < 4; i++) {
            if (cf.getItem(i) == null || cf.getItem(i).getType().isAir()) {
                slot = i;
                break;
            }
        }
        if (slot == -1) return false;

        ItemStack toPlace = hand.clone();
        toPlace.setAmount(1);
        cf.setItem(slot, toPlace);
        cf.update(true);

        if (data.ingredients == null) data.ingredients = new ArrayList<>();
        data.ingredients.add(toPlace);
        if (data.ingredientIds == null) data.ingredientIds = new ArrayList<>();
        data.ingredientIds.add(CookingItemUtils.getItemId(toPlace));

        // Play sound for adding ingredient (audible to all nearby players)
        block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.BLOCKS, 1.0f, 1.0f);

        decrementHand(player, handSlot);
        player.updateInventory();
        return true;
    }

    private void tryPlaceSeasoning(Block block, ItemStack hand, CookingItemData data, Player player, boolean sauce, EquipmentSlot handSlot) {
        // Count existing
        int existingCount = sauce
            ? (data.sauces == null ? 0 : data.sauces.size())
            : (data.seasonings == null ? 0 : data.seasonings.size());
        if (existingCount >= 1) return;

        // Check meal state
        if (CookingProcessManager.hasMealInside(data)) {
            if (!(data.cookingInProgress && sauce)) return;
        }

        // Require valid recipe preview
        if (!CookingProcessManager.isActiveCooking(data) && data.food == null) return;

        // Check campfire state
        org.bukkit.block.data.type.Campfire blockData = (org.bukkit.block.data.type.Campfire) block.getBlockData();
        if (blockData.isLit()) return;

        ItemStack toPlace = hand.clone();
        toPlace.setAmount(1);

        if (sauce) {
            if (data.sauces == null) data.sauces = new ArrayList<>();
            data.sauces.add(toPlace);
            if (data.sauceIds == null) data.sauceIds = new ArrayList<>();
            data.sauceIds.add(CookingItemUtils.getItemId(toPlace));
            CookingVisuals.playSauceEffect(block, toPlace);
            CookingVisuals.startSauceParticles(data);
        } else {
            if (data.seasonings == null) data.seasonings = new ArrayList<>();
            data.seasonings.add(toPlace);
            if (data.seasoningIds == null) data.seasoningIds = new ArrayList<>();
            data.seasoningIds.add(CookingItemUtils.getItemId(toPlace));
            CookingVisuals.playSeasoningEffect(block, toPlace);
            CookingVisuals.startSeasoningParticles(data);
        }

        CookingRecipeManager.checkCombination(data, player);
        if (data.displayEntity != null) CookingVisuals.spawnOrUpdateDisplay(data);

        decrementHand(player, handSlot);
        player.updateInventory();
    }

    private void dropAllItems(Block block, CookingItemData data) {
        Campfire cf = (Campfire) block.getState();
        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
        Random rnd = new Random();
        List<ItemStack> toDrop = new ArrayList<>();

        for (int i = 0; i < 4; i++) {
            ItemStack s = cf.getItem(i);
            if (s != null && s.getType() != Material.AIR) toDrop.add(s.clone());
        }
        if (data.recipient != null && data.recipient.getType() != Material.AIR) toDrop.add(data.recipient.clone());
        if (data.cooked && data.food != null && data.food.getType() != Material.AIR) toDrop.add(data.food.clone());
        toDrop.add(new ItemStack(Material.CHARCOAL, 2));

        sessionManager.removeSession(block.getLocation());

        for (ItemStack stack : toDrop) {
            Location locDrop = dropLoc.clone().add((rnd.nextDouble() - 0.5) * 0.4, 0, (rnd.nextDouble() - 0.5) * 0.4);
            org.bukkit.entity.Item dropped = block.getWorld().dropItemNaturally(locDrop, stack);
            dropped.setPickupDelay(10);
        }

        dropAdditionalItems(data, dropLoc, toDrop, rnd);
    }

    private void dropAdditionalItems(CookingItemData data, Location dropLoc, List<ItemStack> alreadyDropped, Random rnd) {
        dropListIfNotExists(data.ingredients, dropLoc, alreadyDropped, rnd);
        dropListIfNotExists(data.seasonings, dropLoc, alreadyDropped, rnd);
        // Don't drop sauces that are potions/honey bottles - player already got empty bottle back when placing
        dropSaucesIfNotBottle(data.sauces, dropLoc, alreadyDropped, rnd);
    }

    private void dropSaucesIfNotBottle(List<ItemStack> sauces, Location dropLoc, List<ItemStack> alreadyDropped, Random rnd) {
        if (sauces == null) return;
        for (ItemStack item : sauces) {
            if (item == null || item.getType() == Material.AIR) continue;
            // Skip potions and honey bottles - player already received empty glass bottle
            if (item.getType() == Material.POTION || item.getType() == Material.HONEY_BOTTLE) continue;
            boolean exists = alreadyDropped.stream().anyMatch(s -> s != null && s.isSimilar(item));
            if (!exists) {
                Location loc = dropLoc.clone().add((rnd.nextDouble() - 0.5) * 0.4, 0, (rnd.nextDouble() - 0.5) * 0.4);
                org.bukkit.entity.Item d = dropLoc.getWorld().dropItemNaturally(loc, item.clone());
                d.setPickupDelay(10);
            }
        }
    }

    private void dropListIfNotExists(List<ItemStack> items, Location dropLoc, List<ItemStack> alreadyDropped, Random rnd) {
        if (items == null) return;
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            boolean exists = alreadyDropped.stream().anyMatch(s -> s != null && s.isSimilar(item));
            if (!exists) {
                Location loc = dropLoc.clone().add((rnd.nextDouble() - 0.5) * 0.4, 0, (rnd.nextDouble() - 0.5) * 0.4);
                org.bukkit.entity.Item d = dropLoc.getWorld().dropItemNaturally(loc, item.clone());
                d.setPickupDelay(10);
            }
        }
    }

    private void handlePreviewPickup(CookingItemData d) {
        sessionManager.removePreviewFromDisk(d);

        if (!d.burnt && d.viewer != null) {
            Player starter = Bukkit.getPlayer(d.viewer);
            if (starter != null && starter.isOnline()) {
                CookingVisuals.playFinishEffects(d, starter, true);
            }
        }
        CookingVisuals.playWorldFinishSound(d, !d.burnt);

        // Play fire extinguish sound for burnt food
        if (d.burnt && d.campfireLocation != null) {
            d.campfireLocation.getWorld().playSound(
                d.campfireLocation.clone().add(0.5, 0.5, 0.5),
                Sound.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.BLOCKS,
                1.0f,
                0.8f
            );
        }

        awardXp(d);

        if (d.burnTask != null) { d.burnTask.cancel(); d.burnTask = null; }
        CookingVisuals.stopSeasoningParticles(d);
        CookingVisuals.stopSauceParticles(d);

        if (d.campfireLocation != null) sessionManager.removeCookingStandAt(d.campfireLocation);
        d.displayEntity = null;
        sessionManager.cleanUp(d, d.campfireLocation);
    }

    private void handleCookedResultPickup(org.bukkit.entity.Item item) {
        String locKey = item.getPersistentDataContainer().get(COOKED_RESULT_KEY, PersistentDataType.STRING);
        if (locKey == null) return;

        String[] parts = locKey.split(":", 2);
        if (parts.length != 2) return;
        String[] coords = parts[1].split(",");
        if (coords.length != 3) return;

        World w = Bukkit.getWorld(parts[0]);
        if (w == null) return;

        Location campLoc = new Location(w, Integer.parseInt(coords[0]), Integer.parseInt(coords[1]), Integer.parseInt(coords[2]));
        CookingItemData session = sessionManager.getSession(campLoc);

        if (session != null) {
            // Play fire extinguish sound for burnt food
            if (session.burnt && session.campfireLocation != null) {
                session.campfireLocation.getWorld().playSound(
                    session.campfireLocation.clone().add(0.5, 0.5, 0.5),
                    Sound.BLOCK_FIRE_EXTINGUISH,
                    SoundCategory.BLOCKS,
                    1.0f,
                    0.8f
                );
            }

            awardXp(session);
            if (session.burnTask != null) { session.burnTask.cancel(); session.burnTask = null; }
            CookingVisuals.stopSeasoningParticles(session);
            CookingVisuals.stopSauceParticles(session);

            Location useLoc = session.campfireLocation != null ? session.campfireLocation : campLoc;
            sessionManager.removeCookingStandAt(useLoc);
            sessionManager.cleanUp(session, useLoc);
        }
    }

    private void awardXp(CookingItemData data) {
        int xpToAward = Math.max(0, data.cookExp);
        if (data.burnt) xpToAward = Math.max(0, Math.round(xpToAward * 0.4f));

        if (xpToAward > 0 && data.viewer != null) {
            Player starter = Bukkit.getPlayer(data.viewer);
            if (starter != null && starter.isOnline()) {
                CustomPlayer cp = CustomPlayer.getCustomPlayer(starter);
                if (cp == null) cp = CoreUtil.getPlayer(starter);
                if (cp != null) cp.addSkillXp(SkillType.FARMER, xpToAward, starter.getLocation());
            }
        }
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType() == Material.AIR) return;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        for (ItemStack rem : leftover.values()) {
            if (rem != null) player.getWorld().dropItemNaturally(player.getLocation().add(0, 2, 0), rem);
        }
    }

    private void decrementHand(Player player, EquipmentSlot handSlot) {
        ItemStack item = handSlot == EquipmentSlot.OFF_HAND
            ? player.getInventory().getItemInOffHand()
            : player.getInventory().getItemInMainHand();

        Material itemType = item.getType();
        boolean returnsBottle = itemType == Material.POTION || itemType == Material.HONEY_BOTTLE;

        int newAmt = Math.max(0, item.getAmount() - 1);
        if (newAmt == 0) {
            if (handSlot == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(returnsBottle ? new ItemStack(Material.GLASS_BOTTLE) : null);
            } else {
                player.getInventory().setItemInMainHand(returnsBottle ? new ItemStack(Material.GLASS_BOTTLE) : null);
            }
        } else {
            item.setAmount(newAmt);
            // Give back glass bottle if potion/honey was used
            if (returnsBottle) {
                ItemStack bottle = new ItemStack(Material.GLASS_BOTTLE);
                if (player.getInventory().firstEmpty() != -1) {
                    player.getInventory().addItem(bottle);
                } else {
                    player.getWorld().dropItemNaturally(player.getLocation(), bottle);
                }
            }
        }
    }

    private void removeIdFromSession(CookingItemData session, ItemStack removed) {
        String rid = CookingItemUtils.getItemId(removed);
        if (rid == null) return;
        removeFirstOccurrence(session.seasoningIds, rid);
        removeFirstOccurrence(session.sauceIds, rid);
    }

    private void removeFromSessionLists(CookingItemData session, ItemStack taken) {
        removeFirstSimilar(session.ingredients, taken);
        removeFirstSimilar(session.seasonings, taken);

        String tid = CookingItemUtils.getItemId(taken);
        if (tid != null) {
            removeFirstOccurrence(session.ingredientIds, tid);
            removeFirstOccurrence(session.seasoningIds, tid);
            removeFirstOccurrence(session.sauceIds, tid);
        }
    }

    private void removeFirstSimilar(List<ItemStack> list, ItemStack item) {
        if (list == null || item == null) return;
        Iterator<ItemStack> it = list.iterator();
        while (it.hasNext()) {
            ItemStack s = it.next();
            if (s != null && s.isSimilar(item)) { it.remove(); return; }
        }
    }

    private void removeFirstOccurrence(List<String> list, String value) {
        if (list == null || value == null) return;
        for (int i = 0; i < list.size(); i++) {
            if (value.equals(list.get(i))) { list.remove(i); return; }
        }
    }

    private boolean isCampfireEmpty(Campfire cf) {
        for (int i = 0; i < 4; i++) {
            ItemStack it = cf.getItem(i);
            if (it != null && !it.getType().isAir()) return false;
        }
        return true;
    }

    private List<ItemStack> cloneList(List<ItemStack> source) {
        List<ItemStack> result = new ArrayList<>();
        if (source != null) {
            for (ItemStack s : source) {
                if (s != null) result.add(s.clone());
            }
        }
        return result;
    }


    // Clean up orphaned cooking entities when chunks are loaded (e.g., after rejoin)
    // Any cooking entity not linked to an active session is considered orphaned and removed.
    // This works because loadPersistedPreviews() creates fresh entities at server start.

    private static final NamespacedKey PREVIEW_KEY = new NamespacedKey(Specialization.getInstance(), "cooking_preview");
    private static final NamespacedKey RECIPIENT_KEY = new NamespacedKey(Specialization.getInstance(), "cooking_recipient");

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (org.bukkit.entity.Entity entity : event.getEntities()) {
            // Check for orphaned preview Items - remove if not in active session
            if (entity instanceof Item item) {
                if (item.getPersistentDataContainer().has(PREVIEW_KEY, PersistentDataType.BOOLEAN)) {
                    if (!isEntityInActiveSession(item)) {
                        item.remove();
                    }
                }
            }
            // Check for orphaned recipient ItemDisplays - remove if not in active session
            else if (entity instanceof ItemDisplay itemDisplay) {
                if (itemDisplay.getPersistentDataContainer().has(RECIPIENT_KEY, PersistentDataType.STRING)) {
                    if (!isRecipientDisplayInActiveSession(itemDisplay)) {
                        itemDisplay.remove();
                    }
                }
            }
            // Check for orphaned cooking ArmorStands - remove if not in active session
            else if (entity instanceof ArmorStand stand) {
                if (stand.getPersistentDataContainer().has(sessionManager.getStandKey(), PersistentDataType.BOOLEAN)) {
                    if (!isStandInActiveSession(stand)) {
                        stand.remove();
                    }
                }
            }
        }
    }


    private boolean isEntityInActiveSession(Item item) {
        for (CookingItemData data : sessionManager.getSessions().values()) {
            if (data != null && data.displayEntity != null &&
                data.displayEntity.getUniqueId().equals(item.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isRecipientDisplayInActiveSession(ItemDisplay display) {
        for (CookingItemData data : sessionManager.getSessions().values()) {
            if (data != null && data.recipientDisplay != null &&
                data.recipientDisplay.getUniqueId().equals(display.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    private boolean isStandInActiveSession(ArmorStand stand) {
        for (CookingItemData data : sessionManager.getSessions().values()) {
            if (data != null && data.stand != null &&
                data.stand.getUniqueId().equals(stand.getUniqueId())) {
                return true;
            }
        }
        return false;
    }
}
