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

import net.momirealms.craftengine.bukkit.api.BukkitAdaptors;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import org.joml.Matrix4f;

public class CookingVisuals {

    private static final NamespacedKey RECIPIENT_KEY = new NamespacedKey(Specialization.getInstance(), "cooking_recipient");
    private static final NamespacedKey PREVIEW_KEY = new NamespacedKey(Specialization.getInstance(), "cooking_preview");

    public static void spawnOrUpdateDisplay(CookingItemData data) {
        if (data == null || data.food == null) return;

        final Location center;
        if (data.stand != null && data.stand.isValid()) {
            center = data.stand.getLocation().clone().add(0.0, 0.4, 0.0);
        } else if (data.campfireLocation != null) {
            center = data.campfireLocation.clone().add(0.5, 0.4, 0.5);
        } else {
            return;
        }

        if (data.displayEntity != null && data.displayEntity.isValid()) {
            ItemStack cur = data.displayEntity.getItemStack();
            if (cur == null || !cur.isSimilar(data.food)) {
                data.displayEntity.setItemStack(data.food.clone());
            }
            data.displayEntity.teleport(center);
            data.displayEntity.setVelocity(new Vector(0, 0, 0));
            data.displayEntity.setGravity(false);
            data.displayEntity.setPickupDelay(Integer.MAX_VALUE);
            data.displayEntity.setInvulnerable(true);
            forceFixPreviewPosition(data, center);
        } else {
            Item dropped = center.getWorld().dropItem(center, data.food.clone());
            dropped.setPickupDelay(Integer.MAX_VALUE);
            dropped.setInvulnerable(true);
            dropped.setPersistent(false);
            dropped.getPersistentDataContainer().set(PREVIEW_KEY, PersistentDataType.BOOLEAN, true);
            dropped.setGravity(false);
            dropped.setUnlimitedLifetime(true);
            dropped.setVelocity(new Vector(0, 0, 0));
            dropped.teleport(center);
            data.displayEntity = dropped;
            forceFixPreviewPosition(data, center);
        }

        if (data.maintenanceTask != null) {
            data.maintenanceTask.cancel();
            data.maintenanceTask = null;
        }

        data.maintenanceTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (data.destroyed) {
                    cancel();
                    return;
                }
                if (data.displayEntity == null || !data.displayEntity.isValid()) return;

                Location cur = data.displayEntity.getLocation();
                if (cur.distanceSquared(center) > 0.0001) {
                    data.displayEntity.teleport(center);
                }
                data.displayEntity.setVelocity(new Vector(0, 0, 0));
                data.displayEntity.setGravity(false);
                data.displayEntity.setUnlimitedLifetime(true);
                data.displayEntity.setPickupDelay(Integer.MAX_VALUE);
                data.displayEntity.setInvulnerable(true);
            }
        }.runTaskTimer(Specialization.getInstance(), 1L, 1L);
    }

    public static void forceFixPreviewPosition(CookingItemData data, Location center) {
        if (data == null || center == null) return;
        if (data.displayEntity == null || !data.displayEntity.isValid()) return;
        data.displayEntity.teleport(center);
        data.displayEntity.setVelocity(new Vector(0, 0, 0));
        data.displayEntity.setGravity(false);
        data.displayEntity.setUnlimitedLifetime(true);
        data.displayEntity.setPickupDelay(Integer.MAX_VALUE);
        data.displayEntity.setInvulnerable(true);
    }

    public static void markReady(CookingItemData data) {
        if (data == null) return;

        Location center = data.campfireLocation != null
            ? data.campfireLocation.clone().add(0.5, 0.4, 0.5)
            : (data.stand != null ? data.stand.getLocation().clone().add(0, 0.4, 0) : null);

        if (center == null) return;

        if (data.burnt) {
            center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center, 20, 0.2, 0.2, 0.2, 0.01);
        } else {
            center.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 25, 0.2, 0.25, 0.2, 0.02);
        }

        if (data.displayEntity != null && data.displayEntity.isValid()) {
            data.displayEntity.teleport(center);
            data.displayEntity.setVelocity(new Vector(0, 0, 0));
            data.displayEntity.setGravity(false);
            data.displayEntity.setPickupDelay(Integer.MAX_VALUE);
            data.displayEntity.setInvulnerable(true);
            playHoldPreviewPosition(data, center, 60);
        }

        if (data.progressTask != null) data.progressTask.cancel();
        data.progressTask = new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (data.destroyed || ticks++ > 400) {
                    cancel();
                    return;
                }
                if (data.burnt) {
                    center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center, 8, 0.2, 0.2, 0.2, 0.01);
                } else {
                    center.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 10, 0.15, 0.2, 0.15, 0.01);
                }
            }
        }.runTaskTimer(Specialization.getInstance(), 0L, 20L);
    }

    public static void stopProgressBar(CookingItemData data) {
        if (data == null) return;
        if (data.progressTask != null) {
            data.progressTask.cancel();
            data.progressTask = null;
        }
    }

    public static void startSeasoningParticles(CookingItemData data) {
        if (data == null) return;
        if (data.seasoningTask != null) {
            data.seasoningTask.cancel();
            data.seasoningTask = null;
        }

        Location center = data.campfireLocation != null
            ? data.campfireLocation.clone().add(0.5, 0.4, 0.5)
            : (data.stand != null ? data.stand.getLocation().clone().add(0, 0.4, 0) : null);
        if (center == null) return;

        data.seasoningTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (data.destroyed || data.burnt || data.displayEntity == null || !data.displayEntity.isValid()) {
                    cancel();
                    return;
                }
                for (ItemStack s : data.seasonings) {
                    if (s == null) continue;
                    String id = getItemIdFromStack(s);

                    if (s.getType() == Material.POTION) {
                        data.displayEntity.getWorld().spawnParticle(Particle.WITCH, center, 6, 0.15, 0.15, 0.15, 0.01);
                    } else if ("minecraft:ink_sac".equals(id)) {
                        data.displayEntity.getWorld().spawnParticle(Particle.SMOKE, center, 4, 0.12, 0.12, 0.12, 0.01);
                    } else if ("minecraft:glow_ink_sac".equals(id)) {
                        data.displayEntity.getWorld().spawnParticle(Particle.GLOW, center, 6, 0.12, 0.12, 0.12, 0.01);
                    } else if ("minecraft:ghast_tear".equals(id)) {
                        data.displayEntity.getWorld().spawnParticle(Particle.END_ROD, center, 4, 0.08, 0.08, 0.08, 0.01);
                    } else if (id != null && (id.endsWith("sugar") || id.endsWith("honey_bottle") || id.endsWith("cocoa_beans") || id.endsWith("salt") || id.endsWith("glow_lichen") || id.endsWith("blaze_powder"))) {
                        org.bukkit.Color color = getSeasoningColor(id);
                        data.displayEntity.getWorld().spawnParticle(Particle.DUST, center, 6, 0.12, 0.12, 0.12, 0.01, new Particle.DustOptions(color, 0.9f));
                    }
                }
            }
        }.runTaskTimer(Specialization.getInstance(), 0L, 10L);
    }

    public static void stopSeasoningParticles(CookingItemData data) {
        if (data == null) return;
        if (data.seasoningTask != null) {
            data.seasoningTask.cancel();
            data.seasoningTask = null;
        }
    }

    public static void removeDisplay(CookingItemData data) {
        if (data == null) return;
        if (data.maintenanceTask != null) {
            data.maintenanceTask.cancel();
            data.maintenanceTask = null;
        }
        if (data.holdTask != null) {
            data.holdTask.cancel();
            data.holdTask = null;
        }
        stopSeasoningParticles(data);
        stopSauceParticles(data);
        if (data.displayEntity != null) {
            data.displayEntity.remove();
            data.displayEntity = null;
        }
    }

    public static void cleanupVisuals(CookingItemData data) {
        if (data == null) return;
        stopProgressBar(data);
        stopSeasoningParticles(data);
        stopSauceParticles(data);
        removeDisplay(data);
        if (data.recipientDisplay != null) {
            data.recipientDisplay.remove();
            data.recipientDisplay = null;
        }
        stopAmbient(data);
    }

    public static void playFinishEffects(CookingItemData data, Player starter, boolean success) {
        String soundId = success ? "specialization:cooking_success" : "specialization:cooking_fail";
        String cfg = SpecializationConfig.getCookingConfig().getString(success ? "finish_sound_success" : "finish_sound_fail");
        if (cfg != null && !cfg.isBlank()) soundId = cfg;

        Location loc = data != null && data.campfireLocation != null
            ? data.campfireLocation.clone().add(0.4, 0.4, 0.4)
            : (starter != null ? starter.getLocation() : null);

        if (loc != null) {
            var ceWorld = BukkitAdaptors.adapt(loc.getWorld());
            ceWorld.playSound(LocationUtils.toVec3d(loc), Key.of(soundId), 1f, 1f, SoundSource.PLAYER);
        }
        if (data != null && data.stand != null) {
            data.stand.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, data.stand.getLocation().add(0, 0.6, 0), 20, 0.2, 0.2, 0.2, 0.05);
        }
        stopAmbient(data);
    }

    public static void spawnFlameParticles(CookingItemData data) {
        if (data == null) return;
        if (data.stand != null && data.stand.isValid()) {
            data.stand.getWorld().spawnParticle(Particle.FLAME, data.stand.getLocation().add(0, 0.6, 0), 4, 0.08, 0.08, 0.08, 0.01);
        } else if (data.campfireLocation != null) {
            Location center = data.campfireLocation.clone().add(0.5, 0.9, 0.5);
            center.getWorld().spawnParticle(Particle.FLAME, center, 4, 0.08, 0.08, 0.08, 0.01);
        }
    }

    public static void playStartEffects(CookingItemData data) {
        if (data == null) return;

        Location center;
        if (data.stand != null && data.stand.isValid()) {
            center = data.stand.getLocation();
            center.getWorld().playSound(center, Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0f, 0.8f);
            startAmbientTask(data, center);
            data.stand.getWorld().spawnParticle(Particle.FLAME, center.clone().add(0, 0.6, 0), 6, 0.08, 0.08, 0.08, 0.01);
        } else if (data.campfireLocation != null) {
            center = data.campfireLocation.clone().add(0.5, 0.9, 0.5);
            center.getWorld().playSound(center, Sound.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0f, 0.8f);
            startAmbientTask(data, center);
            center.getWorld().spawnParticle(Particle.FLAME, center, 6, 0.08, 0.08, 0.08, 0.01);
        }

        if (data.displayEntity != null && data.displayEntity.isValid() && data.campfireLocation != null) {
            data.displayEntity.teleport(data.campfireLocation.clone().add(0.5, 0.4, 0.5));
            data.displayEntity.setVelocity(new Vector(0, 0, 0));
            data.displayEntity.setGravity(false);
            data.displayEntity.setPickupDelay(Integer.MAX_VALUE);
            data.displayEntity.setInvulnerable(true);
        }
    }

    private static void startAmbientTask(CookingItemData data, Location center) {
        if (data.ambientTask != null) {
            data.ambientTask.cancel();
            data.ambientTask = null;
        }
        data.ambientTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (data.destroyed) {
                    cancel();
                    return;
                }
                center.getWorld().playSound(center, Sound.BLOCK_FIRE_AMBIENT, 1.0f, 0.4f);
            }
        }.runTaskTimer(Specialization.getInstance(), 0L, 60L);
    }

    public static void playCancelEffects(CookingItemData data) {
        if (data == null) return;
        if (data.stand != null && data.stand.isValid()) {
            data.stand.getWorld().playSound(data.stand.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.4f);
            data.stand.getWorld().spawnParticle(Particle.LARGE_SMOKE, data.stand.getLocation().add(0, 0.6, 0), 8, 0.2, 0.2, 0.2, 0.01);
        } else if (data.campfireLocation != null) {
            Location center = data.campfireLocation.clone().add(0.5, 0.9, 0.5);
            center.getWorld().playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.4f);
            center.getWorld().spawnParticle(Particle.LARGE_SMOKE, center, 8, 0.2, 0.2, 0.2, 0.01);
        }
        stopAmbient(data);

        Location holdCenter = data.campfireLocation == null
            ? (data.stand != null ? data.stand.getLocation().clone().add(0, 0.4, 0) : null)
            : data.campfireLocation.clone().add(0.5, 0.4, 0.5);
        playHoldPreviewPosition(data, holdCenter, 60);
    }

    public static void playBreakEffects(CookingItemData data) {
        if (data == null) return;
        if (data.stand != null && data.stand.isValid()) {
            data.stand.getWorld().playSound(data.stand.getLocation(), Sound.ENTITY_ARMOR_STAND_BREAK, 1f, 0.4f);
            data.stand.getWorld().spawnParticle(Particle.SMOKE, data.stand.getLocation().add(0, 0.5, 0), 8, 0.2, 0.2, 0.2, 0.01);
        }
        stopAmbient(data);
    }

    public static void playWorldFinishSound(CookingItemData data, boolean success) {
        if (data == null) return;
        String key = success ? "specialization:cooking_success" : "specialization:cooking_fail";

        Location loc = null;
        if (data.campfireLocation != null) {
            loc = data.campfireLocation.clone().add(0.5, 0.5, 0.5);
        } else if (data.displayEntity != null && data.displayEntity.isValid()) {
            loc = data.displayEntity.getLocation();
        }
        if (loc == null) return;

        var ceWorld = BukkitAdaptors.adapt(loc.getWorld());
        ceWorld.playSound(LocationUtils.toVec3d(loc), Key.of(key), 1f, 1f, SoundSource.BLOCK);
    }

    public static void spawnRecipientDisplay(CookingItemData data) {
        if (data == null || data.recipient == null || data.campfireLocation == null) return;
        Location center = data.campfireLocation.clone().add(0.5, 0.5, 0.5);

        if (data.recipientDisplay != null) {
            data.recipientDisplay.remove();
            data.recipientDisplay = null;
        }

        org.bukkit.entity.Entity e = center.getWorld().spawnEntity(center, org.bukkit.entity.EntityType.ITEM_DISPLAY);
        if (!(e instanceof org.bukkit.entity.ItemDisplay disp)) return;

        disp.setItemStack(data.recipient.clone());
        disp.setInvulnerable(true);
        disp.setPersistent(false);
        disp.setGravity(false);

        String sessionKey = data.campfireLocation.getWorld().getName() + ":" +
            data.campfireLocation.getBlockX() + "," +
            data.campfireLocation.getBlockY() + "," +
            data.campfireLocation.getBlockZ();
        disp.getPersistentDataContainer().set(RECIPIENT_KEY, PersistentDataType.STRING, sessionKey);

        float yawDeg = getYawFromCampfire(data);
        disp.setRotation(yawDeg - 90f, 90f);

        Matrix4f mat = new Matrix4f()
            .rotateX((float) Math.toRadians(-90f))
            .rotateX((float) Math.toRadians(90f))
            .rotateZ((float) Math.toRadians(90f))
            .scale(0.7f);
        disp.setTransformationMatrix(mat);
        data.recipientDisplay = disp;
    }

    private static float getYawFromCampfire(CookingItemData data) {
        if (data.stand != null && data.stand.isValid()) {
            return data.stand.getLocation().getYaw();
        }
        if (data.campfireLocation == null) return 90f;

        org.bukkit.block.data.BlockData bd = data.campfireLocation.getBlock().getBlockData();
        if (!(bd instanceof Directional d)) return 90f;

        return switch (d.getFacing()) {
            case NORTH -> 180f;
            case EAST -> -90f;
            case SOUTH -> 0f;
            case NORTH_EAST -> -135f;
            case NORTH_WEST -> 135f;
            case SOUTH_EAST -> -45f;
            case SOUTH_WEST -> 45f;
            default -> 90f;
        };
    }

    public static void playSeasoningEffect(Block block, ItemStack seasoning) {
        if (block == null || seasoning == null) return;
        Location center = block.getLocation().add(0.5, 0.9, 0.5);
        String id = getItemIdFromStack(seasoning);

        org.bukkit.Color color = getSeasoningColor(id);
        block.getWorld().spawnParticle(Particle.DUST, center, 25, 0.2, 0.2, 0.2, 0.01, new Particle.DustOptions(color, 1.2f));
        block.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, center, 6, 0.15, 0.15, 0.15, 0.01);
        block.getWorld().playSound(center, Sound.ENTITY_WANDERING_TRADER_DRINK_MILK, SoundCategory.BLOCKS, 1f, 0.8f);
    }

    public static void playSauceEffect(Block block, ItemStack sauce) {
        if (block == null || sauce == null) return;
        Location center = block.getLocation().add(0.5, 0.9, 0.5);
        String id = getItemIdFromStack(sauce);

        org.bukkit.Color color;
        if (sauce.getType() == Material.POTION) {
            color = getPotionColor(sauce);
        } else {
            color = switch (id == null ? "" : id) {
                case "minecraft:ink_sac" -> org.bukkit.Color.fromRGB(29, 29, 33);
                case "minecraft:glow_ink_sac" -> org.bukkit.Color.fromRGB(0, 255, 255);
                case "minecraft:ghast_tear" -> org.bukkit.Color.fromRGB(255, 255, 255);
                default -> org.bukkit.Color.fromRGB(180, 180, 255);
            };
        }

        block.getWorld().spawnParticle(Particle.DUST, center, 18, 0.2, 0.2, 0.2, 0.01, new Particle.DustOptions(color, 1.2f));

        if ("minecraft:ink_sac".equals(id)) {
            block.getWorld().spawnParticle(Particle.SQUID_INK, center, 8, 0.15, 0.15, 0.15, 0.01);
        } else if ("minecraft:glow_ink_sac".equals(id)) {
            block.getWorld().spawnParticle(Particle.GLOW_SQUID_INK, center, 12, 0.15, 0.15, 0.15, 0.0);
            block.getWorld().spawnParticle(Particle.GLOW, center, 6, 0.1, 0.1, 0.1, 0.01);
        } else if ("minecraft:ghast_tear".equals(id)) {
            block.getWorld().spawnParticle(Particle.END_ROD, center, 8, 0.12, 0.12, 0.12, 0.01);
            block.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, center, 6, 0.1, 0.1, 0.1, 0.01);
        } else if (sauce.getType() == Material.POTION) {
            block.getWorld().spawnParticle(Particle.SPLASH, center, 15, 0.2, 0.2, 0.2, 0.02);
            block.getWorld().spawnParticle(Particle.ENTITY_EFFECT, center, 8, 0.15, 0.15, 0.15, 0.01, color);
        }
        block.getWorld().playSound(center, Sound.ENTITY_ARMADILLO_EAT, SoundCategory.BLOCKS, 1f, 0.3f);
    }

    public static void startSauceParticles(CookingItemData data) {
        if (data == null) return;
        if (data.sauceTask != null) {
            data.sauceTask.cancel();
            data.sauceTask = null;
        }

        Location center = data.campfireLocation != null
            ? data.campfireLocation.clone().add(0.5, 0.4, 0.5)
            : (data.stand != null ? data.stand.getLocation().clone().add(0, 0.4, 0) : null);
        if (center == null) return;

        data.sauceTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (data.destroyed || data.burnt || data.displayEntity == null || !data.displayEntity.isValid()) {
                    cancel();
                    return;
                }
                for (ItemStack s : data.sauces) {
                    if (s == null) continue;
                    String id = getItemIdFromStack(s);

                    if (s.getType() == Material.POTION) {
                        org.bukkit.Color c = getPotionColor(s);
                        data.displayEntity.getWorld().spawnParticle(Particle.SPLASH, center, 6, 0.12, 0.12, 0.12, 0.005);
                        data.displayEntity.getWorld().spawnParticle(Particle.DUST, center, 8, 0.15, 0.15, 0.15, 0.01, new Particle.DustOptions(c, 1.0f));
                    } else if ("minecraft:ink_sac".equals(id)) {
                        data.displayEntity.getWorld().spawnParticle(Particle.SQUID_INK, center, 4, 0.1, 0.1, 0.1, 0.005);
                    } else if ("minecraft:glow_ink_sac".equals(id)) {
                        data.displayEntity.getWorld().spawnParticle(Particle.GLOW, center, 6, 0.1, 0.1, 0.1, 0.01);
                        data.displayEntity.getWorld().spawnParticle(Particle.GLOW_SQUID_INK, center, 3, 0.08, 0.08, 0.08, 0.0);
                    } else if ("minecraft:ghast_tear".equals(id)) {
                        data.displayEntity.getWorld().spawnParticle(Particle.END_ROD, center, 5, 0.1, 0.1, 0.1, 0.01);
                        data.displayEntity.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, center, 3, 0.08, 0.08, 0.08, 0.005);
                    }
                }
            }
        }.runTaskTimer(Specialization.getInstance(), 0L, 10L);
    }

    public static void stopSauceParticles(CookingItemData data) {
        if (data == null) return;
        if (data.sauceTask != null) {
            data.sauceTask.cancel();
            data.sauceTask = null;
        }
    }

    public static void stopAmbient(CookingItemData data) {
        if (data == null) return;
        if (data.ambientTask != null) {
            data.ambientTask.cancel();
            data.ambientTask = null;
        }
    }

    public static void playHoldPreviewPosition(CookingItemData data, Location center, int ticks) {
        if (data == null || center == null || data.displayEntity == null || !data.displayEntity.isValid()) return;
        if (data.holdTask != null) {
            data.holdTask.cancel();
            data.holdTask = null;
        }
        data.holdTask = new BukkitRunnable() {
            int t = 0;
            @Override
            public void run() {
                if (data.destroyed || data.displayEntity == null || !data.displayEntity.isValid() || t++ >= ticks) {
                    cancel();
                    return;
                }
                forceFixPreviewPosition(data, center);
            }
        }.runTaskTimer(Specialization.getInstance(), 0L, 1L);
    }

    // === Helper methods ===

    private static String getItemIdFromStack(ItemStack stack) {
        if (stack == null) return null;
        var wrapped = net.momirealms.craftengine.bukkit.item.BukkitItemManager.instance().wrap(stack);
        if (wrapped != null && wrapped.getCustomItem().isPresent()) {
            return wrapped.getCustomItem().get().id().value();
        }
        return "minecraft:" + stack.getType().name().toLowerCase();
    }

    private static org.bukkit.Color getSeasoningColor(String id) {
        if (id == null) return org.bukkit.Color.fromRGB(200, 200, 200);
        return switch (id) {
            case "specialization:salt", "minecraft:salt" -> org.bukkit.Color.fromRGB(230, 230, 230);
            case "minecraft:sugar" -> org.bukkit.Color.fromRGB(255, 255, 255);
            case "minecraft:cocoa_beans" -> org.bukkit.Color.fromRGB(78, 42, 15);
            case "minecraft:honey_bottle" -> org.bukkit.Color.fromRGB(235, 150, 30);
            case "minecraft:glow_lichen" -> org.bukkit.Color.fromRGB(106, 168, 138);
            case "minecraft:blaze_powder" -> org.bukkit.Color.fromRGB(255, 200, 50);
            default -> org.bukkit.Color.fromRGB(200, 200, 200);
        };
    }

    private static org.bukkit.Color getPotionColor(ItemStack stack) {
        if (stack == null || stack.getType() != Material.POTION) {
            return org.bukkit.Color.fromRGB(180, 180, 255);
        }

        var meta = stack.getItemMeta();
        if (!(meta instanceof org.bukkit.inventory.meta.PotionMeta pm)) {
            return org.bukkit.Color.fromRGB(180, 180, 255);
        }

        org.bukkit.Color c = pm.getColor();
        if (c != null) return c;

        org.bukkit.potion.PotionType potionType = pm.getBasePotionType();
        if (potionType == null) return org.bukkit.Color.fromRGB(180, 180, 255);

        String typeName = potionType.name().toLowerCase();

        if (typeName.contains("healing")) return org.bukkit.Color.fromRGB(248, 36, 35);
        if (typeName.contains("harming")) return org.bukkit.Color.fromRGB(168, 100, 105);
        if (typeName.contains("poison")) return org.bukkit.Color.fromRGB(135, 163, 102);
        if (typeName.contains("regeneration")) return org.bukkit.Color.fromRGB(204, 91, 170);
        if (typeName.contains("swiftness")) return org.bukkit.Color.fromRGB(50, 234, 254);
        if (typeName.contains("fire_resistance")) return org.bukkit.Color.fromRGB(255, 153, 0);
        if (typeName.contains("night_vision")) return org.bukkit.Color.fromRGB(194, 255, 102);
        if (typeName.contains("invisibility")) return org.bukkit.Color.fromRGB(245, 245, 245);
        if (typeName.contains("water_breathing")) return org.bukkit.Color.fromRGB(152, 218, 192);
        if (typeName.contains("slow_falling")) return org.bukkit.Color.fromRGB(243, 207, 185);
        if (typeName.contains("slowness")) return org.bukkit.Color.fromRGB(179, 207, 236);
        if (typeName.contains("strength")) return org.bukkit.Color.fromRGB(255, 199, 0);
        if (typeName.contains("weakness")) return org.bukkit.Color.fromRGB(71, 76, 71);
        if (typeName.contains("leaping")) return org.bukkit.Color.fromRGB(253, 255, 132);
        if (typeName.contains("turtle_master")) return org.bukkit.Color.fromRGB(142, 124, 132);
        if (typeName.contains("luck")) return org.bukkit.Color.fromRGB(89, 193, 6);
        if (typeName.contains("wind_charged")) return org.bukkit.Color.fromRGB(189, 201, 255);
        if (typeName.contains("weaving")) return org.bukkit.Color.fromRGB(121, 107, 93);
        if (typeName.contains("oozing")) return org.bukkit.Color.fromRGB(152, 254, 162);
        if (typeName.contains("infested")) return org.bukkit.Color.fromRGB(141, 156, 142);
        if (typeName.contains("water") || typeName.contains("awkward") || typeName.contains("mundane") || typeName.contains("thick")) {
            return org.bukkit.Color.fromRGB(56, 93, 198);
        }

        return org.bukkit.Color.fromRGB(255, 0, 255);
    }
}
