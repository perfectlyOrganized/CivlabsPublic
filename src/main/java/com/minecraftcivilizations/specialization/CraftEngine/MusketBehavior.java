package com.minecraftcivilizations.specialization.CraftEngine;

import com.minecraftcivilizations.specialization.Specialization;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptors;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import net.momirealms.craftengine.core.item.context.UseOnContext;
import net.momirealms.craftengine.core.sound.SoundSource;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MusketBehavior extends ItemBehavior {
    private final Key requiredAmmo;
    private final int requiredLoads; // Number of times crossbow must be loaded (default 3)
    public static final Factory FACTORY = new Factory();

    // Accuracy tracking - stores the timestamp when player started being still
    private static final ConcurrentHashMap<UUID, Long> playerStillSince = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Location> playerLastLocation = new ConcurrentHashMap<>();

    // Time required to gain full accuracy (1.25 seconds = 1250ms)
    private static final long ACCURACY_CHARGE_TIME_MS = 1250;

    // Spread constants (in blocks at 60m distance)
    private static final double FULL_ACCURACY_SPREAD_AT_60 = 3.0;
    private static final double NO_ACCURACY_SPREAD_AT_60 = 10.0;

    public static class Factory implements ItemBehaviorFactory {
        @Override
        public ItemBehavior create(Pack pack, Path path, String node, Key key, Map<String, Object> arguments) {
            String ammoId = (String) arguments.get("ammo");
            int requiredLoads = (Integer) arguments.getOrDefault("reload-time", 3);

            if (ammoId == null) {
                throw new IllegalArgumentException("Missing required parameter 'ammo'");
            }
            return new MusketBehavior(Key.of(ammoId), requiredLoads);
        }
    }

    public MusketBehavior(Key requiredAmmo, int requiredLoads) {
        this.requiredAmmo = requiredAmmo;
        this.requiredLoads = requiredLoads;
    }

    public Key getRequiredAmmo() {
        return requiredAmmo;
    }

    public int getRequiredLoads() {
        return requiredLoads;
    }

    /**
     * Gets the current load count for a musket
     */
    public static int getLoadCount(ItemStack itemStack) {
        NamespacedKey key = new NamespacedKey(Specialization.getInstance(), "musketLoadCount");
        return itemStack.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
    }

    /**
     * Increments the load count for a musket
     */
    public static void incrementLoadCount(ItemStack itemStack) {
        NamespacedKey key = new NamespacedKey(Specialization.getInstance(), "musketLoadCount");
        int current = getLoadCount(itemStack);
        itemStack.editPersistentDataContainer(pdc ->
            pdc.set(key, PersistentDataType.INTEGER, current + 1)
        );
    }

    /**
     * Resets the load count for a musket
     */
    public static void resetLoadCount(ItemStack itemStack) {
        NamespacedKey key = new NamespacedKey(Specialization.getInstance(), "musketLoadCount");
        itemStack.editPersistentDataContainer(pdc -> pdc.set(key, PersistentDataType.INTEGER, 0));
    }

    /**
     * Checks if musket is fully loaded (loaded required number of times)
     */
    public static boolean isFullyLoaded(ItemStack itemStack, int requiredLoads) {
        return getLoadCount(itemStack) >= requiredLoads;
    }

    /**
     * Resets the accuracy for a player (called when they move, jump, take damage, etc.)
     */
    public static void resetAccuracy(UUID playerId) {
        playerStillSince.remove(playerId);
        playerLastLocation.remove(playerId);
    }

    /**
     * Updates accuracy tracking for a player. Call this regularly (e.g., every tick).
     * Returns true if player has full accuracy.
     */
    public static boolean updateAccuracyTracking(org.bukkit.entity.Player player) {
        UUID playerId = player.getUniqueId();
        Location currentLoc = player.getLocation();
        Location lastLoc = playerLastLocation.get(playerId);

        // Check if player moved (crouch walking doesn't count)
        boolean moved = false;
        if (lastLoc != null) {
            // Check horizontal movement (ignore Y for crouch walking check)
            double dx = currentLoc.getX() - lastLoc.getX();
            double dz = currentLoc.getZ() - lastLoc.getZ();
            double horizontalMovement = Math.sqrt(dx * dx + dz * dz);

            // If sneaking, allow slow movement (crouch walking)
            if (player.isSneaking()) {
                // Crouch walking speed is about 1.3 blocks/second = 0.065 blocks/tick
                moved = horizontalMovement > 0.07;
            } else {
                moved = horizontalMovement > 0.01;
            }

            // Jumping always breaks accuracy
            if (currentLoc.getY() > lastLoc.getY() + 0.1) {
                moved = true;
            }
        }

        playerLastLocation.put(playerId, currentLoc.clone());

        if (moved) {
            playerStillSince.remove(playerId);
            return false;
        }

        // Player is still - start or continue tracking
        if (!playerStillSince.containsKey(playerId)) {
            playerStillSince.put(playerId, System.currentTimeMillis());
        }

        long stillDuration = System.currentTimeMillis() - playerStillSince.get(playerId);
        return stillDuration >= ACCURACY_CHARGE_TIME_MS;
    }

    /**
     * Gets the current accuracy level (0.0 = no accuracy, 1.0 = full accuracy, 1.5 = perfect accuracy)
     * Perfect accuracy (1.5) is achieved by sneaking while standing still after the charge time.
     */
    private static double getAccuracyLevel(org.bukkit.entity.Player player) {
        UUID playerId = player.getUniqueId();
        Long stillSince = playerStillSince.get(playerId);

        if (stillSince == null) {
            return 0.0;
        }

        long stillDuration = System.currentTimeMillis() - stillSince;
        double baseAccuracy = Math.min(1.0, (double) stillDuration / ACCURACY_CHARGE_TIME_MS);

        // Perfect accuracy bonus: if sneaking AND has full accuracy, grant perfect accuracy (1.5)
        // This means almost no spread at all
        if (player.isSneaking() && baseAccuracy >= 1.0) {
            return 1.5; // Perfect accuracy - minimal spread
        }

        return baseAccuracy;
    }

    /**
     * Calculates spread based on distance and accuracy level.
     * At 60m: no accuracy (0.0) = 10 blocks spread, full accuracy (1.0) = 3 blocks spread
     * Perfect accuracy (1.5) = 0.5 blocks spread (almost pinpoint)
     * Spread scales with distance - further shots are less accurate.
     */
    private static double calculateSpread(double distance, double accuracyLevel) {
        double spreadAt60;

        if (accuracyLevel >= 1.5) {
            // Perfect accuracy - almost no spread (0.5 blocks at 60m)
            spreadAt60 = 0.5;
        } else if (accuracyLevel >= 1.0) {
            // Full accuracy - 3 blocks spread at 60m
            spreadAt60 = FULL_ACCURACY_SPREAD_AT_60;
        } else {
            // Interpolate between no accuracy spread and full accuracy spread
            spreadAt60 = NO_ACCURACY_SPREAD_AT_60 - (accuracyLevel * (NO_ACCURACY_SPREAD_AT_60 - FULL_ACCURACY_SPREAD_AT_60));
        }

        // Base spread scales linearly with distance
        double baseSpread = (spreadAt60 / 60.0) * distance;

        // Distance penalty: shots beyond 40 blocks become progressively less accurate
        // This simulates bullet drop and wind effects at range
        double distancePenalty = 1.0;
        if (distance > 40) {
            // Add 5% spread penalty per 10 blocks beyond 40
            distancePenalty = 1.0 + ((distance - 40) / 10.0) * 0.05;
        }

        return baseSpread * distancePenalty;
    }


    /**
     * Spawns particle trail from start to end point - ENHANCED VERSION
     */
    private static void spawnParticleBeam(Vec3d start, Vec3d end, org.bukkit.World world) {
        double distance = Math.sqrt(Vec3d.distanceToSqr(start, end));
        int particleCount = (int) (distance * 3);

        for (int i = 0; i <= particleCount; i++) {
            double progress = (double) i / particleCount;
            double x = start.x + (end.x - start.x) * progress;
            double y = start.y + (end.y - start.y) * progress;
            double z = start.z + (end.z - start.z) * progress;

            // Enhanced muzzle flash effect near start
            if (progress < 0.08) {
                world.spawnParticle(Particle.FLAME, x, y, z, 3, 0.08, 0.08, 0.08, 0.02);
                world.spawnParticle(Particle.LAVA, x, y, z, 1, 0.05, 0.05, 0.05, 0);
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, x, y, z, 2, 0.1, 0.1, 0.1, 0.03);
                world.spawnParticle(Particle.SMOKE, x, y, z, 3, 0.12, 0.12, 0.12, 0.02);
            }

            // Main bullet trail with spark effect
            world.spawnParticle(Particle.CRIT, x, y, z, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.ELECTRIC_SPARK, x, y, z, 1, 0.02, 0.02, 0.02, 0);

            // Occasional smoke puffs along trail
            if (i % 4 == 0) {
                world.spawnParticle(Particle.SMOKE, x, y, z, 1, 0.02, 0.02, 0.02, 0);
            }

            // Dust trail effect
            if (i % 6 == 0) {
                world.spawnParticle(Particle.DUST, x, y, z, 1, 0.01, 0.01, 0.01, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(180, 180, 180), 0.5f));
            }
        }
    }

    /**
     * Main shooting method
     */
    public static void shootProjectile(Entity shooter, Location eyeLocation, Vector direction, org.bukkit.World world) {
        shootProjectile(shooter, eyeLocation, direction, world, 0, 0); // Default: no multishot, no piercing
    }

    /**
     * Main shooting method with enchantment support
     */
    public static void shootProjectile(Entity shooter, Location eyeLocation, Vector direction, org.bukkit.World world, int multishotLevel, int piercingLevel) {
        double range = 80.0; // Max range in blocks

        // Get accuracy level for the shooter
        double accuracyLevel = 0.0;
        if (shooter instanceof org.bukkit.entity.Player player) {
            accuracyLevel = getAccuracyLevel(player);
            // Reset accuracy after shooting
            resetAccuracy(player.getUniqueId());

            // Recoil boost: if looking down and in the air, apply upward boost
            applyRecoilBoost(player, direction);
        }

        // Determine number of projectiles (Multishot)
        int projectileCount = 1;
        if (multishotLevel > 0) {
            projectileCount = 3; // Multishot always fires 3 projectiles
        }

        // Fire projectiles
        for (int p = 0; p < projectileCount; p++) {
            Vector projectileDirection;

            if (projectileCount > 1) {
                // Multishot: spread projectiles slightly (about 10 degrees apart)
                double spreadAngle = 0.0;
                if (p == 0) spreadAngle = -0.15; // Left projectile
                else if (p == 2) spreadAngle = 0.15; // Right projectile
                // p == 1 is center, no spread

                projectileDirection = applyMultishotSpread(direction.clone(), spreadAngle);
            } else {
                projectileDirection = direction.clone();
            }

            // Apply accuracy spread
            Vector adjustedDirection = applySpread(projectileDirection, range, accuracyLevel);

            // Fire single projectile with piercing support
            fireSingleProjectile(shooter, eyeLocation, adjustedDirection, world, range, piercingLevel);
        }

        // Play sound at 2x projectile range (160 blocks) - use higher volume for distance
        World ceWorld = BukkitAdaptors.adapt(world);
        // Play sound with increased range (volume 4.0 = ~160 block range)
        ceWorld.playSound(LocationUtils.toVec3d(shooter.getLocation()), Key.of("specialization:rifle_shot"), 4.0f, 0.9f + (float) (Math.random() * 0.2), SoundSource.PLAYER);
    }

    /**
     * Applies multishot spread to direction
     */
    private static Vector applyMultishotSpread(Vector direction, double spreadAngle) {
        Vector dir = direction.normalize();
        Vector helper = Math.abs(dir.getY()) < 0.9 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        Vector right = dir.getCrossProduct(helper).normalize();

        return dir.add(right.multiply(spreadAngle)).normalize();
    }

    /**
     * Fires a single projectile with piercing support
     */
    private static void fireSingleProjectile(Entity shooter, Location eyeLocation, Vector adjustedDirection, org.bukkit.World world, double range, int piercingLevel) {
        // Max entities to pierce (0 = no piercing, 1 = pierce 1, etc.)
        int maxPierce = piercingLevel;
        int entitiesPierced = 0;

        Location currentOrigin = eyeLocation.clone();
        double remainingRange = range;
        Vec3d lastEndPoint = LocationUtils.toVec3d(eyeLocation);

        java.util.Set<Entity> hitEntities = new java.util.HashSet<>();

        while (remainingRange > 0) {
            // Ray trace for blocks
            RayTraceResult blockResult = world.rayTraceBlocks(
                    currentOrigin,
                    adjustedDirection,
                    remainingRange,
                    FluidCollisionMode.NEVER,
                    true
            );

            double maxDistance = remainingRange;
            Vec3d blockHitPoint = null;

            if (blockResult != null && blockResult.getHitBlock() != null) {
                maxDistance = blockResult.getHitPosition().distance(currentOrigin.toVector());
                blockHitPoint = LocationUtils.toVec3d(blockResult.getHitPosition().toLocation(world));

                // TNT interaction
                Block hitBlock = blockResult.getHitBlock();
                if (hitBlock.getType() == Material.TNT) {
                    hitBlock.setType(Material.AIR);
                    Location tntLocation = hitBlock.getLocation().add(0.5, 0, 0.5);
                    world.createExplosion(tntLocation, 4.0f, false, true, shooter);
                }
            }

            // Ray trace for entities (excluding already hit ones)
            final java.util.Set<Entity> alreadyHit = hitEntities;
            RayTraceResult entityResult = world.rayTraceEntities(
                    currentOrigin,
                    adjustedDirection,
                    maxDistance,
                    0.5,
                    entity -> entity != shooter && entity instanceof LivingEntity && !alreadyHit.contains(entity)
            );

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

            // Spawn particle trail for this segment
            spawnParticleBeam(lastEndPoint, endPoint, world);
            lastEndPoint = endPoint;

            // Handle entity hit
            if (hitEntity instanceof LivingEntity livingEntity) {
                hitEntities.add(hitEntity);

                // Apply damage and effects
                applyDamageToEntity(shooter, livingEntity, adjustedDirection, world);

                entitiesPierced++;

                // Check if we can pierce more
                if (entitiesPierced > maxPierce) {
                    break; // Stop piercing
                }

                // Continue from hit position
                double hitDistance = currentOrigin.toVector().distance(entityResult.getHitPosition());
                remainingRange -= hitDistance + 0.5; // Small offset to pass through entity
                currentOrigin = entityResult.getHitPosition().toLocation(world).add(adjustedDirection.clone().multiply(0.5));
            } else {
                // Hit block or max range - stop
                break;
            }
        }
    }

    /**
     * Applies damage and effects to a hit entity
     */
    private static void applyDamageToEntity(Entity shooter, LivingEntity livingEntity, Vector direction, org.bukkit.World world) {
        // Enhanced impact particles
        Location hitLoc = livingEntity.getLocation().add(0, 1, 0);
        world.spawnParticle(Particle.DAMAGE_INDICATOR, hitLoc, 12, 0.3, 0.5, 0.3, 0);
        world.spawnParticle(Particle.CRIT, hitLoc, 18, 0.4, 0.6, 0.4, 0.15);
        world.spawnParticle(Particle.SMOKE, hitLoc, 10, 0.3, 0.4, 0.3, 0.08);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, hitLoc, 5, 0.2, 0.3, 0.2, 0.05);
        world.spawnParticle(Particle.DUST, hitLoc, 8, 0.3, 0.4, 0.3, 0,
            new Particle.DustOptions(org.bukkit.Color.fromRGB(139, 0, 0), 1.2f)); // Blood red

        // Check for shield blocking
        if (livingEntity instanceof org.bukkit.entity.Player player && player.isBlocking()) {
            ItemStack shield = player.getInventory().getItemInOffHand();
            if (shield.getType() == Material.SHIELD) {
                shield.damage(20, livingEntity);
            }
            return;
        }

        // High damage to deal 3.5-4 hearts through full iron armor at optimal range
        double baseDamage = 24.0;
        double hitDistance = shooter.getLocation().distance(livingEntity.getLocation());

        // Reverse distance scaling (as requested): less damage up close, more from afar
        double damageMultiplier;
        if (hitDistance <= 10) {
            damageMultiplier = 0.5; // Weak up close (12 damage / 6 hearts)
        } else if (hitDistance <= 30) {
            damageMultiplier = 0.5 + ((hitDistance - 10) / 20.0) * 0.5; // Scales up to 1.0x at 30 blocks
        } else if (hitDistance <= 50) {
            damageMultiplier = 1.0 + ((hitDistance - 30) / 20.0) * 0.3; // Scales up to 1.3x at 50 blocks
        } else {
            damageMultiplier = 1.3; // Max damage at long range (31.2 damage / 15+ hearts)
        }

        double damage = baseDamage * damageMultiplier;

        // Apply damage
        livingEntity.damage(damage, shooter);

        // Apply Slowness 3 for 2 seconds
        livingEntity.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                40,
                2,
                false,
                true,
                true
        ));

        // Apply strong knockback
        Vector knockback = direction.clone().multiply(2.0);
        livingEntity.setVelocity(knockback);
    }

    /**
     * Applies recoil boost when player is looking down and jumping while shooting.
     * Similar to a rocket jump but more subtle - like a wind charge effect.
     */
    private static void applyRecoilBoost(org.bukkit.entity.Player player, Vector shootDirection) {
        // Check if player is looking downward (pitch > 45 degrees = looking down)
        float pitch = player.getLocation().getPitch();

        // Pitch is positive when looking down (90 = straight down)
        if (pitch > 45) {
            // Check if player is not on ground (jumping/falling)
            if (!player.isOnGround()) {
                // Calculate boost strength based on how much they're looking down
                // At 45 degrees: minimal boost, at 90 degrees (straight down): max boost
                double lookDownFactor = (pitch - 45) / 45.0; // 0.0 at 45°, 1.0 at 90°

                // Base boost values (subtle, like a wind charge)
                double verticalBoost = 0.4 + (lookDownFactor * 0.3); // 0.4 to 0.7
                double horizontalBoost = 0.2 + (lookDownFactor * 0.2); // 0.2 to 0.4

                // Get the opposite direction of where they're shooting (recoil)
                Vector recoil = shootDirection.clone().multiply(-1);

                // Apply the boost
                Vector currentVelocity = player.getVelocity();
                Vector boost = new Vector(
                        recoil.getX() * horizontalBoost,
                        Math.max(verticalBoost, recoil.getY() * -1 * verticalBoost), // Always some upward component
                        recoil.getZ() * horizontalBoost
                );

                player.setVelocity(currentVelocity.add(boost));

                // Visual/audio feedback for the boost
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 8, 0.3, 0.1, 0.3, 0.05);
                player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation(), 5, 0.2, 0.1, 0.2, 0.02);
            }
        }
    }

    /**
     * Applies spread to the direction vector based on accuracy level
     */
    private static Vector applySpread(Vector direction, double distance, double accuracyLevel) {
        double spread = calculateSpread(distance, accuracyLevel);
        // Convert spread in blocks to angle deviation
        double spreadAngle = Math.atan(spread / distance);

        double xOffset = ThreadLocalRandom.current().nextGaussian() * spreadAngle;
        double yOffset = ThreadLocalRandom.current().nextGaussian() * spreadAngle;

        Vector dir = direction.normalize();
        Vector helper = Math.abs(dir.getY()) < 0.9 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        Vector right = dir.getCrossProduct(helper).normalize();
        Vector up = right.getCrossProduct(dir).normalize();

        return dir.add(right.multiply(xOffset)).add(up.multiply(yOffset)).normalize();
    }

    /**
     * Calculates damage based on distance using the polynomial function from the doc:
     * f(x) = x^5 * (-0.0000001235168665456686257)
     *      + x^4 * 0.0000283627654767012725847
     *      + x^3 * (-0.0022133182312182662923560)
     *      + x^2 * 0.0588415821457835240887695
     *      + x * (-0.0039157022143823987031937)
     *      + 0.9472591990512069853028212
     *
     * Returns damage in half hearts (for full iron armor)
     */
    private static double calculateDamage(double distance) {
        double damage = Math.pow(distance, 5) * (-0.0000001235168665456686257)
                      + Math.pow(distance, 4) * 0.0000283627654767012725847
                      + Math.pow(distance, 3) * (-0.0022133182312182662923560)
                      + Math.pow(distance, 2) * 0.0588415821457835240887695
                      + distance * (-0.0039157022143823987031937)
                      + 0.9472591990512069853028212;

        // Clamp damage to reasonable values (minimum 1, max based on curve peak ~14)
        return Math.max(1.0, Math.min(damage, 20.0));
    }

    public InteractionResult useOnBlock(UseOnContext context) {
        return use(context.getWorld(), context.getPlayer(), context.getHand());
    }

    @Override
    public InteractionResult use(World world, Player CEplayer, InteractionHand hand) {
        if (CEplayer == null) return InteractionResult.PASS;
        org.bukkit.entity.Player bukkitPlayer = (org.bukkit.entity.Player) CEplayer.platformPlayer();
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        Item<?> item = CEplayer.getItemInHand(hand);
        if (item.getItem() instanceof ItemStack itemStack) {
            // Check if musket is fully loaded (loaded 3 times via crossbow mechanic)
            if (isFullyLoaded(itemStack, requiredLoads)) {
                if (bukkitPlayer.hasCooldown(itemStack)) return InteractionResult.PASS;

                // Fire the musket!
                item.hurtAndBreak(1, null, null);
                Location eyeLocation = bukkitPlayer.getEyeLocation();
                Vector direction = eyeLocation.getDirection().normalize();
                shootProjectile(bukkitPlayer, eyeLocation, direction, (org.bukkit.World) world.platformWorld());

                // Random cooldown between 3-5 seconds (60-100 ticks)
                int randomCooldown = 60 + ThreadLocalRandom.current().nextInt(41);
                bukkitPlayer.setCooldown(itemStack.getType(), randomCooldown);

                // Reset load count after firing
                resetLoadCount(itemStack);

                // Keep crossbow visually loaded (dummy arrow for aesthetics)
                org.bukkit.inventory.meta.CrossbowMeta meta = (org.bukkit.inventory.meta.CrossbowMeta) itemStack.getItemMeta();
                if (meta.getChargedProjectiles().isEmpty()) {
                    meta.addChargedProjectile(new ItemStack(Material.ARROW));
                    itemStack.setItemMeta(meta);
                }

                return InteractionResult.SUCCESS;
            }

            // Not fully loaded - let vanilla crossbow loading happen (handled by MusketListener)
            int currentLoads = getLoadCount(itemStack);
            CEplayer.sendActionBar(Component.text("Load progress: " + currentLoads + "/" + requiredLoads));
        }

        return InteractionResult.PASS; // Let vanilla crossbow behavior handle loading
    }
}


