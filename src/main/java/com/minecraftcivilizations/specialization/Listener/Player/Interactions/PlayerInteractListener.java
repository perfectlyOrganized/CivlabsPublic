package com.minecraftcivilizations.specialization.Listener.Player.Interactions;

import com.google.gson.reflect.TypeToken;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.Skill;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import minecraftcivilizations.com.minecraftCivilizationsCore.Options.Pair;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.*;
import java.util.regex.Pattern;

public class PlayerInteractListener implements Listener {

    private final Set<UUID> cascadingSugarcane = new HashSet<>();

    @EventHandler
    public void onOpenBlockInventory(InventoryOpenEvent e) {
        InventoryType type = e.getInventory().getType();
        List<InventoryType> defaultAllow = SpecializationConfig.getCanUseBlockConfig().get("default", new TypeToken<>() {});
        if (defaultAllow.contains(type)) return;

        CustomPlayer player = CoreUtil.getPlayer(e.getPlayer());
        for (Skill skill : player.getSkills()) {
            SkillType skillType = skill.getSkillType();
            int playerSkillLevel = player.getSkillLevel(skillType);

            for (SkillLevel skillLevel : SkillLevel.values()) {
                if (skillLevel.getLevel() <= playerSkillLevel) {
                    String configKey = skillType + "_" + skillLevel;
                    List<InventoryType> types = SpecializationConfig.getCanUseBlockConfig().get(configKey, new TypeToken<>() {});
                    if (types != null && types.contains(type)) {
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

    @EventHandler
    public void onLibrarianEnchantItem(PlayerInteractEvent e) {
        if (!e.getAction().isRightClick() || !e.getPlayer().isSneaking() || e.getItem() == null || e.getHand().equals(EquipmentSlot.OFF_HAND))
            return;
        if (!e.getPlayer().getInventory().getItemInOffHand().getType().equals(Material.BOOK))
            return;

        CustomPlayer player = CoreUtil.getPlayer(e.getPlayer());
        int xpBase = SpecializationConfig.getLibrarianConfig().get("BLESS_ITEM_XP_LEVEL_REQUIREMENT", Integer.class);
        int skillMin = SpecializationConfig.getLibrarianConfig().get("BLESS_ITEM_LIBRARIAN_LEVEL", Integer.class);
        int xpLevelAmount = xpBase * (player.getSkillLevel(SkillType.LIBRARIAN) - skillMin + 1);
        if (xpLevelAmount > e.getPlayer().getLevel()) return;

        String regex = SpecializationConfig.getLibrarianConfig().get("ENCHANTABLE_TOOL_REGEX", String.class);
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
                SpecializationConfig.getLibrarianConfig().get("BANNED_BLESS_ENCHANTS", new TypeToken<>() {});

        List<Enchantment> validEnchants = RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ENCHANTMENT)
                .stream()
                .filter(enchant -> {
                    if (bannedBlessEnchants.contains(enchant.getKey())) return false;
                    if (!enchant.canEnchantItem(e.getItem())) return false;
                    for (Enchantment existing : e.getItem().getEnchantments().keySet()) {
                        if (enchant.conflictsWith(existing)) return false;
                    }
                    return true;
                })
                .toList();

        if (validEnchants.isEmpty()) {
            PlayerUtil.message(e.getPlayer(), ChatColor.RED + "This item cannot be blessed further.");
            return;
        }

        Enchantment enchant = validEnchants.get(new Random().nextInt(validEnchants.size()));
        int level = new Random().nextInt(1 + player.getSkillLevel(SkillType.LIBRARIAN) - skillMin);
        if (level <= 0) level = 1;
        int finalLevel = Math.min(enchant.getMaxLevel(), level);

        meta.addEnchant(enchant, finalLevel, false);

        List<Component> lore = meta.hasLore() ? new ArrayList<>(Objects.requireNonNull(meta.lore())) : new ArrayList<>();
        String enchantDisplay = enchant.getKey().getKey().replace("_", " ");
        lore.add(Component.text(ChatColor.GOLD + "Blessed with " + enchantDisplay + " " + finalLevel + " by " + e.getPlayer().getName()));
        meta.lore(lore);
        e.getItem().setItemMeta(meta);

        e.getPlayer().setLevel(e.getPlayer().getLevel() - xpLevelAmount);
        e.getPlayer().getInventory().getItemInOffHand()
                .setAmount(e.getPlayer().getInventory().getItemInOffHand().getAmount() - 1);

        PlayerUtil.message(e.getPlayer(), ChatColor.GOLD + "✨ Your " + typeName.replace("_", " ") + " has been blessed with " + enchantDisplay + " " + finalLevel + "!");
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

        Pair<SkillType, Double> pair =
                SpecializationConfig.getXpGainFromBreakingConfig()
                        .get(Material.SUGAR_CANE, new TypeToken<Pair<SkillType, Double>>() {});
        double xpPer = pair != null && pair.secondValue() != null ? pair.secondValue() : 0d;

        if (cascadingSugarcane.contains(e.getPlayer().getUniqueId())) {
            if (xpPer > 0) cp.addSkillXp(SkillType.FARMER, xpPer);
            return;
        }

        cascadingSugarcane.add(e.getPlayer().getUniqueId());
        try {
            if (xpPer > 0) cp.addSkillXp(SkillType.FARMER, xpPer);

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
        if (!e.getAction().isRightClick() || e.getHand() == EquipmentSlot.OFF_HAND) return;
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
    public void onAnvilFinish(InventoryClickEvent e) {
        if (e.getView() instanceof AnvilView view) {
            String renameText = view.getRenameText();
            if (renameText != null && renameText.matches("^\\[lore [0-9]].*")) {
                CustomPlayer player = CoreUtil.getPlayer(e.getWhoClicked());
                int level = SpecializationConfig.getLibrarianConfig().get("ITEM_LORE_LIBRARIAN_LEVEL", Integer.class);
                if (player.getSkillLevel(SkillType.LIBRARIAN) < level) return;

                int number = Integer.parseInt(String.valueOf(renameText.charAt(6)));
                ItemStack result = view.getTopInventory().getResult();
                if (result == null) return;
                ItemStack oldItem = view.getTopInventory().getFirstItem();
                if (oldItem.hasData(DataComponentTypes.CUSTOM_NAME))
                    result.setData(DataComponentTypes.CUSTOM_NAME, view.getTopInventory().getFirstItem().getData(DataComponentTypes.CUSTOM_NAME));
                else {
                    result.unsetData(DataComponentTypes.CUSTOM_NAME);
                }
                ArrayList<Component> lines = new ArrayList<>(result.getData(DataComponentTypes.LORE).lines());
                if (lines.size() < number) {
                    for (int i = 0; i < number - lines.size() + 1; i++) lines.add(Component.empty());
                }
                lines.set(number - 1, Component.text(renameText.substring(8).trim()));

                result.setData(DataComponentTypes.LORE, ItemLore.lore(lines));
            }
        }
    }

    @EventHandler
    public void anvilRenameEvent(InventoryClickEvent e) {
        if (e.getView() instanceof AnvilView view) {
            String renameText = view.getRenameText();
            if (renameText != null && renameText.matches("^\\[lore [0-9]]")) {
                int number = renameText.charAt(7);
                ItemStack result = view.getTopInventory().getResult();
                result.unsetData(DataComponentTypes.CUSTOM_NAME);
                result.getData(DataComponentTypes.LORE).lines().add(number + 1, Component.text(renameText.substring(8)));
            }
        }
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
}
