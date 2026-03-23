package com.minecraftcivilizations.specialization.CraftEngine;

import com.minecraftcivilizations.specialization.Specialization;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptors;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.item.context.UseOnContext;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ShotgunBehavior extends ItemBehavior {

    public static final Key SHOTGUN_ID = Key.of("specialization:shotgun");
    public static final Key SHOTGUN_AMMO_ID = Key.of("specialization:shotgun_shell");
    public static final int MAX_SHOTS = 2; // Double Barrel

    public static final Factory FACTORY = new Factory();

    public static class Factory implements ItemBehaviorFactory {
        @Override
        public ItemBehavior create(Pack pack, Path path, String node, Key key, Map<String, Object> arguments) {
            return new ShotgunBehavior();
        }
    }

    public ShotgunBehavior() {
    }

    public static int getShotsLeft(ItemStack itemStack) {
        NamespacedKey key = new NamespacedKey(Specialization.getInstance(), "shotgunShotsLeft");
        return itemStack.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
    }

    public static void setShotsLeft(ItemStack itemStack, int amount) {
        NamespacedKey key = new NamespacedKey(Specialization.getInstance(), "shotgunShotsLeft");
        itemStack.editPersistentDataContainer(pdc -> pdc.set(key, PersistentDataType.INTEGER, amount));
    }

    private static void spawnParticleBeam(Vec3d start, Vec3d end, org.bukkit.World world) {
        double distance = Math.sqrt(Vec3d.distanceToSqr(start, end));
        int particleCount = (int) (distance * 2);

        for (int i = 0; i <= particleCount; i++) {
            double progress = (double) i / particleCount;
            double x = start.x + (end.x - start.x) * progress;
            double y = start.y + (end.y - start.y) * progress;
            double z = start.z + (end.z - start.z) * progress;

            if (progress < 0.1) {
                world.spawnParticle(Particle.FLAME, x, y, z, 1, 0.05, 0.05, 0.05, 0.01);
                world.spawnParticle(Particle.SMOKE, x, y, z, 2, 0.1, 0.1, 0.1, 0.02);
            }

            world.spawnParticle(Particle.CRIT, x, y, z, 1, 0.05, 0.05, 0.05, 0);

            if (i % 4 == 0) {
                world.spawnParticle(Particle.SMOKE, x, y, z, 1, 0.02, 0.02, 0.02, 0);
            }
        }
    }

    public static void shootProjectile(Entity shooter, Location eyeLocation, Vector direction, org.bukkit.World world,
            int piercingLevel) {
        double range = 25.0; // Short range for shotgun
        int pelletCount = 12; // Firing a dense volley of 12 pellets

        double baseSpread = 1.2;
        double distanceSpreadAngle = Math.atan(baseSpread / range);

        for (int p = 0; p < pelletCount; p++) {
            double xOffset = ThreadLocalRandom.current().nextGaussian() * distanceSpreadAngle;
            double yOffset = ThreadLocalRandom.current().nextGaussian() * distanceSpreadAngle;

            Vector dir = direction.normalize();
            Vector helper = Math.abs(dir.getY()) < 0.9 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
            Vector right = dir.getCrossProduct(helper).normalize();
            Vector up = right.getCrossProduct(dir).normalize();

            Vector adjustedDirection = dir.add(right.multiply(xOffset)).add(up.multiply(yOffset)).normalize();

            // Randomize individual pellet raytrace range slightly to create a messy bullet
            // spread
            double pelletRange = range * (0.8 + ThreadLocalRandom.current().nextDouble() * 0.2);

            fireSinglePellet(shooter, eyeLocation, adjustedDirection, world, pelletRange, piercingLevel);
        }

        World ceWorld = BukkitAdaptors.adapt(world);
        ceWorld.playSound(LocationUtils.toVec3d(shooter.getLocation()), Key.of("entity.generic.explode"), 1.0f, 1.8f,
                SoundSource.PLAYER);
        ceWorld.playSound(LocationUtils.toVec3d(shooter.getLocation()), Key.of("specialization:rifle_shot"), 4.0f, 0.5f,
                SoundSource.PLAYER);

        if (shooter instanceof org.bukkit.entity.Player player) {
            double recoilMultiplier;
            if (player.isSneaking()) {
                recoilMultiplier = 0.0; // Perfect stability
            } else if (player.isSprinting()) {
                recoilMultiplier = -0.35; // Heavy ragdoll while sprinting
            } else {
                double horizontalVel = Math.pow(player.getVelocity().getX(), 2)
                        + Math.pow(player.getVelocity().getZ(), 2);
                if (horizontalVel > 0.005) {
                    recoilMultiplier = -0.15; // Mild knockback walking
                } else {
                    recoilMultiplier = -0.05; // Tiny bump standing still
                }
            }

            if (recoilMultiplier != 0.0) {
                Vector selfRecoil = direction.clone().setY(0).normalize().multiply(recoilMultiplier);
                player.setVelocity(player.getVelocity().add(selfRecoil));
            }
        }
    }

    private static void fireSinglePellet(Entity shooter, Location eyeLocation, Vector adjustedDirection,
            org.bukkit.World world, double range, int piercingLevel) {
        int maxPierce = piercingLevel;
        int entitiesPierced = 0;

        Location currentOrigin = eyeLocation.clone();
        double remainingRange = range;
        Vec3d lastEndPoint = LocationUtils.toVec3d(eyeLocation);
        java.util.Set<Entity> hitEntities = new java.util.HashSet<>();

        while (remainingRange > 0) {
            RayTraceResult blockResult = world.rayTraceBlocks(currentOrigin, adjustedDirection, remainingRange,
                    FluidCollisionMode.NEVER, true);
            double maxDistance = remainingRange;
            Vec3d blockHitPoint = null;

            if (blockResult != null && blockResult.getHitBlock() != null) {
                maxDistance = blockResult.getHitPosition().distance(currentOrigin.toVector());
                blockHitPoint = LocationUtils.toVec3d(blockResult.getHitPosition().toLocation(world));
            }

            final java.util.Set<Entity> alreadyHit = hitEntities;
            RayTraceResult entityResult = world.rayTraceEntities(currentOrigin, adjustedDirection, maxDistance, 0.3,
                    entity -> entity != shooter && entity instanceof LivingEntity && !alreadyHit.contains(entity));

            Vec3d endPoint;
            Entity hitEntity = null;

            if (entityResult != null && entityResult.getHitEntity() != null) {
                hitEntity = entityResult.getHitEntity();
                endPoint = LocationUtils.toVec3d(entityResult.getHitPosition().toLocation(world));
            } else if (blockHitPoint != null) {
                endPoint = blockHitPoint;
            } else {
                Vector maxPoint = currentOrigin.toVector().add(adjustedDirection.clone().multiply(maxDistance));
                endPoint = LocationUtils.toVec3d(maxPoint.toLocation(world));
            }

            spawnParticleBeam(lastEndPoint, endPoint, world);
            lastEndPoint = endPoint;

            if (hitEntity instanceof LivingEntity livingEntity) {
                hitEntities.add(hitEntity);
                applyDamageToEntity(shooter, livingEntity, adjustedDirection, world);
                entitiesPierced++;
                if (entitiesPierced > maxPierce)
                    break;

                double hitDistance = currentOrigin.toVector().distance(entityResult.getHitPosition());
                remainingRange -= hitDistance + 0.5;
                currentOrigin = entityResult.getHitPosition().toLocation(world)
                        .add(adjustedDirection.clone().multiply(0.5));
            } else {
                break;
            }
        }
    }

    private static void applyDamageToEntity(Entity shooter, LivingEntity livingEntity, Vector direction,
            org.bukkit.World world) {
        Location hitLoc = livingEntity.getLocation().add(0, 1, 0);
        world.spawnParticle(Particle.DAMAGE_INDICATOR, hitLoc, 1, 0.2, 0.2, 0.2, 0);
        world.spawnParticle(Particle.DUST, hitLoc, 2, 0.2, 0.2, 0.2, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(139, 0, 0), 0.8f));

        if (livingEntity instanceof org.bukkit.entity.Player player && player.isBlocking()) {
            ItemStack shield = player.getInventory().getItemInOffHand();
            if (shield.getType() == Material.SHIELD) {
                shield.damage(1, livingEntity);
            }
            return;
        }

        double hitDistance = shooter.getLocation().distance(livingEntity.getLocation());

        // Extremely devastating point-blank damage (capable of one-shotting Iron
        // Golems)
        // 12 pellets * 10.0 damage = 120 total possible damage!
        double pelletDamage;
        if (hitDistance <= 4.0) {
            pelletDamage = 10.0; // Extends the one-shot kill zone to 4 full blocks
        } else if (hitDistance <= 10.0) {
            pelletDamage = 5.0; // Still deals absolutely lethal damage (60) at mid-range
        } else if (hitDistance <= 16.0) {
            pelletDamage = 2.5; // Moderate drop-off (30 damage)
        } else if (hitDistance <= 22.0) {
            pelletDamage = 1.0; // Grazing damage
        } else {
            pelletDamage = 0.5; // Minimal damage past 22 blocks
        }

        // Extremely crucial: Minecraft natively gives entities 10 ticks (0.5s) of
        // invulnerability upon taking damage.
        // This causes 11 of our 12 shotgun pellets to be instantly ignored by the game
        // engine because they hit on the same tick.
        // By zeroing the NoDamageTicks, we force every single pellet to independently
        // connect and deal its massive collective damage.
        livingEntity.setNoDamageTicks(0);
        livingEntity.damage(pelletDamage, shooter);

        // Individual pellets give very tiny knockback, summing up to a strong shove if
        // many hit
        Vector knockback = direction.clone().multiply(0.08);
        livingEntity.setVelocity(livingEntity.getVelocity().add(knockback));
    }

    public InteractionResult useOnBlock(UseOnContext context) {
        return use(context.getWorld(), context.getPlayer(), context.getHand());
    }

    @Override
    public InteractionResult use(World world, Player CEplayer, InteractionHand hand) {
        return InteractionResult.PASS; // Handled exclusively by ShotgunListener to mirror Musket/Revolver patterns
    }
}
