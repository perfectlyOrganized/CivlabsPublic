package com.minecraftcivilizations.specialization.Listener.Player;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.OpenLab;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public final class XpGainMonitor {

    private static final long WINDOW_MS = 30_000L;
    private static final long ALERT_COOLDOWN_MS = 10_000L;
    private static final long RAPID_WINDOW_MS = 10_000L;
    private static final long BUCKET_MS = 300L;
    private static final int RAPID_MIN_EVENTS = 2;

    private static final Map<UUID, Map<SkillType, List<Long>>> rapidXpBuckets = new HashMap<>();
    private static final Map<UUID, Map<SkillType, List<XpRecord>>> xpMap = new HashMap<>();
    private static final Map<UUID, Long> lastAlertTimes = new HashMap<>();
    private static final Map<UUID, Map<String, String>> batchedAlerts = new HashMap<>();
    private static final Map<UUID, Long> batchStartTimes = new HashMap<>();

    private static final Map<String, Double> thresholds = new HashMap<>();
    private static final Map<String, Long> cooldowns = new HashMap<>();

    private static class XpRecord {
        final double xp;
        final long time;
        XpRecord(double xp, long time) { this.xp = xp; this.time = time; }
    }

    private XpGainMonitor() {}
    public static NamespacedKey XP_MONITOR_KEY = new NamespacedKey(OpenLab.getInstance(), "xpmonitor");

    public static void init() {
        var cfg = SpecializationConfig.getXpMonitorConfig();
        for (SkillType type : SkillType.values()) {
            Double t = cfg.getDouble(type.name() + ".threshold");
            Long cd = Long.valueOf(cfg.getInt(type.name() + ".cooldown-seconds"));
            thresholds.put(type.name(), t != null ? t : 500.0);
            cooldowns.put(type.name(), cd != null ? cd : 30L);
        }
        Bukkit.getLogger().info("[XpGainMonitor] Loaded XP thresholds: " + thresholds + ", cooldowns: " + cooldowns);
    }

    public static void handleXpGain(Player player, SkillType type, double amount) {
        if (player == null || type == null || amount <= 0) return;
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        // --- RAPID detection ---
        Map<SkillType, List<Long>> playerBuckets = rapidXpBuckets.computeIfAbsent(uuid, k -> new HashMap<>());
        List<Long> bucketTimes = playerBuckets.computeIfAbsent(type, k -> new ArrayList<>());
        bucketTimes.add(now);
        bucketTimes.removeIf(ts -> now - ts > RAPID_WINDOW_MS);

        int totalBuckets = (int) Math.max(1, (RAPID_WINDOW_MS + BUCKET_MS - 1) / BUCKET_MS);
        int[] counts = new int[totalBuckets];
        for (long ts : bucketTimes) {
            int idx = (int) ((now - ts) / BUCKET_MS);
            if (idx >= 0 && idx < totalBuckets) counts[idx]++;
        }

        int sustainingBuckets = 0;
        for (int c : counts) if (c >= RAPID_MIN_EVENTS) sustainingBuckets++;
        int maxPerBucket = Arrays.stream(counts).max().orElse(0);
        double sustainRatio = (double) sustainingBuckets / totalBuckets;

        String rapidLevel = null;
        if (sustainRatio >= 0.9) rapidLevel = "§cCRITICAL";
        else if (sustainRatio >= 0.7) rapidLevel = "§6WARNING";
        else if (sustainRatio >= 0.5) rapidLevel = "§eNOTICE";

        // --- SUPPRESS noise after recent alert ---
        long lastAlert = lastAlertTimes.getOrDefault(uuid, 0L);
        if (rapidLevel == null && now - lastAlert < ALERT_COOLDOWN_MS) return;

        // --- Threshold detection ---
        Map<SkillType, List<XpRecord>> playerXpMap = xpMap.computeIfAbsent(uuid, k -> new HashMap<>());
        List<XpRecord> xpList = playerXpMap.computeIfAbsent(type, k -> new ArrayList<>());
        xpList.removeIf(r -> now - r.time > WINDOW_MS);
        xpList.add(new XpRecord(amount, now));
        double totalXp = xpList.stream().mapToDouble(r -> r.xp).sum();
        double threshold = thresholds.getOrDefault(type.name(), 500.0);
        boolean thresholdExceeded = totalXp > threshold;

        // --- Batch accumulation ---
        Map<String, String> playerBatch = batchedAlerts.computeIfAbsent(uuid, k -> new LinkedHashMap<>());
        boolean addedToBatch = false;

        if (rapidLevel != null) {
            Component readable = switch (rapidLevel) {
                case "§cCRITICAL" -> Component.text()
                        .append(Component.text("has been gaining " + type.name().toLowerCase() + " XP extremely rapidly"))
                        .append(Component.text("§f")) // If you need to reset color, use resetStyle() instead
                        .build();
                case "§6WARNING" -> Component.text()
                        .append(Component.text("has been gaining " + type.name().toLowerCase() + " XP very rapidly"))
                        .append(Component.text("§f"))
                        .build();
                default -> Component.text("has been gaining " + type.name().toLowerCase() + " XP rapidly.");
            };

            // Create the message as a Component instead of String
            Component message = Component.text()
                    .append(dotRapid(rapidLevel))
                    .append(Component.text(" rapid "))
                    .append(Component.text(type.name().toLowerCase()))
                    .append(Component.text(" (" + maxPerBucket + "/" + BUCKET_MS + "ms)"))
                    .build();

            // If you need to store this as a string (for playerBatch), serialize it
            String serializedMsg = LegacyComponentSerializer.legacySection().serialize(message);
            playerBatch.put(type.name(), serializedMsg);

            addedToBatch = true;
        }

        if (addedToBatch) batchStartTimes.putIfAbsent(uuid, now);

        // --- Alert timing logic ---
        if (!playerBatch.isEmpty()) {
            long batchCreated = batchStartTimes.getOrDefault(uuid, now);

            // discard stale batch
            if (now - batchCreated > RAPID_WINDOW_MS * 2) {
                playerBatch.clear();
                batchStartTimes.remove(uuid);
                return;
            }

            if (now - lastAlert >= ALERT_COOLDOWN_MS) {
                recordAlert(uuid, now);

                String summary = buildSummary(playerBatch);
                Component hover = buildHover(playerBatch, counts, sustainingBuckets, totalBuckets, maxPerBucket, sustainRatio);

                Component msg = Component.text("XP-Alert ")
                        .color(NamedTextColor.DARK_RED)
                        .decorate(TextDecoration.BOLD)
                        .append(Component.text("» ")
                                .color(NamedTextColor.DARK_GRAY)
                                .decoration(TextDecoration.BOLD, false))
                        .append(Component.text(player.getName(), NamedTextColor.WHITE))
                        .append(Component.text(" — " + summary, NamedTextColor.GRAY))
                        .hoverEvent(HoverEvent.showText(hover))
                        .append(Component.text(" "))
                        .append(Component.text("[Teleport]", NamedTextColor.BLUE)
                                .clickEvent(ClickEvent.runCommand("/tp " + player.getName())));

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!(p.isOp() || p.hasPermission("civlabs.xpmonitor"))) continue;

                    Byte visibility = p.getPersistentDataContainer().get(XpGainMonitor.XP_MONITOR_KEY, PersistentDataType.BYTE);
                    if (visibility != null && visibility == 1) {
                        PlayerUtil.sendMessage(p,msg);
                    }
                }
            }
        }
    }

    private static void clearPlayerData(UUID uuid) {
        Map<String, String> batch = batchedAlerts.get(uuid);
        if (batch != null) batch.clear();
        batchStartTimes.remove(uuid);

        Map<SkillType, List<Long>> buckets = rapidXpBuckets.get(uuid);
        if (buckets != null) for (List<Long> l : buckets.values()) l.clear();

        Map<SkillType, List<XpRecord>> xpRecords = xpMap.get(uuid);
        if (xpRecords != null) for (List<XpRecord> l : xpRecords.values()) l.clear();
    }

    private static String buildSummary(Map<String, String> batch) {
        List<String> out = new ArrayList<>();

        for (String v : batch.values()) {
            String color = v.substring(0, 2); // preserves "§c", "§6", "§e", "§b"
            String type = v.contains("rapid") ? "rapid" : "threshold";
            out.add(color + type);
        }

        return String.join("+", out);
    }




    private static Component buildHover(Map<String, String> batch, int[] counts, int sustainingBuckets, int totalBuckets, int maxPerBucket, double ratio) {
        Component hover = Component.text("Details:\n", NamedTextColor.GRAY);
        for (Map.Entry<String, String> e : batch.entrySet()) {
            String value = e.getValue();
            NamedTextColor color = NamedTextColor.GRAY;

            if (value.startsWith("CRITICAL")) color = NamedTextColor.RED;
            else if (value.startsWith("WARNING")) color = NamedTextColor.GOLD;
            else if (value.startsWith("NOTICE")) color = NamedTextColor.WHITE;
            else if (value.contains("Threshold")) color = NamedTextColor.AQUA;

            hover = hover.append(Component.text(e.getKey() + ": " + value + "\n", color));
        }
        hover = hover.append(Component.text("\nSustained windows: " + sustainingBuckets + "/" + totalBuckets
                + " (" + String.format("%.0f%%", ratio * 100) + ")\nMax per bucket: " + maxPerBucket
                + "\nBuckets: " + Arrays.toString(counts), NamedTextColor.DARK_GRAY));
        return hover;
    }

    private static Component dotRapid(String level) {
        return switch (level) {
            case "§cCRITICAL" -> Component.text("●", NamedTextColor.RED);
            case "§6WARNING" -> Component.text("●", NamedTextColor.GOLD);
            case "§eNOTICE" -> Component.text("●", NamedTextColor.YELLOW);
            default -> Component.text("●", NamedTextColor.GRAY);
        };
    }

    public static void saveConfigToDisk() {
        var cfg = SpecializationConfig.getXpMonitorConfig();
        for (String key : thresholds.keySet()) {
            cfg.setDouble(key + ".threshold", thresholds.get(key));
        }

        for (String key : cooldowns.keySet()) {
            cfg.setInteger(key + ".cooldown-seconds", cooldowns.get(key).intValue());
        }
    }


    public static boolean setThreshold(SkillType type, double value) {
        thresholds.put(type.name(), value);
        return true;
    }

    public static boolean setCooldown(SkillType type, long value) {
        cooldowns.put(type.name(), value);
        return true;
    }

    public static double getThreshold(SkillType type) {
        return thresholds.getOrDefault(type.name(), 500.0);
    }

    public static long getCooldown(SkillType type) {
        return cooldowns.getOrDefault(type.name(), 30L);
    }



    private static final String DOT_THRESHOLD = "§b●";


    private static void recordAlert(UUID uuid, long now) {
        lastAlertTimes.put(uuid, now);
    }
}
