package com.minecraftcivilizations.specialization.Listener.Player;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Specialization;
import minecraftcivilizations.com.minecraftCivilizationsCore.Config.ConfigFile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NametagVisibilityListener implements Listener {

    private static final String TEAM_VISIBLE = "nt_visible";
    private static final String TEAM_HIDDEN = "nt_hidden";

    private final Specialization plugin;
    private final ConfigFile config;
    private final Set<UUID> managedScoreboards = new HashSet<>();
    private BukkitTask task;

    public NametagVisibilityListener(Specialization plugin) {
        this.plugin = plugin;
        this.config = SpecializationConfig.nametagVisibility();
        if (!config.getBoolean("ENABLED")) {
            return;
        }

        int intervalTicks = Math.max(1, (int) Math.round(config.getDouble("CHECK_INTERVAL_TICKS")));

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            setupScoreboard(onlinePlayer);
        }

        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAllViewers, 1L, intervalTicks);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!managedScoreboards.contains(player.getUniqueId())) {
                continue;
            }
            if (player.getScoreboard() != mainScoreboard) {
                player.setScoreboard(mainScoreboard);
            }
        }
        managedScoreboards.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!config.getBoolean("ENABLED")) {
            return;
        }

        setupScoreboard(event.getPlayer());
        // Delay one tick so the joining player is fully spawned and present in all viewers' player lists.
        Bukkit.getScheduler().runTask(plugin, this::refreshAllViewers);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!config.getBoolean("ENABLED")) {
            return;
        }

        String entry = event.getPlayer().getName();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            Team visibleTeam = viewer.getScoreboard().getTeam(TEAM_VISIBLE);
            Team hiddenTeam = viewer.getScoreboard().getTeam(TEAM_HIDDEN);
            if (visibleTeam != null) {
                visibleTeam.removeEntry(entry);
            }
            if (hiddenTeam != null) {
                hiddenTeam.removeEntry(entry);
            }
        }
    }

    private void refreshAllViewers() {
        if (!config.getBoolean("ENABLED")) {
            return;
        }

        double maxDistance = config.getDouble("MAX_DISTANCE");
        double maxDistanceSquared = maxDistance * maxDistance;
        boolean hideWhenSneaking = config.getBoolean("HIDE_WHEN_SNEAKING");

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            setupScoreboard(viewer);
            Team visibleTeam = viewer.getScoreboard().getTeam(TEAM_VISIBLE);
            Team hiddenTeam = viewer.getScoreboard().getTeam(TEAM_HIDDEN);
            if (visibleTeam == null || hiddenTeam == null) {
                continue;
            }

            for (Player target : Bukkit.getOnlinePlayers()) {
                String entry = target.getName();
                if (viewer.equals(target)) {
                    visibleTeam.removeEntry(entry);
                    hiddenTeam.removeEntry(entry);
                    continue;
                }

                boolean shouldShow = shouldShowNametag(viewer, target, maxDistanceSquared, hideWhenSneaking);
                setVisibility(entry, shouldShow, visibleTeam, hiddenTeam);
            }
        }
    }

    private boolean shouldShowNametag(Player viewer, Player target, double maxDistanceSquared, boolean hideWhenSneaking) {
        if (!viewer.getWorld().equals(target.getWorld())) {
            return false;
        }
        if (!viewer.canSee(target)) {
            return false;
        }
        if (hideWhenSneaking && target.isSneaking()) {
            return false;
        }
        if (target.isInvisible()) {
            return false;
        }
        if (viewer.getLocation().distanceSquared(target.getLocation()) > maxDistanceSquared) {
            return false;
        }
        return hasStrictLineOfSight(viewer, target);
    }

    private boolean hasStrictLineOfSight(Player viewer, Player target) {
        Location from = viewer.getEyeLocation();
        Location to = target.getEyeLocation();

        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        if (distance <= 0.01D) {
            return true;
        }

        Block startBlock = from.getBlock();
        Block endBlock = to.getBlock();
        BlockIterator iterator = new BlockIterator(from.getWorld(), from.toVector(), direction.normalize(), 0.0D, (int) Math.ceil(distance));

        while (iterator.hasNext()) {
            Block current = iterator.next();
            if (current.equals(startBlock) || current.equals(endBlock)) {
                continue;
            }
            if (current.getType().isOccluding()) {
                return false;
            }
        }

        return true;
    }

    private void setupScoreboard(Player viewer) {
        Scoreboard mainScoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        if (viewer.getScoreboard() == mainScoreboard) {
            viewer.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
            managedScoreboards.add(viewer.getUniqueId());
        }

        Scoreboard scoreboard = viewer.getScoreboard();
        Team visibleTeam = scoreboard.getTeam(TEAM_VISIBLE);
        if (visibleTeam == null) {
            visibleTeam = scoreboard.registerNewTeam(TEAM_VISIBLE);
        }
        Team hiddenTeam = scoreboard.getTeam(TEAM_HIDDEN);
        if (hiddenTeam == null) {
            hiddenTeam = scoreboard.registerNewTeam(TEAM_HIDDEN);
        }

        visibleTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        hiddenTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
    }

    private void setVisibility(String entry, boolean visible, Team visibleTeam, Team hiddenTeam) {
        if (visible) {
            if (!visibleTeam.hasEntry(entry)) {
                hiddenTeam.removeEntry(entry);
                visibleTeam.addEntry(entry);
            }
            return;
        }

        if (!hiddenTeam.hasEntry(entry)) {
            visibleTeam.removeEntry(entry);
            hiddenTeam.addEntry(entry);
        }
    }
}
