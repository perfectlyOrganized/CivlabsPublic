package com.minecraftcivilizations.specialization.Player;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.minecraftcivilizations.specialization.Skill.Skill;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import com.mojang.authlib.GameProfile;
import org.bukkit.util.StringUtil;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class CustomPlayerManager implements Listener {
    private final ConcurrentHashMap<UUID, CustomPlayer> customPlayers = new ConcurrentHashMap<>();
    @Getter
    @Setter
    private Consumer<Player> onPlayerJoin = player -> {
        CustomPlayer customPlayer = null;
        try {
            customPlayer = load(player.getUniqueId());
        } catch (FileNotFoundException _e) {}
        if (customPlayer == null) {
            addCustomPlayer(new CustomPlayer(player.getUniqueId()));
        }
    };
    @Getter
    @Setter
    private Consumer<AsyncPlayerPreLoginEvent> onPrePlayerJoin = player -> {
        CustomPlayer customPlayer = null;
        try {
            customPlayer = load(player.getUniqueId());
        } catch (FileNotFoundException _e) {}
        if (customPlayer == null) {
            addCustomPlayer(new CustomPlayer(player.getUniqueId()));
        }
    };

    @Setter
    private Class<? extends CustomPlayer> customPlayerClass = CustomPlayer.class;

    @Setter
    private Consumer<PlayerQuitEvent> onPlayerQuit = event -> {removeCustomPlayer(event.getPlayer().getUniqueId());};
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public CustomPlayer getCustomPlayer(UUID uuid) {
        return customPlayers.get(uuid);
    }

    public void saveAll() {
       customPlayers.keys().asIterator().forEachRemaining(this::save);

    }

    public CustomPlayer load(String UUID) throws FileNotFoundException {
        FileReader reader = new FileReader(Specialization.getInstance().getDataFolder() + "/" + UUID + ".json");
        CustomPlayer customPlayer = gson.fromJson(reader, customPlayerClass);
        return addCustomPlayer(customPlayer);
    }

    public CustomPlayer load(UUID UUID) throws FileNotFoundException {
        return load(UUID.toString());
    }

    public void save(UUID UUID) {
        try (FileWriter writer = new FileWriter(Specialization.getInstance().getDataFolder() + "/" + UUID.toString() + ".json")) {
            String json = gson.toJson(Specialization.customPlayerManager.getCustomPlayer(UUID), customPlayerClass);
            writer.write(json);
        } catch (IOException e) {
            Specialization.logger.severe(String.format("Couldn't save %s custom player", UUID));
        }
    }

    public CustomPlayer addCustomPlayer(CustomPlayer player) {
        assert player != null : "player is null when adding a new custom player?";
        if (getCustomPlayer(player.getUuid()) != null) {
            customPlayers.remove(player.getUuid());
        }
        customPlayers.put(player.getUuid(), player);
        return player;
    }

    public void removeCustomPlayer(UUID player) {
        if (getCustomPlayer(player) != null) {
            Specialization.customPlayerManager.save(player);
            customPlayers.remove(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        CustomPlayer customPlayer = null;
        try {
            customPlayer = load( player.getUniqueId() );
        } catch (FileNotFoundException ignored) {}
        if (customPlayer == null) {
            customPlayer = addCustomPlayer(new CustomPlayer( player.getUniqueId() ));
            customPlayer.setName(Component.text(player.getName()));
        }
        if (customPlayer == null) { // should never happen
            Specialization.logger.severe(String.format("%s was not able to be processed??? (null shit! \uD83D\uDC80\uD83D\uDC80\uD83D\uDC80)", player.getUniqueId()));
            return;
        }
        Specialization.getInstance().applyCustomName(player, customPlayer.getName());


        // TODO PDC-xp-hotfix for later if we need it
        //  customPlayer.reloadSkillsXp(playerJoinEvent.getPlayer());

        // Assign player to team based on their highest skill
//            TeamManager.setTeam(playerJoinEvent.getPlayer());

    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CustomPlayer customPlayer =  getCustomPlayer(event.getPlayer().getUniqueId());
        if (customPlayer != null) {
            if (customPlayer.isDowned()) {
                // Save that they were downed when they logged out
                customPlayer.setWasDownedOnLogout(true);
                // Clean up current session state to prevent infinite death loop
                customPlayer.setDowned(false);
//                    playerDeathListener.setDowned(playerQuitEvent.getPlayer(), true);
            } else {
                // They weren't downed, so clear the flag
                customPlayer.setWasDownedOnLogout(false);
            }
        }
        removeCustomPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPreJoin(AsyncPlayerPreLoginEvent event) {
        CustomPlayer customPlayer = null;
        try {
            customPlayer = this.load(event.getUniqueId());
        } catch (FileNotFoundException e) {
            Specialization.getInstance().getLogger().warning(String.format("Couldn't load player %s", event.getName()));
        }

        if (customPlayer != null) {
            addCustomPlayer(customPlayer);
        } else {
            customPlayer = addCustomPlayer(new CustomPlayer(event.getUniqueId()));
            customPlayer.setName(Component.text(Specialization.localNameGenerator.nextName()).color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
            double height = Skill.mapValue(Math.random(), 0.0, 1.0, .85, 1.0);
            customPlayer.setHeight(height);
        }

        UUID uniqueId = event.getUniqueId();
        if (!Specialization.playerUtilMap.containsKey(uniqueId)) {
            Specialization.playerUtilMap.put(uniqueId, new PlayerUtil(uniqueId));
        }
    }
}
