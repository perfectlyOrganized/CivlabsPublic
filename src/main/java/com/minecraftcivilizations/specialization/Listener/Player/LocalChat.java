package com.minecraftcivilizations.specialization.Listener.Player;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.util.ComponentUtils;
import com.minecraftcivilizations.specialization.util.LoreUtils;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
/*
* This used to hold chat bubbles system. It has been removed
* as I plan to release it as a standalone plugin - Jfrogy
 */
public class LocalChat implements Listener {

    // ---- CONSTANTS ----
    private static final int MAX_CHARS = 155;
    private static final float BASE_HEIGHT = 0.6f;
    private static final float NAMEPLATE_OFFSET = -0.4f;
    private static final float BOB_AMPLITUDE = 0.02f;
    private static final double BOB_PERIOD_MS = 3000.0;
    private static final int CHARS_PER_TICK = 3;
    private static final String CHAT_COLOR = "<#f5f2c8>";
    private static final String QUOTE_COLOR = "<#b7a96f>";
    private static final boolean POP_SOUND = true;
    private static final float POP_VOLUME = 0.6f;
    private static final float POP_PITCH_VARIANCE = 0.2f;

    // toggle typing animation on/off
    private static final boolean ENABLE_TYPING_ANIMATION = true;

    private static final int BOB_PERIOD_TICKS = Math.max(1, (int) (BOB_PERIOD_MS / 50.0));
    private static final float[] BOB_TABLE = new float[BOB_PERIOD_TICKS];
    static {
        for (int i = 0; i < BOB_PERIOD_TICKS; i++) {
            double phase = (i / (double) BOB_PERIOD_TICKS) * Math.PI * 2.0;
            BOB_TABLE[i] = (float) (Math.sin(phase) * BOB_AMPLITUDE);
        }
    }

    // ---- DATA CLASSES ----

    private static class PlayerSession {
        final Player player;
        ArmorStand nameplate;
        PlayerSession(Player p){ this.player = p; }
    }

    private final Map<UUID, PlayerSession> sessions = new HashMap<>();
    private boolean running = false;

    // ---- EVENTS ----
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        CustomPlayer customPlayer = CustomPlayer.getCustomPlayer(p);
        String raw = MiniMessage.miniMessage().stripTags(e.getMessage().trim());

        // Global debug / announcement channel
        if (handleGlobalChat(p, raw)) {
            e.setCancelled(true);
            return;
        }

        // Spectators / invisible don't speak locally
        if (p.getGameMode() == GameMode.SPECTATOR ||
                p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true);

        String fmt = SpecializationConfig
                .getChatConfig()
                .getString("DEFAULT_FORMAT");

        String nameString = ComponentUtils.serializeComponentAsString(customPlayer.getName()).replaceAll("§[0-9a-fklmnor]", "");;
        Specialization.logger.info(nameString);
        Bukkit.getScheduler().runTask(Specialization.getInstance(), () -> {
            for (Player near : getNearbyPlayers(p)) {
                PlayerUtil.sendMessage(near,fmt.formatted(nameString, raw));
            }
            PlayerUtil.sendMessage(p, fmt.formatted(nameString, raw));
        });

        Debug.broadcast(
                "globalchat",
                "<gray>" + customPlayer.getName() + " » </gray>" + e.getMessage()
        );
    }

    private boolean handleGlobalChat(Player p, String msg) {
        String prefix = SpecializationConfig
                .getChatConfig()
                .getString("ANNOUNCEMENT_PREFIX");

        if (!msg.startsWith(prefix) || !p.isOp()) return false;

        String actual = msg.substring(prefix.length()).trim();
        String fmt = SpecializationConfig
                .getChatConfig()
                .getString("ANNOUNCEMENT_FORMAT");

        Bukkit.getOnlinePlayers()
                .forEach(pl -> PlayerUtil.sendRichMessage(pl,fmt.formatted(actual)));

        return true;
    }

    private List<Player> getNearbyPlayers(Player p) {
        double r = SpecializationConfig
                .getChatConfig()
                .getDouble("CHAT_RADIUS");

        return p.getNearbyEntities(r, r, r)
                .stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .toList();
    }

}
