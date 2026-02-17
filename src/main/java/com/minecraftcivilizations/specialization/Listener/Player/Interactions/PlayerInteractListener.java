package com.minecraftcivilizations.specialization.Listener.Player.Interactions;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.Skill;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import com.typesafe.config.Config;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PlayerInteractListener implements Listener {

    private final Set<UUID> cascadingSugarcane = new HashSet<>();

    @EventHandler
    public void onOpenBlockInventory(InventoryOpenEvent e) {
        InventoryType type = e.getInventory().getType();
        List<String> defaultAllow = SpecializationConfig.getCanUseBlockConfig().getStringList("default");
        if (defaultAllow.contains(type.toString())) return;

        CustomPlayer player = CoreUtil.getPlayer(e.getPlayer());
        for (Skill skill : player.getSkills()) {
            SkillType skillType = skill.getSkillType();
            int playerSkillLevel = player.getSkillLevel(skillType);

            for (SkillLevel skillLevel : SkillLevel.values()) {
                if (skillLevel.getLevel() <= playerSkillLevel) {
                    String configKey = skillType + "_" + skillLevel;
                    List<String> types = SpecializationConfig.getCanUseBlockConfig().getStringList(configKey);
                    if (types != null && types.contains(type.toString())) {
                        return;
                    }
                }
            }
        }
        e.getPlayer().sendMessage("You are unable to access: "+ type.toString() + ", report if this is a bug.");
        e.setCancelled(true);
    }

    @EventHandler
    public void onWaterSmushCrop(BlockFromToEvent e) {
        if (e.getToBlock().getBlockData() instanceof Ageable) {
            e.getToBlock().setType(Material.AIR);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDestoryFarmland(BlockBreakEvent e) {
        if (e.getBlock().getType().equals(Material.FARMLAND)) {
            e.getBlock().getRelative(BlockFace.UP).setType(Material.AIR);
            e.getBlock().setType(Material.AIR);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerSmushCrop(PlayerInteractEvent e) {
        if (e.getAction().equals(Action.PHYSICAL)) {
            if (e.getClickedBlock() == null) {
                return;
            }

            if (e.getClickedBlock().getType().equals(Material.FARMLAND)) {
                e.setCancelled(true);
                e.getClickedBlock().setType(Material.DIRT);
                e.getClickedBlock().getRelative(BlockFace.UP).setType(Material.AIR);
            }
        }
    }
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
            LegacyComponentSerializer.legacySection();
    @EventHandler
    public void onLibrarianEnchantItem(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK || !e.getPlayer().isSneaking() || e.getItem() == null || e.getHand().equals(EquipmentSlot.OFF_HAND))
            return;
        if (!e.getPlayer().getInventory().getItemInOffHand().getType().equals(Material.BOOK))
            return;

        CustomPlayer player = CoreUtil.getPlayer(e.getPlayer());
        int xpBase = SpecializationConfig.getLibrarianConfig().getInteger("BLESS_ITEM_XP_LEVEL_REQUIREMENT");
        int skillMin = SpecializationConfig.getLibrarianConfig().getInteger("BLESS_ITEM_LIBRARIAN_LEVEL");
        int xpLevelAmount = xpBase * (player.getSkillLevel(SkillType.LIBRARIAN) - skillMin + 1);
        if (xpLevelAmount > e.getPlayer().getLevel()) return;

        String regex = SpecializationConfig.getLibrarianConfig().getString("ENCHANTABLE_TOOL_REGEX");
        String typeName = e.getItem().getType().name().toLowerCase();
        if (!Pattern.compile(regex).matcher(typeName).find()) return;
        if (player.getSkillLevel(SkillType.LIBRARIAN) < skillMin) return;

        ItemMeta meta = e.getItem().getItemMeta();
        if (meta == null) return;

        if (!e.getItem().getEnchantments().isEmpty()) {
            PlayerUtil.message(e.getPlayer(), ChatColor.RED + "This item has already been blessed.");
            return;
        }

        List<NamespacedKey> bannedBlessEnchants =
                SpecializationConfig.getLibrarianConfig().getStringList("BANNED_BLESS_ENCHANTS").stream().map(NamespacedKey::fromString).toList();

        List<Enchantment> validEnchants = Registry.ENCHANTMENT.stream()
                .filter(enchant -> {
                    if (bannedBlessEnchants.contains(enchant.getKey())) return false;
                    if (!enchant.canEnchantItem(e.getItem())) return false;
                    for (Enchantment existing : e.getItem().getEnchantments().keySet()) {
                        if (enchant.conflictsWith(existing)) return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        if (validEnchants.isEmpty()) {
            PlayerUtil.message(e.getPlayer(), ChatColor.RED + "This item cannot be blessed further.");
            return;
        }

        Enchantment enchant = validEnchants.get(new Random().nextInt(validEnchants.size()));
        int level = new Random().nextInt(1 + player.getSkillLevel(SkillType.LIBRARIAN) - skillMin);
        if (level <= 0) level = 1;
        int finalLevel = level; //Math.min(enchant.getMaxLevel(), level);

        meta.addEnchant(enchant, finalLevel, false);

        List<Component> loreComponents = new ArrayList<>();
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                loreComponents.add(LEGACY_SERIALIZER.deserialize(line));
            }
        }

        String enchantDisplay = capitalizeWords(enchant.getKey().getKey().replace("_", " "));
        String levelRoman = toRoman(finalLevel);
        String newLine = ChatColor.GOLD + "Blessed with " + ChatColor.YELLOW + enchantDisplay + " " + levelRoman + ChatColor.GOLD + " by " + ChatColor.AQUA + e.getPlayer().getName();
        loreComponents.add(LEGACY_SERIALIZER.deserialize(newLine));

// Convert back to Strings and set lore
        List<String> loreStrings = loreComponents.stream()
                .map(LEGACY_SERIALIZER::serialize)
                .collect(Collectors.toList());
        meta.setLore(loreStrings);

        e.getPlayer().setLevel(e.getPlayer().getLevel() - xpLevelAmount);
        e.getPlayer().getInventory().getItemInOffHand()
                .setAmount(e.getPlayer().getInventory().getItemInOffHand().getAmount() - 1);

        PlayerUtil.message(e.getPlayer(), ChatColor.GOLD + "✨ Your " + capitalizeWords(typeName.replace("_", " ")) + " has been blessed with " + enchantDisplay + " " + levelRoman + "!");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSweetBerryHarvest(PlayerHarvestBlockEvent e) {
        if (e.getHarvestedBlock().getType() != Material.SWEET_BERRY_BUSH) return;
        boolean producedBerries = e.getItemsHarvested().stream()
                .anyMatch(item -> item.getType() == Material.SWEET_BERRIES);
        if (!producedBerries) return;

        Player player = e.getPlayer();
        CustomPlayer cp = CoreUtil.getPlayer(player);
        if (cp != null) {
            cp.addSkillXp(SkillType.FARMER, 3);
        }
    }

    @EventHandler
    public void onSugarcaneBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.SUGAR_CANE) return;

        CustomPlayer cp = CoreUtil.getPlayer(e.getPlayer());
        if (cp == null) return;
        Config farmerConfig = SpecializationConfig.getXpGainFromBreakingConfig().getObject(SkillType.FARMER.toString());
        double xp = farmerConfig.getDouble(Material.SUGAR_CANE.toString());


        if (cascadingSugarcane.contains(e.getPlayer().getUniqueId())) {
            if (xp > 0) cp.addSkillXp(SkillType.FARMER, xp);
            return;
        }

        cascadingSugarcane.add(e.getPlayer().getUniqueId());
        try {
            if (xp > 0) cp.addSkillXp(SkillType.FARMER, xp);

            List<Block> stack = new ArrayList<>();
            Block b = e.getBlock().getRelative(BlockFace.UP);
            while (b.getType() == Material.SUGAR_CANE) {
                stack.add(b);
                b = b.getRelative(BlockFace.UP);
            }

            Collections.reverse(stack);
            for (Block cane : stack) {
                e.getPlayer().breakBlock(cane);
            }
        } finally {
            cascadingSugarcane.remove(e.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onSugarcanePhysics(BlockPhysicsEvent e) {
        if (e.getBlock().getType() != Material.SUGAR_CANE) return;

        Block base = e.getBlock();
        Block below = base.getRelative(BlockFace.DOWN);
        if (below.getType() == Material.SUGAR_CANE) return;
        if (hasAdjacentWaterOrWaterlogged(below)) return;

        Block b = base;
        while (b.getType() == Material.SUGAR_CANE) {
            b.setType(Material.AIR, false);
            b = b.getRelative(BlockFace.UP);
        }

        e.setCancelled(true);
    }

    private boolean hasAdjacentWaterOrWaterlogged(Block block) {
        BlockFace[] faces = new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        for (BlockFace face : faces) {
            Block adj = block.getRelative(face);
            if (adj.getType() == Material.WATER) return true;
            org.bukkit.block.data.BlockData data = adj.getBlockData();
            if (data instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged()) return true;
        }
        return false;
    }

    @EventHandler
    public void onHarvestGlowBerries(PlayerInteractEvent e) {
        Action action = e.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK || e.getHand() == EquipmentSlot.OFF_HAND) return;
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        Player player = e.getPlayer();

        Material type = clicked.getType();
        if (type != Material.CAVE_VINES && type != Material.CAVE_VINES_PLANT) return;

        String data = clicked.getBlockData().getAsString();
        if (!data.contains("berries=true")) return;
        if(player.isSneaking()){
            return;
        }

        CustomPlayer cp = CoreUtil.getPlayer(player);
        if (cp != null) {
            cp.addSkillXp(SkillType.FARMER, 1);
        }
    }

    @EventHandler
    public void onMilk(PlayerItemConsumeEvent e) {
        if (e.getItem().getType() != Material.MILK_BUCKET) return;

        Bukkit.getScheduler().runTaskLater(
                com.minecraftcivilizations.specialization.Specialization.getInstance(),
                () -> {
                    CustomPlayer cp = CoreUtil.getPlayer(e.getPlayer());
                    if (cp != null) {
//                        cp.applyEffects();
                    }
                },
                1L
        );
    }




    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (e.getBucket().equals(Material.LAVA_BUCKET)) {
            CustomPlayer player = CoreUtil.getPlayer(e);
            if (player.getSkillLevel(SkillType.BLACKSMITH) < SkillLevel.EXPERT.getLevel()) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (e.getBucket().equals(Material.LAVA_BUCKET)) {
            CustomPlayer player = CoreUtil.getPlayer(e);
            if (player.getSkillLevel(SkillType.BLACKSMITH) < SkillLevel.EXPERT.getLevel()) e.setCancelled(true);
        }
    }

    private String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) return str;
        String[] words = str.split(" ");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (!words[i].isEmpty()) {
                result.append(Character.toUpperCase(words[i].charAt(0)));
                if (words[i].length() > 1) {
                    result.append(words[i].substring(1).toLowerCase());
                }
            }
            if (i < words.length - 1) {
                result.append(" ");
            }
        }
        return result.toString();
    }

    private String toRoman(int num) {
        if (num <= 0 || num > 10) return String.valueOf(num);
        String[] romanNumerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return romanNumerals[num - 1];
    }
}
