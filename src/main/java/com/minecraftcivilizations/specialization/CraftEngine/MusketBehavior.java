package com.minecraftcivilizations.specialization.CraftEngine;

import com.minecraftcivilizations.specialization.Specialization;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptors;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.plugin.reflection.minecraft.CoreReflections;
import net.momirealms.craftengine.bukkit.plugin.user.BukkitServerPlayer;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.bukkit.world.BukkitWorld;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.item.CustomItem;
import net.momirealms.craftengine.core.item.context.UseOnContext;
import net.momirealms.craftengine.core.registry.BuiltInRegistries;
import net.momirealms.craftengine.core.sound.SoundData;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.world.Position;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class MusketBehavior extends ItemBehavior {
    private final NamespacedKey RELOAD_KEY = new NamespacedKey(Specialization.getInstance(), "reloadCount");
    private final Key requiredAmmo;
    private final NamespacedKey IS_LOADED_KEY = new NamespacedKey(Specialization.getInstance(), "isReloaded");
    private final int reloadTime;
    public static final Factory FACTORY = new Factory();
    public static class Factory implements ItemBehaviorFactory {
        @Override
        public ItemBehavior create(Pack pack, Path path, String node, Key key, Map<String, Object> arguments) {
            String ammoId = (String) arguments.get("ammo");
            int reloadTime = (Integer) arguments.getOrDefault("reload-time", 10);
            if (ammoId == null) {
                throw new IllegalArgumentException("Missing required parameter 'ammo'");
            }
            return new MusketBehavior(Key.of(ammoId), reloadTime);
        }
    }
    public MusketBehavior(Key requiredAmmo, int reloadTime) {
        this.requiredAmmo = requiredAmmo;
        this.reloadTime = reloadTime;
    }

    private boolean hasAmmunition(Player player) {
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) player.platformPlayer();
        for (ItemStack item : bukkitPlayer.getInventory().getContents()) {
            if (item != null && item.getType() == Material.FIREWORK_STAR) {
                Item<ItemStack> wrapped = BukkitItemManager.instance().wrap(item);
                if (wrapped.getCustomItem().isPresent() &&
                        wrapped.getCustomItem().get().id().equals(requiredAmmo)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void consumeAmmunition(Player player) {
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) player.platformPlayer();
        for (ItemStack item : bukkitPlayer.getInventory().getContents()) {
            if (item == null || item.getType() != Material.FIREWORK_STAR) continue;

            Item<ItemStack> wrapped = BukkitItemManager.instance().wrap(item);
            CustomItem<ItemStack> customItem = wrapped.getCustomItem().orElse(null);
            if (customItem != null && customItem.id().equals(requiredAmmo)) {
                // Remove one item
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    bukkitPlayer.getInventory().remove(item);
                }
                break;
            }
        }
    }

    static private void spawnParticleBeam(Vec3d start, Vec3d end, org.bukkit.World world) {
        double distanceSq = Vec3d.distanceToSqr(start, end);
        double distance = Math.sqrt(distanceSq);
        int particleCount = (int) (distance * 2); // 2 particles per block

        for (int i = 0; i <= particleCount; i++) {
            double progress = (double) i / particleCount;
            Vec3d pos = new Vec3d(
                    start.x + (end.x - start.x) * progress,
                    start.y + (end.y - start.y) * progress,
                    start.z + (end.z - start.z) * progress
            );

            // Spawn particles at each point
            world.spawnParticle(Particle.SMOKE, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.CRIT, pos.x, pos.y, pos.z, 1, 0, 0, 0, 0);
        }
    }
    static public void shootParticleBeam(Entity shooter, Location eyeLocation, Vector direction, org.bukkit.World world) {
        double range = 50.0;

        Vector velocity = shooter.getVelocity();
        double speed = velocity.length();

        double accuracy = calculateAccuracy(speed, shooter);
        Vector adjustedDirection = adjustDirectionGaussian(direction, accuracy);

        // First, ray trace for BLOCKS
        RayTraceResult blockResult = world.rayTraceBlocks(
                eyeLocation,
                adjustedDirection,
                range,
                FluidCollisionMode.NEVER,
                true // Ignore passable blocks
        );

        // Calculate max distance (either block hit or max range)
        double maxDistance = range;
        Vec3d blockHitPoint = null;

        if (blockResult != null && blockResult.getHitBlock() != null) {
            maxDistance = blockResult.getHitPosition().distance(eyeLocation.toVector());
            blockHitPoint = LocationUtils.toVec3d(blockResult.getHitPosition().toLocation(world));
        }

        // Now ray trace for ENTITIES within the limited distance
        RayTraceResult entityResult = world.rayTraceEntities(
                eyeLocation,
                direction,
                maxDistance,
                1.0, // Larger hitbox expansion for better accuracy
                entity -> entity != shooter && entity instanceof LivingEntity
        );

        // Determine final end point
        Vec3d endPoint;
        Entity hitEntity = null;

        if (entityResult != null && entityResult.getHitEntity() != null) {
            hitEntity = entityResult.getHitEntity();
            endPoint = LocationUtils.toVec3d(entityResult.getHitPosition().toLocation(world));
        } else if (blockHitPoint != null) {
            // Hit a block, not an entity
            endPoint = blockHitPoint;
        } else {
            // Nothing hit, use max range
            Vector maxPoint = eyeLocation.toVector().add(direction.clone().multiply(maxDistance));
            endPoint = LocationUtils.toVec3d(maxPoint.toLocation(world));
        }

        // Spawn particles
        spawnParticleBeam(LocationUtils.toVec3d(eyeLocation), endPoint, world);
        World ceWorld = BukkitAdaptors.adapt(world);
        ceWorld.playSound(LocationUtils.toVec3d(shooter.getLocation()), Key.of("specialization:rifle_shot"), 1f, 0.9f + (float) (Math.random() * 0.2),SoundSource.PLAYER);
        if (blockResult != null && blockResult.getHitBlock() != null) {
            Block hitBlock = blockResult.getHitBlock();
            if (hitBlock.getType() == Material.TNT) {
                hitBlock.setType(Material.AIR);
                Location tntLocation = hitBlock.getLocation().add(0.5, 0, 0.5);
                world.createExplosion(
                        tntLocation,
                        4.0f,
                        false,
                        true,
                        shooter
                );
            }
        }
        // Handle hit
        if (hitEntity instanceof LivingEntity livingEntity) {
            world.spawnParticle(Particle.FLASH, livingEntity.getLocation(), 5);

            if (livingEntity instanceof org.bukkit.entity.Player player && player.isBlocking()) {
                org.bukkit.inventory.ItemStack item = player.getInventory().getItemInOffHand();
                if (item.getType() == Material.SHIELD) {
                    item.damage(20, (LivingEntity) shooter);
                }
                return;
            }
            double distance = shooter.getLocation().distance(livingEntity.getLocation());
            double damage = calculateDamage(distance);
            livingEntity.damage(damage, shooter);
            // Apply knockback
            Vector knockback = direction.multiply(2);
            livingEntity.setVelocity(knockback);

            // Visual hit effect
        }
    }

    private static Vector rotateAroundYAxis(Vector vector, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = vector.getX() * cos - vector.getZ() * sin;
        double z = vector.getX() * sin + vector.getZ() * cos;
        return new Vector(x, vector.getY(), z);
    }

    // Helper method to rotate vector around arbitrary axis
    private static Vector rotateAroundAxis(Vector vector, Vector axis, double angle) {
        axis = axis.clone().normalize();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double dot = vector.dot(axis);

        Vector cross = axis.getCrossProduct(vector);

        return vector.clone().multiply(cos)
                .add(cross.multiply(sin))
                .add(axis.multiply(dot * (1 - cos)));
    }

    private static Vector adjustDirectionGaussian(Vector originalDirection, double accuracy) {
        // Higher spreadAmount = less accurate
        double spreadAmount = (1.0 - accuracy) * 0.15;

        double randomYaw = (ThreadLocalRandom.current().nextGaussian() * spreadAmount);
        double randomPitch = (ThreadLocalRandom.current().nextGaussian() * spreadAmount);

        Vector rotated = originalDirection.clone();

        if (Math.abs(randomYaw) > 0.001) {
            rotated = rotateAroundYAxis(rotated, randomYaw);
        }

        if (Math.abs(randomPitch) > 0.001) {
            rotated = rotateAroundAxis(rotated,
                    originalDirection.clone().crossProduct(new Vector(0, 1, 0)).normalize(),
                    randomPitch
            );
        }

        return rotated.normalize();
    }
    private static double calculateAccuracy(double speed, Entity shooter) {
        double baseAccuracy = 0.95; // standing still

        if (shooter instanceof  org.bukkit.entity.Player player) {
            if (player.isSneaking()) {
                baseAccuracy = 1;
            }
            if (!player.isOnGround()) {
                baseAccuracy *= 0.8;
            }

            if (player.isSprinting()) {
                baseAccuracy *= 0.7;
            }
        }

        double speedPenalty = Math.min(0.9, speed * 10.0); // Max 90% penalty
        double accuracy = baseAccuracy * (1.0 - speedPenalty);

        return Math.max(0.01, accuracy);
    }
    private static double calculateDamage(Double distance) {
        double baseDamage = 5.0;

            // Using Horner's Method for better performance and precision:
            // f(x) = ((((a*x + b)*x + c)*x + d)*x + e)*x + f

        return ((((-1.235168665e-7 * distance
                    + 2.836276548e-5) * distance
                    - 2.213318231e-3) * distance
                    + 5.884158215e-2) * distance
                    - 3.915702214e-3) * distance
                    + 0.9472591991;
    }

    public InteractionResult useOnBlock(UseOnContext context) {
        return use(context.getWorld(), context.getPlayer(), context.getHand());
    }

    @Override
    public InteractionResult use(World world, Player CEplayer, InteractionHand hand) {
        // Get the item in hand
        if (CEplayer == null) return InteractionResult.PASS;
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) CEplayer.platformPlayer();
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        Item<?> item = CEplayer.getItemInHand(hand);
        if (item.getItem() instanceof ItemStack itemStack) {
            if (bukkitPlayer.hasCooldown(itemStack)) return InteractionResult.PASS;
            if (itemStack.getPersistentDataContainer().has(IS_LOADED_KEY)) {
                item.hurtAndBreak(1, null, null);
                Location eyeLocation = bukkitPlayer.getEyeLocation();
                Vector direction = eyeLocation.getDirection().normalize();
                shootParticleBeam(bukkitPlayer, eyeLocation, direction, (org.bukkit.World) world.platformWorld());
                bukkitPlayer.setCooldown(itemStack.getType(), 60);
                itemStack.editPersistentDataContainer(pdc -> {
                    pdc.remove(IS_LOADED_KEY);
                });
                return InteractionResult.SUCCESS;
            }


            if (!hasAmmunition(CEplayer)) {
                CEplayer.sendMessage(Component.text( "Requires " + requiredAmmo.value() + "!"), false);
                return InteractionResult.SUCCESS_AND_CANCEL;
            }

            itemStack.editPersistentDataContainer(pdc -> {
                int reload = pdc.getOrDefault(RELOAD_KEY, PersistentDataType.INTEGER, 0);
                if (reload >= reloadTime) {
                    reload = 0;
                    pdc.set(IS_LOADED_KEY, PersistentDataType.BOOLEAN, true);
                    CEplayer.sendActionBar(Component.text("Loaded"));
                    consumeAmmunition(CEplayer);
                    world.playSound(LocationUtils.toVec3d(bukkitPlayer.getLocation()), Key.of("specialization:rifle_reload"), 1f, 1.2f + (float) (Math.random() * 0.1),SoundSource.PLAYER);
                } else {
                    world.playSound(LocationUtils.toVec3d(bukkitPlayer.getLocation()), Key.of("specialization:rifle_reload"), 1f, 0.9f + (float) (Math.random() * 0.1),SoundSource.PLAYER);
                    bukkitPlayer.setCooldown(itemStack.getType(), 20);
                    CEplayer.sendActionBar(Component.text(reload));
                }
                reload += 1;
                pdc.set(RELOAD_KEY, PersistentDataType.INTEGER, reload);
            });
            return InteractionResult.SUCCESS_AND_CANCEL;
        }

        return super.use(world, CEplayer, hand);
    }
}