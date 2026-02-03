package com.minecraftcivilizations.specialization.Cooking;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.*;

/**
 * Utility class for adding lore to cooked food items.
 */
public class CookingLoreUtils {

    /**
     * Apply cooking lore to a food item.
     *
     * @param food The cooked food ItemStack
     * @param cookedBy UUID of the player who cooked it
     * @param seasonings List of seasoning items used
     * @param sauces List of sauce items used
     * @param burnt Whether the food is burnt
     * @return The modified ItemStack with lore
     */
    public static ItemStack applyLore(ItemStack food, UUID cookedBy, List<ItemStack> seasonings, List<ItemStack> sauces, boolean burnt) {
        if (food == null || food.getType() == Material.AIR) return food;

        ItemMeta meta = food.getItemMeta();
        if (meta == null) return food;

        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();

        // Add "Cooked by playername" in orange
        String playerName = getPlayerName(cookedBy);
        if (playerName != null) {
            lore.add(Component.text("Cooked by ", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(playerName, NamedTextColor.GOLD)
                    .decoration(TextDecoration.ITALIC, false)));
        }

        // Only add seasoning/sauce lore if NOT burnt
        if (!burnt) {
            // Add seasoning line if present
            if (seasonings != null) {
                for (ItemStack seasoning : seasonings) {
                    if (seasoning == null || seasoning.getType() == Material.AIR) continue;
                    lore.add(buildSeasoningLoreLine(seasoning));
                }
            }

            // Add sauce line if present
            if (sauces != null) {
                for (ItemStack sauce : sauces) {
                    if (sauce == null || sauce.getType() == Material.AIR) continue;
                    lore.add(buildSauceLoreLine(sauce));
                }
            }

            // Add blank line before effects if we have seasonings/sauces
            if ((seasonings != null && !seasonings.isEmpty()) || (sauces != null && !sauces.isEmpty())) {
                lore.add(Component.empty());
            }

            // Add effect lore lines in vanilla style (§9 blue)
            // First, merge duplicate effects (take highest amplifier and longest duration)
            List<PotionEffect> effects = CookingEffectManager.getStoredEffects(food);
            List<PotionEffect> mergedEffects = mergeEffects(effects);
            for (PotionEffect effect : mergedEffects) {
                Component effectLine = buildEffectLoreLine(effect);
                if (effectLine != null) lore.add(effectLine);
            }
        } else {
            // Add burnt indicator if burnt
            lore.add(Component.text("Overcooked!", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, true));
        }

        meta.lore(lore);
        food.setItemMeta(meta);
        return food;
    }

    private static String getPlayerName(UUID uuid) {
        if (uuid == null) return null;
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) return player.getName();
        // Try offline player
        return Bukkit.getOfflinePlayer(uuid).getName();
    }

    /**
     * Merge duplicate effects - take highest amplifier and its duration.
     * If same amplifier, take the longer duration.
     */
    private static List<PotionEffect> mergeEffects(List<PotionEffect> effects) {
        if (effects == null || effects.isEmpty()) return effects;

        Map<PotionEffectType, PotionEffect> merged = new LinkedHashMap<>();
        for (PotionEffect effect : effects) {
            PotionEffectType type = effect.getType();
            if (merged.containsKey(type)) {
                PotionEffect existing = merged.get(type);
                // Take highest amplifier; if same, take longer duration
                if (effect.getAmplifier() > existing.getAmplifier()) {
                    // New effect has higher amplifier - use it
                    merged.put(type, effect);
                } else if (effect.getAmplifier() == existing.getAmplifier() && effect.getDuration() > existing.getDuration()) {
                    // Same amplifier but longer duration - use the longer one
                    merged.put(type, effect);
                }
                // Otherwise keep existing (higher or equal amplifier with equal or longer duration)
            } else {
                merged.put(type, effect);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private static Component buildSeasoningLoreLine(ItemStack seasoning) {
        TextColor color = getSeasoningColor(seasoning);
        String name = getItemDisplayName(seasoning);

        return Component.text("Seasoned with ", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(name, color)
                .decoration(TextDecoration.ITALIC, false));
    }

    private static Component buildSauceLoreLine(ItemStack sauce) {
        TextColor color = getSauceColor(sauce);
        String name = sauce.getType() == Material.POTION ? getPotionDisplayName(sauce) : getItemDisplayName(sauce);

        return Component.text("Glazed with ", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)
            .append(Component.text(name, color)
                .decoration(TextDecoration.ITALIC, false));
    }

    private static Component buildEffectLoreLine(PotionEffect effect) {
        if (effect == null) return null;

        PotionEffectType type = effect.getType();
        String effectName = getEffectDisplayName(type);
        int amplifier = effect.getAmplifier();
        String levelStr = amplifierToRoman(amplifier + 1);

        String displayText = effectName;
        if (shouldShowLevel(type) && amplifier > 0) {
            displayText += " " + levelStr;
        }

        // Don't show duration for instant effects
        if (!isInstantEffect(type)) {
            displayText += " (" + formatDuration(effect.getDuration()) + ")";
        }

        // Negative effects use red (§c), positive use blue (§9)
        TextColor color = isNegativeEffect(type) ? TextColor.color(0xFF5555) : TextColor.color(0x5555FF);

        return Component.text(displayText, color)
            .decoration(TextDecoration.ITALIC, false);
    }

    private static boolean isNegativeEffect(PotionEffectType type) {
        return type.equals(PotionEffectType.SLOWNESS) ||
               type.equals(PotionEffectType.MINING_FATIGUE) ||
               type.equals(PotionEffectType.INSTANT_DAMAGE) ||
               type.equals(PotionEffectType.NAUSEA) ||
               type.equals(PotionEffectType.BLINDNESS) ||
               type.equals(PotionEffectType.HUNGER) ||
               type.equals(PotionEffectType.WEAKNESS) ||
               type.equals(PotionEffectType.POISON) ||
               type.equals(PotionEffectType.WITHER) ||
               type.equals(PotionEffectType.LEVITATION) ||
               type.equals(PotionEffectType.UNLUCK) ||
               type.equals(PotionEffectType.BAD_OMEN) ||
               type.equals(PotionEffectType.DARKNESS) ||
               type.equals(PotionEffectType.WIND_CHARGED) ||
               type.equals(PotionEffectType.WEAVING) ||
               type.equals(PotionEffectType.OOZING) ||
               type.equals(PotionEffectType.INFESTED) ||
               type.equals(PotionEffectType.TRIAL_OMEN) ||
               type.equals(PotionEffectType.RAID_OMEN);
    }

    private static boolean isInstantEffect(PotionEffectType type) {
        return type.equals(PotionEffectType.INSTANT_HEALTH) ||
               type.equals(PotionEffectType.INSTANT_DAMAGE) ||
               type.equals(PotionEffectType.SATURATION);
    }

    private static boolean shouldShowLevel(PotionEffectType type) {
        // These effects don't show levels
        if (type.equals(PotionEffectType.FIRE_RESISTANCE)) return false;
        if (type.equals(PotionEffectType.NIGHT_VISION)) return false;
        if (type.equals(PotionEffectType.WATER_BREATHING)) return false;
        if (type.equals(PotionEffectType.INVISIBILITY)) return false;
        if (type.equals(PotionEffectType.SLOW_FALLING)) return false;
        if (type.equals(PotionEffectType.CONDUIT_POWER)) return false;
        if (type.equals(PotionEffectType.DOLPHINS_GRACE)) return false;
        if (type.equals(PotionEffectType.BAD_OMEN)) return false;
        if (type.equals(PotionEffectType.GLOWING)) return false;
        if (type.equals(PotionEffectType.WIND_CHARGED)) return false;
        if (type.equals(PotionEffectType.WEAVING)) return false;
        if (type.equals(PotionEffectType.OOZING)) return false;
        if (type.equals(PotionEffectType.INFESTED)) return false;
        if (type.equals(PotionEffectType.TRIAL_OMEN)) return false;
        if (type.equals(PotionEffectType.RAID_OMEN)) return false;
        return true;
    }

    /**
     * Convert amplifier (1-based) to Roman numeral.
     */
    private static String amplifierToRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(level);
        };
    }

    /**
     * Format duration in ticks to mm:ss format.
     */
    private static String formatDuration(int ticks) {
        int totalSeconds = ticks / 20;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    /**
     * Get display name for a potion effect type.
     */
    private static String getEffectDisplayName(PotionEffectType type) {
        if (type == null) return "Unknown";

        // Handle all vanilla effects
        if (type.equals(PotionEffectType.SPEED)) return "Speed";
        if (type.equals(PotionEffectType.SLOWNESS)) return "Slowness";
        if (type.equals(PotionEffectType.HASTE)) return "Haste";
        if (type.equals(PotionEffectType.MINING_FATIGUE)) return "Mining Fatigue";
        if (type.equals(PotionEffectType.STRENGTH)) return "Strength";
        if (type.equals(PotionEffectType.INSTANT_HEALTH)) return "Instant Health";
        if (type.equals(PotionEffectType.INSTANT_DAMAGE)) return "Instant Damage";
        if (type.equals(PotionEffectType.JUMP_BOOST)) return "Jump Boost";
        if (type.equals(PotionEffectType.NAUSEA)) return "Nausea";
        if (type.equals(PotionEffectType.REGENERATION)) return "Regeneration";
        if (type.equals(PotionEffectType.RESISTANCE)) return "Resistance";
        if (type.equals(PotionEffectType.FIRE_RESISTANCE)) return "Fire Resistance";
        if (type.equals(PotionEffectType.WATER_BREATHING)) return "Water Breathing";
        if (type.equals(PotionEffectType.INVISIBILITY)) return "Invisibility";
        if (type.equals(PotionEffectType.BLINDNESS)) return "Blindness";
        if (type.equals(PotionEffectType.NIGHT_VISION)) return "Night Vision";
        if (type.equals(PotionEffectType.HUNGER)) return "Hunger";
        if (type.equals(PotionEffectType.WEAKNESS)) return "Weakness";
        if (type.equals(PotionEffectType.POISON)) return "Poison";
        if (type.equals(PotionEffectType.WITHER)) return "Wither";
        if (type.equals(PotionEffectType.HEALTH_BOOST)) return "Health Boost";
        if (type.equals(PotionEffectType.ABSORPTION)) return "Absorption";
        if (type.equals(PotionEffectType.SATURATION)) return "Saturation";
        if (type.equals(PotionEffectType.GLOWING)) return "Glowing";
        if (type.equals(PotionEffectType.LEVITATION)) return "Levitation";
        if (type.equals(PotionEffectType.LUCK)) return "Luck";
        if (type.equals(PotionEffectType.UNLUCK)) return "Bad Luck";
        if (type.equals(PotionEffectType.SLOW_FALLING)) return "Slow Falling";
        if (type.equals(PotionEffectType.CONDUIT_POWER)) return "Conduit Power";
        if (type.equals(PotionEffectType.DOLPHINS_GRACE)) return "Dolphin's Grace";
        if (type.equals(PotionEffectType.BAD_OMEN)) return "Bad Omen";
        if (type.equals(PotionEffectType.HERO_OF_THE_VILLAGE)) return "Hero of the Village";
        if (type.equals(PotionEffectType.DARKNESS)) return "Darkness";
        // 1.21+ effects
        if (type.equals(PotionEffectType.TRIAL_OMEN)) return "Trial Omen";
        if (type.equals(PotionEffectType.RAID_OMEN)) return "Raid Omen";
        if (type.equals(PotionEffectType.WIND_CHARGED)) return "Wind Charged";
        if (type.equals(PotionEffectType.WEAVING)) return "Weaving";
        if (type.equals(PotionEffectType.OOZING)) return "Oozing";
        if (type.equals(PotionEffectType.INFESTED)) return "Infested";

        // Fallback: format the key name
        String keyName = type.getKey().getKey();
        return formatName(keyName);
    }

    /**
     * Get the color for a seasoning item based on its type.
     */
    private static TextColor getSeasoningColor(ItemStack item) {
        if (item == null) return NamedTextColor.WHITE;

        return switch (item.getType()) {
            case COCOA_BEANS -> TextColor.color(0x8B4513); // Brown (Saddle Brown)
            case SUGAR -> NamedTextColor.WHITE;
            case HONEY_BOTTLE -> TextColor.color(0xFFB300); // Amber/Honey color
            case GLOW_LICHEN -> TextColor.color(0x7FFFD4); // Aquamarine/Glow color
            case BLAZE_POWDER -> TextColor.color(0xFF4500); // Orange Red
            default -> {
                // Check for custom items by name/id
                String id = CookingItemUtils.getItemId(item);
                if (id != null) {
                    if (id.contains("salt")) yield NamedTextColor.WHITE;
                    if (id.contains("cocoa") || id.contains("chocolate")) yield TextColor.color(0x8B4513);
                    if (id.contains("sugar") || id.contains("sweet")) yield NamedTextColor.WHITE;
                    if (id.contains("honey") || id.contains("glaze")) yield TextColor.color(0xFFB300);
                    if (id.contains("lichen")) yield TextColor.color(0x7FFFD4);
                    if (id.contains("blaze") || id.contains("pepper")) yield TextColor.color(0xFF4500);
                }
                yield NamedTextColor.GRAY;
            }
        };
    }

    /**
     * Get the color for a sauce item based on its type.
     */
    private static TextColor getSauceColor(ItemStack item) {
        if (item == null) return NamedTextColor.WHITE;

        return switch (item.getType()) {
            case POTION -> getPotionColor(item);
            case INK_SAC -> TextColor.color(0x1D1D21); // Dark ink color
            case GLOW_INK_SAC -> TextColor.color(0x00FFFF); // Cyan/Glow
            case GHAST_TEAR -> TextColor.color(0xE8E8E8); // Light gray/White
            default -> {
                String id = CookingItemUtils.getItemId(item);
                if (id != null) {
                    if (id.contains("ink")) yield TextColor.color(0x1D1D21);
                    if (id.contains("glow")) yield TextColor.color(0x00FFFF);
                    if (id.contains("ghast") || id.contains("tear")) yield TextColor.color(0xE8E8E8);
                }
                yield NamedTextColor.GRAY;
            }
        };
    }

    /**
     * Get color based on potion type.
     */
    private static TextColor getPotionColor(ItemStack potion) {
        if (!(potion.getItemMeta() instanceof PotionMeta potionMeta)) return NamedTextColor.LIGHT_PURPLE;

        PotionType type = potionMeta.getBasePotionType();
        if (type == null) return NamedTextColor.LIGHT_PURPLE;

        return switch (type) {
            // Healing/Regeneration - Pink
            case HEALING, STRONG_HEALING, REGENERATION, STRONG_REGENERATION, LONG_REGENERATION -> TextColor.color(0xFF69B4);
            // Fire Resistance - Orange
            case FIRE_RESISTANCE, LONG_FIRE_RESISTANCE -> TextColor.color(0xFF6600);
            // Swiftness/Leaping - Light blue
            case SWIFTNESS, STRONG_SWIFTNESS, LONG_SWIFTNESS, LEAPING, STRONG_LEAPING, LONG_LEAPING -> TextColor.color(0x7FDBFF);
            // Strength - Dark red
            case STRENGTH, STRONG_STRENGTH, LONG_STRENGTH -> TextColor.color(0x932423);
            // Night Vision - Navy blue
            case NIGHT_VISION, LONG_NIGHT_VISION -> TextColor.color(0x1F1FA1);
            // Invisibility - Gray
            case INVISIBILITY, LONG_INVISIBILITY -> TextColor.color(0x7F8C8D);
            // Water Breathing - Blue
            case WATER_BREATHING, LONG_WATER_BREATHING -> TextColor.color(0x2980B9);
            // Poison - Green
            case POISON, STRONG_POISON, LONG_POISON -> TextColor.color(0x4E9A06);
            // Weakness - Dark gray
            case WEAKNESS, LONG_WEAKNESS -> TextColor.color(0x484848);
            // Slowness - Slate
            case SLOWNESS, STRONG_SLOWNESS, LONG_SLOWNESS -> TextColor.color(0x5A6268);
            // Harming - Purple
            case HARMING, STRONG_HARMING -> TextColor.color(0x430A5D);
            // Luck - Green
            case LUCK -> TextColor.color(0x339933);
            // Slow Falling - White
            case SLOW_FALLING, LONG_SLOW_FALLING -> NamedTextColor.WHITE;
            // Turtle Master - Olive
            case TURTLE_MASTER, STRONG_TURTLE_MASTER, LONG_TURTLE_MASTER -> TextColor.color(0x6B8E23);
            // 1.21+ new potions
            case WIND_CHARGED -> TextColor.color(0xA0D8EF); // Light sky blue (wind effect color)
            case WEAVING -> TextColor.color(0x4A4A4A); // Dark gray (cobweb color)
            case OOZING -> TextColor.color(0x7CB342); // Lime green (slime color)
            case INFESTED -> TextColor.color(0x808080); // Gray (silverfish color)
            // Base potions - Blue
            case AWKWARD, MUNDANE, THICK, WATER -> NamedTextColor.BLUE;
            default -> NamedTextColor.LIGHT_PURPLE;
        };
    }

    private static String getPotionDisplayName(ItemStack potion) {
        if (!(potion.getItemMeta() instanceof PotionMeta potionMeta)) return "Potion";

        PotionType type = potionMeta.getBasePotionType();
        if (type == null) return "Potion";

        String typeName = type.name();
        int level = typeName.startsWith("STRONG_") ? 2 : 1;

        String effectName = switch (type) {
            case HEALING, STRONG_HEALING -> "Instant Health";
            case REGENERATION, STRONG_REGENERATION, LONG_REGENERATION -> "Regeneration";
            case FIRE_RESISTANCE, LONG_FIRE_RESISTANCE -> "Fire Resistance";
            case SWIFTNESS, STRONG_SWIFTNESS, LONG_SWIFTNESS -> "Speed";
            case LEAPING, STRONG_LEAPING, LONG_LEAPING -> "Jump Boost";
            case STRENGTH, STRONG_STRENGTH, LONG_STRENGTH -> "Strength";
            case NIGHT_VISION, LONG_NIGHT_VISION -> "Night Vision";
            case INVISIBILITY, LONG_INVISIBILITY -> "Invisibility";
            case WATER_BREATHING, LONG_WATER_BREATHING -> "Water Breathing";
            case POISON, STRONG_POISON, LONG_POISON -> "Poison";
            case WEAKNESS, LONG_WEAKNESS -> "Weakness";
            case SLOWNESS, STRONG_SLOWNESS, LONG_SLOWNESS -> "Slowness";
            case HARMING, STRONG_HARMING -> "Instant Damage";
            case LUCK -> "Luck";
            case SLOW_FALLING, LONG_SLOW_FALLING -> "Slow Falling";
            case TURTLE_MASTER, STRONG_TURTLE_MASTER, LONG_TURTLE_MASTER -> "Turtle Master";
            case WIND_CHARGED -> "Wind Charged";
            case WEAVING -> "Weaving";
            case OOZING -> "Oozing";
            case INFESTED -> "Infested";
            case AWKWARD -> "Awkward";
            case MUNDANE -> "Mundane";
            case THICK -> "Thick";
            case WATER -> "Water";
            default -> "Potion";
        };

        // Potions that don't show levels
        if (type == PotionType.AWKWARD || type == PotionType.MUNDANE ||
            type == PotionType.THICK || type == PotionType.WATER ||
            type == PotionType.WIND_CHARGED || type == PotionType.WEAVING ||
            type == PotionType.OOZING || type == PotionType.INFESTED ||
            type == PotionType.TURTLE_MASTER || type == PotionType.STRONG_TURTLE_MASTER ||
            type == PotionType.LONG_TURTLE_MASTER) {
            return effectName;
        }

        String levelStr = amplifierToRoman(level);
        return effectName + " " + levelStr;
    }

    /**
     * Get a nice display name for an item.
     */
    private static String getItemDisplayName(ItemStack item) {
        if (item == null) return "Unknown";

        // First check for CraftEngine custom item ID
        String customId = CookingItemUtils.getItemId(item);
        if (customId != null && !customId.startsWith("minecraft:")) {
            // Handle custom items by their ID
            if (customId.contains("salt")) return "Salt";
            if (customId.contains("cocoa") || customId.contains("chocolate")) return "Cocoa";
            if (customId.contains("honey")) return "Honey";
            if (customId.contains("lichen")) return "Glow Lichen";
            if (customId.contains("blaze") || customId.contains("pepper")) return "Blaze Pepper";
        }

        // Check if item has custom display name
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component displayName = meta.displayName();
            if (displayName != null) {
                return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(displayName);
            }
        }

        // Convert material name to nice format
        return switch (item.getType()) {
            case COCOA_BEANS -> "Cocoa";
            case SUGAR -> "Sugar";
            case HONEY_BOTTLE -> "Honey";
            case GLOW_LICHEN -> "Glow Lichen";
            case BLAZE_POWDER -> "Blaze Pepper";
            case INK_SAC -> "Ink";
            case GLOW_INK_SAC -> "Glow Ink";
            case GHAST_TEAR -> "Ghast Tear";
            default -> formatName(item.getType().name());
        };
    }

    private static String formatName(String name) {
        String[] words = name.toLowerCase().replace("_", " ").split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return result.toString().trim();
    }
}
