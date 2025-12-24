package com.minecraftcivilizations.specialization.Combat.Mobs;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Combat.Instinct;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.MathUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class HuntPlayerMobGoal implements Goal<Mob> {
    public static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey(Specialization.getInstance(), "monster_hunt_player"));

    private final Mob mob;
    private final double follow_range;
    private final boolean breaks_blocks;

    private float breakAmount = 0f;
    private Block block;
    private Collection<Player> nearbyPlayers;
    private static final Random random = new Random();

    private Entity last_target = null;
    private int tick = 0;
    private int reacquire_tick = 0;
    private float mobBreakScalar;

    public HuntPlayerMobGoal(Mob mob, double follow_range, boolean breaks_blocks, double break_scalar) {
        this.mob = mob;
        this.follow_range = follow_range;
        this.breaks_blocks = breaks_blocks;
        this.mobBreakScalar = (float)break_scalar;
    }

    @Override
    public boolean shouldActivate() {
        boolean b = mob.getTarget() == null || (breaks_blocks && mob.getTarget() != null);
        // Activate if we need to search for a target, or if we have a target and this goal should manage breaking
        Debug.broadcast("huntplayer", "should activate: "+(b?"<green>TRUE":"<red>FALSE"));
        return b;
    }

    @Override
    public boolean shouldStayActive() {
        // Stay active while searching or while we have a target (so tick() can handle both acquiring and breaking)
        return true;
    }

    @Override
    public void start() {
        Debug.broadcast("huntplayer", "<green>starting hunt player for " + mob.getName());
        // immediate attempt to find a target if none exists
        if (mob.getTarget() == null) {
            calculateNewTarget(true);
        } else {
            // ensure internal state reflects current target
            last_target = mob.getTarget();
            breakAmount = 0f;
            block = null;
            nearbyPlayers = null;
        }
        tick = 0;
        reacquire_tick = 0;
    }

    public void calculateRandomTarget(boolean detect_guardsman_level) {
        Predicate<Player> validGamemode = p ->
                p.getGameMode() == GameMode.SURVIVAL ||
                        p.getGameMode() == GameMode.ADVENTURE;

        List<Player> valid_targets = mob.getLocation().getNearbyPlayers(follow_range).stream()
                .filter(validGamemode)
                .filter(p -> p.getLocation().distance(mob.getLocation()) < follow_range)
                .toList();

        if (valid_targets.isEmpty()) return;

        if (detect_guardsman_level) {
            int max_level = valid_targets.stream()
                    .mapToInt(p -> CoreUtil.getPlayer(p).getSkillLevel(SkillType.GUARDSMAN))
                    .max()
                    .orElse(0);

            valid_targets = valid_targets.stream()
                    .filter(p -> CoreUtil.getPlayer(p).getSkillLevel(SkillType.GUARDSMAN) == max_level)
                    .toList();
        }

        Player chosen = valid_targets.get(ThreadLocalRandom.current().nextInt(valid_targets.size()));
        mob.setTarget(chosen);
    }



    public void calculateNewTarget(boolean detect_guardsman_level) {
//        Debug.broadcast("mob", "Calculating New Target with guardsman");
        double guardsman_zone_radius_base = 6; // the minimum radius for guardsman attraction
        double guardsman_zone_radius_per_lvl = 2; //each level increase for guardsman

        Predicate<Player> validGamemode = p ->
                p.getGameMode() == GameMode.SURVIVAL ||
                        p.getGameMode() == GameMode.ADVENTURE;

        mob.getLocation().getNearbyPlayers(follow_range).stream()
                .filter(validGamemode)
                .filter(p -> p.getLocation().distance(mob.getLocation()) < follow_range)
                .min((p1, p2) -> {
                    CustomPlayer player1 = CoreUtil.getPlayer(p1);
                    CustomPlayer player2 = CoreUtil.getPlayer(p2);

                    int lvl1 = player1.getSkillLevel(SkillType.GUARDSMAN);
                    int lvl2 = player2.getSkillLevel(SkillType.GUARDSMAN);

                    double zone1 = guardsman_zone_radius_base + (lvl1 * guardsman_zone_radius_per_lvl);
                    double zone2 = guardsman_zone_radius_base + (lvl2 * guardsman_zone_radius_per_lvl);

                    double d1sq = p1.getLocation().distanceSquared(mob.getLocation());
                    double d2sq = p2.getLocation().distanceSquared(mob.getLocation());

                    boolean p1_in_zone = d1sq <= (zone1 * zone1);
                    boolean p2_in_zone = d2sq <= (zone2 * zone2);

                    if (lvl1 != lvl2) {
                        if (lvl1 > lvl2) {
                            if (!p1_in_zone && p2_in_zone) return 1;
                            if (p1_in_zone && !p2_in_zone) return -1;
                            return Integer.compare(lvl2, lvl1);
                        } else {
                            if (!p2_in_zone && p1_in_zone) return -1;
                            if (p2_in_zone && !p1_in_zone) return 1;
                            return Integer.compare(lvl2, lvl1);
                        }
                    }

                    // ALWAYS fall back to distance check (including equal guardsman)
                    if (mob.getWorld().equals(p1.getWorld()) && mob.getWorld().equals(p2.getWorld())) {
                        return Double.compare(d1sq, d2sq);
                    }
                    return 0;
                })
                .ifPresent(player -> mob.setTarget(player));
    }


    @Override
    public void tick() {
        // If we don't have a target, periodically try to acquire one

        tick++;
        if (tick % 40 == 0) { // once per second
//            if (mob.getTarget() == null) {
//                calculateNewTarget(true);
//            }else

            if (mob.getTarget() == null) {
                calculateNewTarget(false);
            }
            if (ThreadLocalRandom.current().nextDouble() < 0.5) {
                calculateNewTarget(true);
//                if(ThreadLocalRandom.current().nextDouble() < 0.30){
//                }else{
//
//                }
            }
            if (mob.getTarget() == null) {
                calculateNewTarget(false);
                return;
            }
            tick = 0;
        }


        // We have a target. detect target changes and reset state
        Entity current_target = mob.getTarget();
        if(current_target==null) {
            return;
        }else if(current_target.getLocation().distance(current_target.getLocation())>follow_range){
            mob.setTarget(null);
        }else if (current_target != last_target) {
            last_target = current_target;
            breakAmount = 0f;
            block = null;
            nearbyPlayers = null;
            // small immediate raytrace attempt next tick
            reacquire_tick = 0;
        }

        // If this mob doesn't break blocks, nothing more to do here (movement/pathing handled by other systems)
        if (!breaks_blocks) return;

        // Do not attempt breaking during daytime
        if (mob.getWorld().isDayTime()) {
            block = null;
            breakAmount = 0f;
            nearbyPlayers = null;
            return;
        }

        double reach_distance = 3; //reach for breaking blocks


        // Occasional chance check before attempting to break anything.
        // Only run this check when we don't currently have a candidate block.
        if (block == null) {
            double percentage = SpecializationConfig.getMobConfig().get("BLOCK_BREAK_CHANCE_PERCENTAGE", Double.class);
            if (random.nextDouble() > percentage / 100d) {
                // Skip breaking attempt this cycle; try again later (every 20 ticks)
                reacquire_tick++;
                if (reacquire_tick < 20) return;
                reacquire_tick = 0;
            }

            // Basic validation: same world and within configured target radius
            if (!mob.getWorld().equals(current_target.getWorld())) return;

            double spray = 0.35; // angular offset for block raytrace, increase to increase block randomization


            Vector vectorToPlayer = current_target.getLocation().subtract(mob.getEyeLocation()).toVector().normalize().add(MathUtils.randomVectorCentered(spray)).normalize();
            // Raytrace for a blocking block up to distance 5 from the mob's eye (like the previous logic)
            RayTraceResult result = mob.getWorld().rayTrace(mob.getEyeLocation().add(MathUtils.randomVectorCentered(0.15)), vectorToPlayer.normalize(), reach_distance, FluidCollisionMode.NEVER, true, .15, entity -> false);
            if (result == null || result.getHitBlock() == null) {
                Location leglocation = mob.getLocation().add(0,0.5,0);
                vectorToPlayer = current_target.getLocation().subtract(leglocation).toVector();
                result = mob.getWorld().rayTrace(leglocation, vectorToPlayer.normalize(), reach_distance, FluidCollisionMode.NEVER, true, .15, entity -> false);
                if (result == null || result.getHitBlock() == null) {
//                    Debug.broadcast("huntplayer", "<#554400>Both blocks null");
                    return;
                }else{
//                    Debug.broadcast("huntplayer", "<gray>⛏ Block Found ⬇");
                }
            }else{
//                Debug.broadcast("huntplayer", "<gray>⛏ Block Found ⬆");
            }

            Block hit_block = result.getHitBlock();
            if (hit_block == null) return;

            float blockModifier = getBlockModifier(hit_block);
            if(blockModifier==0){
                return;
            }
//            List<String> deniedBlocks = SpecializationConfig.getMobConfig()
//                    .get("BLOCK_BREAK_IGNORE_LIST_REGEX", new TypeToken<List<String>>() {});

            if (hit_block.getType() == Material.AIR) return;

            String material_name = hit_block.getType().name();

//            if (deniedBlocks.stream().anyMatch(material_name::matches)) {
//                return;
//            }

            /**
             * Prevents mobs from breaking blocks at their feet
             */
            if(hit_block.getLocation().getY() < mob.getLocation().getY()-0.25){
                //block is below mob
                if(current_target.getLocation().getY()>=hit_block.getLocation().getY()){
                    return;
                }
            }

            // We have a valid block to break
            block = hit_block;
            breakAmount = 0f;
            nearbyPlayers = block.getLocation().getNearbyPlayers(16).stream()
                    .filter(player -> player.getGameMode().equals(GameMode.SURVIVAL))
                    .collect(Collectors.toSet());

            // Trigger instinct system (same behavior as previous BreakBlockMobGoal)
            if(mob instanceof Monster mon) {
                Instinct.onMobStartBreakingBlock(mon);
            }
        }

        // If we have a block, increment break progress and show visuals
        if (block != null) {

            /**
             * Block validation, incase it was broken
             * Also checks if the mob has walked too far away from the block
             */
            if (block.getType() == Material.AIR || block.getLocation().distance(mob.getLocation()) > reach_distance) {
                if (nearbyPlayers != null && !nearbyPlayers.isEmpty()) {
                    nearbyPlayers.forEach(player -> player.sendBlockDamage(block.getLocation(), 0));
                }

                block = null;
                breakAmount = 0f;
                nearbyPlayers = null;
                return;
            }

            float blockModifier = getBlockModifier(block);
            if(blockModifier==0){
                return;
            }

            float breakPercentagePerTick = 5f; // 1 second per block
            if (ReinforcementManager.isReinforced(block)){
                if(ReinforcementManager.isLightlyReinforced(block)){
                    breakPercentagePerTick = 0.5f;
                }
                if(ReinforcementManager.isHeavilyReinforced(block)){
                    block = null;
                    breakAmount = 0f;
                    nearbyPlayers = null;
                    return;
                }
            }
            breakPercentagePerTick *= blockModifier;
            breakPercentagePerTick *= mobBreakScalar;
            breakAmount += breakPercentagePerTick / 100f;

            if (breakAmount >= 1.0f) {
                // Only break "hard" blocks
                if (block.getBlockData().getMaterial().getHardness() > 0) {
                    block.breakNaturally(true, false);
                }
                // reset so we'll attempt to find a new obstruction next cycle
                block = null;
                breakAmount = 0f;
                nearbyPlayers = null;
                return;
            }

            // Send block damage to nearby players so they see progress
            if (nearbyPlayers != null && !nearbyPlayers.isEmpty()) {
                nearbyPlayers.forEach(player -> player.sendBlockDamage(block.getLocation(), breakAmount));
            }
        }
    }

    /**
     * Returns how quickly block break
     * Higher values break faster
     */
    private float getBlockModifier(Block block) {
        Material type = block.getType();

        if(type.name().contains("BRICK") || type.name().contains("_TILE")){
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
            case NETHERRACK:
            case CRIMSON_NYLIUM:
            case WARPED_NYLIUM:
            case SOUL_SOIL:
            case SOUL_SAND:
                return 1.75f;
            case CLAY:
            case FARMLAND:
            case COARSE_DIRT:
            case ROOTED_DIRT:
            case MAGMA_BLOCK:
            case MELON:
            case PUMPKIN:
            case CARVED_PUMPKIN:
            case JACK_O_LANTERN:
                return 1.55f;
            case CACTUS:
                return 1.25f;
            case COBBLESTONE:
            case COBBLED_DEEPSLATE:
            case DRIPSTONE_BLOCK:
            case TUFF:
                return 0.8f; // loose stone, breaks quick
            case STONE:
            case DEEPSLATE:
            case BASALT:
            case POLISHED_BASALT:
            case SMOOTH_BASALT:
                return 0.6f; // solid stone, holds together
            case ANDESITE:
            case DIORITE:
            case GRANITE:
                return 0.6f;
            case NETHERITE_BLOCK:
                return 0.05f;
            case CHEST:
                return 0.9f;
            case BARREL:
                return 0.6f;
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
            case SMITHING_TABLE:
            case STONECUTTER:
            case GRINDSTONE:
            case COBWEB:
                return 0.5f;
            case SLIME_BLOCK:
            case HONEY_BLOCK:
                return 0.4f;
            case TINTED_GLASS:
            case COPPER_BLOCK:
                return 0.25f;
            case IRON_BLOCK:
                return 0.125f;
            case DIAMOND_BLOCK:
                return 0.075f;
            case ANVIL:
                return 0.1f;
            case CHIPPED_ANVIL:
                return 0.2f;
            case DAMAGED_ANVIL:
                return 0.3f;
            case ENCHANTING_TABLE:
            case JUKEBOX:
            case RESPAWN_ANCHOR:
            case LODESTONE:
                return 0.125f;
            case CRYING_OBSIDIAN:
            case OBSIDIAN:
                return 0.025f;
                //BLACKLIST:
            case ANCIENT_DEBRIS:
            case DEEPSLATE_DIAMOND_ORE:
            case DIAMOND_ORE:
            case BEDROCK:
            case SPAWNER:
                return 0;
        }
        if(type.name().contains("BLACKSTONE")){
            return 0.6f;
        }
        if(type.name().contains("_LEAVES")){
            return 2.5f;
        }
        if(type.name().contains("_LOGS")){
            return 0.75f;
        }
        if(type.name().contains("GLASS")){
            return 1.5f;
        }
        if(type.name().contains("_ORE") || type.name().endsWith("_CONCRETE")){
            return 0.125f;
        }
        return 1.0f;
    }

    @Override
    public void stop() {
        Debug.broadcast("huntplayer", "<gray> stopping");
        // clean up internal state
        last_target = null;
        block = null;
        breakAmount = 0f;
        nearbyPlayers = null;
        Goal.super.stop();
    }

    @Override
    public GoalKey<Mob> getKey() {
        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        // This goal both picks a target and manages movement/interaction (breaking)
        return EnumSet.of(GoalType.TARGET, GoalType.MOVE);
    }
}
