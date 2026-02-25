package com.minecraftcivilizations.specialization.player;

import com.minecraftcivilizations.specialization.OpenLab;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

public class PreJoinEventListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);


            Player player = event.getPlayer();

            // Play your join sound
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1, 1);

            // List of all custom recipe keys you want players to always discover
            List<NamespacedKey> alwaysUnlockedRecipes = List.of(
                    new NamespacedKey(OpenLab.getInstance(), "hearty_soup"),
                    new NamespacedKey(OpenLab.getInstance(), "cat_spawn_egg"),
                    new NamespacedKey(OpenLab.getInstance(), "bell")
                    // add more NamespacedKey objects for other recipes
            );

            // Unlock all recipes for the player if not already discovered
            for (NamespacedKey key : alwaysUnlockedRecipes) {
                if (!player.hasDiscoveredRecipe(key)) {
                    player.discoverRecipe(key);
                }
            }
        }

    }
