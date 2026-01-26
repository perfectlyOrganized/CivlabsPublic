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
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class CookingListener implements Listener {

    private final NamespacedKey STAND_KEY = new NamespacedKey(Specialization.getInstance(), "cooking_stand");
    private final NamespacedKey COOKED_RESULT_KEY = new NamespacedKey(Specialization.getInstance(), "cooked_result");

    private final Map<Location, CookingItemData> cookingSessions = new HashMap<>();
    private final File previewsFile = new File(Specialization.getInstance().getDataFolder(), "cooking_previews.yml");
    private final FileConfiguration previewsConfig = YamlConfiguration.loadConfiguration(previewsFile);

    /**
     * Compute a 90-degree aligned yaw based on campfire facing (NORTH/SOUTH/EAST/WEST).
     * If block is null or not directional, return fallbackYaw.
     */
    private float getCampfireFacingYaw(Block block, float fallbackYaw) {
        if (block == null) return fallbackYaw;
        org.bukkit.block.data.BlockData bd = block.getBlockData();
        if (bd instanceof org.bukkit.block.data.Directional) {
            org.bukkit.block.data.Directional dir = (org.bukkit.block.data.Directional) bd;
            switch (dir.getFacing()) {
                case NORTH:
                    return 180f;
                case SOUTH:
                    return 0f;
                case WEST:
                    return 90f;
                case EAST:
                    return -90f;
                default:
                    return fallbackYaw;
            }
        }
        return fallbackYaw;
    }

    private boolean ensureSessionFresh(Block block) {
        if (block == null || block.getType() != Material.CAMPFIRE) return true;
        CookingItemData session = cookingSessions.get(block.getLocation());
        if (session == null) return true;
        // If session has active cooking, cooked item, preview, recipient display or stand -> it's fresh
        boolean standValid = session.stand != null && session.stand.isValid();
        boolean hasPreview = session.displayEntity != null && session.displayEntity.isValid();
        boolean hasRecipientDisplay = session.recipientDisplay != null && session.recipientDisplay.isValid();
        boolean campfireHasItems = false;
        Campfire cf = (Campfire) block.getState();
        for (int i = 0; i < 4; i++) { ItemStack it = cf.getItem(i); if (it != null && !it.getType().isAir()) { campfireHasItems = true; break; } }
        if (session.cookingInProgress || session.cooked || session.burnt || standValid || hasPreview || hasRecipientDisplay || campfireHasItems) return true;
        // Otherwise it's stale -> clean up and allow interactions
        cleanUp(session, block.getLocation());
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CAMPFIRE) return;
        // Clean up any stale session data for this campfire before processing interaction
        ensureSessionFresh(block);
        Player player = event.getPlayer();
        // Determine the actual ItemStack in the interacting hand (event.getItem can be null for off-hand)
        ItemStack handItem = (event.getHand() == EquipmentSlot.OFF_HAND) ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();

        // previous diagnostic logging was removed

        // Sneak-right-click to extract the top-most (last) non-empty campfire slot item and give to player
        if (player.isSneaking()) {
            // PlayerInteractEvent can fire for both hands (main and off). Only handle extraction on MAIN_HAND
            if (event.getHand() == EquipmentSlot.OFF_HAND) return;
            // Do not allow extraction or modifications while a meal is inside (cooking/ready/burnt)
            CookingItemData running = cookingSessions.get(block.getLocation());
            if (hasMealInside(running)) {
                PlayerUtil.message(player, "<red>Cannot add or remove items while a meal is inside.");
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
                // persist the change so clients/vanilla logic observe the removal
                cfSneak.update(true);
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
                        // ensure we persist any slot clears (defensive)
                        cfSneak.update(true);
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

        if (cookingSessions.containsKey(block.getLocation())) {
            CookingItemData data = cookingSessions.get(block.getLocation());
            // debug message removed
            if (handItem.getType() == Material.AIR) {
                if (data.cooked) {
                    // remove only this session's recipient ItemDisplay so other campfires remain untouched
                    if (data.recipientDisplay != null) {
                        data.recipientDisplay.remove();
                        data.recipientDisplay = null;
                    }
                    // remove preview entity and persisted preview to avoid duplicate/resurrected previews
                    if (data.displayEntity != null) {
                        removePreviewFromDisk(data);
                        data.displayEntity.remove();
                        data.displayEntity = null;
                    }
                    // Drop the cooked item at the campfire center so it behaves like a normal drop (no inventory teleporting)
                    if (data.campfireLocation != null && data.food != null) {
                        Location dropLoc = data.campfireLocation.clone().add(0.5, 0.5, 0.5);
                        org.bukkit.entity.Item dropped = data.campfireLocation.getWorld().dropItemNaturally(dropLoc, data.food.clone());
                        dropped.getPersistentDataContainer().set(COOKED_RESULT_KEY, PersistentDataType.STRING,
                                data.campfireLocation.getWorld().getName()+":"+data.campfireLocation.getBlockX()+","+data.campfireLocation.getBlockY()+","+data.campfireLocation.getBlockZ());
                        // Play pickup/finish sound only for the collector and play world sound for nearby players if not burnt
                        if (!data.burnt) {
                            if (data.viewer != null) {
                                org.bukkit.entity.Player starter = Bukkit.getPlayer(data.viewer);
                                if (starter != null && starter.isOnline()) {
                                    CookingVisuals.playFinishEffects(data, starter, true);
                                }
                            }
                            CookingVisuals.playWorldFinishSound(data, true);
                        } else {
                            // burnt: play fail sound for starter
                            if (data.viewer != null) {
                                org.bukkit.entity.Player starter = Bukkit.getPlayer(data.viewer);
                                if (starter != null && starter.isOnline()) CookingVisuals.playFinishEffects(data, starter, false);
                            }
                            CookingVisuals.playWorldFinishSound(data, false);
                        }
                    } else if (data.food != null) {
                        // fallback: give to player if drop point missing
                        giveOrDrop(player, data.food.clone());
                    }

                    // Award XP to the player who started the cooking (if configured)
                    int xpToAward = Math.max(0, data.cookExp);
                    if (data.burnt) xpToAward = Math.max(0, Math.round(xpToAward * 0.4f));
                    if (xpToAward > 0 && data.viewer != null) {
                        org.bukkit.entity.Player starter = Bukkit.getPlayer(data.viewer);
                        if (starter != null && starter.isOnline()) {
                            com.minecraftcivilizations.specialization.Player.CustomPlayer cp = com.minecraftcivilizations.specialization.Player.CustomPlayer.getCustomPlayer(starter);
                            if (cp == null) cp = com.minecraftcivilizations.specialization.util.CoreUtil.getPlayer(starter);
                            if (cp != null) cp.addSkillXp(com.minecraftcivilizations.specialization.Skill.SkillType.FARMER, xpToAward, starter.getLocation());
                        }
                    }

                    // Cancel any pending burn task to avoid double-drops
                    if (data.burnTask != null) { data.burnTask.cancel(); data.burnTask = null; }

                    // remove the invisible armor stand tied to this campfire
                    removeCookingStandAt(block.getLocation());

                    // Use the campfire block location (we're inside onPlayerInteract, 'stand' isn't available here)
                    cleanUp(data, block.getLocation());
                }
                event.setCancelled(true);
                return;
            }

            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);

            if (isAllowedIngredient(handItem)) {
                if (hasMealInside(data)) { PlayerUtil.message(player, "<red>Cannot add or remove items while a meal is inside."); event.setCancelled(true); return; }
                if (tryPlaceItemOnCampfire(block, handItem, data, player, false, event.getHand())) {
                    checkCombination(data, player);
                }
                event.setCancelled(true);
                return;
            } else if (isAllowedSeasoning(handItem)) {
                if (hasMealInside(data)) { PlayerUtil.message(player, "<red>Cannot add or remove items while a meal is inside."); event.setCancelled(true); return; }
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
        // face campfire facing
        float faceYaw = getCampfireFacingYaw(block, player.getLocation().getYaw());
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
        data.viewer = player.getUniqueId();
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
        // Validate armor stand and session
        if (!(event.getRightClicked() instanceof ArmorStand)) return;
        ArmorStand stand = (ArmorStand) event.getRightClicked();
        if (!stand.getPersistentDataContainer().has(STAND_KEY, PersistentDataType.BOOLEAN)) return;

        // Compute campfire block/location from the stand
        Location campLoc = stand.getLocation().subtract(0.5, 1, 0.5);
        Block campBlock = campLoc.getBlock();
        CookingItemData data = cookingSessions.get(campBlock.getLocation());

        Player player = event.getPlayer();
        EquipmentSlot handSlot = event.getHand();
        ItemStack hand = (handSlot == EquipmentSlot.OFF_HAND) ? player.getInventory().getItemInOffHand() : player.getInventory().getItemInMainHand();

        if (data == null) {
            PlayerUtil.message(player, "<red>No active cooking session found for this stand.");
            event.setCancelled(true);
            return;
        }

        // Prevent players from equipping arbitrary items on the invisible stand
        if (hand != null && hand.getType() != Material.AIR) {
            String handId = getItemId(hand);
            java.util.List<String> recipients = SpecializationConfig.getCookingConfig().getStringList("possible_recipients");
            if (!recipients.contains(handId)) {
                event.setCancelled(true);
                // Defensive: clear any equipment that might have been applied
                try { stand.getEquipment().setHelmet(null); stand.getEquipment().setItemInMainHand(null); stand.getEquipment().setItemInOffHand(null); } catch (Exception ignored) {}
            }
        }

        // If player interacts with empty hand -> either collect cooked food, pickup recipient (before ingredients), spawn preview, or start cooking
        if (hand == null || hand.getType() == Material.AIR) {
            // Collect cooked food if ready
            if (data.cooked) {
                // remove recipient display
                if (data.recipientDisplay != null) { data.recipientDisplay.remove(); data.recipientDisplay = null; }
                // remove preview entity and persisted preview to avoid duplicate/resurrected previews
                if (data.displayEntity != null) {
                    removePreviewFromDisk(data);
                    data.displayEntity.remove();
                    data.displayEntity = null;
                }
                // Drop the cooked item at the campfire center so it behaves like a normal drop (no inventory teleporting)
                if (data.campfireLocation != null && data.food != null) {
                    Location dropLoc = data.campfireLocation.clone().add(0.5, 0.5, 0.5);
                    org.bukkit.entity.Item dropped = data.campfireLocation.getWorld().dropItemNaturally(dropLoc, data.food.clone());
                    dropped.getPersistentDataContainer().set(COOKED_RESULT_KEY, PersistentDataType.STRING,
                            data.campfireLocation.getWorld().getName()+":"+data.campfireLocation.getBlockX()+","+data.campfireLocation.getBlockY()+","+data.campfireLocation.getBlockZ());
                    if (!data.burnt) {
                        if (data.viewer != null) {
                            org.bukkit.entity.Player starter = Bukkit.getPlayer(data.viewer);
                            if (starter != null && starter.isOnline()) CookingVisuals.playFinishEffects(data, starter, true);
                        }
                        CookingVisuals.playWorldFinishSound(data, true);
                    } else {
                        if (data.viewer != null) {
                            org.bukkit.entity.Player starter = Bukkit.getPlayer(data.viewer);
                            if (starter != null && starter.isOnline()) CookingVisuals.playFinishEffects(data, starter, false);
                        }
                        CookingVisuals.playWorldFinishSound(data, false);
                    }
                } else if (data.food != null) {
                    // fallback: give to player if drop point missing
                    giveOrDrop(player, data.food.clone());
                }

                // Award XP to the starter if configured
                int xpToAward = Math.max(0, data.cookExp);
                if (data.burnt) xpToAward = Math.max(0, Math.round(xpToAward * 0.4f));
                if (xpToAward > 0 && data.viewer != null) {
                    org.bukkit.entity.Player starter = Bukkit.getPlayer(data.viewer);
                    if (starter != null && starter.isOnline()) {
                        com.minecraftcivilizations.specialization.Player.CustomPlayer cp = com.minecraftcivilizations.specialization.Player.CustomPlayer.getCustomPlayer(starter);
                        if (cp == null) cp = com.minecraftcivilizations.specialization.util.CoreUtil.getPlayer(starter);
                        if (cp != null) cp.addSkillXp(com.minecraftcivilizations.specialization.Skill.SkillType.FARMER, xpToAward, starter.getLocation());
                    }
                }

                // Cancel any pending burn task to avoid double-drops
                if (data.burnTask != null) { data.burnTask.cancel(); data.burnTask = null; }

                // remove the invisible armor stand tied to this campfire
                removeCookingStandAt(campBlock.getLocation());

                // Use the campfire block location (we're inside onPlayerInteract, 'stand' isn't available here)
                cleanUp(data, campBlock.getLocation());
                event.setCancelled(true);
                return;
            }

            // If no ingredients placed yet -> allow picking up recipient
            if (data.ingredients.isEmpty()) {
                if (data.recipient != null) giveOrDrop(player, data.recipient.clone());
                PlayerUtil.message(player, "<green>Picked up recipient item.");
                removeCookingStandAt(campBlock.getLocation());
                cleanUp(data, campBlock.getLocation());
                event.setCancelled(true);
                return;
            }

            // If preview missing -> spawn preview for this viewer
            if (data.displayEntity == null) {
                data.viewer = player.getUniqueId();
                CookingVisuals.spawnOrUpdateDisplay(data);
                savePreviewToDisk(data);
                PlayerUtil.message(player, "<green>Preview spawned above campfire.");
                event.setCancelled(true);
                return;
            }

            // If cooking already running -> inform
            if (isActiveCooking(data)) {
                PlayerUtil.message(player, "<yellow>Cooking already in progress.");
                event.setCancelled(true);
                return;
            }

            // Start cooking
            // ensure preview is fixed before cooking starts to avoid brief drift
            Location center = data.campfireLocation != null ? data.campfireLocation.clone().add(0.5, 0.4, 0.5) : (data.stand != null ? data.stand.getLocation().clone().add(0, 0.4, 0) : null);
            CookingVisuals.forceFixPreviewPosition(data, center);
            startCooking(data, player);
            event.setCancelled(true);
            return;
        }

        // Non-empty hand: placing ingredients or seasonings on campfire
        if (isAllowedIngredient(hand)) {
            CustomPlayer cPlayer = CoreUtil.getPlayer(player);
            if (cPlayer.getSkillLevel(SkillType.FARMER) < 2) {
                PlayerUtil.message(player, "<red>You need to be Farmer level 2 or higher to use this ingredient!");
                return;
            }
            if (isActiveCooking(data)) { PlayerUtil.message(player, "<red>Cannot modify campfire slots while cooking is in progress."); return; }
            if (tryPlaceItemOnCampfire(campBlock, hand, data, player, false, handSlot)) {
                event.setCancelled(true);
                checkCombination(data, player);
                if (data.food != null) PlayerUtil.message(player, "<aqua>Recipe matched — result shown on campfire stand");
            }
            return;
        } else if (isAllowedSeasoning(hand)) {
            CustomPlayer cPlayer = CoreUtil.getPlayer(player);
            if (cPlayer.getSkillLevel(SkillType.FARMER) < 2) { PlayerUtil.message(player, "<red>You need to be Farmer level 2 or higher to use this additive!"); return; }
            if (isActiveCooking(data)) { PlayerUtil.message(player, "<red>Cannot modify campfire slots while cooking is in progress."); return; }
            if (tryPlaceItemOnCampfire(campBlock, hand, data, player, true, handSlot)) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand)) return;
        ArmorStand stand = (ArmorStand) event.getEntity();
        if (!stand.getPersistentDataContainer().has(STAND_KEY, PersistentDataType.BOOLEAN)) return;
        if (!(event.getDamager() instanceof Player)) return;
        Player player = (Player) event.getDamager();

        Location campLoc = stand.getLocation().subtract(0.5, 1, 0.5);
        CookingItemData data = getSessionForStand(stand);
        if (data == null) data = cookingSessions.get(campLoc.getBlock().getLocation());
        // Resync authoritative campfire slot contents into session lists to avoid stale state after removals/adds
        resyncSessionFromCampfire(data);
        if (data == null) return;

        // If the meal is already cooked (ready or burnt) then left-click (hit) collects it — same behavior as right-click collect
        if (data.cooked) {
            // remove only this session's recipient ItemDisplay so other campfires remain untouched
            if (data.recipientDisplay != null) { data.recipientDisplay.remove(); data.recipientDisplay = null; }
            // Drop the cooked item at the campfire center so it behaves like a normal drop (no inventory teleporting)
            if (data.campfireLocation != null && data.food != null) {
                Location dropLoc = data.campfireLocation.clone().add(0.5, 0.5, 0.5);
                org.bukkit.entity.Item dropped = data.campfireLocation.getWorld().dropItemNaturally(dropLoc, data.food.clone());
                dropped.getPersistentDataContainer().set(COOKED_RESULT_KEY, PersistentDataType.STRING,
                        data.campfireLocation.getWorld().getName()+":"+data.campfireLocation.getBlockX()+","+data.campfireLocation.getBlockY()+","+data.campfireLocation.getBlockZ());
                // Play pickup/finish sound only for the collector
                // success sound for the starter and nearby players
                try {
                    if (!data.burnt) {
                        if (data.viewer != null) {
                            org.bukkit.entity.Player starter = Bukkit.getPlayer(data.viewer);
                            if (starter != null && starter.isOnline()) {
                                CookingVisuals.playFinishEffects(data, starter, true);
                            }
                        }
                        CookingVisuals.playWorldFinishSound(data, true);
                    } else {
                        if (data.viewer != null) {
                            org.bukkit.entity.Player starter = Bukkit.getPlayer(data.viewer);
                            if (starter != null && starter.isOnline()) CookingVisuals.playFinishEffects(data, starter, false);
                        }
                        CookingVisuals.playWorldFinishSound(data, false);
                    }
                } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[Cooking] Error playing finish sounds: " + ex.getMessage()); }
            } else if (data.food != null) {
                // fallback: give to player if drop point missing
                giveOrDrop(player, data.food.clone());
            }

            // Award XP only when not burnt
            int xpToAward = Math.max(0, data.cookExp);
            if (!data.burnt && xpToAward > 0 && data.viewer != null) {
                org.bukkit.entity.Player starter = Bukkit.getPlayer(data.viewer);
                if (starter != null && starter.isOnline()) {
                    com.minecraftcivilizations.specialization.Player.CustomPlayer cp = com.minecraftcivilizations.specialization.Player.CustomPlayer.getCustomPlayer(starter);
                    if (cp == null) cp = com.minecraftcivilizations.specialization.util.CoreUtil.getPlayer(starter);
                    if (cp != null) cp.addSkillXp(com.minecraftcivilizations.specialization.Skill.SkillType.FARMER, xpToAward, starter.getLocation());
                }
            }

            // Cancel any pending burn task to avoid double-drops
            if (data.burnTask != null) { data.burnTask.cancel(); data.burnTask = null; }

            // remove the invisible armor stand tied to this campfire
            removeCookingStandAt(stand.getLocation());

            // cleanup session and visuals
            cleanUp(data, stand.getLocation().subtract(0.5, 1, 0.5));
            event.setCancelled(true);
            return;
        }

        // if cooking in progress -> cancel cooking but DO NOT return items (player can remove them manually).
        if (isActiveCooking(data)) {
            stopCookingProcesses(data);
            CookingVisuals.playCancelEffects(data);
            PlayerUtil.message(player, "<yellow>Cooking cancelled.");
            event.setCancelled(true);
            return;
        }

        // Recompute recipe matching from authoritative campfire slots so a cancel + re-add cycle is recognized
        data.viewer = player.getUniqueId();
        // ensure the checker knows who the viewer is (so spawn messages/preview target the hitter)
        checkCombination(data, player);

        // Defensive re-check: if no resolved food, try once more after forcing viewer to this hitter
        if (data.food == null) {
            data.viewer = player.getUniqueId();
            checkCombination(data, player);
        }
        // If a preview display is missing but we have a resolved result item, spawn it so the hit action reflects the visible preview.
        if (data.displayEntity == null && data.food != null) {
            data.viewer = player.getUniqueId();
            CookingVisuals.spawnOrUpdateDisplay(data);
            savePreviewToDisk(data);
        }
        // single hit start: if there's a preview result, start cooking; otherwise notify
        if (data.food == null) {
            PlayerUtil.message(player, PlayerUtil.buildLogo() + " <yellow>No valid recipe to cook yet. Add ingredients first.");
            return;
        }
        PlayerUtil.message(player, "<green>Starting cooking process...");
        // ensure preview is fixed before cooking starts to avoid brief drift
        Location center = data.campfireLocation != null ? data.campfireLocation.clone().add(0.5, 0.4, 0.5) : (data.stand != null ? data.stand.getLocation().clone().add(0, 0.4, 0) : null);
        CookingVisuals.forceFixPreviewPosition(data, center);
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

        Campfire cf = (Campfire) block.getState();
        // Drop campfire slot items and tracked ingredient/recipient lists
        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
        Random rnd = new Random();
        List<ItemStack> toDrop = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            ItemStack s = cf.getItem(i);
            if (s != null && s.getType() != Material.AIR) toDrop.add(s.clone());
        }
        if (data.recipient != null && data.recipient.getType() != Material.AIR) toDrop.add(data.recipient.clone());
        // If the session has a finished cooked item, drop it as well so breakers receive the result
        if (data.cooked && data.food != null && data.food.getType() != Material.AIR) {
            toDrop.add(data.food.clone());
        }
        toDrop.add(new ItemStack(Material.CHARCOAL, 2));
        cookingSessions.remove(block.getLocation());
        for (ItemStack stack : toDrop) {
            Location locDrop = dropLoc.clone().add((rnd.nextDouble()-0.5)*0.4, 0, (rnd.nextDouble()-0.5)*0.4);
            org.bukkit.entity.Item dropped = block.getWorld().dropItemNaturally(locDrop, stack);
            dropped.setPickupDelay(10);
        }
        // Also include any tracked ingredients/seasonings from the session that might not be present in campfire slots
        if (data.ingredients != null) {
            for (ItemStack ing : data.ingredients) {
                if (ing == null || ing.getType() == Material.AIR) continue;
                boolean exists = false;
                for (ItemStack s : toDrop) { if (s != null && s.isSimilar(ing)) { exists = true; break; } }
                if (!exists) {
                    Location loc2 = dropLoc.clone().add((rnd.nextDouble()-0.5)*0.4, 0, (rnd.nextDouble()-0.5)*0.4);
                    org.bukkit.entity.Item d = block.getWorld().dropItemNaturally(loc2, ing.clone());
                    d.setPickupDelay(10);
                }
            }
        }
        if (data.seasonings != null) {
            for (ItemStack sng : data.seasonings) {
                if (sng == null || sng.getType() == Material.AIR) continue;
                boolean exists = false;
                for (ItemStack s : toDrop) { if (s != null && s.isSimilar(sng)) { exists = true; break; } }
                if (!exists) {
                    Location loc2 = dropLoc.clone().add((rnd.nextDouble()-0.5)*0.4, 0, (rnd.nextDouble()-0.5)*0.4);
                    org.bukkit.entity.Item d = block.getWorld().dropItemNaturally(loc2, sng.clone());
                    d.setPickupDelay(10);
                }
            }
        }
        // remove preview entity and persisted preview so it won't reappear
        if (data.displayEntity != null) {
            removePreviewFromDisk(data);
            data.displayEntity.remove();
        }

        if (data.stand != null) {
            data.stand.getEquipment().setItemInMainHand(null); data.stand.getEquipment().setItemInOffHand(null);
            CookingVisuals.playBreakEffects(data);
            data.stand.remove();
        }
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (org.bukkit.entity.Entity e : block.getWorld().getNearbyEntities(center, 2.0, 2.0, 2.0)) {
            if (e instanceof ArmorStand) {
                ArmorStand as = (ArmorStand) e;
                if (as.getPersistentDataContainer().has(STAND_KEY, PersistentDataType.BOOLEAN)) {
                    as.getEquipment().setItemInMainHand(null); as.remove();
                }
            }
        }

        try { event.setDropItems(false); } catch (NoSuchMethodError e) { Specialization.getInstance().getLogger().warning("[Cooking] setDropItems not available on this server API build: " + e.getMessage()); }
        cleanUpKeepDrops(data, block.getLocation());
    }

    private void cleanUp(CookingItemData data, Location loc) {
        if (data == null) return;
        // Stop visuals and ambient
        CookingVisuals.stopProgressBar(data);
        CookingVisuals.stopAmbient(data);
        // Cancel scheduled tasks
        if (data.progressTask != null) data.progressTask.cancel(); data.progressTask = null;
        if (data.cookTask != null) data.cookTask.cancel(); data.cookTask = null;
        if (data.maintenanceTask != null) data.maintenanceTask.cancel(); data.maintenanceTask = null;
        if (data.burnTask != null) data.burnTask.cancel(); data.burnTask = null;
        data.cookingInProgress = false;
        data.cooked = false;
        data.burnt = false;
        data.viewer = null;
        // Remove by exact location key if provided
        if (loc != null) cookingSessions.remove(loc);
        // Defensive: also remove any lingering entries that reference this data object
        try { cookingSessions.values().removeIf(v -> v == data); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error removing lingering session references: " + e.getMessage()); }
        try {
            Block block = loc == null ? null : loc.getBlock();
            if (block != null && block.getType() == Material.CAMPFIRE) {
                org.bukkit.block.data.type.Campfire campfireData = (org.bukkit.block.data.type.Campfire) block.getBlockData();
                // Always reset campfire to unlit when cleaning up so the station is usable again
                if (campfireData.isLit()) { campfireData.setLit(false); block.setBlockData(campfireData); }
                Campfire campfire = (Campfire) block.getState();
                for (int i = 0; i < 4; i++) campfire.setItem(i, null);
                campfire.update(true);
            }
        } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error during cleanUp: " + e.getMessage()); }
    }

    private void cleanUpKeepDrops(CookingItemData data, Location loc) {
        if (data == null) return;
        if (data.displayEntity != null) { removePreviewFromDisk(data); data.displayEntity.remove(); data.displayEntity = null; }
        if (data.recipientDisplay != null) { data.recipientDisplay.remove(); data.recipientDisplay = null; }
        if (data.stand != null) {
            if (data.stand.isValid()) {
                try { data.stand.getEquipment().setHelmet(null); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error clearing stand helmet: " + e.getMessage()); }
                try { data.stand.getEquipment().setItemInMainHand(null); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error clearing stand mainhand: " + e.getMessage()); }
            }
            data.stand.remove();
        }
        if (loc != null) cookingSessions.remove(loc);
        Block block = loc == null ? null : loc.getBlock();
        if (block != null && block.getType() == Material.CAMPFIRE) {
            try {
                org.bukkit.block.data.type.Campfire campfireData = (org.bukkit.block.data.type.Campfire) block.getBlockData();
                // Always reset campfire to unlit when cleaning up so the station is usable again
                if (campfireData.isLit()) { campfireData.setLit(false); block.setBlockData(campfireData); }
                Campfire campfire = (Campfire) block.getState();
                for (int i = 0; i < 4; i++) campfire.setItem(i, null);
                campfire.update(true);
            } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error during cleanUpKeepDrops: " + e.getMessage()); }
        }
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.getType() == Material.AIR) return;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack);
        if (!leftover.isEmpty()) for (ItemStack rem : leftover.values()) if (rem != null) player.getWorld().dropItemNaturally(player.getLocation().add(0,2,0), rem);
    }

    private String getItemId(ItemStack item) {
        try {
            net.momirealms.craftengine.core.item.Item<ItemStack> wrapped = BukkitItemManager.instance().wrap(item);
            return wrapped.getCustomItem().isPresent() ? wrapped.getCustomItem().get().id().value() : "minecraft:" + item.getType().name().toLowerCase();
        } catch (Exception e) {
            return "minecraft:" + item.getType().name().toLowerCase();
        }
    }

    // Robust check that treats CraftEngine custom items and plain minecraft ids as allowed ingredients
    private boolean isAllowedIngredient(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        // Try CraftEngine custom item id first
        try {
            var wrapped = BukkitItemManager.instance().wrap(item);
            if (wrapped != null && wrapped.getCustomItem().isPresent()) {
                String id = wrapped.getCustomItem().get().id().value();
                if (SpecializationConfig.getCookingConfig().getStringList("possible_ingredients").contains(id)) return true;
            }
        } catch (Throwable ignored) {}
        // Fallback to basic id
        String id = getItemId(item);
        return SpecializationConfig.getCookingConfig().getStringList("possible_ingredients").contains(id);
    }

    private boolean isAllowedSeasoning(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        try {
            var wrapped = BukkitItemManager.instance().wrap(item);
            if (wrapped != null && wrapped.getCustomItem().isPresent()) {
                String id = wrapped.getCustomItem().get().id().value();
                if (SpecializationConfig.getCookingConfig().getStringList("possible_seasonings").contains(id) || SpecializationConfig.getCookingConfig().getStringList("possible_sauces").contains(id)) return true;
            }
        } catch (Throwable ignored) {}
        String id = getItemId(item);
        return SpecializationConfig.getCookingConfig().getStringList("possible_seasonings").contains(id)
                || SpecializationConfig.getCookingConfig().getStringList("possible_sauces").contains(id)
                || item.getType() == Material.POTION;
    }


    public void loadPersistedPreviews() {
        if (!previewsFile.exists()) return;
        for (String k : previewsConfig.getKeys(false)) {
            try {
                String[] parts = k.split(":"); if (parts.length != 2) continue;
                String worldName = parts[0]; String[] coords = parts[1].split(","); if (coords.length != 3) continue;
                org.bukkit.World w = Bukkit.getWorld(worldName); if (w == null) continue;
                int x = Integer.parseInt(coords[0]); int y = Integer.parseInt(coords[1]); int z = Integer.parseInt(coords[2]);
                Location loc = new Location(w, x, y, z);
                ItemStack stack = previewsConfig.getItemStack(k + ".displayItem");
                String recipientId = previewsConfig.getString(k + ".recipientId");
                boolean cooked = previewsConfig.getBoolean(k + ".cooked", false);
                Location standLoc = loc.clone().add(0.5, 0.0, 0.5);
                float yaw = getCampfireFacingYaw(loc.getBlock(), 0f);
                standLoc.setYaw(yaw);
                ArmorStand stand = loc.getWorld().spawn(standLoc, ArmorStand.class, s -> {
                    s.setVisible(false); s.setInvisible(true); s.setGravity(false); s.setBasePlate(false); s.setSmall(true);
                    s.getPersistentDataContainer().set(STAND_KEY, PersistentDataType.BOOLEAN, true);
                });
                CookingItemData data = new CookingItemData(stand, stack == null ? null : stack.clone(), recipientId);
                data.campfireLocation = loc; data.food = stack == null ? null : stack.clone(); data.cooked = cooked;
                cookingSessions.put(loc, data);
                if (stack != null) CookingVisuals.spawnOrUpdateDisplay(data);
            } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[Cooking] Failed to load persisted preview: " + ex.getMessage()); }
        }
    }


    private boolean tryPlaceItemOnCampfire(Block block, ItemStack hand, CookingItemData data, Player player, boolean isSeasoning, EquipmentSlot handSlot) {
        if (block == null || block.getType() != Material.CAMPFIRE) return false;
        // Do not allow modifications if the station currently contains a meal (cooking, ready or burnt)
        if (hasMealInside(data)) { PlayerUtil.message(player, "<red>Cannot add or remove items while a meal is inside."); return false; }
        org.bukkit.block.data.type.Campfire blockData = (org.bukkit.block.data.type.Campfire) block.getBlockData();
        if (blockData.isLit()) { PlayerUtil.message(player, "<red>You must extinguish the campfire (right-click with a shovel) to enter cooking mode."); return false; }
        if (isActiveCooking(data)) { Specialization.getInstance().getLogger().info("[Cooking] refuse place: activeCooking=" + isActiveCooking(data) + " cooked=" + data.cooked); PlayerUtil.message(player, "<red>Cannot add or remove items while cooking is in progress."); return false; }
        try {
            Campfire cf = (Campfire) block.getState(); int slot = -1; for (int i = 0; i < 4; i++) { ItemStack cur = cf.getItem(i); if (cur == null || cur.getType().isAir()) { slot = i; break; } }
            if (slot == -1) { PlayerUtil.message(player, isSeasoning ? "<red>No empty seasoning slots available." : "<red>No empty ingredient slots available."); return false; }
            if (isSeasoning) { if (data.seasonings.size() >= 2) { PlayerUtil.message(player, "<red>Only 2 seasonings/sauces are allowed per cooking session."); return false; } }
            ItemStack toPlace = hand.clone(); toPlace.setAmount(1); cf.setItem(slot, toPlace); cf.update(true);
            if (isSeasoning) { data.seasonings.add(toPlace); CookingVisuals.playSeasoningEffect(block, toPlace); } else { data.ingredients.add(toPlace); }
            decrementPlayerHandBySlot(player, handSlot); player.updateInventory(); return true;
        } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[Cooking] Failed placing ingredient: " + ex.getMessage()); }
        return false;
    }

    private void resyncSessionFromCampfire(CookingItemData data) {
        if (data == null || data.campfireLocation == null) return;
        Block b = data.campfireLocation.getBlock(); if (b.getType() != Material.CAMPFIRE) return;
        Campfire cf = (Campfire) b.getState(); List<ItemStack> newIngredients = new ArrayList<>(); List<ItemStack> newSeasonings = new ArrayList<>();
        for (int i = 0; i < 4; i++) { ItemStack it = cf.getItem(i); if (it == null || it.getType().isAir()) continue; String id = getItemId(it); if (SpecializationConfig.getCookingConfig().getStringList("possible_seasonings").contains(id) || SpecializationConfig.getCookingConfig().getStringList("possible_sauces").contains(id) || it.getType() == Material.POTION) newSeasonings.add(it.clone()); else newIngredients.add(it.clone()); }
        data.ingredients = newIngredients; data.seasonings = newSeasonings;
    }

    private void savePreviewToDisk(CookingItemData data) {
        if (data == null || data.displayEntity == null || !data.displayEntity.isValid() || data.campfireLocation == null) return;
        String key = data.campfireLocation.getWorld().getName() + ":" + data.campfireLocation.getBlockX() + "," + data.campfireLocation.getBlockY() + "," + data.campfireLocation.getBlockZ();
        previewsConfig.set(key + ".recipientId", data.recipientId);
        previewsConfig.set(key + ".displayItem", data.displayEntity.getItemStack());
        previewsConfig.set(key + ".cooked", data.cooked);
        try { previewsConfig.save(previewsFile); } catch (IOException e) { Specialization.getInstance().getLogger().warning("[Cooking] Failed to save preview config: " + e.getMessage()); throw new RuntimeException(e); }
    }

    private void removePreviewFromDisk(CookingItemData data) {
        if (data == null || data.campfireLocation == null) return;
        String key = data.campfireLocation.getWorld().getName() + ":" + data.campfireLocation.getBlockX() + "," + data.campfireLocation.getBlockY() + "," + data.campfireLocation.getBlockZ();
        previewsConfig.set(key, null);
        try { previewsConfig.save(previewsFile); } catch (IOException e) { Specialization.getInstance().getLogger().warning("[Cooking] Failed to remove preview from disk: " + e.getMessage()); throw new RuntimeException(e); }
    }

    private void stopCookingProcesses(CookingItemData data) {
        if (data == null) return;
        // Stop visuals and ambient
        CookingVisuals.stopProgressBar(data);
        CookingVisuals.stopAmbient(data);
        // Cancel scheduled tasks
        if (data.progressTask != null) data.progressTask.cancel(); data.progressTask = null;
        if (data.cookTask != null) data.cookTask.cancel(); data.cookTask = null;
        if (data.maintenanceTask != null) data.maintenanceTask.cancel(); data.maintenanceTask = null;
        if (data.burnTask != null) data.burnTask.cancel(); data.burnTask = null;
        data.cookingInProgress = false;
        data.cooked = false;
        data.burnt = false;
        data.viewer = null;
    }


    private void checkCombination(CookingItemData data, Player player) {
        if (data == null) return;
        List<String> currentIds = new ArrayList<>();
        try {
            if (data.campfireLocation != null) {
                Block block = data.campfireLocation.getBlock();
                if (block.getType() == Material.CAMPFIRE) {
                    Campfire cf = (Campfire) block.getState();
                    for (int i = 0; i < 4; i++) {
                        ItemStack it = cf.getItem(i);
                        if (it != null && !it.getType().isAir()) currentIds.add(getItemId(it));
                    }
                } else {
                    for (ItemStack is : data.ingredients) if (is != null) currentIds.add(getItemId(is));
                }
            } else {
                for (ItemStack is : data.ingredients) if (is != null) currentIds.add(getItemId(is));
            }
        } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error while reading campfire slots: " + e.getMessage()); }

        Config cookingConfig = SpecializationConfig.getCookingConfig().getConfig();
        int playerLevelInt = 0;
        try {
            com.minecraftcivilizations.specialization.Player.CustomPlayer specCP = com.minecraftcivilizations.specialization.Player.CustomPlayer.getCustomPlayer(player);
            if (specCP != null) playerLevelInt = specCP.getSkillLevel(SkillType.FARMER);
            else { CustomPlayer coreCP = CoreUtil.getPlayer(player); if (coreCP != null) playerLevelInt = coreCP.getSkillLevel(SkillType.FARMER); }
        } catch (Exception t) { Specialization.getInstance().getLogger().warning("[Cooking] Could not resolve CustomPlayer for " + player.getName() + ", defaulting farmer level to 0"); }
        SkillLevel playerSkill = SkillLevel.getSkillLevelFromInt(playerLevelInt);

        try {
            if (playerSkill.getLevel() < 2) {
                List<ConfigObject> allTiers = new ArrayList<>();
                for (SkillLevel t : SkillLevel.values()) { String tk = "FARMER_" + t.name(); try { allTiers.addAll(cookingConfig.getObjectList(tk)); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error reading recipes for tier " + tk + ": " + e.getMessage()); } }
                boolean wouldMatch = false;
                for (ConfigObject obj : allTiers) {
                    try {
                        Config r = obj.toConfig(); String recip = r.getString("recipient"); if (!recip.equals(data.recipientId)) continue;
                        List<String> req = r.hasPath("ingredients") ? r.getStringList("ingredients") : List.of(); Collections.sort(req);
                        List<String> curSorted = new ArrayList<>(currentIds); Collections.sort(curSorted);
                        if (req.equals(curSorted)) { wouldMatch = true; break; }
                    } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error parsing recipe object: " + e.getMessage()); }
                }
                if (wouldMatch) { try { PlayerUtil.message(player, "<red>You need to be Farmer level 2 or higher to cook this dish!", 1); } catch (Exception e) { try { player.sendMessage("§cYou need to be Farmer level 2 or higher to cook this dish!"); } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[Cooking] Failed to send fallback message: " + ex.getMessage()); } } return; }
            }
        } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error during level-check: " + e.getMessage()); }

        boolean matchedAny = false;
        SkillLevel[] levels = SkillLevel.values(); Arrays.sort(levels, Comparator.comparingInt(SkillLevel::getLevel).reversed());
        for (SkillLevel tier : levels) {
            if (tier.getLevel() > playerSkill.getLevel()) continue;
            String tierKey = "FARMER_" + tier.name();
            List<? extends ConfigObject> tierRecipes = cookingConfig.getObjectList(tierKey);
            for (ConfigObject configObject : tierRecipes) {
                Config recipe = configObject.toConfig(); String recipient = recipe.getString("recipient"); if (!recipient.equals(data.recipientId)) continue;
                List<String> ingredients = recipe.hasPath("ingredients") ? recipe.getStringList("ingredients") : List.of(); ingredients.sort(String::compareTo); Collections.sort(currentIds);
                if (!ingredients.equals(currentIds)) continue;
                matchedAny = true;
                String resultId = recipe.getString("result"); int expAmt = recipe.hasPath("exp") ? recipe.getInt("exp") : 0; int cookSeconds = recipe.hasPath("cooking_time") ? recipe.getInt("cooking_time") : 10;
                boolean made = resolveResultItem(data, resultId);
                if (made) { data.cookExp = expAmt; data.cookTimeSeconds = cookSeconds; }
                if (made && data.food != null) { CookingVisuals.spawnOrUpdateDisplay(data); if (player.isOnline()) { try { player.playSound(player.getLocation(), Sound.BLOCK_COMPOSTER_EMPTY, SoundCategory.BLOCKS, 1.0f, 0.5f); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Failed to play composter sound: " + e.getMessage()); } } }
                break;
            }
            if (matchedAny) break;
        }

        if (!matchedAny) {
            if (data.food != null) {
                data.food = null; data.cookExp = 0;
                if (data.displayEntity != null) { data.displayEntity.remove(); data.displayEntity = null; }
                if (player.isOnline()) PlayerUtil.message(player, "<yellow>Recipe invalid or incomplete for this recipient.");
            }
        }
    }

    private boolean resolveResultItem(CookingItemData data, String resultId) {
        boolean made = false; if (data == null || resultId == null) return false;
        try {
            Optional<net.momirealms.craftengine.core.item.CustomItem<ItemStack>> ce = BukkitItemManager.instance().getCustomItem(net.momirealms.craftengine.core.util.Key.of(resultId));
            if (ce != null && ce.isPresent()) { data.food = ce.get().buildItemStack(1); made = true; }
        } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error resolving CraftEngine custom item: " + e.getMessage()); }
        if (!made) {
            try { com.minecraftcivilizations.specialization.CustomItem.CustomItem ci = com.minecraftcivilizations.specialization.CustomItem.CustomItemManager.getInstance().getCustomItem(resultId); if (ci != null) { data.food = ci.createItemStack(1); made = true; } } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error resolving legacy custom item: " + e.getMessage()); }
        }
        return made;
    }

    private void decrementPlayerHandBySlot(Player player, EquipmentSlot handSlot) {
        if (handSlot == EquipmentSlot.OFF_HAND) { ItemStack off = player.getInventory().getItemInOffHand(); int newAmt = Math.max(0, off.getAmount() - 1); if (newAmt == 0) player.getInventory().setItemInOffHand(null); else off.setAmount(newAmt); return; }
        ItemStack main = player.getInventory().getItemInMainHand(); int newAmt = Math.max(0, main.getAmount() - 1); if (newAmt == 0) player.getInventory().setItemInMainHand(null); else main.setAmount(newAmt);
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        org.bukkit.entity.Item it = event.getEntity(); ItemStack stack = it.getItemStack(); Location loc = it.getLocation();
        for (Map.Entry<Location, CookingItemData> entry : cookingSessions.entrySet()) {
            Location campLoc = entry.getKey();
            if (!campLoc.getWorld().equals(loc.getWorld())) continue;
            if (campLoc.distanceSquared(loc) > 4.0) continue;
            CookingItemData d = entry.getValue();
            if (d == null) continue;
            if (d.destroyed) continue;
            String spawnedId = "minecraft:" + stack.getType().name().toLowerCase();
            boolean matches = d.ingredients.stream().anyMatch(i -> ("minecraft:" + i.getType().name().toLowerCase()).equals(spawnedId));
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
        // First, check explicit preview pickups
        for (CookingItemData d : cookingSessions.values()) {
            if (d == null || d.displayEntity == null) continue;
            if (d.displayEntity.getUniqueId().equals(item.getUniqueId())) {
                if (!d.cooked) { event.setCancelled(true); return; }
                // If cooked, treat this as a collection action
                removePreviewFromDisk(d);

                if (!d.burnt) {
                    if (d.viewer != null) {
                        org.bukkit.entity.Player starter = Bukkit.getPlayer(d.viewer);
                        if (starter != null && starter.isOnline()) {
                            CookingVisuals.playFinishEffects(d, starter, true);
                        }
                    }
                    CookingVisuals.playWorldFinishSound(d, true);
                } else {
                    if (d.viewer != null) {
                        org.bukkit.entity.Player starter = Bukkit.getPlayer(d.viewer);
                        if (starter != null && starter.isOnline()) CookingVisuals.playFinishEffects(d, starter, false);
                    }
                    CookingVisuals.playWorldFinishSound(d, false);
                }

                // Award XP to the player who started the cooking (if configured)
                int xpToAward = Math.max(0, d.cookExp);
                if (d.burnt) xpToAward = Math.max(0, Math.round(xpToAward * 0.4f));
                if (xpToAward > 0 && d.viewer != null) {
                    org.bukkit.entity.Player starter = Bukkit.getPlayer(d.viewer);
                    if (starter != null && starter.isOnline()) {
                        com.minecraftcivilizations.specialization.Player.CustomPlayer cp = com.minecraftcivilizations.specialization.Player.CustomPlayer.getCustomPlayer(starter);
                        if (cp == null) cp = com.minecraftcivilizations.specialization.util.CoreUtil.getPlayer(starter);
                        if (cp != null) cp.addSkillXp(com.minecraftcivilizations.specialization.Skill.SkillType.FARMER, xpToAward, starter.getLocation());
                    }
                }

                if (d.burnTask != null) { d.burnTask.cancel(); d.burnTask = null; }

                if (d.campfireLocation != null) removeCookingStandAt(d.campfireLocation);

                d.displayEntity = null;

                if (d.campfireLocation != null) cleanUp(d, d.campfireLocation); else cleanUp(d, null);

                return;
            }
        }
        // Second, check if the picked up Item entity is a dropped cooked result (has cooked_result key)
        if (item.getPersistentDataContainer().has(COOKED_RESULT_KEY, PersistentDataType.STRING)) {
            String locKey = item.getPersistentDataContainer().get(COOKED_RESULT_KEY, PersistentDataType.STRING);
            if (locKey != null) {
                String[] parts = locKey.split(":", 2); if (parts.length != 2) return;
                String[] coords = parts[1].split(","); if (coords.length != 3) return;
                org.bukkit.World w = Bukkit.getWorld(parts[0]); if (w == null) return;
                int x = Integer.parseInt(coords[0]); int y = Integer.parseInt(coords[1]); int z = Integer.parseInt(coords[2]);
                Location campLoc = new Location(w, x, y, z);
                CookingItemData session = cookingSessions.get(campLoc);
                if (session != null) {
                    int xpToAward = Math.max(0, session.cookExp);
                    if (session.burnt) xpToAward = Math.max(0, Math.round(xpToAward * 0.4f));
                    if (xpToAward > 0 && session.viewer != null) {
                        org.bukkit.entity.Player starter = Bukkit.getPlayer(session.viewer);
                        if (starter != null && starter.isOnline()) {
                            com.minecraftcivilizations.specialization.Player.CustomPlayer cp = com.minecraftcivilizations.specialization.Player.CustomPlayer.getCustomPlayer(starter);
                            if (cp == null) cp = com.minecraftcivilizations.specialization.util.CoreUtil.getPlayer(starter);
                            if (cp != null) cp.addSkillXp(com.minecraftcivilizations.specialization.Skill.SkillType.FARMER, xpToAward, starter.getLocation());
                        }
                    }
                    if (session.burnTask != null) { session.burnTask.cancel(); session.burnTask = null; }
                    removeCookingStandAt(campLoc);
                    cleanUp(session, campLoc);
                }
            }
        }
    }

    private CookingItemData getSessionForStand(ArmorStand stand) {
        if (stand == null) return null;
        for (CookingItemData d : cookingSessions.values()) {
            if (d == null) continue;
            if (d.stand != null && d.stand.getUniqueId().equals(stand.getUniqueId())) return d;
        }
        return null;
    }

    private boolean isActiveCooking(CookingItemData d) {
        return d != null && !d.cooked && (d.cookingInProgress || d.cookTask != null);
    }

    // True when a meal is present in the station (cooking, ready or burnt)
    private boolean hasMealInside(CookingItemData d) {
        return d != null && (d.cookingInProgress || d.cooked || d.burnt);
    }

    private void startCooking(CookingItemData session, Player starter) {
        if (session == null) return; if (session.food == null) { if (starter != null) PlayerUtil.message(starter, "<yellow>No valid recipe to cook yet."); return; }
        if (session.cookingInProgress || session.cookTask != null) return;
        int cookSeconds; try { cookSeconds = Math.max(1, session.cookTimeSeconds); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error reading cook time: " + e.getMessage()); return; }
        final int READY_TICKS = cookSeconds * 20; final int BURN_TICKS = (int) Math.ceil(READY_TICKS * 1.2);
        session.cookingInProgress = true; session.viewer = (starter == null ? null : starter.getUniqueId());
        // Start persistent flame effect while cooking: play sound once and spawn particles periodically (no repeated sound)
        CookingVisuals.playStartEffects(session);
        if (session.cookTask != null) try { session.cookTask.cancel(); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[Cooking] Error cancelling previous cookTask: " + e.getMessage()); }
        // Start a maintenance task (flameTask) to keep flame particles active during cooking without overwriting preview maintenanceTask
        if (session.flameTask != null) { try { session.flameTask.cancel(); } catch (Exception ignored) {} session.flameTask = null; }
        session.flameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (session.destroyed) { cancel(); return; }
                if (session.cooked || !session.cookingInProgress) { cancel(); return; }
                // spawn particles only to avoid repeating the fire sound
                CookingVisuals.spawnFlameParticles(session);
            }
        }.runTaskTimer(Specialization.getInstance(), 0L, 10L);

        session.cookTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() {
                try {
                    if (session.destroyed) return;
                    session.cooked = true;
                    session.cookingInProgress = false;
                    // Clear campfire slots and remove recipient from the campfire — the preview remains (final food) but ingredients/recipient are removed
                    try {
                        if (session.campfireLocation != null) {
                            Block b = session.campfireLocation.getBlock();
                            if (b.getType() == Material.CAMPFIRE) {
                                Campfire cf = (Campfire) b.getState();
                                for (int i = 0; i < 4; i++) cf.setItem(i, null);
                                cf.update(true);
                            }
                        }
                    } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[Cooking] Error clearing campfire slots on ready: " + ex.getMessage()); }
                    // Remove recipient display and clear tracked ingredients/seasonings/recipient
                    try { if (session.recipientDisplay != null) { session.recipientDisplay.remove(); session.recipientDisplay = null; } } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[Cooking] Error removing recipient display on ready: " + ex.getMessage()); }
                    session.ingredients.clear(); session.seasonings.clear(); session.recipient = null; session.recipientId = null;

                    if (session.viewer != null) {
                        org.bukkit.entity.Player p = Bukkit.getPlayer(session.viewer);
                        if (p != null && p.isOnline()) {
                            PlayerUtil.message(p, "<green>Your meal is ready! Right-click the stand to collect.");
                        }
                    }
                    // mark ready which will spawn glow particles (visuals)
                    CookingVisuals.markReady(session);
                } catch (Exception ex) {
                    Specialization.getInstance().getLogger().warning("[Cooking] Error marking session ready: " + ex.getMessage());
                }
            }
        }.runTaskLater(Specialization.getInstance(), READY_TICKS);
        if (session.burnTask != null) try { session.burnTask.cancel(); } catch (Exception ignored) {}
        int burnDelayTicks = Math.max(READY_TICKS + 1, BURN_TICKS);
        session.burnTask = new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (session.destroyed) return;
                    // only burn if item already reached ready state
                    if (!session.cooked) return;
                    session.burnt = true;
                    // Attempt to resolve the burnt_food custom item and update preview
                    try {
                        resolveResultItem(session, "specialization:burnt_food");
                    } catch (Exception e) {
                        Specialization.getInstance().getLogger().warning("[Cooking] Failed to resolve burnt food item: " + e.getMessage());
                    }
                    // Update the visual preview to show the burnt item (if resolved)
                    try { CookingVisuals.spawnOrUpdateDisplay(session); CookingVisuals.forceFixPreviewPosition(session, session.campfireLocation.clone().add(0.5, 0.4, 0.5)); } catch (Exception ignored) { }
                    // Notify the original starter that the item burned (if they are online)
                    try {
                        if (session.viewer != null) {
                            org.bukkit.entity.Player p = Bukkit.getPlayer(session.viewer);
                            if (p != null && p.isOnline()) CookingVisuals.playCancelEffects(session);
                        }
                    } catch (Exception ignored) {}
                    // Mark ready so visuals show LARGE_SMOKE and allows collection of burnt item
                    try { CookingVisuals.markReady(session); } catch (Exception ignored) {}
                    // Do NOT drop the item automatically; the cook (or anyone) must right-click to collect. Keep ingredients/recipient until collection.
                } catch (Exception ex) {
                    Specialization.getInstance().getLogger().warning("[Cooking] Error during burnTask: " + ex.getMessage());
                }
            }
        }.runTaskLater(Specialization.getInstance(), burnDelayTicks);
    }

    private void removeCookingStandAt(Location loc) {
        if (loc == null) return; Location center = loc.clone().add(0.5, 0.5, 0.5);
        for (org.bukkit.entity.Entity ent : loc.getWorld().getNearbyEntities(center, 2.0, 2.0, 2.0)) {
            if (ent instanceof ArmorStand) {
                ArmorStand as = (ArmorStand) ent;
                try { if (as.getPersistentDataContainer().has(STAND_KEY, PersistentDataType.BOOLEAN)) { as.getEquipment().setItemInMainHand(null); as.remove(); } } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[Cooking] Error removing cooking stand entity: " + ex.getMessage()); }
            }
        }
    }

}
