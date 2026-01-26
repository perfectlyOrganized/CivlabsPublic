package com.minecraftcivilizations.specialization.Cooking;

import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;
import com.minecraftcivilizations.specialization.Specialization;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.data.Directional;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;

// CraftEngine imports (optional) - use reflectively if absent
import net.momirealms.craftengine.bukkit.api.BukkitAdaptors;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.world.Vec3d;
import org.joml.Matrix4f;

public class CookingVisuals {

    private static final NamespacedKey RECIPIENT_KEY = new NamespacedKey(Specialization.getInstance(), "cooking_recipient");
    private static final NamespacedKey PREVIEW_KEY = new NamespacedKey(Specialization.getInstance(), "cooking_preview");

    public static void spawnOrUpdateDisplay(CookingItemData data) {
        if (data == null || data.food == null) return;
        final Location center;
        if (data.stand != null && data.stand.isValid()) {
            // place slightly above the stand so the item is clearly above the campfire
            center = data.stand.getLocation().clone().add(0.0, 0.4, 0.0);
        } else if (data.campfireLocation != null) {
            // place the preview about 1.3 blocks above the campfire block to avoid collision-push
            center = data.campfireLocation.clone().add(0.5, 0.4, 0.5);
        } else {
            return;
        }

        try {
            if (data.displayEntity != null && data.displayEntity.isValid()) {
                data.displayEntity.setItemStack(data.food.clone());
                data.displayEntity.teleport(center);
                data.displayEntity.setVelocity(new Vector(0,0,0));
                data.displayEntity.setGravity(false);
                data.displayEntity.setInvulnerable(true);
                try { data.displayEntity.setUnlimitedLifetime(true); } catch (Throwable t) { /* older server may not support */ }
                data.displayEntity.setPickupDelay(Integer.MAX_VALUE);
                // ensure strict fix immediately when updating
                forceFixPreviewPosition(data, center);
            } else {
                 Item dropped = center.getWorld().dropItem(center, data.food.clone());
                 // configure preview as a static dropped Item (no gravity, unpickable, persistent)
                 dropped.setPickupDelay(Integer.MAX_VALUE);
                 dropped.setInvulnerable(true);
                 try { dropped.setPersistent(true); } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[CookingVisuals] setPersistent failed: " + ex.getMessage()); }
                 try { dropped.getPersistentDataContainer().set(PREVIEW_KEY, PersistentDataType.BOOLEAN, true); } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[CookingVisuals] set preview pdc failed: " + ex.getMessage()); }
                 dropped.setGravity(false);
                 try { dropped.setUnlimitedLifetime(true); } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[CookingVisuals] setUnlimitedLifetime failed: " + ex.getMessage()); }
                 dropped.setVelocity(new Vector(0,0,0));
                 try { dropped.teleport(center); } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[CookingVisuals] teleport preview failed: " + ex.getMessage()); }
                 data.displayEntity = dropped;
                 // ensure strict fix immediately
                 forceFixPreviewPosition(data, center);
              }

            // schedule a maintenance task to reapply static flags and teleport the preview back if it moved slightly
            if (data.maintenanceTask != null) { data.maintenanceTask.cancel(); data.maintenanceTask = null; }
            data.maintenanceTask = new BukkitRunnable() {
                @Override public void run() {
                    if (data == null || data.destroyed) { cancel(); return; }
                    try {
                        if (data.displayEntity == null || !data.displayEntity.isValid()) { return; }
                        // strict re-teleport every tick if slightly off to keep preview fixed
                        Location cur = data.displayEntity.getLocation();
                        double dx = cur.distanceSquared(center);
                        if (dx > 0.0001) { // threshold ~0.01 block
                            data.displayEntity.teleport(center);
                        }
                        // reapply static properties every tick to avoid physics drift
                        data.displayEntity.setVelocity(new Vector(0,0,0));
                        data.displayEntity.setGravity(false);
                        try { data.displayEntity.setUnlimitedLifetime(true); } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[CookingVisuals] refresh setUnlimitedLifetime failed: " + ex.getMessage()); }
                        data.displayEntity.setPickupDelay(Integer.MAX_VALUE);
                        data.displayEntity.setInvulnerable(true);
                    } catch (Throwable t) {
                        Specialization.getInstance().getLogger().warning("[CookingVisuals] maintenanceTask error: " + t.getMessage());
                    }
                }
            }.runTaskTimer(Specialization.getInstance(), 1L, 1L);
           } catch (Exception ex) {
              Specialization.getInstance().getLogger().warning("[CookingVisuals] spawnOrUpdateDisplay error: " + ex.getMessage());
          }
     }

    // Helper that teleports preview to center and reapplies static flags; safe to call repeatedly
    public static void forceFixPreviewPosition(CookingItemData data, Location center) {
        if (data == null || center == null) return;
        try {
            if (data.displayEntity == null || !data.displayEntity.isValid()) return;
            data.displayEntity.teleport(center);
            data.displayEntity.setVelocity(new Vector(0,0,0));
            data.displayEntity.setGravity(false);
            try { data.displayEntity.setUnlimitedLifetime(true); } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[CookingVisuals] forceFix setUnlimitedLifetime failed: " + ex.getMessage()); }
            data.displayEntity.setPickupDelay(Integer.MAX_VALUE);
            data.displayEntity.setInvulnerable(true);
        } catch (Throwable t) {
            Specialization.getInstance().getLogger().warning("[CookingVisuals] forceFixPreviewPosition error: " + t.getMessage());
        }
    }

    public static void markReady(CookingItemData data) {
         if (data == null) return;
        // Keep maintenanceTask running so preview remains fixed; ensure preview is positioned and static now
         try {
            Location center = data.campfireLocation != null ? data.campfireLocation.clone().add(0.5, 0.4, 0.5) : (data.stand != null ? data.stand.getLocation().clone().add(0, 0.4, 0) : null);
             if (center != null) {
                 if (data.burnt) {
                     center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center, 20, 0.2, 0.2, 0.2, 0.01);
                 } else {
                     try { center.getWorld().spawnParticle(Particle.GLOW, center, 20, 0.15, 0.15, 0.15, 0.0); } catch (IllegalArgumentException iae) { center.getWorld().spawnParticle(Particle.END_ROD, center, 20, 0.15, 0.15, 0.15, 0.0); }
                 }
                // Ensure preview item is forced to exact center and made static (prevents drift when becoming ready)
                if (data.displayEntity != null && data.displayEntity.isValid()) {
                    try {
                        data.displayEntity.teleport(center);
                    } catch (Throwable t) {}
                    try { data.displayEntity.setVelocity(new Vector(0,0,0)); } catch (Throwable t) {}
                    try { data.displayEntity.setGravity(false); } catch (Throwable t) {}
                    try { data.displayEntity.setPickupDelay(Integer.MAX_VALUE); } catch (Throwable t) {}
                    try { data.displayEntity.setInvulnerable(true); } catch (Throwable t) {}
                }
                 if (data.progressTask != null) data.progressTask.cancel();
                 data.progressTask = new BukkitRunnable() {
                     int ticks = 0;
                     @Override public void run() {
                         if (data.destroyed) { cancel(); return; }
                         ticks++;
                         if (ticks > (20 * 20)) { cancel(); return; }
                         try {
                             if (data.burnt) center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center, 8, 0.2, 0.2, 0.2, 0.01);
                             else {
                                 try { center.getWorld().spawnParticle(Particle.GLOW, center, 8, 0.12, 0.12, 0.12, 0.0); } catch (IllegalArgumentException iae) { center.getWorld().spawnParticle(Particle.END_ROD, center, 8, 0.12, 0.12, 0.12, 0.0); }
                             }
                         } catch (Exception e) {
                             Specialization.getInstance().getLogger().warning("[CookingVisuals] progressTask error: " + e.getMessage());
                         }
                     }
                 }.runTaskTimer(Specialization.getInstance(), 0L, 20L);
             }
         } catch (Exception ex) {
             Specialization.getInstance().getLogger().warning("[CookingVisuals] markReady error: " + ex.getMessage());
         }
     }

    public static void stopProgressBar(CookingItemData data) {
        if (data == null) return;
        if (data.progressTask != null) { data.progressTask.cancel(); data.progressTask = null; }
    }

    public static void removeDisplay(CookingItemData data) {
        if (data == null) return;
        if (data.maintenanceTask != null) { data.maintenanceTask.cancel(); data.maintenanceTask = null; }
        if (data.displayEntity != null) {
            data.displayEntity.remove();
            data.displayEntity = null;
        }
    }

    public static void cleanupVisuals(CookingItemData data) {
        if (data == null) return;
        stopProgressBar(data);
        removeDisplay(data);
        if (data.recipientDisplay != null) { data.recipientDisplay.remove(); data.recipientDisplay = null; }
        stopAmbient(data);
    }

    public static void playFinishEffects(CookingItemData data, Player starter, boolean success) {
        String defaultSuccess = "specialization:cooking_success";
        String defaultFail = "specialization:cooking_fail";
        String soundId = success ? defaultSuccess : defaultFail;
        try {
            String cfg = SpecializationConfig.getCookingConfig().getString(success ? "finish_sound" : "fail_sound");
            if (cfg != null && !cfg.isBlank()) soundId = cfg;
        } catch (Exception e) {
            Specialization.getInstance().getLogger().warning("[CookingVisuals] failed reading finish_sound config: " + e.getMessage());
        }
        Location loc = data != null && data.campfireLocation != null ? data.campfireLocation.clone().add(0.4, 0.4, 0.4) : (starter != null ? starter.getLocation() : null);
        if (loc != null) {
            try {
                var ceWorld = BukkitAdaptors.adapt(loc.getWorld());
                Vec3d pos = LocationUtils.toVec3d(loc);
                ceWorld.playSound(pos, Key.of(soundId), 1f, 1f, SoundSource.PLAYER);
            } catch (Throwable t) {
                Specialization.getInstance().getLogger().warning("[CookingVisuals] craftengine playSound failed: " + t.getMessage());
            }
        }
        try {
            if (data != null && data.stand != null) {
                data.stand.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, data.stand.getLocation().add(0, 0.6, 0), 20, 0.2, 0.2, 0.2, 0.05);
            }
        } catch (Exception ex) {
            Specialization.getInstance().getLogger().warning("[CookingVisuals] playFinishEffects particle error: " + ex.getMessage());
        }
        stopAmbient(data);
    }

    // Spawn only flame particles (no sound) — used repeatedly by maintenance task to avoid sound spam
    public static void spawnFlameParticles(CookingItemData data) {
        if (data == null) return;
        try {
            if (data.stand != null && data.stand.isValid()) {
                data.stand.getWorld().spawnParticle(Particle.FLAME, data.stand.getLocation().add(0, 0.6, 0), 4, 0.08, 0.08, 0.08, 0.01);
            } else if (data.campfireLocation != null) {
                Location center = data.campfireLocation.clone().add(0.5, 0.9, 0.5);
                center.getWorld().spawnParticle(Particle.FLAME, center, 4, 0.08, 0.08, 0.08, 0.01);
            }
        } catch (Exception e) {
            Specialization.getInstance().getLogger().warning("[CookingVisuals] spawnFlameParticles error: " + e.getMessage());
        }
    }

    public static void playStartEffects(CookingItemData data) {
        if (data == null) return;
        try {
            Location center;
            if (data.stand != null && data.stand.isValid()) {
                center = data.stand.getLocation();
                if (data.ambientTask != null) { data.ambientTask.cancel(); data.ambientTask = null; }
                data.ambientTask = new BukkitRunnable() {
                    @Override public void run() {
                        if (data == null || data.destroyed) { cancel(); return; }
                        try {
                            data.stand.getWorld().playSound(data.stand.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1.0f, 0.4f);
                        } catch (Exception e) {
                            Specialization.getInstance().getLogger().warning("[CookingVisuals] ambient play error: " + e.getMessage());
                        }
                    }
                }.runTaskTimer(Specialization.getInstance(), 0L, 60L);
                data.stand.getWorld().spawnParticle(Particle.FLAME, data.stand.getLocation().add(0, 0.6, 0), 6, 0.08, 0.08, 0.08, 0.01);
            } else if (data.campfireLocation != null) {
                center = data.campfireLocation.clone().add(0.5, 0.9, 0.5);
                if (data.ambientTask != null) { data.ambientTask.cancel(); data.ambientTask = null; }
                data.ambientTask = new BukkitRunnable() {
                    @Override public void run() {
                        if (data == null || data.destroyed) { cancel(); return; }
                        try {
                            center.getWorld().playSound(center, Sound.BLOCK_FIRE_AMBIENT, 1.0f, 0.4f);
                        } catch (Exception e) {
                            Specialization.getInstance().getLogger().warning("[CookingVisuals] ambient play error: " + e.getMessage());
                        }
                    }
                }.runTaskTimer(Specialization.getInstance(), 0L, 60L);
                center.getWorld().spawnParticle(Particle.FLAME, center, 6, 0.08, 0.08, 0.08, 0.01);
            }

            // Re-apply static flags to preview if present (prevents movement when cooking starts)
            if (data.displayEntity != null && data.displayEntity.isValid()) {
                try {
                    if (data.campfireLocation != null) data.displayEntity.teleport(data.campfireLocation.clone().add(0.5, 0.4, 0.5));
                } catch (Throwable t) { Specialization.getInstance().getLogger().warning("[CookingVisuals] teleport preview failed: " + t.getMessage()); }
                try { data.displayEntity.setVelocity(new Vector(0,0,0)); } catch (Throwable t) { Specialization.getInstance().getLogger().warning("[CookingVisuals] setVelocity failed: " + t.getMessage()); }
                try { data.displayEntity.setGravity(false); } catch (Throwable t) { Specialization.getInstance().getLogger().warning("[CookingVisuals] setGravity failed: " + t.getMessage()); }
                try { data.displayEntity.setPickupDelay(Integer.MAX_VALUE); } catch (Throwable t) { Specialization.getInstance().getLogger().warning("[CookingVisuals] setPickupDelay failed: " + t.getMessage()); }
                try { data.displayEntity.setInvulnerable(true); } catch (Throwable t) { Specialization.getInstance().getLogger().warning("[CookingVisuals] setInvulnerable failed: " + t.getMessage()); }
            }
        } catch (Exception ex) {
            Specialization.getInstance().getLogger().warning("[CookingVisuals] playStartEffects error: " + ex.getMessage());
        }
    }

    public static void playCancelEffects(CookingItemData data) {
        if (data == null) return;
        try {
            if (data.stand != null && data.stand.isValid()) {
                data.stand.getWorld().playSound(data.stand.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.4f);
                data.stand.getWorld().spawnParticle(Particle.LARGE_SMOKE, data.stand.getLocation().add(0, 0.6, 0), 8, 0.2, 0.2, 0.2, 0.01);
            } else if (data.campfireLocation != null) {
                Location center = data.campfireLocation.clone().add(0.5, 0.9, 0.5);
                center.getWorld().playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.4f);
                center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center, 8, 0.2, 0.2, 0.2, 0.01);
            }
        } catch (Exception ex) {
            Specialization.getInstance().getLogger().warning("[CookingVisuals] playCancelEffects error: " + ex.getMessage());
        }
        stopAmbient(data);
    }

    public static void playBreakEffects(CookingItemData data) {
        if (data == null) return;
        try {
            if (data.stand != null && data.stand.isValid()) {
                data.stand.getWorld().playSound(data.stand.getLocation(), Sound.ENTITY_ARMOR_STAND_BREAK, 1f, 0.4f);
                data.stand.getWorld().spawnParticle(Particle.SMOKE, data.stand.getLocation().add(0, 0.5, 0), 8, 0.2, 0.2, 0.2, 0.01);
            }
        } catch (Exception ex) {
            Specialization.getInstance().getLogger().warning("[CookingVisuals] playBreakEffects error: " + ex.getMessage());
        }
        stopAmbient(data);
    }

    public static void playWorldFinishSound(CookingItemData data, boolean success) {
        String defaultSuccess = "specialization:cooking_success";
        String defaultFail = "specialization:cooking_failed";
        String soundId = success ? defaultSuccess : defaultFail;
        try {
            String cfg = SpecializationConfig.getCookingConfig().getString(success ? "finish_sound" : "fail_sound");
            if (cfg != null && !cfg.isBlank()) soundId = cfg;
        } catch (Exception e) {
            Specialization.getInstance().getLogger().warning("[CookingVisuals] failed reading finish_sound config: " + e.getMessage());
        }
        Location loc = data.campfireLocation != null ? data.campfireLocation.clone().add(0.5, 0.5, 0.5) : (data.stand != null ? data.stand.getLocation() : null);
        if (loc != null) {
            try {
                var ceWorld = BukkitAdaptors.adapt(loc.getWorld());
                Vec3d pos = LocationUtils.toVec3d(loc);
                ceWorld.playSound(pos, Key.of(soundId), 1f, 1f, SoundSource.PLAYER);
            } catch (Throwable t) {
                Specialization.getInstance().getLogger().warning("[CookingVisuals] craftengine playSound failed: " + t.getMessage());
            }
        }
        stopAmbient(data);
    }

    public static void spawnRecipientDisplay(CookingItemData data) {
        if (data == null || data.recipient == null || data.campfireLocation == null) return;
        Location center = data.campfireLocation.clone().add(0.5, 0.5, 0.5);
        try {
            if (data.recipientDisplay != null) {
                data.recipientDisplay.remove();
                data.recipientDisplay = null;
            }
            org.bukkit.World w = center.getWorld();
            org.bukkit.entity.Entity e = w.spawnEntity(center, org.bukkit.entity.EntityType.ITEM_DISPLAY);
            if (e instanceof org.bukkit.entity.ItemDisplay disp) {
                disp.setItemStack(data.recipient.clone());
                disp.setInvulnerable(true);
                disp.setPersistent(true);
                if (data.campfireLocation != null) {
                    String sessionKey = data.campfireLocation.getWorld().getName() + ":" + data.campfireLocation.getBlockX() + "," + data.campfireLocation.getBlockY() + "," + data.campfireLocation.getBlockZ();
                    disp.getPersistentDataContainer().set(RECIPIENT_KEY, PersistentDataType.STRING, sessionKey);
                } else {
                    disp.getPersistentDataContainer().set(RECIPIENT_KEY, PersistentDataType.STRING, data.recipientId);
                }

                disp.setGravity(false);

                // Use campfire-facing (original behavior) and then rotate each axis by -90 degrees
                float yawDeg = 90f;
                if (data.stand != null && data.stand.isValid()) {
                    yawDeg = data.stand.getLocation().getYaw();
                } else if (data.campfireLocation != null) {
                    org.bukkit.block.Block b = data.campfireLocation.getBlock();
                    org.bukkit.block.data.BlockData bd = b.getBlockData();
                    if (bd instanceof Directional d) {
                        switch (d.getFacing()) {
                            case NORTH -> yawDeg = 180f;
                            case EAST -> yawDeg = -90f;
                            case SOUTH -> yawDeg = 0f;
                            case NORTH_EAST -> yawDeg = -135f;
                            case NORTH_WEST -> yawDeg = 135f;
                            case SOUTH_EAST -> yawDeg = -45f;
                            case SOUTH_WEST -> yawDeg = 45f;
                            default -> yawDeg = 90f;
                        }
                    }
                }
                float computedYaw = yawDeg - 90f;

                // Apply yaw/pitch rotation
                try { disp.setRotation(computedYaw, 90f); } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[CookingVisuals] setRotation failed: " + ex.getMessage()); }

                // Now rotate each axis by -90 degrees via Transformation (use Matrix4f to compose rotations around X,Y,Z)
                try {
                    // Rotate only around X by -90 degrees so the item lies flat on the campfire, keep yaw for facing
                    Matrix4f mat = new Matrix4f()
                            .rotateX((float)Math.toRadians(-90f))
                            .rotateX((float)Math.toRadians(90f))
                            .rotateZ((float)Math.toRadians(90f))
                            .scale(0.7f);
                     disp.setTransformationMatrix(mat);
                  } catch (Exception ex) { Specialization.getInstance().getLogger().warning("[CookingVisuals] setTransformation failed: " + ex.getMessage()); }
                 data.recipientDisplay = disp;
             }
         } catch (Exception ex) {
             Specialization.getInstance().getLogger().warning("[CookingVisuals] spawnRecipientDisplay error: " + ex.getMessage());
         }
     }

    public static void playSeasoningEffect(Block block, ItemStack seasoning) {
        if (block == null || seasoning == null) return;
        try {
            Location center = block.getLocation().add(0.5, 0.9, 0.5);
            String id = getItemIdFromStack(seasoning);
            org.bukkit.Color color = switch (id == null ? "" : id) {
                case "specialization:salt" -> org.bukkit.Color.fromBGR(255, 255, 255);
                case "minecraft:sugar" -> org.bukkit.Color.fromBGR(255, 255, 255);
                case "minecraft:cocoa_beans" -> org.bukkit.Color.fromBGR(85, 53, 26);
                case "minecraft:honey_bottle" -> org.bukkit.Color.fromBGR(255, 200, 0);
                case "minecraft:glow_lichen" -> org.bukkit.Color.fromBGR(120, 255, 120);
                case "minecraft:blaze_powder" -> org.bukkit.Color.fromBGR(255, 150, 50);
                default -> org.bukkit.Color.fromBGR(200, 200, 200);
            };
            org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(color, 1f);
            try {
                Particle p = Particle.valueOf("REDSTONE");
                block.getWorld().spawnParticle(p, center, 20, 0.2, 0.2, 0.2, 0.01, dust);
            } catch (IllegalArgumentException iae) {
                block.getWorld().spawnParticle(Particle.SMOKE, center, 8, 0.2, 0.2, 0.2, 0.01);
            }
        } catch (Exception e) {
            Specialization.getInstance().getLogger().warning("[CookingVisuals] playSeasoningEffect error: " + e.getMessage());
        }
    }

    private static String getItemIdFromStack(ItemStack stack) {
        if (stack == null) return null;
        // Attempt CraftEngine id extraction - fallback to minecraft:id
        try {
            var wrapped = net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance().wrap(stack);
            if (wrapped != null && wrapped.getCustomItem().isPresent()) return wrapped.getCustomItem().get().id().value();
        } catch (Throwable ignored) {}
        return "minecraft:" + stack.getType().name().toLowerCase();
    }

    public static void stopAmbient(CookingItemData data) {
        if (data == null) return;
        if (data.ambientTask != null) { try { data.ambientTask.cancel(); } catch (Exception e) { Specialization.getInstance().getLogger().warning("[CookingVisuals] stopAmbient cancel error: " + e.getMessage()); } data.ambientTask = null; }
    }

}
