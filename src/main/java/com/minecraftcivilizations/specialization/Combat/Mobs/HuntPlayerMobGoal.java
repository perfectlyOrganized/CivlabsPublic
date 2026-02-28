package com.minecraftcivilizations.specialization.Combat.Mobs;

import com.minecraftcivilizations.specialization.Combat.Instinct;
import com.minecraftcivilizations.specialization.OpenLab;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import com.minecraftcivilizations.specialization.util.MathUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.minecraftcivilizations.specialization.util.MathUtils.random;

/**
 * Arclight/Spigot compatible version - no Paper/Destroystokyo APIs
 * This uses BukkitRunnable instead of a custom Goal system
 */
public class HuntPlayerMobGoal implements Listener {

    private final OpenLab plugin;
    private final Map<UUID, HuntData> huntingMobs = new HashMap<>();

    public HuntPlayerMobGoal(OpenLab plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Run every tick to update mob behavior
        new BukkitRunnable() {
            @Override
            public void run() {
                tickAllMobs();
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Add hunting behavior to a mob
     */
    public void addHuntGoal(Mob mob, double followRange, boolean breaksBlocks, double breakScalar) {
        huntingMobs.put(mob.getUniqueId(), new HuntData(mob, followRange, breaksBlocks, (float)breakScalar));
    }

    /**
     * Remove hunting behavior from a mob
     */
    public void removeHuntGoal(Mob mob) {
        huntingMobs.remove(mob.getUniqueId());
    }

    /**
     * Check if a mob has hunting behavior
     */
    public boolean hasHuntGoal(Mob mob) {
        return huntingMobs.containsKey(mob.getUniqueId());
    }

    private void tickAllMobs() {
        Iterator<Map.Entry<UUID, HuntData>> iterator = huntingMobs.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, HuntData> entry = iterator.next();
            HuntData data = entry.getValue();

            // Remove if mob is dead or invalid
            if (data.mob == null || !data.mob.isValid() || data.mob.isDead()) {
                iterator.remove();
                continue;
            }

            // Tick this mob's hunting behavior
            data.tick();
        }
    }

    /**
     * Event listener to handle target changes
     */
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;

        HuntData data = huntingMobs.get(mob.getUniqueId());
        if (data == null) return;

        // If target changed, reset breaking state
        if (event.getTarget() != data.lastTarget) {
            data.block = null;
            data.breakAmount = 0f;
            data.nearbyPlayers = null;
        }
    }

    /**
     * Data class for each hunting mob
     */
    private class HuntData {
        private final Mob mob;
        private final double followRange;
        private final boolean breaksBlocks;
        private final float breakScalar;

        private Entity lastTarget = null;
        @Deprecated
        private int tick = 0;
        private long lastBreakTime = 0;

        // Block breaking fields
        private Block block = null;
        private float breakAmount = 0f;
        private Collection<Player> nearbyPlayers = null;

        HuntData(Mob mob, double followRange, boolean breaksBlocks, float breakScalar) {
            this.mob = mob;
            this.followRange = followRange;
            this.breaksBlocks = breaksBlocks;
            this.breakScalar = breakScalar;
            this.tick = ThreadLocalRandom.current().nextInt(120);
        }

        void tick() {
            tick++;

            // Handle target acquisition every second (20 ticks)
            if (tick % 120 == 0) {
                acquireTarget();
            }

            // Handle block breaking if enabled
            if (breaksBlocks) {
                handleBlockBreaking();
            }

            // Reset tick counter every 120 ticks
            if (tick >= 120) {
                tick = 0;
            }
        }

        private void acquireTarget() {
            // If mob has a target, check if it's still valid
            Entity currentTarget = mob.getTarget();

            if (currentTarget != null) {
                // Check if target is too far away
                if (!mob.getWorld().equals(currentTarget.getWorld()) ||
                        mob.getLocation().distance(currentTarget.getLocation()) > followRange) {
                    mob.setTarget(null);
                    currentTarget = null;
                }

                // Check if target is dead
                if (currentTarget instanceof LivingEntity && ((LivingEntity) currentTarget).isDead()) {
                    mob.setTarget(null);
                    currentTarget = null;
                }
            }

            // If no target, find one
            if (currentTarget == null) {
                findNewTarget();
            }
        }

        private void findNewTarget() {
            Predicate<Player> validGamemode = p ->
                    p.getGameMode() == GameMode.SURVIVAL ||
                            p.getGameMode() == GameMode.ADVENTURE;

            double maxVertical = SpecializationConfig.getMobConfig().getDouble("MOB_RULE_VERTICAL_FOLLOW_RANGE");

            // Get nearby players
            List<Player> nearby = mob.getWorld().getPlayers().stream()
                    .filter(validGamemode)
                    .filter(p -> p.getLocation().distance(mob.getLocation()) <= followRange)
                    .filter(p -> {
                        if (!mob.hasLineOfSight(p)) {
                            return false;
                        }
                        if (mob.getType() == EntityType.SPIDER || mob.getType() == EntityType.CAVE_SPIDER) {
                            return true;
                        }
                        double verticalDistance = Math.abs(p.getLocation().getY() - mob.getLocation().getY());
                        return verticalDistance <= maxVertical;
                    })
                    .toList();

            if (nearby.isEmpty()) return;

            // Find best target based on Guardsman skill
            double guardsmanBaseRadius = 6;
            double guardsmanRadiusPerLevel = 2;

            Player bestTarget = null;
            double bestScore = Double.MAX_VALUE;

            for (Player player : nearby) {
                CustomPlayer cp = CustomPlayerManager.INSTANCE.getCustomPlayer(player);
                if (cp == null) continue;
                if (cp.getCreation_date() < SpecializationConfig.getMobConfig().getDouble("NEW_PLAYER_GRACE_PERIOD")) continue;
                int guardsmanLevel = cp.getSkillLevel(SkillType.GUARDSMAN);
                double guardZone = guardsmanBaseRadius + (guardsmanLevel * guardsmanRadiusPerLevel);
                double distance = player.getLocation().distance(mob.getLocation());

                // Calculate score (lower is better)
                double score;
                if (distance <= guardZone) {
                    // In guard zone - strongly prefer this target
                    score = distance - (guardsmanLevel * 10); // Negative bonus for being in zone
                } else {
                    score = distance + (100 * guardsmanLevel); // Positive penalty for high guardsman far away
                }

                if (score < bestScore) {
                    bestScore = score;
                    bestTarget = player;
                }
            }

            if (bestTarget != null) {
                mob.setTarget(bestTarget);
            }
        }

        private void handleBlockBreaking() {
            Entity target = mob.getTarget();
            if (target == null) return;

            // Don't break blocks during daytime
            if (mob.getWorld().getTime() >= 0 && mob.getWorld().getTime() < 12300) {
                block = null;
                breakAmount = 0f;
                nearbyPlayers = null;
                return;
            }

            double reachDistance = 3.0;

            // If no current block to break, try to find one
            if (block == null) {
                // Random chance to attempt breaking
                double chance = SpecializationConfig.getMobConfig().getDouble("BLOCK_BREAK_CHANCE_PERCENTAGE") / 100.0;
                if (ThreadLocalRandom.current().nextDouble() > chance) {
                    return;
                }

                // Try to find a block between mob and target
                findBlockToBreak(target, reachDistance);
            }

            // If we have a block, break it
            if (block != null) {
                breakBlock(target, reachDistance);
            }
        }

        private void findBlockToBreak(Entity target, double reachDistance) {
            Vector direction = target.getLocation().subtract(mob.getEyeLocation()).toVector().normalize();
            double spray = 0.35;
            direction.add(MathUtils.randomVectorCentered(spray)).normalize();

            // Ray trace from eyes
            RayTraceResult result = mob.getWorld().rayTrace(
                    mob.getEyeLocation().add(MathUtils.randomVectorCentered(0.15)),
                    direction,
                    reachDistance,
                    FluidCollisionMode.NEVER,
                    true,
                    0.15,
                    entity -> false
            );

            // If no hit, try from body
            if (result == null || result.getHitBlock() == null) {
                Location bodyLoc = mob.getLocation().add(0, 0.5, 0);
                Vector bodyDirection = target.getLocation().subtract(bodyLoc).toVector().normalize();
                result = mob.getWorld().rayTrace(
                        bodyLoc,
                        bodyDirection,
                        reachDistance,
                        FluidCollisionMode.NEVER,
                        true,
                        0.15,
                        entity -> false
                );
            }

            // Check if we found a valid block
            if (result != null && result.getHitBlock() != null) {
                Block hitBlock = result.getHitBlock();

                // Validate block
                if (hitBlock.getType() != Material.AIR && getBlockModifier(hitBlock) > 0) {
                    // Don't break blocks below the mob if target is above
                    if (hitBlock.getLocation().getY() >= mob.getLocation().getY() - 0.25 ||
                            target.getLocation().getY() < hitBlock.getLocation().getY()) {

                        block = hitBlock;
                        breakAmount = 0f;
                        nearbyPlayers = block.getWorld().getPlayers().stream()
                                .filter(p -> p.getLocation().distance(block.getLocation()) <= 16)
                                .filter(p -> p.getGameMode() == GameMode.SURVIVAL)
                                .collect(Collectors.toSet());

                        if (mob instanceof Monster) {
                            Instinct.onMobStartBreakingBlock((Monster) mob);
                        }
                    }
                }
            }
        }

        private void breakBlock(Entity target, double reachDistance) {
            // Check if block is still valid
            long currentTime = System.currentTimeMillis();
            if (lastBreakTime == 0) {
                lastBreakTime = currentTime;
                return;
            }
            float timeDelta = (currentTime - lastBreakTime) / 1000.0f;
            lastBreakTime = currentTime;
            timeDelta = Math.min(timeDelta, 0.1f);

            if (block.getType() == Material.AIR ||
                    block.getLocation().distance(mob.getLocation()) > reachDistance) {

                // Clear damage display
                if (nearbyPlayers != null) {
                    nearbyPlayers.forEach(p -> p.sendBlockDamage(block.getLocation(), 0));
                }

                block = null;
                breakAmount = 0f;
                nearbyPlayers = null;
                return;
            }

            float blockModifier = getBlockModifier(block);
            if (blockModifier == 0) {
                block = null;
                lastBreakTime = 0;
                return;
            }


            float breakPerSecond = 0.9f; // Base speed

            // Check reinforcement
            if (ReinforcementManager.isReinforced(block)) {
                if (ReinforcementManager.isLightlyReinforced(block)) {
                    breakPerSecond = 0.5f;
                }
                if (ReinforcementManager.isHeavilyReinforced(block)) {
                    block = null;
                    breakAmount = 0f;
                    nearbyPlayers = null;
                    lastBreakTime = 0;
                    return;
                }
            }

            breakPerSecond *= blockModifier;
            breakPerSecond *= breakScalar;
            breakAmount += breakPerSecond * 0.2f * timeDelta;

            // Check if block is broken
            if (breakAmount >= 1.0f) {
                if (block.getBlockData().getMaterial().getHardness() > 0) {
                    block.breakNaturally();
                    block.getWorld().playSound(
                            block.getLocation(),
                            Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR,
                            SoundCategory.BLOCKS,
                            0.4f,
                            1.0f + random(-0.1f, 0.1f)
                    );
                }

                block = null;
                breakAmount = 0f;
                nearbyPlayers = null;
                lastBreakTime = 0;
                return;
            }

            // Show block damage to nearby players
            if (nearbyPlayers != null && !nearbyPlayers.isEmpty()) {
                nearbyPlayers.forEach(p -> p.sendBlockDamage(block.getLocation(), breakAmount));
            }
        }

        private float getBlockModifier(Block block) {
            Material type = block.getType();
            // Copy your existing getBlockModifier logic here
            // (The long switch statement from your original class)
            if(type.name().contains("BRICK") || type.name().contains("IRON") || type.name().contains("_TILE")){
                return 0;
            }
            switch(type){
                case DIRT:
                case GRAVEL:
                case SAND:
                    return 2.5f;
                case GRASS_BLOCK:
                case MUD:
                case MYCELIUM:
                case PODZOL:
                    return 2.0f;
                default:
                    if(type.name().contains("_LEAVES")) return 2.5f;
                    if(type.name().contains("_LOGS")) return 0.75f;
                    if(type.name().contains("GLASS")) return 1.5f;
                    return 1.0f;
            }
        }
    }
}