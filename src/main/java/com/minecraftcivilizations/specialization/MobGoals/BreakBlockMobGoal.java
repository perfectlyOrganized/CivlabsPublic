package com.minecraftcivilizations.specialization.MobGoals;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Combat.Instinct;
import com.minecraftcivilizations.specialization.Reinforcement.ReinforcementManager;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

public class BreakBlockMobGoal implements Goal<Monster> {
    public static final GoalKey<Monster> KEY = GoalKey.of(Monster.class, new NamespacedKey(Specialization.getInstance(),"monster_break_block"));

    private final Monster monster;
    private float breakAmount = 0;
    private Block block;
    private Collection<Player> nearbyPlayers;
    private static final Random random = new Random();

    public BreakBlockMobGoal(Monster monster) {
        // The constructor takes the Player to follow and the Camel that is following
        this.monster = monster;
    } // change


    @Override
    public boolean shouldActivate() {
        if(monster.getTarget() == null) return false;
        if(monster.getWorld().isDayTime()) return false;
        // if(monster.getLocation().getY() > 60) return false;

        double percentage = SpecializationConfig.getMobConfig().get("BLOCK_BREAK_CHANCE_PERCENTAGE", Double.class);
        if(random.nextDouble() > percentage / 100d) return false;

        if(!monster.getWorld().equals(monster.getTarget().getWorld())) return false;

        Debug.broadcast("breakblock", "breaking should activate");
        Vector vectorToPlayer = monster.getTarget().getLocation().subtract(monster.getEyeLocation()).toVector();
        int targetRange = SpecializationConfig.getMobConfig().get("MOB_RULE_TARGET_RANGE", Integer.class);
        if(vectorToPlayer.lengthSquared() > targetRange*targetRange) return false;
        RayTraceResult result = monster.getWorld().rayTrace(monster.getEyeLocation(), vectorToPlayer.normalize(), 5, FluidCollisionMode.NEVER, true, .15, null);
        if(result == null || result.getHitBlock() == null) {
            result = monster.getWorld().rayTrace(monster.getEyeLocation().subtract(0,1,0), vectorToPlayer.normalize(), 5, FluidCollisionMode.NEVER, true, .15, null);
            if(result == null || result.getHitBlock() == null) return false;
        }


        block = result.getHitBlock();
        if (ReinforcementManager.isReinforced(block)) return false;
        List<String> deniedBlocks = SpecializationConfig.getMobConfig().get("BLOCK_BREAK_IGNORE_LIST_REGEX", new TypeToken<>(){});
        return block.getType() != Material.AIR && deniedBlocks.stream().noneMatch(it -> block.getType().name().matches(it));
    }

    @Override
    public boolean shouldStayActive() {
        Debug.broadcast("breakblock", "<blue>Should stay active: "+(monster.getTarget()!=null?" target is "+monster.getTarget().getName():"<gray> null target"));
        return breakAmount < 1.0 && !monster.getWorld().isDayTime();
    }

    @Override
    public void start() {
        nearbyPlayers = block.getLocation().getNearbyPlayers(16).stream().filter(player -> player.getGameMode().equals(GameMode.SURVIVAL)).collect(Collectors.toSet());
        
        // Trigger Instinct system for nearby Guardsmen
        Instinct.onMobStartBreakingBlock(monster);
        Debug.broadcast("breakblock", "<red>starting break");
    }

    @Override
    public void tick() {
        float breakPercentagePerTick = SpecializationConfig.getMobConfig().get("VISUAL_BREAKING_INCREASE_PER_TICK_PERCENTAGE", Float.class);
        breakAmount += breakPercentagePerTick / 100f;
        if(breakAmount >= 1.0){
            if(block.getBlockData().getMaterial().getHardness() > 0) {
                block.breakNaturally(true, false);
            }
            return;
        }

        nearbyPlayers.forEach(player -> player.sendBlockDamage(block.getLocation(), breakAmount));
    }


    @Override
    public GoalKey<Monster> getKey() {
        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.TARGET, GoalType.MOVE);
    }
}
