package com.minecraftcivilizations.specialization.Listener.Player.Interactions;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.CustomItem.CustomItem;
import com.minecraftcivilizations.specialization.OpenLab;
import com.minecraftcivilizations.specialization.player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import com.typesafe.config.Config;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Objects;
import java.util.Random;

public class FoodInteractionListener implements Listener {

    OpenLab plugin;

    static NamespacedKey BLESSED_FOOD_KEY = new NamespacedKey(OpenLab.getInstance(), "BLESSED_FOOD");

    public FoodInteractionListener(OpenLab plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        CustomPlayer customPlayer = CustomPlayerManager.INSTANCE.getCustomPlayer(player.getUniqueId());
        if (customPlayer == null) return;
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (item == null) return;
            if (item.getType().isEdible() && player.isSneaking()) {
                if (customPlayer.getSkillLevel(SkillType.HEALER) > SpecializationConfig.skillsConfig.getInt("healer_level_to_bless_food")) {
                    // Prevent blessing of golden apples and enchanted golden apples
                    if (item.getType() == Material.GOLDEN_APPLE || item.getType() == Material.ENCHANTED_GOLDEN_APPLE) {
                        PlayerUtil.message(player, ChatColor.RED + "This food is too holy for this...");
                        event.setCancelled(true);
                        return;
                    }

                    if (item.getType() == Material.ROTTEN_FLESH || item.getType() == Material.KELP) {
                        PlayerUtil.message(player, ChatColor.RED + "This food is too filthy for that...");
                        event.setCancelled(true);
                        return;
                    }

                    if (player.getFoodLevel() < 10) {
                        PlayerUtil.message(player, ChatColor.RED + "You're too hungry to do that...", 1);
                        event.setCancelled(true);
                        return;
                    }

                    int healerLevel = customPlayer.getSkillLevel(SkillType.HEALER);
                    if (item.getAmount() >= 1) {
                        ItemMeta meta = item.getItemMeta();
                        if (meta != null && meta.hasLore()) {
                            for (String line : meta.getLore()) {
                                if (ChatColor.stripColor(line).toLowerCase().contains("blessed")) {
                                    PlayerUtil.message(player, ChatColor.RED + "This is already #blessed", 1);
                                    event.setCancelled(true);
                                    return;
                                }
                            }
                        }

                        int blessXp = SpecializationConfig.getHealthConfig().getInt("BLESSED_FOOD_HEALER_XP");
                        int hungerCost = SpecializationConfig.getHealthConfig().getInt("BLESSED_FOOD_HUNGER_COST");

                        ItemStack singleItem = item.clone();
                        singleItem.setAmount(1);
                        blessFood(singleItem, healerLevel);
                        item.setAmount(item.getAmount() - 1);
                        player.setFoodLevel(player.getFoodLevel() - hungerCost);
                        if (player.getInventory().firstEmpty() != -1) {
                            player.getInventory().addItem(singleItem);
                        } else {
                            player.getWorld().dropItemNaturally(player.getLocation(), singleItem);
                            PlayerUtil.message(player, ChatColor.YELLOW + "Your pockets are full. You dropped it");
                        }
                        customPlayer.addSkillXp(SkillType.HEALER, blessXp);
//                        PlayerUtil.message(player,ChatColor.GOLD + "You have blessed one " + getItemName(singleItem));
                    }

                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack consumed = event.getItem();
        CustomPlayer customPlayer = CustomPlayerManager.INSTANCE.getCustomPlayer(player.getUniqueId());
        if (customPlayer == null) return;

        if (isBlessedFood(consumed)) {
            int cooldownTicks = 800;

            // Check if any blessed food is on cooldown
            for (ItemStack invItem : player.getInventory().getContents()) {
                if (invItem != null && isBlessedFood(invItem) && player.hasCooldown(invItem.getType())) {
                    PlayerUtil.message(player, ChatColor.RED + "You must wait before consuming another blessed food...", 1);
                    event.setCancelled(true);
                    return;
                }
            }

            // Apply blessed food effects
            int healerLevel = getBlessedFoodLevel(consumed);
            applyBlessedFoodEffects(player, healerLevel, consumed.getType());

            // Set cooldown **only on items that are actually blessed food**
            for (ItemStack invItem : player.getInventory().getContents()) {
                if (isBlessedFood(invItem)) {
                    player.setCooldown(invItem.getType(), cooldownTicks);
                }
            }

            // Also ensure the consumed item itself has the cooldown
            player.setCooldown(consumed.getType(), cooldownTicks);
        }

        // Existing custom food logic
        if (!customPlayer.eatFood(consumed.getType())) {
            int reduction = SpecializationConfig.getHungerConfig().getInt("HUNGER_REDUCTION_ON_NON_UNIQUE_CONSECUTIVE_FOOD");
            player.setSaturation(player.getSaturation() - reduction);
        }

        if (consumed.getType().equals(Material.DRIED_KELP)) giveKelpEffects(player);
        if (consumed.getType().equals(Material.GOLDEN_APPLE)) giveGoldenAppleEffects(player);
    }




    private void giveKelpEffects(Player player){
        if(new Random().nextDouble() < .1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 20 * 6, 1));
            PlayerUtil.message(player,"<#456e55>You feel a little seasick from eating the kelp.");
        }
    }

    private void giveGoldenAppleEffects(Player player){
        if(new Random().nextDouble() < .2) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 20 * 60, 2));
            PlayerUtil.message(player,"<#dbae32>You feel solidified by the golden nature of the apple.");
        }
    }

    private void blessFood(ItemStack item, int healerLevel) {
        String itemname = getItemName(item);
        CustomItem customItem = null;


            customItem = new CustomItem(item.getType(),
                    Component.text("Blessed " + itemname).color(NamedTextColor.GOLD));
            List<String> configList = SpecializationConfig.skillsConfig.getConfig().getStringList("blessing_effects");
            if (configList.size()-1 < healerLevel) {
                healerLevel = configList.size() -1;
            }
            String effectSummary = SpecializationConfig.skillsConfig.getStringList("blessing_effects").get(healerLevel);
            customItem.addLore(OpenLab.getInstance(), List.of(
                    Component.empty(),
                    Component.text("Blessed Food").color(NamedTextColor.YELLOW),
                    Component.text("Healer Level: " + healerLevel).color(NamedTextColor.GRAY),
                    Component.text(effectSummary).color(NamedTextColor.GRAY)
            ));
            ItemMeta meta = customItem.getItem().getItemMeta();
            meta.getPersistentDataContainer().set(BLESSED_FOOD_KEY, PersistentDataType.BOOLEAN, true);
            item.setItemMeta(meta);
        }



    private void applyBlessedFoodEffects(Player player, int healerLevel, Material itemType) {
        // Fixed, spec-accurate values (ticks); remove food dependence at L2+
        int regenDurationTicks = 0;
        int regenAmplifier = 0; // amp 0 = Regen I, 1 = Regen II, 2 = Regen III

        // Optional extras
        Integer absorptionDurationTicks = null;
        Integer absorptionAmplifier = null;

        String effectSummary = SpecializationConfig.skillsConfig.getStringList("blessing_effects").get(healerLevel).toLowerCase();
        for (String effect : effectSummary.split(",")) {
            List<String> props = List.of(effect.split(" "));

            String attribute = props.get(0);
            int strength = Integer.parseInt(props.get(1));
            int duration =  Integer.parseInt(props.get(2).replace("s", ""));
            // probably should be done better but meh
            
            if (attribute.equals("regeneration")) {
                regenAmplifier = strength;
                regenDurationTicks = duration * 20;
            }
            if (attribute.equals("absorption")) {
                absorptionAmplifier = strength;
                absorptionDurationTicks = duration * 20;
            }
        }

        boolean saturate = false;
        // Apply effects
        if (regenDurationTicks > 0) {
            saturate = true;
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenDurationTicks, regenAmplifier));
        }
        if (absorptionDurationTicks != null && absorptionAmplifier != null) {
            saturate = true;
            player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, absorptionDurationTicks, absorptionAmplifier));
        }




        // Keep your existing “restore max health if below normal” behavior
        if (SpecializationConfig.getHealthConfig().getBoolean("HEALTH_ENABLED")) {
            double currentMaxHealth = Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).getValue();
            double normalMaxHealth = SpecializationConfig.getHealthConfig().getDouble("MAX_HEALTH");
            double healthRestoreAmount = SpecializationConfig.getHealthConfig().getDouble("BLESSED_FOOD_HEALTH_RESTORE_AMOUNT");

            if (currentMaxHealth < normalMaxHealth) {
                double newMaxHealth = Math.min(normalMaxHealth, currentMaxHealth + healthRestoreAmount);
                Objects.requireNonNull(player.getAttribute(Attribute.GENERIC_MAX_HEALTH)).setBaseValue(newMaxHealth);
                PlayerUtil.message(player,ChatColor.GREEN + "You feel your vitality returning! Max health restored to " + (int) newMaxHealth);
            }
        }
    }


    private int getBlessedFoodLevel(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return 0;
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) return 0;
        for (String line : lore) {
            if (line.contains("Healer Level: ")) {
                String levelStr = line.replace("Healer Level: ", "");
                try {
                    return Integer.parseInt(levelStr);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        }
        return 0;
    }



    public static boolean isBlessedFood(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return false;
        return item.getItemMeta().getPersistentDataContainer().has(BLESSED_FOOD_KEY, PersistentDataType.BOOLEAN);
//        List<Component> lore = item.getItemMeta().lore();
//        if (lore == null) return false;
//        for (Component component : lore) {
//            if (component instanceof net.kyori.adventure.text.TextComponent) {
//                String content = ((net.kyori.adventure.text.TextComponent) component).content();
//                if ("Blessed Food".equals(content)) return true;
//            }
//        }
//        return false;
    }

    private String getItemName(ItemStack item) {
        String materialName = item.getType().name();
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();


        for (String word : words) {
            if (result.length() > 0) result.append(" ");
            result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }
        return result.toString();
    }

    private Integer getItemRegen(Material item){
        return switch (item) {
            case CHICKEN,MUTTON,COOKIE,GLOW_BERRIES,MELON_SLICE,POISONOUS_POTATO,
                 COD,SALMON,SPIDER_EYE,SWEET_BERRIES -> 2;
            case CARROT,BEEF,PORKCHOP,RABBIT -> 3;
            case APPLE,CHORUS_FRUIT,GOLDEN_APPLE,ENCHANTED_GOLDEN_APPLE,ROTTEN_FLESH -> 4;
            case BAKED_POTATO,BREAD,COOKED_COD,COOKED_RABBIT -> 5;
            case BEETROOT_SOUP,COOKED_CHICKEN,COOKED_MUTTON,COOKED_SALMON,GOLDEN_CARROT,
                 HONEY_BLOCK,MUSHROOM_STEW,SUSPICIOUS_STEW -> 6;
            case COOKED_PORKCHOP,PUMPKIN_PIE,COOKED_BEEF -> 8;
            case RABBIT_STEW -> 10;
            default -> 1;
        };
    }
}