package com.minecraftcivilizations.specialization.MobGoals;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.minecraftcivilizations.specialization.CraftEngine.MusketBehavior;
import com.minecraftcivilizations.specialization.Specialization;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;

import java.util.EnumSet;

public class ShootPlayerMobGoal implements Goal<Mob> {
    public static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey(Specialization.getInstance(), "monster_shoot_player"));

    private final Mob mob;
    private Entity target;
    private Entity last_target;
    private int cooldown = 0;
    private final double range = 20.0;

    public ShootPlayerMobGoal(Mob mob) {
        this.mob = mob;
    }
    @Override
    public boolean shouldStayActive() {
        return shouldActivate() && mob.getTarget() != null;
    }
    @Override
    public boolean shouldActivate() {
        return true; //Objects.equals(CraftEngineItems.getCustomItemId(monster.getEquipment().getItemInMainHand()), Key.of("specialization:musket"));
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null || target.isDead() || !target.isValid()) {
            return;
        }

        // Check distance
        double distance = mob.getLocation().distance(target.getLocation());
        if (distance > range) {
            // Optionally move closer
            if (mob instanceof Creature) {
                ((Creature) mob).getPathfinder().moveTo(target, 1.0);
            }
            return;
        }

        // Stop moving when in range
        if (mob instanceof Creature) {
            ((Creature) mob).getPathfinder().stopPathfinding();
        }

        // Face target
        mob.lookAt(target.getEyeLocation());

        // Cooldown management
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // Shoot
        Specialization.getInstance().getLogger().info("Shooting at target!");
        shootProjectile(target);

        // Set cooldown (in ticks: 20 ticks = 1 second)
        cooldown = 40; // 2 seconds
    }

    @Override
    public GoalKey<Mob> getKey() {
        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.TARGET);
    }

    private void shootProjectile(Entity target) {
        Location eyeLoc = mob.getEyeLocation();
        Vector direction = target.getLocation()
                .subtract(eyeLoc)
                .toVector()
                .normalize();

        // Spawn and launch projectile
        MusketBehavior.shootParticleBeam(mob, eyeLoc, direction, mob.getWorld());
    }
}