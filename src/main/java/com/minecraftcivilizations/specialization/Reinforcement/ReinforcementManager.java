package com.minecraftcivilizations.specialization.Reinforcement;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.OpenLab;
import com.minecraftcivilizations.specialization.player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ReinforcementManager {

    public static final NamespacedKey namespacedKey = new NamespacedKey(OpenLab.getInstance(), "reinforcedBlocks");

    private static final Map<Vector, Long> lastTimeSpawnedParticle = new HashMap<>();
    private static final Map<Chunk, Set<Reinforcement>> cachedReinforcements = new ConcurrentHashMap<>();
    private static final Map<Chunk, Long> cacheTime = new ConcurrentHashMap<>();
    private static final Map<Chunk, Integer> chunkIndices = new ConcurrentHashMap<>();

    private static final long cooldown = 1000L; // per-block particle cooldown
    private static final long CACHE_EXPIRE_MS = 2 * 60 * 1000L; // 2 minutes
    private static final int CHUNK_RADIUS = 3; //scan radius around player to show particles


    /**
     * Optimized by Jfrogy and redisiged by Jfrogy
     */

    // --------------------- PARTICLE STREAMING ---------------------
    public static void startReinforcement() {

        // --- Player scan every 3 seconds ---
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Iterator<Map.Entry<Chunk, Long>> it = cacheTime.entrySet().iterator();

                // Clean old cache
                while (it.hasNext()) {
                    Map.Entry<Chunk, Long> e = it.next();
                    if (now - e.getValue() > CACHE_EXPIRE_MS) {
                        cachedReinforcements.remove(e.getKey());
                        chunkIndices.remove(e.getKey());
                        it.remove();
                    }
                }

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isHoldingReinforcementItem(player)) continue;

                    Chunk playerChunk = player.getLocation().getChunk();
                    World world = player.getWorld();

                    for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
                        for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                            int cx = playerChunk.getX() + dx;
                            int cz = playerChunk.getZ() + dz;
                            Chunk chunk = world.getChunkAt(cx, cz);

                            if (!cachedReinforcements.containsKey(chunk)) {
                                Set<Reinforcement> set = getReinforcedBlocks(chunk);
                                if (set != null && !set.isEmpty()) {
                                    cachedReinforcements.put(chunk, set);
                                    cacheTime.put(chunk, now);
                                }
                            } else {
                                cacheTime.put(chunk, now); // refresh cache activity
                            }
                        }
                    }
                }
            }
        }.runTaskTimerAsynchronously(OpenLab.getInstance(), 0L, 60L); // every 3 seconds (60 ticks)

        // --- Particle update every tick ---
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!isHoldingReinforcementItem(player)) continue;

                    Chunk baseChunk = player.getLocation().getChunk();
                    World world = player.getWorld();

                    for (int dx = -CHUNK_RADIUS; dx <= CHUNK_RADIUS; dx++) {
                        for (int dz = -CHUNK_RADIUS; dz <= CHUNK_RADIUS; dz++) {
                            int cx = baseChunk.getX() + dx;
                            int cz = baseChunk.getZ() + dz;
                            Chunk chunk = world.getChunkAt(cx, cz);

                            Set<Reinforcement> set = cachedReinforcements.get(chunk);
                            if (set == null || set.isEmpty()) continue;

                            List<Reinforcement> list = new ArrayList<>(set);
                            int index = chunkIndices.getOrDefault(chunk, 0);

                            int batchSize = list.size() > 25 ? 3 : 1; // batch 3 if >25 reinforced blocks
                            for (int i = 0; i < batchSize; i++) {
                                index = (index + 1) % list.size();
                                Reinforcement r = list.get(index);
                                spawnParticle(player, r);
                            }

                            chunkIndices.put(chunk, index);
                        }
                    }
                }
            }
        }.runTaskTimer(OpenLab.getInstance(), 0L, 1L); // every tick
    }

        private static void spawnParticle(Player player, Reinforcement r) {
        long now = System.currentTimeMillis();
        synchronized (lastTimeSpawnedParticle) {
            Long last = lastTimeSpawnedParticle.get(r.location());
            if (last != null && now - last < cooldown) return;
            lastTimeSpawnedParticle.put(r.location(), now);
        }

        Block b = player.getWorld().getBlockAt(r.location().getBlockX(), r.location().getBlockY(), r.location().getBlockZ());
        Location base = b.getLocation().add(0.5, 0.5, 0.5);

        double offset = 0.55;
            double random_a = Math.random()*0.33;
            double random_b = Math.random()*0.33;
        Vector[] dirs = {
                new Vector(offset, random_a, random_b),
                new Vector(-offset, random_b, random_a),
                new Vector(random_a, offset, random_b),
                new Vector(random_b, -offset, random_a),
                new Vector(random_a, random_b, offset),
                new Vector(random_b, random_a, -offset)
        };

        Particle.DustOptions dust;
        if (r.isHeavy()) {
            dust = new Particle.DustOptions(Color.fromRGB(150, 150, 150), 1.6f);
        } else if (r.isWooden()) {
            dust = new Particle.DustOptions(Color.fromRGB(181, 137, 89), 1.0f); // light brown for wooden
        } else {
            dust = new Particle.DustOptions(Color.fromRGB(250, 150, 100), 1.0f); // copper/light
        }



        for (Vector v : dirs) {
            Location loc = base.clone().add(v);
//                    .add(Math.random() * 0.1 - 0.05, Math.random() * 0.1 - 0.05, Math.random() * 0.1 - 0.05);
//            double velX = (Math.random() - 0.5) * 0.02;
//            double velY = (Math.random() - 0.5) * 0.02;
//            double velZ = (Math.random() - 0.5) * 0.02;



            // REDSTONE particle with no gravity and slight drift
            player.spawnParticle(Particle.SCRAPE, loc, 1);
        }
    }



    // --------------------- ITEM CHECK ---------------------
    private static boolean isHoldingReinforcementItem(Player player) {
        if (player == null) return false;
        Material mainHandType = player.getInventory().getItemInMainHand().getType();
        Material offHandType = player.getInventory().getItemInOffHand().getType();
        return mainHandType == Material.IRON_INGOT || mainHandType == Material.COPPER_INGOT ||
               offHandType == Material.IRON_INGOT || offHandType == Material.COPPER_INGOT ||
               isLog(mainHandType) || isLog(offHandType);
    }

    public static boolean isLog(Material material) {
        if (material == null) return false;
        String name = material.name();
        // Include regular logs, stripped logs, wood, stripped wood, bamboo blocks, stems, hyphae
        return name.endsWith("_LOG") || name.endsWith("_WOOD") ||
               name.endsWith("_STEM") || name.endsWith("_HYPHAE") ||
               material == Material.BAMBOO_BLOCK || material == Material.STRIPPED_BAMBOO_BLOCK;
    }


    // --------------------- REINFORCEMENT METHODS ---------------------

    public static final long WOODEN_REINFORCEMENT_DURATION_TICKS = 48000L; // 2 minecraft nights
    public static final double WOODEN_REINFORCEMENT_STRENGTH_MULTIPLIER = 0.6; // 0.6x as strong as light

    public static boolean addReinforcement(Player player, Block block, boolean isHeavy) {
        if (isReinforced(block)) return false; // Block already has any type of reinforcement

        Chunk chunk = block.getChunk();
        Set<Reinforcement> blocks = getReinforcedBlocks(chunk);
        if (blocks == null) blocks = new HashSet<>();
        if (!blocks.add(new Reinforcement(block.getLocation().toVector(), isHeavy))) return false;

        chunk.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, new Gson().toJson(blocks));
        cachedReinforcements.put(chunk, blocks);
        cacheTime.put(chunk, System.currentTimeMillis());

        Player target = player != null ? player :
                PlayerUtil.getNearestPlayer(block.getLocation(), 4.0);
        if (target != null) {
            CustomPlayer cp = CustomPlayerManager.INSTANCE.getCustomPlayer(target.getUniqueId());
            if (cp != null) cp.addSkillXp(SkillType.BUILDER, isHeavy ? 15.0 : 5.0);
        }
        return true;
    }

    public static boolean addWoodenReinforcement(Player player, Block block, Material logMaterial) {
        if (isReinforced(block)) return false; // Block already has any type of reinforcement

        Chunk chunk = block.getChunk();
        Set<Reinforcement> blocks = getReinforcedBlocks(chunk);
        if (blocks == null) blocks = new HashSet<>();

        long expirationTick = block.getWorld().getFullTime() + WOODEN_REINFORCEMENT_DURATION_TICKS;
        if (!blocks.add(new Reinforcement(block.getLocation().toVector(), logMaterial, expirationTick))) return false;

        chunk.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, new Gson().toJson(blocks));
        cachedReinforcements.put(chunk, blocks);
        cacheTime.put(chunk, System.currentTimeMillis());

        if (player != null) {
            CustomPlayer cp = CustomPlayerManager.INSTANCE.getCustomPlayer(player.getUniqueId());
            if (cp != null) cp.addSkillXp(SkillType.BUILDER, 1.0); // 1 builder xp for wooden
        }
        return true;
    }

    public static boolean addReinforcement(Block block, boolean isHeavy) {
        return addReinforcement(null, block, isHeavy);
    }

    public static boolean addReinforcementSilent(Block b, boolean h) {
        if (h ? isHeavilyReinforced(b) : (isLightlyReinforced(b) || isHeavilyReinforced(b))) return false;
        Chunk c = b.getChunk();
        Set<Reinforcement> r = getReinforcedBlocks(c);
        if (r == null) r = new HashSet<>();
        if (!r.add(new Reinforcement(b.getLocation().toVector(), h))) return false;
        c.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, new Gson().toJson(r));
        cachedReinforcements.put(c, r);
        cacheTime.put(c, System.currentTimeMillis());
        return true;
    }

    public static void removeReinforcement(Block block) {
        Chunk chunk = block.getChunk();
        Set<Reinforcement> reinforcedBlocks = getReinforcedBlocks(chunk);
        if (reinforcedBlocks == null) return;
        // The equals method in Reinforcement only compares location, so this removes any reinforcement type at that position
        reinforcedBlocks.removeIf(r -> r.location().getBlockX() == block.getX() &&
                                       r.location().getBlockY() == block.getY() &&
                                       r.location().getBlockZ() == block.getZ());
        chunk.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, new Gson().toJson(reinforcedBlocks));
        cachedReinforcements.put(chunk, reinforcedBlocks);
        cacheTime.put(chunk, System.currentTimeMillis());
    }

    public static boolean isReinforced(Block block) {
        return getReinforcement(block) != null;
    }

    public static boolean isHeavilyReinforced(Block block) {
        Reinforcement r = getReinforcement(block);
        return r != null && r.isHeavy();
    }

    public static boolean isLightlyReinforced(Block block) {
        Reinforcement r = getReinforcement(block);
        return r != null && r.isLight();
    }

    public static boolean isWoodenReinforced(Block block) {
        Reinforcement r = getReinforcement(block);
        return r != null && r.isWooden();
    }

    public static Material getWoodenReinforcementLogMaterial(Block block) {
        Reinforcement r = getReinforcement(block);
        return (r != null && r.isWooden()) ? r.plankMaterial() : null;
    }

    private static Reinforcement getReinforcement(Block block) {
        Set<Reinforcement> blocks = getReinforcedBlocks(block.getChunk());
        if (blocks == null) return null;
        for (Reinforcement r : blocks) {
            if (r.location().getBlockX() == block.getX() &&
                    r.location().getBlockY() == block.getY() &&
                    r.location().getBlockZ() == block.getZ()) {
                return r;
            }
        }
        return null;
    }

    private static Set<Reinforcement> getReinforcedBlocks(Chunk chunk) {
        if (cachedReinforcements.containsKey(chunk)) {
            cacheTime.put(chunk, System.currentTimeMillis());
            return cachedReinforcements.get(chunk);
        }
        if (!chunk.getPersistentDataContainer().has(namespacedKey, PersistentDataType.STRING)) return null;
        String s = chunk.getPersistentDataContainer().get(namespacedKey, PersistentDataType.STRING);
        Set<Reinforcement> set = new Gson().fromJson(s, new TypeToken<Set<Reinforcement>>() {}.getType());
        if (set != null && !set.isEmpty()) {
            cachedReinforcements.put(chunk, set);
            cacheTime.put(chunk, System.currentTimeMillis());
        }
        return set;
    }
}
