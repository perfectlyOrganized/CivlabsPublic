package com.minecraftcivilizations.specialization.Listener.Player;

import com.minecraftcivilizations.specialization.Specialization;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

public class CompassListener implements Listener {

    private final Specialization plugin;
    private final MiniMessage miniMessage;
    private BukkitTask actionBarTask;

    public CompassListener(Specialization plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();

        // refresh y level display
        this.actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshCompassPlayers, 1L, 5L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> sendActionBarIfHoldingCompass(event.getPlayer()));
    }

    public void shutdown() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
    }

    private void refreshCompassPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendActionBarIfHoldingCompass(player);
        }
    }

    private void sendActionBarIfHoldingCompass(Player player) {
        if (!isHoldingCompass(player)) {
            return;
        }

        int yLevel = (int) Math.floor(player.getLocation().getY());
        Component actionBar = miniMessage.deserialize("<red>Current y level: " + yLevel + "</red>");
        player.sendActionBar(actionBar);
    }

    private boolean isHoldingCompass(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.COMPASS
                || player.getInventory().getItemInOffHand().getType() == Material.COMPASS;
    }
}

