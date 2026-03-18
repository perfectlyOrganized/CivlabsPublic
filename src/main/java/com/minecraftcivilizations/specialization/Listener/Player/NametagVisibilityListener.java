package com.minecraftcivilizations.specialization.Listener.Player;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Specialization;
import minecraftcivilizations.com.minecraftCivilizationsCore.Config.ConfigFile;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (onlinePlayers.isEmpty()) {
            return;
        }

        double maxDistance = config.getDouble("MAX_DISTANCE");
        double maxDistanceSquared = maxDistance * maxDistance;
        boolean hideWhenSneaking = config.getBoolean("HIDE_WHEN_SNEAKING");

        Map<UUID, PlayerSnapshot> snapshotsById = new HashMap<>((int) (onlinePlayers.size() / 0.75f) + 1);
        Map<World, List<PlayerSnapshot>> snapshotsByWorld = new HashMap<>();

        for (Player player : onlinePlayers) {
            PlayerSnapshot snapshot = PlayerSnapshot.of(player);
            snapshotsById.put(player.getUniqueId(), snapshot);
            snapshotsByWorld.computeIfAbsent(snapshot.world(), ignored -> new ArrayList<>()).add(snapshot);
        }

        for (Player viewer : onlinePlayers) {
            Team[] teams = getViewerTeams(viewer);
            if (teams == null) {
                setupScoreboard(viewer);
                teams = getViewerTeams(viewer);
                if (teams == null) {
                    continue;
                }
            }

            Team visibleTeam = teams[0];
            Team hiddenTeam = teams[1];

            if (isExcluded(viewer)) {
                for (Player target : onlinePlayers) {
                    String entry = target.getName();
                    visibleTeam.removeEntry(entry);
                    hiddenTeam.removeEntry(entry);
                }
                continue;
            }

            PlayerSnapshot viewerSnapshot = snapshotsById.get(viewer.getUniqueId());
            if (viewerSnapshot == null) {
                continue;
            }

            List<PlayerSnapshot> sameWorldTargets = snapshotsByWorld.get(viewerSnapshot.world());
            if (sameWorldTargets == null || sameWorldTargets.isEmpty()) {
                continue;
            }

            for (PlayerSnapshot targetSnapshot : sameWorldTargets) {
                String entry = targetSnapshot.name();
                if (viewer.getUniqueId().equals(targetSnapshot.player().getUniqueId())) {
                    visibleTeam.removeEntry(entry);
                    hiddenTeam.removeEntry(entry);
                    continue;
                }

                if (isExcluded(targetSnapshot.player())) {
                    visibleTeam.removeEntry(entry);
                    hiddenTeam.removeEntry(entry);
                    continue;
                }

                if (viewerSnapshot.location().distanceSquared(targetSnapshot.location()) > maxDistanceSquared) {
                    setVisibility(entry, false, visibleTeam, hiddenTeam);
                    continue;
                }

                boolean shouldShow = shouldShowNametag(viewer, viewerSnapshot, targetSnapshot, hideWhenSneaking);
                setVisibility(entry, shouldShow, visibleTeam, hiddenTeam);
            }
        }
    }

    private boolean shouldShowNametag(Player viewer, PlayerSnapshot viewerSnapshot, PlayerSnapshot targetSnapshot, boolean hideWhenSneaking) {
        Player target = targetSnapshot.player();
        if (isExcluded(viewer) || isExcluded(target)) {
            return false;
        }
        if (!viewer.canSee(target)) {
            return false;
        }
        if (hideWhenSneaking && targetSnapshot.sneaking()) {
            return false;
        }
        if (targetSnapshot.invisible()) {
            return false;
        }
        return hasStrictLineOfSight(viewerSnapshot.eyeLocation(), targetSnapshot.eyeLocation());
    }

    private boolean hasStrictLineOfSight(Location from, Location to) {
        if (!from.getWorld().equals(to.getWorld())) {
            return false;
        }

        Vector direction = to.toVector().subtract(from.toVector());
        double distance = direction.length();
        if (distance <= 0.01D) {
            return true;
        }

        Vector normalizedDirection = direction.multiply(1.0D / distance);
        RayTraceResult solidHit = from.getWorld().rayTraceBlocks(
                from,
                normalizedDirection,
                distance,
                FluidCollisionMode.NEVER,
                true
        );
        if (solidHit != null) {
            return false;
        }

        return !hitsForbiddenVisibilityBlock(from, normalizedDirection, distance);
    }

    private boolean isExcluded(Player player) {
        return player.isOp() || player.getGameMode() == GameMode.SPECTATOR;
    }

    private boolean hitsForbiddenVisibilityBlock(Location from, Vector normalizedDirection, double maxDistance) {
        Location cursor = from.clone();
        double traveled = 0.0D;
        double dx = normalizedDirection.getX();
        double dy = normalizedDirection.getY();
        double dz = normalizedDirection.getZ();

        while (traveled < maxDistance) {
            double remaining = maxDistance - traveled;
            RayTraceResult hit = cursor.getWorld().rayTraceBlocks(
                    cursor,
                    normalizedDirection,
                    remaining,
                    FluidCollisionMode.ALWAYS,
                    false
            );

            if (hit == null || hit.getHitBlock() == null) {
                return false;
            }

            Material type = hit.getHitBlock().getType();
            if (type == Material.LAVA || type == Material.POWDER_SNOW) {
                return true;
            }

            double step = cursor.toVector().distance(hit.getHitPosition()) + 0.05D;
            if (step <= 0.0D) {
                step = 0.05D;
            }
            traveled += step;
            cursor.add(dx * step, dy * step, dz * step);
        }

        return false;
    }

    private Team[] getViewerTeams(Player viewer) {
        Scoreboard scoreboard = viewer.getScoreboard();
        Team visibleTeam = scoreboard.getTeam(TEAM_VISIBLE);
        Team hiddenTeam = scoreboard.getTeam(TEAM_HIDDEN);
        if (visibleTeam == null || hiddenTeam == null) {
            return null;
        }
        return new Team[]{visibleTeam, hiddenTeam};
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

    private static final class PlayerSnapshot {
        private final Player player;
        private final String name;
        private final World world;
        private final Location location;
        private final Location eyeLocation;
        private final boolean sneaking;
        private final boolean invisible;

        private PlayerSnapshot(Player player, String name, World world, Location location, Location eyeLocation, boolean sneaking, boolean invisible) {
            this.player = player;
            this.name = name;
            this.world = world;
            this.location = location;
            this.eyeLocation = eyeLocation;
            this.sneaking = sneaking;
            this.invisible = invisible;
        }

        static PlayerSnapshot of(Player player) {
            return new PlayerSnapshot(
                    player,
                    player.getName(),
                    player.getWorld(),
                    player.getLocation(),
                    player.getEyeLocation(),
                    player.isSneaking(),
                    player.isInvisible()
            );
        }

        Player player() {
            return player;
        }

        String name() {
            return name;
        }

        World world() {
            return world;
        }

        Location location() {
            return location;
        }

        Location eyeLocation() {
            return eyeLocation;
        }

        boolean sneaking() {
            return sneaking;
        }

        boolean invisible() {
            return invisible;
        }
    }
}
