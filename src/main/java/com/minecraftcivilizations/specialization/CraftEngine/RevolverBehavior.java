package com.minecraftcivilizations.specialization.CraftEngine;

import com.minecraftcivilizations.specialization.Specialization;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptors;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.item.context.UseOnContext;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.core.world.World;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.entity.player.Player;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.util.Key;
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

public class RevolverBehavior extends ItemBehavior {

    public static final Key REVOLVER_ID = Key.of("specialization:revolver");
    public static final Key REVOLVER_AMMO_ID = Key.of("specialization:revolver_round");
    public static final int MAX_SHOTS = 6;

    public static final Factory FACTORY = new Factory();

    public static class Factory implements ItemBehaviorFactory {
        @Override
        public ItemBehavior create(Pack pack, Path path, String node, Key key, Map<String, Object> arguments) {
            return new RevolverBehavior();
        }
    }

    public RevolverBehavior() {}

    public static int getShotsLeft(ItemStack itemStack) {
        NamespacedKey key = new NamespacedKey(Specialization.getInstance(), "revolverShotsLeft");
        return itemStack.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
    }

    public static void setShotsLeft(ItemStack itemStack, int amount) {
        NamespacedKey key = new NamespacedKey(Specialization.getInstance(), "revolverShotsLeft");
        itemStack.editPersistentDataContainer(pdc -> pdc.set(key, PersistentDataType.INTEGER, amount));
    }

    private static void spawnParticleBeam(Vec3d start, Vec3d end, org.bukkit.World world) {
        double distance = Math.sqrt(Vec3d.distanceToSqr(start, end));
        int particleCount = (int) (distance * 3);

        for (int i = 0; i <= particleCount; i++) {
            double progress = (double) i / particleCount;
            double x = start.x + (end.x - start.x) * progress;
            double y = start.y + (end.y - start.y) * progress;
            double z = start.z + (end.z - start.z) * progress;

            if (progress < 0.05) {
                world.spawnParticle(Particle.FLAME, x, y, z, 1, 0.05, 0.05, 0.05, 0.01);
                world.spawnParticle(Particle.SMOKE, x, y, z, 2, 0.1, 0.1, 0.1, 0.02);
            }

            world.spawnParticle(Particle.ELECTRIC_SPARK, x, y, z, 1, 0.01, 0.01, 0.01, 0);

            if (i % 5 == 0) {
                world.spawnParticle(Particle.SMOKE, x, y, z, 1, 0.01, 0.01, 0.01, 0);
            }
        }
    }

    public static void shootProjectile(Entity shooter, Location eyeLocation, Vector direction, org.bukkit.World world, int piercingLevel) {
        double range = 45.0; // Shorter range than musket's 80

        double spread = 2.0; 
        double distanceSpreadAngle = Math.atan(spread / range);
        double xOffset = ThreadLocalRandom.current().nextGaussian() * distanceSpreadAngle;
        double yOffset = ThreadLocalRandom.current().nextGaussian() * distanceSpreadAngle;

        Vector dir = direction.normalize();
        Vector helper = Math.abs(dir.getY()) < 0.9 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        Vector right = dir.getCrossProduct(helper).normalize();
        Vector up = right.getCrossProduct(dir).normalize();

        Vector adjustedDirection = dir.add(right.multiply(xOffset)).add(up.multiply(yOffset)).normalize();

        fireSingleProjectile(shooter, eyeLocation, adjustedDirection, world, range, piercingLevel);

        World ceWorld = BukkitAdaptors.adapt(world);
        ceWorld.playSound(LocationUtils.toVec3d(shooter.getLocation()), Key.of("specialization:revolver_shot"), 3.0f, 1.3f + (float) (Math.random() * 0.2), SoundSource.PLAYER);
    }

    private static void fireSingleProjectile(Entity shooter, Location eyeLocation, Vector adjustedDirection, org.bukkit.World world, double range, int piercingLevel) {
        int maxPierce = piercingLevel;
        int entitiesPierced = 0;

        Location currentOrigin = eyeLocation.clone();
        double remainingRange = range;
        Vec3d lastEndPoint = LocationUtils.toVec3d(eyeLocation);
        java.util.Set<Entity> hitEntities = new java.util.HashSet<>();

        while (remainingRange > 0) {
            RayTraceResult blockResult = world.rayTraceBlocks(currentOrigin, adjustedDirection, remainingRange, FluidCollisionMode.NEVER, true);
            double maxDistance = remainingRange;
            Vec3d blockHitPoint = null;

            if (blockResult != null && blockResult.getHitBlock() != null) {
                maxDistance = blockResult.getHitPosition().distance(currentOrigin.toVector());
                blockHitPoint = LocationUtils.toVec3d(blockResult.getHitPosition().toLocation(world));

                Block hitBlock = blockResult.getHitBlock();
                if (hitBlock.getType() == Material.TNT) {
                    hitBlock.setType(Material.AIR);
                    world.createExplosion(hitBlock.getLocation().add(0.5, 0, 0.5), 4.0f, false, true, shooter);
                }
            }

            final java.util.Set<Entity> alreadyHit = hitEntities;
            RayTraceResult entityResult = world.rayTraceEntities(currentOrigin, adjustedDirection, maxDistance, 0.5,
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
                if (entitiesPierced > maxPierce) break;

                double hitDistance = currentOrigin.toVector().distance(entityResult.getHitPosition());
                remainingRange -= hitDistance + 0.5;
                currentOrigin = entityResult.getHitPosition().toLocation(world).add(adjustedDirection.clone().multiply(0.5));
            } else {
                break;
            }
        }
    }

    private static void applyDamageToEntity(Entity shooter, LivingEntity livingEntity, Vector direction, org.bukkit.World world) {
        Location hitLoc = livingEntity.getLocation().add(0, 1, 0);
        world.spawnParticle(Particle.DAMAGE_INDICATOR, hitLoc, 8, 0.3, 0.5, 0.3, 0);
        world.spawnParticle(Particle.CRIT, hitLoc, 10, 0.4, 0.6, 0.4, 0.15);
        world.spawnParticle(Particle.DUST, hitLoc, 5, 0.3, 0.4, 0.3, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(139, 0, 0), 1.0f));

        if (livingEntity instanceof org.bukkit.entity.Player player && player.isBlocking()) {
            ItemStack shield = player.getInventory().getItemInOffHand();
            if (shield.getType() == Material.SHIELD) {
                shield.damage(10, livingEntity);
            }
            return;
        }

        double hitDistance = shooter.getLocation().distance(livingEntity.getLocation());

        // A realistic handgun is lethal point-blank but loses stopping power very quickly over distance.
        // Since it fires 6 shots semi-auto, it must not deal high damage at range to avoid being overpowered.
        double damage;
        if (hitDistance <= 6) {
            damage = 10.0; // 5 hearts (Point-blank maximum stopping power)
        } else if (hitDistance <= 14) {
            damage = 7.5;  // 3.75 hearts (Close range)
        } else if (hitDistance <= 24) {
            damage = 5.0;  // 2.5 hearts (Medium range)
        } else if (hitDistance <= 35) {
            damage = 3.0;  // 1.5 hearts (Long range)
        } else {
            damage = 1.5;  // 0.75 hearts (Extreme range drop-off)
        }

        livingEntity.damage(damage, shooter);

        // Minimal knockback for a handgun so victims aren't continuously juggled in the air
        Vector knockback = direction.clone().multiply(0.25);
        // We add to current velocity rather than overwriting it, so gravity/momentum is respected
        livingEntity.setVelocity(livingEntity.getVelocity().add(knockback));
    }

    public InteractionResult useOnBlock(UseOnContext context) {
        return use(context.getWorld(), context.getPlayer(), context.getHand());
    }

    @Override
    public InteractionResult use(World world, Player CEplayer, InteractionHand hand) {
        return InteractionResult.PASS; // Handled exclusively by RevolverListener to mirror MusketListener
    }
}
