package com.minecraftcivilizations.specialization.Combat;

import com.minecraftcivilizations.specialization.Combat.Mobs.MobVariation;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Combat.Mobs.MobManager;
import com.minecraftcivilizations.specialization.OpenLab;
import com.minecraftcivilizations.specialization.player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import com.minecraftcivilizations.specialization.util.CooldownManager;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import lombok.Getter;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;

import static com.minecraftcivilizations.specialization.util.MathUtils.random;
import static org.bukkit.entity.EntityType.*;

import static org.bukkit.event.entity.EntityDamageEvent.DamageModifier.*;
/**
 * The parent manager for everything related to combat, including mob damage
 * Routes damage listeners
 * @author Alectriciti ⚡
 */
public class CombatManager implements Listener {


    public static NamespacedKey ARROW_DAMAGE_KEY;
    public static NamespacedKey CRIT_BONUS_KEY;

    @Getter
    private final GuardsmanDamage guardsmanDamage;
//    private final DynamicArmor dynamicArmor; DLC feature by Alectriciti

    @Getter
    private final ArmorDamageReduction armorDamageReduction; // Handles MOB -> PLAYER damage

    @Getter
    private final MobManager mobManager;

    @Getter
    private final ArmorEquipAttributes armorEquip;

    @Getter
    private final Berserk berserk; // Berserk Manager

    @Getter
    private final ExplosionDamage explosionDamage;

    @Getter
    private final ArmorBreakSystem armorBreakSystem;

    @Getter
    final OpenLab plugin;

    public CombatManager(OpenLab specialization) {
        this.plugin = specialization;
        specialization.getServer().getPluginManager().registerEvents(this, specialization);
        CRIT_BONUS_KEY = new NamespacedKey(specialization, "COMBAT_CRIT_BONUS");
        ARROW_DAMAGE_KEY = new NamespacedKey(specialization, "ARROW_DAMAGE");

        guardsmanDamage = new GuardsmanDamage(this);
        mobManager = new MobManager(this);
        armorBreakSystem = new ArmorBreakSystem(this);
//        dynamicArmor = new DynamicArmor(this);
        armorEquip = new ArmorEquipAttributes(this);
        armorDamageReduction = new ArmorDamageReduction(this);
        berserk = new Berserk(this);
        explosionDamage = new ExplosionDamage(this);
    }

    public void initialize(){
        mobManager.populateEntityMappings();
    }

    public static CombatManager getInstance() {
        return OpenLab.getInstance().getCombatManager();
    }

    @EventHandler
    public void ShootBowListener(ProjectileLaunchEvent event){
        Projectile projectile = event.getEntity();
        if(projectile.getType()==ARROW || projectile.getType()==SPECTRAL_ARROW){
        }else{
            return;
        }

        double multiplier = 1.0;

        ProjectileSource source = projectile.getShooter();
        //TODO firework check
        if(source instanceof LivingEntity shooter){
            boolean is_day_time = shooter.getWorld().getTime() < 12300;
            ItemStack weapon = shooter.getEquipment().getItemInMainHand();
            switch(weapon.getType()){
                case BOW:
                    if(shooter instanceof Player ps){
                        multiplier = 1.0;
                        ps.setCooldown(Material.CROSSBOW, 16);
                    }else{
                        //skeleton or mob
                        multiplier = 0.7;
                        if(mobManager.isMobVariation(shooter)) {
                            MobVariation mobVariation = mobManager.getMobVariation(shooter);
                            if (shooter.getWorld().getEnvironment() == World.Environment.NORMAL) {
                                multiplier *= is_day_time ? mobVariation.getDamageMultiplierDay() : mobVariation.getDamageMultiplierNight();
                            } else {
                                multiplier *= mobVariation.getDamageMultiplierNether();}
                        }
                    }
                    break;
                case CROSSBOW:
                    multiplier = 1.5;
                    if(shooter instanceof Player ps){
                        ps.setCooldown(Material.CROSSBOW, 24);
                    }
                    break;
            }
        }else if (source == null) {
            //dispenser
            // Shooter is a dispenser
            multiplier = 2.0;
        }
        projectile.getPersistentDataContainer().set(ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE, multiplier);
    }

    public static final double standard_crit_base_multiplier = 0.25; // All crits multiply by base weapon damage
    public static final double standard_crit_guardsman_multiplier = 0.25; // Multiplier per level of guardsman to add to base crit
    public static final double opening_crit_baseline = 0.5; //All opening crits add this much as a base

    /**
     * Use this to get the crit bonus on any item*
     */
    public static double getCustomWeaponCrit(ItemStack weapon){
        if(weapon.hasItemMeta()) {
            if (weapon.getItemMeta().getPersistentDataContainer().has(CRIT_BONUS_KEY, PersistentDataType.DOUBLE)) {
                return weapon.getItemMeta().getPersistentDataContainer().get(CRIT_BONUS_KEY, PersistentDataType.DOUBLE);
            }
        }
        return 0;
    }


//    @EventHandler(priority = EventPriority.HIGHEST)
//    public void onasdfjkl(EntityDamageEvent event){
//
//        if(event instanceof  EntityDamageByEntityEvent entity_event){
//
//            entity_event.getFina
//        }
//
//    }

    @EventHandler(priority = EventPriority.LOW)
    public void GlobalDamageListener(EntityDamageByEntityEvent event) {
        if(!(event.getEntity() instanceof LivingEntity))return;
        double original_base = event.getDamage(BASE);

        Entity damager = event.getDamager();
        CustomPlayer customPlayer = CustomPlayerManager.INSTANCE.getCustomPlayer(damager.getUniqueId());

        if(damager instanceof Projectile projectile){
            if(projectile.getPersistentDataContainer().has(ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE)) {
                double multiplier = projectile.getPersistentDataContainer().get(ARROW_DAMAGE_KEY, PersistentDataType.DOUBLE);
                event.setDamage(BASE, original_base * multiplier);

                if (event.isApplicable(ARMOR)) {
                    double armor_resist = event.getDamage(ARMOR);
                    event.setDamage(ARMOR, armor_resist * multiplier);
                }
            }
        }


        if(damager instanceof Player player) { //Mobs have a chance to hit zero with this cast
            double DAMAGE_MINIMUM = (0.075 * original_base) * (PlayerUtil.isCritical(player) ? 1.5:1.0);
            double total_final = calculateTotalDamage(event);
            if (total_final <= DAMAGE_MINIMUM) {
                for (EntityDamageEvent.DamageModifier m : EntityDamageEvent.DamageModifier.values()) {
                    if (event.isApplicable(m)) {
                        if (m != BLOCKING)
                            event.setDamage(m, 0);
                    }
                }
                event.setDamage(BASE, DAMAGE_MINIMUM);
            }

            if(event.getEntity() instanceof LivingEntity victim) {
                if (!event.isCancelled()) {
                    if (!OpenLab.getInstance().getPlayerDownedListener().isDowned(player)) {
                        mobManager.applyGuardsmanExp(event, customPlayer, player, victim); //Exp is acquired only after calculating final damage
                    }
                }
            }
        }
    }


    /**
     * Temporary max health for mobs
     */
    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event){
//        AttributeInstance attribute = event.getEntity().getAttribute(Attribute.MAX_HEALTH);
//        double max_health = attribute.getValue()*2;
//        attribute.setBaseValue(max_health);
//        event.getEntity().setHealth(max_health);
    }

    /**
     * Custom Mob Drops
     */
//    @EventHandler
//    public void addCustomMobDrops(EntityDeathEvent e){
//        if (e.getEntity().getKiller() != null) {
//            Player player = e.getEntity().getKiller();
//            assert player != null;
////            CustomPlayer killer = CustomPlayerManager.INSTANCE.getCustomPlayer(e.getEntity().getKiller().getUniqueId());
////            EntityType entity = e.getEntity().getType();
//           // List<NamespacedKey> items = SpecializationConfig.getMobDropsConfig().getStringList(e.getEntityType().name()).stream().map(NamespacedKey::fromString).toList();
//           // Material.matchMaterial(e.getEntityType().getKey().getKey());
//        }
//    }



    /**
     * Calculates the total resulting damage after all Paper/Bukkit modifiers are applied.
     */
    public static double calculateTotalDamage(EntityDamageByEntityEvent event) {
        double total = 0.0;
        for (EntityDamageEvent.DamageModifier modifier : EntityDamageEvent.DamageModifier.values()) {
            try {
                total += event.getDamage(modifier);
            } catch (IllegalArgumentException ignored) {
                // Modifier not applicable for this event
            }
        }
        return total;
    }



}