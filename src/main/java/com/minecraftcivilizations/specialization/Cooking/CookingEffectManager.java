package com.minecraftcivilizations.specialization.Cooking;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import com.minecraftcivilizations.specialization.Specialization;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles applying potion effects to cooked food based on seasonings and sauces used.
 *
 * Seasonings:
 * - Sugar: Hero of the Village I (30s)
 * - Cocoa Beans: Hero of the Village I (60s)
 * - Honey Bottle: Hero of the Village I (45s) + clears harmful effects
 * - Glow Lichen: Hero of the Village II (90s)
 * - Blaze Powder: Hero of the Village III (60s)
 *
 * Sauces:
 * - Potion: Adds the exact potion effect
 * - Ink Sac: +1 amplifier and +30s to Hero of the Village
 * - Glow Ink Sac: Same as Ink Sac + Glowing (60s)
 * - Ghast Tear: Regeneration I (45s) + Hero of the Village III (60s), stacks with seasoning
 */
public class CookingEffectManager {

    // PDC Keys for storing effect data on cooked food
    private static final NamespacedKey EFFECT_DATA_KEY = new NamespacedKey(Specialization.getInstance(), "cooking_effects");
    private static final NamespacedKey CLEAR_HARMFUL_KEY = new NamespacedKey(Specialization.getInstance(), "clear_harmful");

    // PDC Key for storing bonus XP
    private static final NamespacedKey BONUS_EXP_KEY = new NamespacedKey(Specialization.getInstance(), "bonus_exp");

    /**
     * Apply seasoning and sauce effects to the cooked food item.
     * Effects are stored in the item's PersistentDataContainer and applied when consumed.
     *
     * @param food The cooked food item
     * @param seasonings List of seasoning items used
     * @param sauces List of sauce items used
     * @param burnt Whether the food is burnt (burnt food gets no effects)
     * @return The modified food item with effects
     */
    public static ItemStack applyEffects(ItemStack food, List<ItemStack> seasonings, List<ItemStack> sauces, boolean burnt) {
        return applyEffects(food, seasonings, sauces, burnt, new ArrayList<>(), 0);
    }

    /**
     * Apply seasoning, sauce, and native effects to the cooked food item.
     * Effects are stored in the item's PersistentDataContainer and applied when consumed.
     *
     * @param food The cooked food item
     * @param seasonings List of seasoning items used
     * @param sauces List of sauce items used
     * @param burnt Whether the food is burnt (burnt food gets no effects)
     * @param nativeEffects List of native effects from cookingConfig.json (format: "effect:amplifier:duration")
     * @param bonusExp Bonus XP to give when consuming (from cookingConfig.json)
     * @return The modified food item with effects
     */
    public static ItemStack applyEffects(ItemStack food, List<ItemStack> seasonings, List<ItemStack> sauces,
                                          boolean burnt, List<String> nativeEffects, int bonusExp) {
        if (food == null || food.getType() == Material.AIR) return food;
        if (burnt) return food; // Burnt food gets no effects

        ItemMeta meta = food.getItemMeta();
        if (meta == null) return food;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Calculate base effects from seasoning
        int heroAmplifier = -1; // -1 means no effect
        int heroDuration = 0;
        boolean clearHarmful = false;

        // Process seasonings
        if (seasonings != null) {
            for (ItemStack seasoning : seasonings) {
                if (seasoning == null) continue;
                SeasoningEffect effect = getSeasoningEffect(seasoning);
                if (effect != null) {
                    if (effect.heroAmplifier > heroAmplifier) {
                        heroAmplifier = effect.heroAmplifier;
                        heroDuration = effect.heroDuration;
                    } else if (effect.heroAmplifier == heroAmplifier && effect.heroDuration > heroDuration) {
                        heroDuration = effect.heroDuration;
                    }
                    if (effect.clearHarmful) clearHarmful = true;
                }
            }
        }

        // Build effect list
        List<String> effectStrings = new ArrayList<>();

        // Process sauces (they modify/add effects)
        boolean hasInkSac = false;
        boolean hasGlowInkSac = false;
        boolean hasGhastTear = false;
        List<PotionEffect> potionEffects = new ArrayList<>();

        if (sauces != null) {
            for (ItemStack sauce : sauces) {
                if (sauce == null) continue;

                if (sauce.getType() == Material.INK_SAC) {
                    hasInkSac = true;
                } else if (sauce.getType() == Material.GLOW_INK_SAC) {
                    hasGlowInkSac = true;
                } else if (sauce.getType() == Material.GHAST_TEAR) {
                    hasGhastTear = true;
                } else if (sauce.getType() == Material.POTION) {
                    potionEffects.addAll(getPotionEffectsFromItem(sauce));
                }
            }
        }

        // Apply ink sac modifications
        if (hasInkSac || hasGlowInkSac) {
            if (heroAmplifier >= 0) {
                heroAmplifier += 1; // +1 amplifier
                heroDuration += 30 * 20; // +30 seconds in ticks
            }
        }

        // Add glowing effect from glow ink sac
        if (hasGlowInkSac) {
            // Glowing for 60 seconds
            effectStrings.add(encodeEffect(PotionEffectType.GLOWING, 0, 60 * 20));
        }

        // Apply ghast tear effects (stacks with seasoning)
        if (hasGhastTear) {
            // Regeneration I for 45 seconds
            effectStrings.add(encodeEffect(PotionEffectType.REGENERATION, 0, 45 * 20));

            // Hero of the Village III for 60 seconds (stacks)
            if (heroAmplifier >= 0) {
                // Stack: take the higher amplifier
                if (heroAmplifier < 2) heroAmplifier = 2; // Level III = amplifier 2
                heroDuration += 60 * 20; // Add 60 seconds
            } else {
                heroAmplifier = 2; // Level III
                heroDuration = 60 * 20; // 60 seconds
            }
        }

        // Add hero of the village effect if present
        if (heroAmplifier >= 0 && heroDuration > 0) {
            effectStrings.add(encodeEffect(PotionEffectType.HERO_OF_THE_VILLAGE, heroAmplifier, heroDuration));
        }

        // Add all potion effects from sauce
        for (PotionEffect effect : potionEffects) {
            effectStrings.add(encodeEffect(effect.getType(), effect.getAmplifier(), effect.getDuration()));
        }

        // Add native effects from cookingConfig.json
        if (nativeEffects != null) {
            for (String nativeEffect : nativeEffects) {
                // Native effects are already in "effect:amplifier:duration" format
                effectStrings.add(nativeEffect);
            }
        }

        // Store effects in PDC
        if (!effectStrings.isEmpty()) {
            String effectData = String.join(";", effectStrings);
            pdc.set(EFFECT_DATA_KEY, PersistentDataType.STRING, effectData);
        }

        // Store clear harmful flag
        if (clearHarmful) {
            pdc.set(CLEAR_HARMFUL_KEY, PersistentDataType.BOOLEAN, true);
        }

        // Store bonus XP
        if (bonusExp > 0) {
            pdc.set(BONUS_EXP_KEY, PersistentDataType.INTEGER, bonusExp);
        }

        food.setItemMeta(meta);
        return food;
    }

    /**
     * Get bonus XP stored on food item.
     */
    public static int getBonusExp(ItemStack food) {
        if (food == null) return 0;
        ItemMeta meta = food.getItemMeta();
        if (meta == null) return 0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer bonus = pdc.get(BONUS_EXP_KEY, PersistentDataType.INTEGER);
        return bonus != null ? bonus : 0;
    }

    /**
     * Get the effects stored on a cooked food item.
     */
    public static List<PotionEffect> getStoredEffects(ItemStack food) {
        List<PotionEffect> effects = new ArrayList<>();
        if (food == null) return effects;

        ItemMeta meta = food.getItemMeta();
        if (meta == null) return effects;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String effectData = pdc.get(EFFECT_DATA_KEY, PersistentDataType.STRING);

        if (effectData != null && !effectData.isEmpty()) {
            String[] effectStrings = effectData.split(";");
            for (String effectString : effectStrings) {
                PotionEffect effect = decodeEffect(effectString);
                if (effect != null) effects.add(effect);
            }
        }

        return effects;
    }

    /**
     * Check if food should clear harmful effects when consumed.
     */
    public static boolean shouldClearHarmful(ItemStack food) {
        if (food == null) return false;

        ItemMeta meta = food.getItemMeta();
        if (meta == null) return false;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Boolean clearHarmful = pdc.get(CLEAR_HARMFUL_KEY, PersistentDataType.BOOLEAN);
        return clearHarmful != null && clearHarmful;
    }

    /**
     * Encode a potion effect as a string for PDC storage.
     * Format: "effect_name:amplifier:duration"
     */
    private static String encodeEffect(PotionEffectType type, int amplifier, int durationTicks) {
        return type.getKey().getKey() + ":" + amplifier + ":" + durationTicks;
    }

    /**
     * Decode a potion effect from PDC string.
     */
    private static PotionEffect decodeEffect(String encoded) {
        try {
            String[] parts = encoded.split(":");
            if (parts.length != 3) return null;

            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(parts[0]);
            PotionEffectType type = org.bukkit.Registry.POTION_EFFECT_TYPE.get(key);
            if (type == null) return null;

            int amplifier = Integer.parseInt(parts[1]);
            int duration = Integer.parseInt(parts[2]);

            return new PotionEffect(type, duration, amplifier, true, true, true);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get seasoning effect based on the item type.
     */
    private static SeasoningEffect getSeasoningEffect(ItemStack item) {
        if (item == null) return null;

        return switch (item.getType()) {
            case SUGAR -> new SeasoningEffect(0, 30 * 20, false); // Hero I, 30s
            case COCOA_BEANS -> new SeasoningEffect(0, 60 * 20, false); // Hero I, 60s
            case HONEY_BOTTLE -> new SeasoningEffect(0, 45 * 20, true); // Hero I, 45s, clears harmful
            case GLOW_LICHEN -> new SeasoningEffect(1, 90 * 20, false); // Hero II, 90s
            case BLAZE_POWDER -> new SeasoningEffect(2, 60 * 20, false); // Hero III, 60s
            default -> {
                // Check for custom salt item
                String id = CookingItemUtils.getItemId(item);
                if (id != null && id.contains("salt")) {
                    yield new SeasoningEffect(0, 30 * 20, false); // Hero I, 30s (same as sugar)
                }
                yield null;
            }
        };
    }

    /**
     * Extract all potion effects from a potion item.
     */
    private static List<PotionEffect> getPotionEffectsFromItem(ItemStack potion) {
        List<PotionEffect> result = new ArrayList<>();
        if (potion == null || potion.getType() != Material.POTION) return result;

        if (!(potion.getItemMeta() instanceof PotionMeta potionMeta)) return result;

        PotionType type = potionMeta.getBasePotionType();
        if (type != null) {
            List<PotionEffect> effects = type.getPotionEffects();
            if (effects != null) {
                result.addAll(effects);
            }
        }

        if (potionMeta.hasCustomEffects()) {
            result.addAll(potionMeta.getCustomEffects());
        }

        return result;
    }

    /**
     * Helper class to store seasoning effect data.
     */
    private static class SeasoningEffect {
        final int heroAmplifier; // 0 = Level I, 1 = Level II, etc.
        final int heroDuration; // Duration in ticks
        final boolean clearHarmful;

        SeasoningEffect(int heroAmplifier, int heroDuration, boolean clearHarmful) {
            this.heroAmplifier = heroAmplifier;
            this.heroDuration = heroDuration;
            this.clearHarmful = clearHarmful;
        }
    }
}
