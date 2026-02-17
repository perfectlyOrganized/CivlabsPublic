package com.minecraftcivilizations.specialization.Combat;

import com.minecraftcivilizations.specialization.Listener.Player.Inventories.SpecializationCraftItemEvent;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.ItemStackUtils;
import com.minecraftcivilizations.specialization.util.MathUtils;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import static net.md_5.bungee.api.ChatColor.*;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Handles applying attributes to armor, such as speed
 * @author alectriciti
 */
public class ArmorEquipAttributes implements Listener {

    Specialization plugin;
    CombatManager manager;


    public static NamespacedKey WEIGHT_KEY;


    //Used for previous values
    Map<UUID, Double> weight_map = new HashMap<UUID, Double>();

    public ArmorEquipAttributes(CombatManager manager){
        this.manager = manager;
        this.plugin = manager.plugin;
        WEIGHT_KEY = new NamespacedKey(plugin, "armor_weight");
//        namespaceKeyFactory();
        this.plugin.getServer().getPluginManager().registerEvents(this, this.plugin);
        // Initialization in constructor
    }

    /**
     * Applies armor weight based on Blacksmith Level
     */
    @EventHandler
    public void onArmorCraft(SpecializationCraftItemEvent special_event){
        if(special_event.getSkillType()!= SkillType.BLACKSMITH)return;
        if(special_event.getSkillLevel()<2)return;
        CraftItemEvent event = special_event.getEvent();
        if(event.isCancelled())return;
        if(event.getResult()!= Event.Result.ALLOW)return;

        Player player = special_event.getPlayer();

        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType() == Material.AIR) return;

        ItemStack modified = current.clone();
        ItemMeta meta = modified.getItemMeta();
        int lvl = special_event.getSkillLevel();
        EquipmentSlot slot = ArmorStats.getSlot(current.getType());
        if(slot==null)return;

        double weight;
        double weight_modifier = 1.0;
        double material_weight = 0;
        double slot_weight = 0;
        Material mat = ArmorStats.getMaterialType(current.getType());
        if (mat == null) return; //not compatible

        Material item_type = null;
        String typeName = current.getType().name();
        if (typeName.startsWith("IRON_")) {
            item_type = Material.IRON_INGOT;
        } else if (typeName.startsWith("DIAMOND_")) {
            item_type = Material.DIAMOND;
        } else if (typeName.startsWith("GOLDEN_")) {
            item_type = Material.GOLD_INGOT;
        } else if (typeName.startsWith("LEATHER_")) {
            item_type = Material.LEATHER;
        } else if (typeName.startsWith("NETHERITE_")) {
            item_type = Material.NETHERITE_INGOT;
//        } else if (typeName.startsWith("CHAINMAIL_")) {
//            item_type = Material.CHAIN;
        }

        material_weight = getMaterialWeight(mat);
        slot_weight = getSlotModifier(slot);

        boolean luck = player.hasPotionEffect(PotionEffectType.LUCK);
        weight = (material_weight * slot_weight);
        ChatColor color = BLUE;
        double base_chance = 0.25;
        double rare_chance = 0.125;
        double weight_mod_low = 0.75;
        double weight_mod_high = 0.75;
        double knockback_chance = 0.0;
        double armor_trim_chance = 0.0;

        boolean best = false;
        switch(lvl){
            case 2:
                base_chance = 0.01; // 1 in 5
                rare_chance = 0.00;
                weight_mod_low = 0.8;
                weight_mod_high = 0.95;
                armor_trim_chance = 0.1;
                break;
            case 3:
                base_chance = 0.25; // 1 in 4
                rare_chance = 0.025; // ~0.5% chance of crafting best
                weight_mod_low = 0.7;
                weight_mod_high = 0.9;
                knockback_chance = 0.025;
                armor_trim_chance = 0.125;
                break;
            case 4:
                base_chance = 0.33; // 1 in 3
                rare_chance = 0.05; // ~5.0% chance of crafting best
                weight_mod_low = 0.75;
                weight_mod_high = 0.8;
                knockback_chance = 0.05;
                armor_trim_chance = 0.33;
                break;
            case 5:
                base_chance = 0.5; // 1 in 2
                rare_chance = 0.1; // ~10.0% chance of crafting best
                weight_mod_low = 0.6;
                weight_mod_high = 0.8;
                knockback_chance = 0.125;
                armor_trim_chance = 0.75;
                break;
        }
        double knockback_roll = 0;
        /**
         * Roll knockback resist
         */
//        if(ThreadLocalRandom.current().nextDouble()<knockback_chance){
//            switch(item_type){
//                case IRON_INGOT:
//                    knockback_roll = 0.1;
//                    if(lvl == 5 && Math.random()>0.5){
//                        knockback_roll += 0.1;
//                    }
//                    break;
//                case GOLD_INGOT:
//                    knockback_roll = 0.05;
//                    break;
//                case DIAMOND:
//                    knockback_roll = 0.1;
//                    break;
//            }
//        }

        double rng1 = ThreadLocalRandom.current().nextDouble();
        double rng2 = ThreadLocalRandom.current().nextDouble();
        Debug.broadcast("armortrim", "Rng1: "+Debug.formatDecimal(rng1)+ " <red>rng2: " +Debug.formatDecimal(rng2));

        /**
         * Roll weight
         */
        if(rng1 < base_chance && item_type != null) {
            switch(item_type){
                case IRON_INGOT -> {
                    if(ThreadLocalRandom.current().nextDouble()<rare_chance) {
                        color = ChatColor.of("#ECECEC");
                        weight_modifier = 0.425;
                        best = true;
                    }else{
                        weight_modifier = MathUtils.random(weight_mod_low,weight_mod_high);
                    }
                }
                case GOLD_INGOT -> {
                    if(ThreadLocalRandom.current().nextDouble()<rare_chance) {
                        color = ChatColor.of("#DEB12D");
                        weight_modifier = 0.25;
                        best = true;
                    }else{
                        weight_modifier = 0.5 * MathUtils.random(weight_mod_low, weight_mod_high);
                    }
                }
                case DIAMOND -> {
                    if(ThreadLocalRandom.current().nextDouble()<rare_chance) {
                        color = ChatColor.of("#6EECD2");
                        weight_modifier = 0.5;
                        best = true;
                    }else{
                        weight_modifier = MathUtils.random(weight_mod_low, weight_mod_high);
                    }
                }
            }
            current = ArmorEquipAttributes.applyArmorStats(modified, new ArmorStatsCustom((int) (weight * weight_modifier), knockback_roll), color);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_WORK_TOOLSMITH, 0.2f, 0.9f);
            if(best){
                meta = current.getItemMeta();
                current.setItemMeta(meta);
            }
        }else{
            current = ArmorEquipAttributes.applyArmorStats(modified, new ArmorStatsCustom(-1, knockback_roll), BLUE);
        }

        /**
         * Roll armor trim
         */
        if(best){
            armor_trim_chance *= 2; //increase chance of trim if lightest armor
        }

        boolean trimmed = false;
        String trimmed_msg = "";
        if(rng2 < armor_trim_chance){
            BlacksmithArmorTrim armorTrimSystem = Specialization.getInstance().getArmorTrimSystem();
            if(armorTrimSystem!=null){
                ArmorTrim trim = armorTrimSystem.applyArmorTrimToItem(player, current);
                if(trim!=null) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.25f, 1.1f);
                    trimmed = true;
                    String trimmed_color = BlacksmithArmorTrim.getMaterialColor(trim.getMaterial());
                    trimmed_msg = "<" + trimmed_color + ">" +
                            "signature"
                            + "</" + trimmed_color + "> ";
                    String hex_string = ChatColor.of(trimmed_color).toString();
                    meta = current.getItemMeta();
                    ItemStackUtils.setLoreLine(
                            meta,
                            2,
                            ChatColor.DARK_GRAY + "Crafted by " + hex_string + ChatColor.GRAY + player.getName()
                    );
                    meta.addItemFlags(ItemFlag.HIDE_ARMOR_TRIM);
                    current.setItemMeta(meta);
                }
            }
        }
        if(best || trimmed){
            PlayerUtil.message(player,"You've crafted a "+(best?"<white>perfect</white> ":"")+trimmed_msg+"piece of armor!");
        }
        event.setCurrentItem(current);
    }


    public final double weight_offset = -25; //baseline, a player can have up to this before weight becomes effective
    public final double weight_threshold = 50;

    class WeightLevel{

        String title;
        ChatColor color;
        public WeightLevel(String title, ChatColor color){
            this.title = title;
            this.color = color;
        }
    }

    private EquipmentSlot getEquipmentSlot(int slot) {
        return switch (slot) {
            case 39 -> EquipmentSlot.HEAD;
            case 38 -> EquipmentSlot.CHEST;
            case 37 -> EquipmentSlot.LEGS;
            case 36 -> EquipmentSlot.FEET;
            case 40 -> EquipmentSlot.OFF_HAND;
            default -> null; // Not an equipment slot
        };
    }

    public WeightLevel weightLevel(double weight){
        if(weight> weight_threshold *3){
            return new WeightLevel(" (How tf r u this heavy?)", DARK_RED);
        }else if(weight> weight_threshold *2){
            return new WeightLevel(" (Very Heavy)", RED);
        }else if (weight> weight_threshold){
            return new WeightLevel(" (Heavy)", GOLD);
        }else if(weight>0){
            return new WeightLevel(" (Medium)", YELLOW);
        }
        return new WeightLevel("(Light)", GREEN);
    }

    public boolean hasWeight(ItemStack item){
        return item.getItemMeta().getPersistentDataContainer().has(WEIGHT_KEY, PersistentDataType.DOUBLE);
    }

    public double getWeight(ItemStack item){
        if(item == null || item.getItemMeta() == null) return 0;
        if(item.getItemMeta().getPersistentDataContainer().has(WEIGHT_KEY, PersistentDataType.DOUBLE)) {
            return item.getItemMeta().getPersistentDataContainer().get(WEIGHT_KEY, PersistentDataType.DOUBLE);
        }
        return 0;
    }


    /**
     * Applies weight to an armor piece with a custom weight override
     * Used for custom blacksmith armor
     */
    public static ItemStack applyArmorStats(ItemStack item, ArmorStatsCustom custom_stats_override, ChatColor color){
        ItemMeta meta = item.getItemMeta();


//        NamespacedKey key = new NamespacedKey(Specialization.getInstance(),"version");
//        if(meta.getPersistentDataContainer().has(key)){
//
//        }

//        Debug.broadcast("armor", "removing attribute modifiers and applying");
            Collection<AttributeModifier> attributeModifiers = meta.getAttributeModifiers(Attribute.GENERIC_ARMOR);
            if(attributeModifiers!=null) {
                for (AttributeModifier mod : attributeModifiers) {
                    // handle armor modifier
                    meta.removeAttributeModifier(Attribute.GENERIC_ARMOR, mod);
                }
            }
            attributeModifiers = meta.getAttributeModifiers(Attribute.GENERIC_ARMOR_TOUGHNESS);
            if(attributeModifiers!=null) {
                for (AttributeModifier mod : attributeModifiers) {
                    // handle armor modifier
                    meta.removeAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS, mod);
                }
            }

//        meta.removeAttributeModifier(Attribute.ARMOR);
//        meta.removeAttributeModifier(Attribute.ARMOR_TOUGHNESS);
//        meta.removeAttributeModifier(Attribute.ARMOR);
//
//
//        attributeModifiers = meta.getAttributeModifiers(Attribute.ARMOR_TOUGHNESS);
//        if(attributeModifiers!=null) {
//            for (AttributeModifier mod : meta.getAttributeModifiers(Attribute.ARMOR_TOUGHNESS)) {
//                // handle toughness modifier
//            }
//        }
//
//        attributeModifiers = meta.getAttributeModifiers(Attribute.KNOCKBACK_RESISTANCE);
//        if(attributeModifiers!=null) {
//            for (AttributeModifier mod : attributeModifiers) {
//                meta.removeAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, mod);
//                // handle knockback resistance modifier
//            }
//        }

            ArmorStats vanillaStats = ArmorStats.getVanillaStats(item.getType());
            // VANILLA ARMOR OVERRIDE
//            AttributeModifier mod_armor = new AttributeModifier(
//                    new NamespacedKey(Specialization.getInstance(), item.getType().name().toLowerCase()+"_armor"),
//                    vanillaStats.getArmor(),
//                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ARMOR);
//            meta.addAttributeModifier(Attribute.GENERIC_ARMOR, mod_armor);
//            AttributeModifier mod_tough = new AttributeModifier(
//                    new NamespacedKey(Specialization.getInstance(), item.getType().name().toLowerCase()+"_toughness"),
//                    vanillaStats.getToughness(),
//                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ARMOR);
//            meta.addAttributeModifier(Attribute.GENERIC_ARMOR_TOUGHNESS, mod_tough);


//        if(custom_stats_override.getKnockback_resist()>0) {
//            AttributeModifier mod_knockback = new AttributeModifier(
//                    new NamespacedKey(Specialization.getInstance(), item.getType().name().toLowerCase() + "_knockback"),
//                    custom_stats_override.getKnockback_resist(),
//                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ARMOR);
//            meta.removeAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE, mod_knockback);
//            meta.addAttributeModifier(Attribute.GENERIC_KNOCKBACK_RESISTANCE, mod_knockback);
//        }

        item.setItemMeta(meta);

        if(!meta.getPersistentDataContainer().has(WEIGHT_KEY, PersistentDataType.DOUBLE)) {

            double weight; //weight to apply to the item
            if (custom_stats_override.getWeight() != -1) {
                weight = custom_stats_override.getWeight(); //manually apply weight*
            } else {
                double material_weight = 0;
                double slot_weight = 0;
                EquipmentSlot slot = ArmorStats.getSlot(item.getType());
                Material mat = ArmorStats.getMaterialType(item.getType());
                if (mat == null) return item; //not compatible


                material_weight = getMaterialWeight(mat);
                slot_weight = getSlotModifier(slot);

                weight = (material_weight * slot_weight);
            }

            //apply weight
            meta.getPersistentDataContainer().set(WEIGHT_KEY, PersistentDataType.DOUBLE, weight);


            double slowness_debuff = -weight / 1000;

//            AttributeModifier mod_water_weight = new AttributeModifier(
//                    new NamespacedKey(Specialization.getInstance(), item.getType().name().toLowerCase() + "_weight_slowness"),
//                    slowness_debuff,
//                    AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ARMOR);
//            meta.addAttributeModifier(Attribute.GENERIC_MOVEMENT_SPEED, mod_water_weight);

            item.setItemMeta(meta);
            ItemStackUtils.setLoreLine(item, 0, color + "+" + weight + " Weight");
//        Debug.broadcast("weight", "weight applied! "+BLUE+"MAT: "+material_weight+" "+GREEN+"SLOT: "+slot_weight);
        }
        return item;
    }

    public static double getMaterialWeight(Material mat) {
        // #TODO move to config!
        double material_weight = 0;
        switch (mat) {
            case LEATHER:
                material_weight = 1.0;
                break;
            case CHAIN:
                material_weight = 2.0;
                break;
            case GOLD_INGOT:
                material_weight = 2.5;
                break;
            case IRON_INGOT:
                material_weight = 3.0;
                break;
            case DIAMOND:
                material_weight = 5.0;
                break;
            case NETHERITE_INGOT:
                material_weight = 6.0;
                break;
        }
        return material_weight;
    }

    public static double getSlotModifier(EquipmentSlot slot) {
        double slot_weight = 0;
        switch (slot) {
            case HEAD -> {
                slot_weight = 5.0;
            }
            case CHEST -> {
                slot_weight = 8.0;
            }
            case LEGS -> {
                slot_weight = 7.0;
            }
            case FEET -> {
                slot_weight = 4.0;
            }
        }
        return slot_weight;
    }








}
