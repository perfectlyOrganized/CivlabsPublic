package com.minecraftcivilizations.specialization.Cooking;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Campfire;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import com.minecraftcivilizations.specialization.Specialization;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages all active cooking sessions and persistence.
 */
public class CookingSessionManager {

    private static CookingSessionManager instance;

    private final Map<Location, CookingItemData> cookingSessions = new HashMap<>();
    private final File previewsFile;
    private final FileConfiguration previewsConfig;
    private final NamespacedKey STAND_KEY;
    private final NamespacedKey PREVIEW_KEY;
    private final NamespacedKey RECIPIENT_KEY;

    private CookingSessionManager() {
        this.previewsFile = new File(Specialization.getInstance().getDataFolder(), "cooking_previews.yml");
        this.previewsConfig = YamlConfiguration.loadConfiguration(previewsFile);
        this.STAND_KEY = new NamespacedKey(Specialization.getInstance(), "cooking_stand");
        this.PREVIEW_KEY = new NamespacedKey(Specialization.getInstance(), "cooking_preview");
        this.RECIPIENT_KEY = new NamespacedKey(Specialization.getInstance(), "cooking_recipient");
    }

    public static CookingSessionManager getInstance() {
        if (instance == null) {
            instance = new CookingSessionManager();
        }
        return instance;
    }

    public NamespacedKey getStandKey() {
        return STAND_KEY;
    }

    public Map<Location, CookingItemData> getSessions() {
        return cookingSessions;
    }

    public CookingItemData getSession(Location loc) {
        return cookingSessions.get(loc);
    }

    public void putSession(Location loc, CookingItemData data) {
        cookingSessions.put(loc, data);
    }

    public void removeSession(Location loc) {
        cookingSessions.remove(loc);
    }

    public boolean hasSession(Location loc) {
        return cookingSessions.containsKey(loc);
    }

    public CookingItemData getSessionForStand(ArmorStand stand) {
        if (stand == null) return null;
        for (CookingItemData d : cookingSessions.values()) {
            if (d == null) continue;
            if (d.stand != null && d.stand.getUniqueId().equals(stand.getUniqueId())) return d;
        }
        return null;
    }

    public void savePreviewToDisk(CookingItemData data) {
        if (data == null || data.campfireLocation == null) return;
        // Save even if no preview exists - we need to persist recipient data too
        String key = data.campfireLocation.getWorld().getName() + ":" +
                     data.campfireLocation.getBlockX() + "," +
                     data.campfireLocation.getBlockY() + "," +
                     data.campfireLocation.getBlockZ();
        previewsConfig.set(key + ".recipientId", data.recipientId);
        previewsConfig.set(key + ".recipient", data.recipient);
        if (data.displayEntity != null && data.displayEntity.isValid()) {
            previewsConfig.set(key + ".displayItem", data.displayEntity.getItemStack());
        } else {
            previewsConfig.set(key + ".displayItem", null);
        }
        previewsConfig.set(key + ".cooked", data.cooked);
        saveConfig();
    }

    /**
     * Save session to disk when recipient is first placed (before recipe validation)
     */
    public void saveSessionToDisk(CookingItemData data) {
        if (data == null || data.campfireLocation == null || data.recipient == null) return;
        String key = data.campfireLocation.getWorld().getName() + ":" +
                     data.campfireLocation.getBlockX() + "," +
                     data.campfireLocation.getBlockY() + "," +
                     data.campfireLocation.getBlockZ();
        previewsConfig.set(key + ".recipientId", data.recipientId);
        previewsConfig.set(key + ".recipient", data.recipient);
        previewsConfig.set(key + ".cooked", false);
        saveConfig();
    }

    public void removePreviewFromDisk(CookingItemData data) {
        if (data == null || data.campfireLocation == null) return;
        String key = data.campfireLocation.getWorld().getName() + ":" +
                     data.campfireLocation.getBlockX() + "," +
                     data.campfireLocation.getBlockY() + "," +
                     data.campfireLocation.getBlockZ();
        previewsConfig.set(key, null);
        saveConfig();
    }

    private void saveConfig() {
        try {
            previewsConfig.save(previewsFile);
        } catch (IOException e) {
            Specialization.getInstance().getLogger().warning("[Cooking] Failed to save preview config: " + e.getMessage());
        }
    }

    /**
     * Remove all orphaned cooking entities (preview items, recipient displays, and armor stands)
     * from all loaded worlds. Should be called before loadPersistedPreviews() on server start.
     */
    public void cleanupOrphanedCookingEntities() {
        int removedPreviews = 0;
        int removedRecipients = 0;
        int removedStands = 0;

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item item) {
                    if (item.getPersistentDataContainer().has(PREVIEW_KEY, PersistentDataType.BOOLEAN)) {
                        item.remove();
                        removedPreviews++;
                    }
                }
                else if (entity instanceof ItemDisplay itemDisplay) {
                    if (itemDisplay.getPersistentDataContainer().has(RECIPIENT_KEY, PersistentDataType.STRING)) {
                        itemDisplay.remove();
                        removedRecipients++;
                    }
                }
                else if (entity instanceof ArmorStand stand) {
                    if (stand.getPersistentDataContainer().has(STAND_KEY, PersistentDataType.BOOLEAN)) {
                        stand.remove();
                        removedStands++;
                    }
                }
            }
        }

        if (removedPreviews > 0 || removedRecipients > 0 || removedStands > 0) {
            Specialization.getInstance().getLogger().info("[Cooking] Cleaned up orphaned entities: " +
                removedPreviews + " preview items, " +
                removedRecipients + " recipient displays, " +
                removedStands + " armor stands");
        }
    }

    public void loadPersistedPreviews() {
        // First, clean up any orphaned entities from previous sessions/crashes
        cleanupOrphanedCookingEntities();

        if (!previewsFile.exists()) return;
        for (String k : previewsConfig.getKeys(false)) {
            String[] parts = k.split(":");
            if (parts.length != 2) continue;
            String worldName = parts[0];
            String[] coords = parts[1].split(",");
            if (coords.length != 3) continue;
            World w = Bukkit.getWorld(worldName);
            if (w == null) continue;
            int x = Integer.parseInt(coords[0]);
            int y = Integer.parseInt(coords[1]);
            int z = Integer.parseInt(coords[2]);
            Location loc = new Location(w, x, y, z);

            // Check if campfire still exists at this location
            if (loc.getBlock().getType() != Material.CAMPFIRE) {
                // Campfire was removed, delete this entry
                previewsConfig.set(k, null);
                continue;
            }

            ItemStack stack = previewsConfig.getItemStack(k + ".displayItem");
            ItemStack recipient = previewsConfig.getItemStack(k + ".recipient");
            String recipientId = previewsConfig.getString(k + ".recipientId");
            boolean cooked = previewsConfig.getBoolean(k + ".cooked", false);

            // Skip if no recipient data
            if (recipient == null && recipientId == null) continue;

            Location standLoc = loc.clone().add(0.5, 0.0, 0.5);
            float yaw = CookingSyncUtils.getCampfireFacingYaw(loc.getBlock(), 0f);
            standLoc.setYaw(yaw);
            ArmorStand stand = loc.getWorld().spawn(standLoc, ArmorStand.class, s -> {
                s.setVisible(false);
                s.setInvisible(true);
                s.setGravity(false);
                s.setBasePlate(false);
                s.setSmall(true);
                s.setPersistent(false);
                s.getPersistentDataContainer().set(STAND_KEY, PersistentDataType.BOOLEAN, true);
            });

            CookingItemData data = new CookingItemData(stand, stack == null ? null : stack.clone(), recipientId);
            data.campfireLocation = loc;
            data.food = stack == null ? null : stack.clone();
            data.cooked = cooked;
            data.recipient = recipient;
            cookingSessions.put(loc, data);

            // Spawn recipient display if we have recipient data
            if (recipient != null) {
                CookingVisuals.spawnRecipientDisplay(data);
            }

            // Spawn preview display if we have food data (valid recipe result)
            if (stack != null) {
                CookingVisuals.spawnOrUpdateDisplay(data);
            }
        }
        saveConfig();
    }

    public void cleanUp(CookingItemData data, Location loc) {
        if (data == null) return;

        CookingVisuals.stopProgressBar(data);
        CookingVisuals.stopAmbient(data);

        if (data.progressTask != null) { data.progressTask.cancel(); data.progressTask = null; }
        if (data.cookTask != null) { data.cookTask.cancel(); data.cookTask = null; }
        if (data.maintenanceTask != null) { data.maintenanceTask.cancel(); data.maintenanceTask = null; }
        if (data.burnTask != null) { data.burnTask.cancel(); data.burnTask = null; }
        if (data.flameTask != null) { data.flameTask.cancel(); data.flameTask = null; }
        if (data.holdTask != null) { data.holdTask.cancel(); data.holdTask = null; }

        data.cookingInProgress = false;
        data.cooked = false;
        data.burnt = false;

        if (data.seasonings != null) data.seasonings.clear();
        if (data.sauces != null) data.sauces.clear();
        if (data.ingredientIds != null) data.ingredientIds.clear();
        if (data.seasoningIds != null) data.seasoningIds.clear();
        if (data.sauceIds != null) data.sauceIds.clear();

        data.viewer = null;

        if (loc != null) cookingSessions.remove(loc);
        cookingSessions.values().removeIf(v -> v == data);

        Block block = loc == null ? null : loc.getBlock();
        if (block != null && block.getType() == Material.CAMPFIRE) {
            org.bukkit.block.data.type.Campfire campfireData = (org.bukkit.block.data.type.Campfire) block.getBlockData();
            if (campfireData.isLit()) {
                campfireData.setLit(false);
                block.setBlockData(campfireData);
            }
            Campfire campfire = (Campfire) block.getState();
            for (int i = 0; i < 4; i++) campfire.setItem(i, null);
            campfire.update(true);
        }
    }

    public void cleanUpKeepDrops(CookingItemData data, Location loc) {
        if (data == null) return;

        if (data.displayEntity != null) {
            removePreviewFromDisk(data);
            data.displayEntity.remove();
            data.displayEntity = null;
        }
        if (data.recipientDisplay != null) {
            data.recipientDisplay.remove();
            data.recipientDisplay = null;
        }

        CookingVisuals.stopSeasoningParticles(data);
        CookingVisuals.stopSauceParticles(data);

        if (data.stand != null) {
            if (data.stand.isValid()) {
                data.stand.getEquipment().setHelmet(null);
                data.stand.getEquipment().setItemInMainHand(null);
            }
            data.stand.remove();
        }

        if (loc != null) cookingSessions.remove(loc);

        if (data.seasonings != null) data.seasonings.clear();
        if (data.sauces != null) data.sauces.clear();
        if (data.ingredientIds != null) data.ingredientIds.clear();
        if (data.seasoningIds != null) data.seasoningIds.clear();
        if (data.sauceIds != null) data.sauceIds.clear();

        Block block = loc == null ? null : loc.getBlock();
        if (block != null && block.getType() == Material.CAMPFIRE) {
            org.bukkit.block.data.type.Campfire campfireData = (org.bukkit.block.data.type.Campfire) block.getBlockData();
            if (campfireData.isLit()) {
                campfireData.setLit(false);
                block.setBlockData(campfireData);
            }
            Campfire campfire = (Campfire) block.getState();
            for (int i = 0; i < 4; i++) campfire.setItem(i, null);
            campfire.update(true);
        }
    }

    public void removeCookingStandAt(Location loc) {
        if (loc == null) return;

        CookingItemData session = cookingSessions.get(loc);
        if (session != null && session.stand != null) {
            session.stand.getEquipment().setItemInMainHand(null);
            session.stand.remove();
            session.stand = null;
            return;
        }

        Location center = loc.clone().add(0.5, 0.5, 0.5);
        for (org.bukkit.entity.Entity ent : loc.getWorld().getNearbyEntities(center, 0.6, 0.6, 0.6)) {
            if (ent instanceof ArmorStand as) {
                if (as.getPersistentDataContainer().has(STAND_KEY, PersistentDataType.BOOLEAN)) {
                    as.getEquipment().setItemInMainHand(null);
                    as.remove();
                }
            }
        }
    }

    public boolean ensureSessionFresh(Block block) {
        if (block == null || block.getType() != Material.CAMPFIRE) return true;
        CookingItemData session = cookingSessions.get(block.getLocation());
        if (session == null) return true;

        boolean standValid = session.stand != null && session.stand.isValid();
        boolean hasPreview = session.displayEntity != null && session.displayEntity.isValid();
        boolean hasRecipientDisplay = session.recipientDisplay != null && session.recipientDisplay.isValid();
        boolean campfireHasItems = false;

        Campfire cf = (Campfire) block.getState();
        for (int i = 0; i < 4; i++) {
            ItemStack it = cf.getItem(i);
            if (it != null && !it.getType().isAir()) {
                campfireHasItems = true;
                break;
            }
        }

        if (session.cookingInProgress || session.cooked || session.burnt || standValid || hasPreview || hasRecipientDisplay || campfireHasItems)
            return true;

        cleanUp(session, block.getLocation());
        return true;
    }

    /**
     * Shutdown and cleanup all cooking sessions. Called when the server is stopping.
     * Removes all cooking entities to prevent orphaned entities on next server start.
     */
    public void shutdown() {
        Specialization.getInstance().getLogger().info("[Cooking] Shutting down cooking system...");

        // Clean up all active sessions
        for (Map.Entry<Location, CookingItemData> entry : new HashMap<>(cookingSessions).entrySet()) {
            CookingItemData data = entry.getValue();
            if (data == null) continue;

            // Cancel all tasks
            if (data.progressTask != null) { data.progressTask.cancel(); data.progressTask = null; }
            if (data.cookTask != null) { data.cookTask.cancel(); data.cookTask = null; }
            if (data.maintenanceTask != null) { data.maintenanceTask.cancel(); data.maintenanceTask = null; }
            if (data.burnTask != null) { data.burnTask.cancel(); data.burnTask = null; }
            if (data.flameTask != null) { data.flameTask.cancel(); data.flameTask = null; }
            if (data.holdTask != null) { data.holdTask.cancel(); data.holdTask = null; }
            if (data.seasoningTask != null) { data.seasoningTask.cancel(); data.seasoningTask = null; }
            if (data.sauceTask != null) { data.sauceTask.cancel(); data.sauceTask = null; }

            // Remove entities
            if (data.displayEntity != null && data.displayEntity.isValid()) {
                data.displayEntity.remove();
                data.displayEntity = null;
            }
            if (data.recipientDisplay != null && data.recipientDisplay.isValid()) {
                data.recipientDisplay.remove();
                data.recipientDisplay = null;
            }
            if (data.stand != null && data.stand.isValid()) {
                data.stand.remove();
                data.stand = null;
            }
        }

        cookingSessions.clear();
        cleanupOrphanedCookingEntities();
        Specialization.getInstance().getLogger().info("[Cooking] Cooking system shutdown complete.");
    }
}
