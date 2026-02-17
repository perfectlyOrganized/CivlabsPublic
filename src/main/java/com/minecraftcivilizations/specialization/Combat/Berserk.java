package com.minecraftcivilizations.specialization.Combat;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.PotionEffectData;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import lombok.NonNull;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Berserk implements Listener {

    CombatManager combatManager;

    public Berserk(CombatManager manager){
        this.combatManager = manager;
        manager.plugin.getServer().getPluginManager().registerEvents(this, manager.plugin);
    }

    // Tracks players who already triggered Berserk this life
    private final Set<UUID> usedBerserkThisLife = ConcurrentHashMap.newKeySet();

    @EventHandler
    public void onPlayerDamage(EntityDamageByEntityEvent e) {
        if (e.isCancelled()) return;
        if (e.getEntity().getType() != EntityType.PLAYER) return;

        Player player = (Player) e.getEntity();
        CustomPlayer damaged = CoreUtil.getPlayer(player);

        if (damaged.getSkillLevel(SkillType.GUARDSMAN) < 1) return;

        // Already used this life? bail.
       // if (usedBerserkThisLife.contains(player.getUniqueId())) return;

        double before = player.getHealth();
        double after = Math.max(0.0, before - e.getFinalDamage());

        // Trigger only on crossing from >5 to <=5 this hit
        if (before > 5.0 && after <= 5.0) {
            showBerserkDurationTitle(player);
            applyBerserk(player);

            // Mark as used for this life
            usedBerserkThisLife.add(player.getUniqueId());
        }
    }

    // Clear the flag when the player actually dies
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        usedBerserkThisLife.remove(e.getEntity().getUniqueId());
    }

    // Also clear on respawn (covers edge cases with death-cancelling plugins)
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent e) {
        usedBerserkThisLife.remove(e.getPlayer().getUniqueId());
    }

    public void showBerserkDurationTitle(final @NonNull Player target) {
        final Title.Times times = Title.Times.times(
                Duration.ofMillis(500),
                Duration.ofMillis(3000),
                Duration.ofMillis(1000)
        );

        PlayerUtil.showTitle(target, Component.text("Awakened"), Component.text("Muscle memory floods back from battles never fought."));
    }

    public void applyBerserk(Player player) {
        for (PotionEffectType potionEffectType : PotionEffectType.values()) {
            try {
                String effectKey = potionEffectType.getKey().getKey().toUpperCase();
                List<? extends Config> effects = SpecializationConfig.getBerserkConfig().getList("berserk_effect");
                effects.forEach((config -> {
                    Config effect = config.getObject(effectKey).toConfig();
                    PotionEffectData effectData = new PotionEffectData( effect.getInt("duration"), effect.getInt("amplifier"));

                    if (effectData.amplifier() > 0 && effectData.duration() > 0) {
                        player.addPotionEffect(new PotionEffect(
                                potionEffectType,
                                effectData.duration(),
                                effectData.amplifier(),
                                false,
                                false
                        ));
                    }
                }));
            } catch (ConfigException.Missing _e) {

            }
            catch (Exception ex) {
                System.err.println("Failed to apply berserk effect " + potionEffectType.getKey() + ": " + ex.getMessage());
            }
        }
    }
}
