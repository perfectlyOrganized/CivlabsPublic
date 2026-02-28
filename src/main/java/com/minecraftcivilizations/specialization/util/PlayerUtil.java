package com.minecraftcivilizations.specialization.util;

import com.minecraftcivilizations.specialization.OpenLab;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Utilities related to player management
 * Instances of this class can hold transient player data
 * Player Cooldowns
 */
public class PlayerUtil {
//    public static Component LOGO = buildLogo();
    private static Map<String, Long> messageCooldowns = new HashMap<>();

     public static Component buildLogo() {
        // Using a smooth gradient across the logo text
        String logoGradient = "<#747ab6>[<gradient:#708EFA:#5E4F9F>OpenLabs</gradient>]";
        return MiniMessage.miniMessage().deserialize(logoGradient);
    }

    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacySection();

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    public static void sendRichMessage(Player player, String miniMessage) {
        Component component = MINI_MESSAGE.deserialize(miniMessage);
        sendMessage(player, component);
    }

    public static void sendMessage(Player player, Component message) {
        Audience audience = OpenLab.adventure.player(player.getUniqueId());
        audience.sendMessage(message);
    }

    public static void sendMessage(Player player, String miniMessage) {
        Component component = MiniMessage.miniMessage().deserialize(miniMessage);
        sendMessage(player, component);
    }

public static void sendActionBar(Player player, Component component) {
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(LEGACY_SERIALIZER.serialize(component))
        );
    }
    /**
     * Sends a message with an optional cooldown in seconds.
     * If cooldownSeconds <= 0, no cooldown is applied.
     */

    public static void showTitle(Player target, Component title, Component subtitle) {
        String titleText = LegacyComponentSerializer.legacySection().serialize(title);
        String subtitleText = LegacyComponentSerializer.legacySection().serialize(subtitle);

        target.sendTitle(titleText, subtitleText, 10, 70, 20);
    }

    public static void message(Player player, Object msg, double cooldownSeconds) {
        String key = "msg_" + player.getUniqueId();

        if (cooldownSeconds > 0) {
            long now = System.currentTimeMillis();
            if (messageCooldowns.containsKey(key) && now < messageCooldowns.get(key)) {
                return; // still on cooldown
            }
            messageCooldowns.put(key, now + (long) (cooldownSeconds * 1000));
        }

        Component prefix = MiniMessage.miniMessage().deserialize("<dark_gray> » ");
        Component messageComp;

        if (msg instanceof Component comp) {
            messageComp = comp;
        } else if (msg instanceof String str) {
            str = legacyToMini("<gray>" + str);
            messageComp = MiniMessage.miniMessage().deserialize(str);
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + msg.getClass());
        }
        sendMessage(player,buildLogo().append(prefix).append(messageComp));
    }

    public static Player getNearestPlayer(Location location, double radius) {
        double radiusSquared = radius * radius;
        Player nearest = null;
        double nearestDistanceSquared = Double.MAX_VALUE;

        for (Player player : location.getWorld().getPlayers()) {
            if (!player.isOnline()) continue;

            double distanceSquared = player.getLocation().distanceSquared(location);
            if (distanceSquared <= radiusSquared && distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearest = player;
            }
        }

        return nearest;
    }
    public static void message(Player player, Object msg) {
        message(player, msg, 0);
    }

    /**
     * Convert legacy Minecraft formatting codes (§c, §7, §a, etc.) to MiniMessage syntax.
     */
    private static String legacyToMini(String input) {
        if (input == null || input.isEmpty()) return "";
        return input
                .replace("§0", "<black>")
                .replace("§1", "<dark_blue>")
                .replace("§2", "<dark_green>")
                .replace("§3", "<dark_aqua>")
                .replace("§4", "<dark_red>")
                .replace("§5", "<dark_purple>")
                .replace("§6", "<gold>")
                .replace("§7", "<gray>")
                .replace("§8", "<dark_gray>")
                .replace("§9", "<blue>")
                .replace("§a", "<green>")
                .replace("§b", "<aqua>")
                .replace("§c", "<red>")
                .replace("§d", "<light_purple>")
                .replace("§e", "<yellow>")
                .replace("§f", "<white>")
                .replace("§l", "<bold>")
                .replace("§m", "<strikethrough>")
                .replace("§n", "<underlined>")
                .replace("§o", "<italic>")
                .replace("§r", "<reset>");
    }




    public PlayerUtil(UUID player){

    }




    private Map<String, Long> cooldowns = new HashMap<String, Long>();


    /**
     * Set cooldown for this key for [durationMillis] milliseconds
     * @param key
     * @param ticks
     */
    public void setCooldown(String key, long ticks) {
        cooldowns.put(key, System.currentTimeMillis() + (ticks*50));
    }

    /**
     * Returns true if still on cooldown
     */
    public boolean isOnCooldown(String key) {
        if(!cooldowns.containsKey(key)) {
//    		cooldowns.put(key, System.currentTimeMillis());
            return false;
        }
        Long expireTime = cooldowns.get(key);
        return expireTime != null && System.currentTimeMillis() < expireTime;
    }


    public static boolean tryConsumeXp(Player player, int xp_amount) {
        int total = getExp(player);
        if (total < xp_amount) return false;

        changeExp(player, -xp_amount);
//        setTotalXp(player, total);
        return true;
    }


    /**
     * Calculate a player's total experience based on level and progress to next.
     *
     * @param player the Player
     * @return the amount of experience the Player has
     *
     * @see <a href=http://minecraft.wiki/Experience#Leveling_up>Experience#Leveling_up</a>
     */
    public static int getExp(Player player) {
        return getExpFromLevel(player.getLevel())
                + Math.round(getExpToNext(player.getLevel()) * player.getExp());
    }

    /**
     * Calculate total experience based on level.
     *
     * @param level the level
     * @return the total experience calculated
     *
     * @see <a href=http://minecraft.wiki/Experience#Leveling_up>Experience#Leveling_up</a>
     */
    public static int getExpFromLevel(int level) {
        if (level > 30) {
            return (int) (4.5 * level * level - 162.5 * level + 2220);
        }
        if (level > 15) {
            return (int) (2.5 * level * level - 40.5 * level + 360);
        }
        return level * level + 6 * level;
    }

    /**
     * Calculate level (including progress to next level) based on total experience.
     *
     * @param exp the total experience
     * @return the level calculated
     */
    public static double getLevelFromExp(long exp) {
        int level = getIntLevelFromExp(exp);

        // Get remaining exp progressing towards next level. Cast to float for next bit of math.
        float remainder = exp - (float) getExpFromLevel(level);

        // Get level progress with float precision.
        float progress = remainder / getExpToNext(level);

        // Slap both numbers together and call it a day. While it shouldn't be possible for progress
        // to be an invalid value (value < 0 || 1 <= value)
        return ((double) level) + progress;
    }

    /**
     * Calculate level based on total experience.
     *
     * @param exp the total experience
     * @return the level calculated
     */
    public static int getIntLevelFromExp(long exp) {
        if (exp > 1395) {
            return (int) ((Math.sqrt(72 * exp - 54215D) + 325) / 18);
        }
        if (exp > 315) {
            return (int) (Math.sqrt(40 * exp - 7839D) / 10 + 8.1);
        }
        if (exp > 0) {
            return (int) (Math.sqrt(exp + 9D) - 3);
        }
        return 0;
    }

    public static boolean isCritical(Player player) {
        // Critical hit conditions from Minecraft
        return player.getFallDistance() > 0.0f &&
                !player.isOnGround() &&
                !player.isInWater() &&
                !player.isClimbing() &&
                player.getVelocity().getY() < 0.0 && // Actually falling
                player.getAttackCooldown() == 1.0f;   // Full cooldown for max damage
    }
    /**
     * Get the total amount of experience required to progress to the next level.
     *
     * @param level the current level
     *
     * @see <a href=http://minecraft.wiki/Experience#Leveling_up>Experience#Leveling_up</a>
     */
    private static int getExpToNext(int level) {
        if (level >= 30) {
            // Simplified formula. Internal: 112 + (level - 30) * 9
            return level * 9 - 158;
        }
        if (level >= 15) {
            // Simplified formula. Internal: 37 + (level - 15) * 5
            return level * 5 - 38;
        }
        // Internal: 7 + level * 2
        return level * 2 + 7;
    }

    /**
     * Change a Player's experience.
     *
     * <p>This method is preferred over {@link Player#giveExp(int)}.
     * <br>In older versions the method does not take differences in exp per level into account.
     * This leads to overlevelling when granting players large amounts of experience.
     * <br>In modern versions, while differing amounts of experience per level are accounted for, the
     * approach used is loop-heavy and requires an excessive number of calculations, which makes it
     * quite slow.
     *
     * @param player the Player affected
     * @param exp the amount of experience to add or remove
     */
    public static void changeExp(Player player, int exp) {
        exp += getExp(player);

        if (exp < 0) {
            exp = 0;
        }

        double levelAndExp = getLevelFromExp(exp);
        int level = (int) levelAndExp;
        player.setLevel(level);
        player.setExp((float) (levelAndExp - level));
    }


}
