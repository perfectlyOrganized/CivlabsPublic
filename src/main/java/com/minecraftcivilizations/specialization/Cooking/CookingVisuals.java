package com.minecraftcivilizations.specialization.Cooking;

import org.bukkit.*;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import com.minecraftcivilizations.specialization.Specialization;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;

// CraftEngine imports (optional) - use reflectively if absent
import net.momirealms.craftengine.bukkit.api.BukkitAdaptors;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.world.Vec3d;
import org.joml.Vector3f;
import org.joml.AxisAngle4f;

public class CookingVisuals {

    private static final NamespacedKey RECIPIENT_KEY = new NamespacedKey(Specialization.getInstance(), "cooking_recipient");
    private static final NamespacedKey PREVIEW_KEY = new NamespacedKey(Specialization.getInstance(), "cooking_preview");

    public static void spawnOrUpdateDisplay(CookingItemData data) {
        if (data == null || data.food == null) return;
        // compute a non-mutating center location and lift the preview by +0.4 blocks to avoid clipping into the campfire
        final Location center;
        if (data.stand != null && data.stand.isValid()) {
            // use the stand position and raise slightly so the preview item is clearly above the campfire
            center = data.stand.getLocation().clone().add(0.0, 0.6, 0.0);
        } else if (data.campfireLocation != null) {
            // campfireLocation is a block location (integer coord) — use block center and raise by ~1.4
            // spawn higher so the preview item is clearly visible and not clipping into the campfire
            center = data.campfireLocation.clone().add(0.5, 2.1, 0.5);
        } else {
            center = null;
        }
        if (center == null) return;
        try {
            // Do not remove the recipient ItemDisplay here; it should stay visible while placed on the campfire.
            // The preview dropped item will be spawned slightly above the center so both are visible.
             // If there is an existing dropped item, update its itemstack and only correct position when necessary
            if (data.displayEntity != null && data.displayEntity.isValid()) {
                try { data.displayEntity.setItemStack(data.food.clone()); } catch (Throwable ignored) {}
                // Only correct horizontal drift (X/Z); preserve Y so the vanilla bobbing is not fought.
                try {
                    Location cur = data.displayEntity.getLocation();
                    double dx = cur.getX() - center.getX();
                    double dz = cur.getZ() - center.getZ();
                    double horizDistSq = dx*dx + dz*dz;
                    // if drifted more than ~0.25 blocks horizontally, teleport back to the center X/Z but keep current Y
                    if (horizDistSq > 0.25) {
                        Location target = center.clone();
                        target.setY(cur.getY()); // preserve current Y to not fight bobbing
                        try { data.displayEntity.teleport(target); } catch (Throwable ignored) {}
                        try { data.displayEntity.setVelocity(new Vector(0,0,0)); } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            } else {
                // spawn a real dropped Item so the client plays the vanilla bobbing animation
                Item dropped = center.getWorld().dropItem(center, data.food.clone());
                // configure to be a static preview: unpickable, invulnerable, persistent, no gravity, unlimited lifetime
                try { dropped.setPickupDelay(Integer.MAX_VALUE); } catch (Throwable ignored) {}
                try { dropped.setInvulnerable(true); } catch (Throwable ignored) {}
                try { dropped.setPersistent(true); } catch (Throwable ignored) {}
                try { dropped.getPersistentDataContainer().set(PREVIEW_KEY, PersistentDataType.BOOLEAN, true); } catch (Throwable ignored) {}
                // disable gravity so the preview stays still (sits on top of campfire)
                try { dropped.setGravity(false); } catch (Throwable ignored) {}
                try { dropped.setUnlimitedLifetime(true); } catch (Throwable ignored) {}
                try { dropped.setVelocity(new Vector(0,0,0)); } catch (Throwable ignored) {}
                // store entity
                data.displayEntity = dropped;

                // start a maintenance task that occasionally corrects the item's position if it drifts
                // Run less frequently (every 20 ticks) and only teleport when the item moved too far from center
                try { if (data.maintenanceTask != null) data.maintenanceTask.cancel(); } catch (Throwable ignored) {}
                data.maintenanceTask = new BukkitRunnable() {
                    @Override public void run() {
                        if (data.displayEntity == null || !data.displayEntity.isValid()) { cancel(); return; }
                        try {
                            Location cur = data.displayEntity.getLocation();
                            double dx = cur.getX() - center.getX();
                            double dz = cur.getZ() - center.getZ();
                            double horizDistSq = dx*dx + dz*dz;
                            // if moved more than ~0.5 blocks horizontally or Y drifted, teleport back to exact center
                            double dy = Math.abs(cur.getY() - center.getY());
                            if (horizDistSq > 0.5*0.5 || dy > 0.2) {
                                Location target = center.clone();
                                try { data.displayEntity.teleport(target); } catch (Throwable ignored) {}
                                try { data.displayEntity.setVelocity(new Vector(0,0,0)); } catch (Throwable ignored) {}
                                try { data.displayEntity.setGravity(false); } catch (Throwable ignored) {}
                            }
                        } catch (Throwable ignored) {}
                    }
                }.runTaskTimer(Specialization.getInstance(), 40L, 80L);
            }
            // ensure armor stand doesn't show item
            try {
                if (data.stand != null && data.stand.isValid()) {
                    try { data.stand.getEquipment().setHelmet(null); } catch (Throwable ignored) {}
                    try { data.stand.getEquipment().setItemInMainHand(null); } catch (Throwable ignored) {}
                    try { data.stand.getEquipment().setItemInOffHand(null); } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ex) {
            // logging removed
        }
    }

    public static void startProgressBar(CookingItemData data, Player starter, int durationTicks) {
        if (data == null) return;
        try {
            // Create bossbar and set initial title to show remaining seconds
            int totalTicks = Math.max(1, durationTicks);
            int totalSeconds = (totalTicks + 19) / 20; // ceil
            String initSuffix = (totalSeconds == 1) ? " second" : " seconds";
            Component initComp = MiniMessage.miniMessage().deserialize("<yellow>Cooking time: <white>" + totalSeconds + initSuffix);
            String initTitle = LegacyComponentSerializer.legacySection().serialize(initComp);
            BossBar bar = Bukkit.createBossBar(initTitle, BarColor.GREEN, BarStyle.SOLID);
            data.bossBar = bar;
            try { if (starter != null && starter.isOnline()) bar.addPlayer(starter); } catch (Throwable ignored) {}
            bar.setVisible(true);
            bar.setProgress(0.0);
            // schedule progress task (runs every tick so progress is smooth, title shows remaining seconds)
            try { if (data.progressTask != null) data.progressTask.cancel(); } catch (Throwable ignored) {}
            data.progressTask = new BukkitRunnable() {
                int tick = 0;
                @Override public void run() {
                    try {
                        // Determine center location for proximity checks
                        Location centerLoc = null;
                        if (data.stand != null && data.stand.isValid()) centerLoc = data.stand.getLocation();
                        else if (data.campfireLocation != null) centerLoc = data.campfireLocation.clone().add(0.5, 0.5, 0.5);

                        boolean inRange = false;
                        if (starter != null && starter.isOnline() && centerLoc != null) {
                            try {
                                double dist2 = starter.getLocation().distanceSquared(centerLoc);
                                // within 5 blocks allowed; pause if farther than 5 blocks
                                inRange = dist2 <= (5.0 * 5.0);
                            } catch (Throwable ignored) { inRange = false; }
                        }

                        // Show/hide boss bar based on range
                        if (data.bossBar != null) {
                            try {
                                data.bossBar.setVisible(inRange);
                                if (inRange && starter != null && starter.isOnline() && !data.bossBar.getPlayers().contains(starter)) {
                                    data.bossBar.addPlayer(starter);
                                }
                            } catch (Throwable ignored) {}
                        }

                        // If out of range, do not advance progress or spawn particles; keep the task alive to resume when player returns
                        if (!inRange) {
                            return;
                        }

                        tick++;
                        double progress = Math.min(1.0, (double) tick / (double) totalTicks);
                        int secondsLeft = Math.max(0, (totalTicks - tick + 19) / 20);
                        if (data.bossBar != null) {
                            try { data.bossBar.setProgress(progress); } catch (Throwable ignored) {}
                            try {
                                String tickSuffix = (secondsLeft == 1) ? " second" : " seconds";
                                Component tickComp = MiniMessage.miniMessage().deserialize("<yellow>Cooking time: <white>" + secondsLeft + tickSuffix);
                                String tickTitle = LegacyComponentSerializer.legacySection().serialize(tickComp);
                                data.bossBar.setTitle(tickTitle);
                            } catch (Throwable ignored) {}
                        }

                        // Spawn a small flame particle effect periodically so the campfire visually shows flames while cooking.
                        // Do this every 5 ticks to avoid excessive particles.
                        try {
                            if (tick % 5 == 0) {
                                Location particleLoc = null;
                                if (data.stand != null && data.stand.isValid()) particleLoc = data.stand.getLocation().add(0, 0.6, 0);
                                else if (data.campfireLocation != null) particleLoc = data.campfireLocation.clone().add(0.5, 0.9, 0.5);
                                if (particleLoc != null) {
                                    try { particleLoc.getWorld().spawnParticle(Particle.FLAME, particleLoc, 6, 0.12, 0.12, 0.12, 0.02); } catch (Throwable ignored) {}
                                }
                            }
                        } catch (Throwable ignored) {}

                        if (tick >= totalTicks) {
                            // finished; stop task (cleanup will be done by caller)
                            try { this.cancel(); } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                }
            }.runTaskTimer(Specialization.getInstance(), 0, 1);
        } catch (Throwable ignored) {}
    }

    public static void stopProgressBar(CookingItemData data) {
        if (data == null) return;
        try { if (data.progressTask != null) { data.progressTask.cancel(); data.progressTask = null; } } catch (Throwable ignored) {}
        try { if (data.bossBar != null) { data.bossBar.removeAll(); data.bossBar.setVisible(false); data.bossBar = null; } } catch (Throwable ignored) {}
    }

    public static void removeDisplay(CookingItemData data) {
        if (data == null) return;
        try {
            if (data.maintenanceTask != null) { data.maintenanceTask.cancel(); data.maintenanceTask = null; }
        } catch (Throwable ignored) {}
        try {
            if (data.displayEntity != null) {
                try { data.displayEntity.remove(); } catch (Throwable ignored) {}
                data.displayEntity = null;
            }
        } catch (Throwable ignored) {}
    }

    public static void cleanupVisuals(CookingItemData data) {
        if (data == null) return;
        stopProgressBar(data);
        removeDisplay(data);
    }

    public static void playFinishEffects(CookingItemData data, Player starter) {
        // Try to play CraftEngine .ogg sound first (configurable via cookingConfig.finish_sound)
        String soundId = "specialization:cooking_success";
        try {
            try { soundId = SpecializationConfig.getCookingConfig().getString("finish_sound"); } catch (Throwable ignored) {}
            if (soundId == null || soundId.isBlank()) soundId = "specialization:cooking_success";
            Location loc = data != null && data.campfireLocation != null ? data.campfireLocation.clone().add(0.4, 0.4, 0.4) : (starter != null ? starter.getLocation() : null);
            // logging removed
             if (loc != null) {
                 var ceWorld = BukkitAdaptors.adapt(loc.getWorld());
                 // assume ceWorld is non-null when CraftEngine is present; any exception will be caught below
                 Vec3d pos = LocationUtils.toVec3d(loc);
                 ceWorld.playSound(pos, Key.of(soundId), 1f, 1f, SoundSource.PLAYER);
                 // logging removed
             }
         } catch (Throwable t) {
            // logging removed
             // fallback to simple jingle for the starter
             try {
                 if (starter != null && starter.isOnline()) playItemGet(starter);
                 // also play a quick bukkit fallback at the location for testing
                 Location fallbackLoc = data != null && data.campfireLocation != null ? data.campfireLocation.clone().add(0.5, 0.5, 0.5) : (starter != null ? starter.getLocation() : null);
                 if (fallbackLoc != null) {
                     fallbackLoc.getWorld().playSound(fallbackLoc, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 1f, 1f);
                 }
             } catch (Throwable ignored2) {}
         }

        try {
            if (data != null && data.stand != null) {
                data.stand.getWorld().spawnParticle(Particle.FLAME, data.stand.getLocation().add(0,0.6,0), 20, 0.2,0.2,0.2, 0.05);
            }
        } catch (Throwable ignored) {}
    }

    public static void playStartEffects(CookingItemData data) {
        if (data == null) return;
        try {
            if (data.stand != null && data.stand.isValid()) {
                data.stand.getWorld().playSound(data.stand.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 1.0f, 0.4f);
            }
        } catch (Throwable ignored) {}
    }

    public static void playCancelEffects(CookingItemData data) {
        if (data == null) return;
        try {
            if (data.stand != null && data.stand.isValid()) {
                data.stand.getWorld().playSound(data.stand.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.4f);
                data.stand.getWorld().spawnParticle(Particle.LARGE_SMOKE, data.stand.getLocation().add(0, 0.6, 0), 8, 0.2, 0.2, 0.2, 0.01);
            }
        } catch (Throwable ignored) {}
    }

    public static void playBreakEffects(CookingItemData data) {
        if (data == null) return;
        try {
            if (data.stand != null && data.stand.isValid()) {
                data.stand.getWorld().playSound(data.stand.getLocation(), Sound.ENTITY_ARMOR_STAND_BREAK, 1f, 0.4f);
                data.stand.getWorld().spawnParticle(Particle.SMOKE, data.stand.getLocation().add(0, 0.5, 0), 8, 0.2, 0.2, 0.2, 0.01);
            }
        } catch (Throwable ignored) {}
    }

    // Also play a world-level finish sound so nearby players hear it (called when cooking completes)
    public static void playWorldFinishSound(CookingItemData data) {
        try {
            String soundId = "specialization:cooking_success";
            try { soundId = SpecializationConfig.getCookingConfig().getString("finish_sound"); } catch (Throwable ignored) {}
            if (soundId == null || soundId.isBlank()) soundId = "specialization:cooking_success";
            Location loc = data.campfireLocation != null ? data.campfireLocation.clone().add(0.5, 0.5, 0.5) : (data.stand != null ? data.stand.getLocation() : null);
            // logging removed
             if (loc != null) {
                 try {
                     var ceWorld = BukkitAdaptors.adapt(loc.getWorld());
                     if (ceWorld == null) throw new IllegalStateException("BukkitAdaptors.adapt returned null (CraftEngine not available)");
                     Vec3d pos = LocationUtils.toVec3d(loc);
                     ceWorld.playSound(pos, Key.of(soundId), 1f, 1f, SoundSource.PLAYER);
                     // logging removed
                     return;
                 } catch (Throwable t) {
                    // logging removed
                 }

                 // fallback: play jingle to nearby players and a bukkit pling for debug
                 for (org.bukkit.entity.Entity e : loc.getWorld().getNearbyEntities(loc, 16, 16, 16)) {
                     if (e instanceof Player p) {
                         try { playItemGet(p); } catch (Throwable ignored) {}
                     }
                 }
                 try { loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, SoundCategory.PLAYERS, 1f, 1f); } catch (Throwable ignored) {}
             }
         } catch (Throwable ignored) {}
    }

    public static void playItemGet(Player player) {
        // Short, simple jingle when item is obtained
        int[] notes = {24, 19, 21, 24};
        int[] delays = {0, 6, 12, 18};
        for (int i = 0; i < notes.length; i++) {
            final int note = notes[i];
            final long delay = delays[Math.min(i, delays.length - 1)];
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        player.playSound(
                                player.getLocation(),
                                Sound.BLOCK_NOTE_BLOCK_BELL,
                                1.0f,
                                (float) Math.pow(2, (note - 12) / 12.0)
                        );
                    } catch (Throwable ignored) {}
                }
            }.runTaskLater(Specialization.getInstance(), delay);
        }
    }

    public static void spawnRecipientDisplay(CookingItemData data) {
        if (data == null || data.recipient == null || data.campfireLocation == null) return;
        try {
            // compute center of campfire; position slightly lower so it visually lies on the campfire
            Location center = data.campfireLocation.clone().add(0.5, 0.5, 0.5);
            // remove previous if present
            try { if (data.recipientDisplay != null) data.recipientDisplay.remove(); } catch (Throwable ignored) {}
            org.bukkit.World w = center.getWorld();
            org.bukkit.entity.Entity e = w.spawnEntity(center, org.bukkit.entity.EntityType.ITEM_DISPLAY);
            if (e instanceof org.bukkit.entity.ItemDisplay disp) {
                try { disp.setItemStack(data.recipient.clone()); } catch (Throwable ignored) {}
                try { disp.setInvulnerable(true); } catch (Throwable ignored) {}
                try { disp.setPersistent(true); } catch (Throwable ignored) {}
                try {
                    // store session-specific key to avoid collisions between multiple campfires using the same recipient
                    if (data.campfireLocation != null) {
                        String sessionKey = data.campfireLocation.getWorld().getName() + ":" + data.campfireLocation.getBlockX() + "," + data.campfireLocation.getBlockY() + "," + data.campfireLocation.getBlockZ();
                        disp.getPersistentDataContainer().set(RECIPIENT_KEY, PersistentDataType.STRING, sessionKey);
                    } else {
                        disp.getPersistentDataContainer().set(RECIPIENT_KEY, PersistentDataType.STRING, data.recipientId);
                    }
                } catch (Throwable ignored) {}
                try { disp.setGravity(false); } catch (Throwable ignored) {}
                // Rotate so the item is lying flat and rotated horizontally.
                // Determine yaw relative to the campfire facing (so the preview lies correctly on different campfire orientations)
                try {
                    float yawDeg = 90f; // default yaw
                    try {
                        if (data.campfireLocation != null) {
                            org.bukkit.block.Block b = data.campfireLocation.getBlock();
                            org.bukkit.block.data.BlockData bd = b.getBlockData();
                            if (bd instanceof Directional d) {
                                BlockFace face = d.getFacing();
                                switch (face) {
                                    case NORTH -> yawDeg = 180f;
                                    case EAST -> yawDeg = -90f;
                                    case SOUTH -> yawDeg = 0f;
                                    case WEST -> yawDeg = 90f;
                                    case NORTH_EAST -> yawDeg = -135f;
                                    case NORTH_WEST -> yawDeg = 135f;
                                    case SOUTH_EAST -> yawDeg = -45f;
                                    case SOUTH_WEST -> yawDeg = 45f;
                                    // WEST and other faces keep default 90f
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                    // pitch (first arg) controls the tilt; some server builds expect (pitch, yaw) ordering — preserve original pitch of 90
                    try { disp.setRotation(yawDeg, 90f); } catch (Throwable ignored) {}
                    // Scale to approximate a normal dropped item size and ensure it lies flat: use Transformation (JOML)
                    try {
                        // Normal item size roughly 0.5 on each axis; adjust if you want larger/smaller
                        Vector3f translation = new Vector3f(0f, 0f, 0f);
                        AxisAngle4f leftRot = new AxisAngle4f();
                        Vector3f scale = new Vector3f(0.7f, 0.7f, 0.7f);
                        AxisAngle4f rightRot = new AxisAngle4f();
                        disp.setTransformation(new org.bukkit.util.Transformation(translation, leftRot, scale, rightRot));
                        // additionally rotate the display by -90 degrees yaw around Y to lie horizontally
                        try { disp.setRotation(yawDeg - 90f, 90f); } catch (Throwable ignored) {}
                    } catch (Throwable ignored) {}
                } catch (Throwable ignored) {}
                // persist reference so cleanup can remove it
                try { data.recipientDisplay = disp; } catch (Throwable ignored) {}
             }
        } catch (Throwable ex) {
            // logging removed
        }
    }

    public static void playSeasoningEffect(Block block, ItemStack seasoning) {
        if (block == null || seasoning == null) return;
        try {
            Location center = block.getLocation().add(0.5, 0.9, 0.5);
            String id = null;
            try { id = getItemIdFromStack(seasoning); } catch (Throwable ignored) {}
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
        } catch (Throwable ignored) {}
    }

    private static String getItemIdFromStack(ItemStack item) {
        try {
            net.momirealms.craftengine.core.item.Item<ItemStack> wrapped = net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance().wrap(item);
            return wrapped.getCustomItem().isPresent() ? wrapped.getCustomItem().get().id().value() : "minecraft:" + item.getType().name().toLowerCase();
        } catch (Throwable ignored) {
            return "minecraft:" + item.getType().name().toLowerCase();
        }
    }
}
