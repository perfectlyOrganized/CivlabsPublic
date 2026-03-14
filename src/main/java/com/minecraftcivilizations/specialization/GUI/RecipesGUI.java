package com.minecraftcivilizations.specialization.GUI;

import com.minecraftcivilizations.specialization.Skill.Skill;
import com.minecraftcivilizations.specialization.player.CustomPlayer;
import com.minecraftcivilizations.specialization.Recipe.RecipeBlocker;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.StaffTools.Debug;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import com.minecraftcivilizations.specialization.util.ItemStackUtils;
import com.minecraftcivilizations.specialization.util.LoreUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Banner;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

import static com.minecraftcivilizations.specialization.Skill.Skill.Companion;

public class RecipesGUI extends GUI {
    public SkillType skillType;
    public CustomPlayer customPlayer;
    private int page = 1;
    private final int maxPage = (int) Math.ceil((Skill.Companion.getMAX_LEVEL()-1)/5.0);
    public RecipesGUI(CustomPlayer customPlayer, SkillType skillType) {
        super(Component.text(skillType != null ? "Unlocked in " + SkillType.getDisplayName(skillType) : "Choose SkillTree To View").color(NamedTextColor.BLACK), 54, new HashMap<>(), new HashMap<>() {
            {
                put(GUIPlaceOption.SHOULD_PLACE_EXIT, true);
                put(GUIPlaceOption.SHOULD_PLACE_BACK, true);
            }
        });
        this.skillType = skillType;
        this.customPlayer = customPlayer;
    }

    @Override
    public void open(Player player) {
        if (page < maxPage) {
            getItems().put(1, new GUIItem(ItemStackUtils.makeItemGUIItem(new ItemStack(Material.valueOf("CREATEDECO_DECAL_RIGHT")), "Next page ("+ (page+1) +")").getItem(), () -> {
                page++;
                this.open(player);
            }));
        } else {
            getItems().put(1,ItemStackUtils.makeItemGUIItem(new ItemStack(Material.BLACK_STAINED_GLASS_PANE), ""));
        }
        getItems().put(0, new GUIItem(ItemStackUtils.makeItemGUIItem(new ItemStack(Material.valueOf("CREATEDECO_DECAL_LEFT")), page > 1 ? "Previous page ("+ (page-1) +")" : "Back to Class Menu").getItem(), () -> {
            if (page > 1) {
                page--;
                this.open(player);
            } else {
                new ClassGUI().open(Bukkit.getPlayer(customPlayer.getUuid()));
            }
        }));


        for (int index = 0; index < Math.min(5,Skill.Companion.getMAX_LEVEL()-1-(page-1)*5); index++) {
            getItems().put(45+index*2, viewRecipesItem(index+1+(page-1)*5));
        }
        double levelPercentage;
        int playerLevel = customPlayer.getSkillLevel(skillType);
        int pageStart = (page - 1) * 5 + 1;
        int pageEnd = page * 5;

        if (playerLevel < pageStart) {
            levelPercentage = 0.0;
        } else if (playerLevel >= pageEnd) {
            levelPercentage = 100.0;
        } else {
            int levelsOnThisPage = playerLevel - pageStart + 1;
            levelPercentage = (levelsOnThisPage / 5.0) * 100;
        }

        for (int index = 0; index < 18; index++) {
            boolean isUnlocked = levelPercentage > ((double) index /18) * 100;
            getItems().put(18+index, ItemStackUtils.makeItemGUIItem(isUnlocked ?
                new ItemStack(Material.LIME_STAINED_GLASS_PANE) :
                new ItemStack(Material.GRAY_STAINED_GLASS_PANE), levelPercentage +"%"
            ));
        }

        GUIItem guiItem = ItemStackUtils.makeGUIItemOfType(skillType.getSkillWorkstation(), SkillType.getDisplayName(skillType));
        ItemMeta meta = guiItem.getItem().getItemMeta();
        if (meta != null) LoreUtils.setLore(meta, LoreUtils.createDescriptionLoreLine(skillType.getSkillDescription()));

        getItems().put(4, guiItem);
        super.open(player);
    }
    public GUIItem recipeItem(int level) {
        if (customPlayer.getSkillLevel(skillType) >= level) {
            return ItemStackUtils.makeItemGUIItem(new ItemStack(Material.LIME_STAINED_GLASS_PANE), SkillLevel.Companion.getDisplayName(level) + " Unlocked");
        }
        return ItemStackUtils.makeItemGUIItem(new ItemStack(Material.RED_STAINED_GLASS_PANE), SkillLevel.Companion.getDisplayName(level) + " Not Unlocked");
    }

    public GUIItem viewRecipesItem(int requiredLevel) {
        GUIItem guiItem;

//        //Debug only
//        if(recipe_exceptions==null) {
//        if(Debug.isAnyoneListening("recipe", false)) {
//            recipe_exceptions = generateRecipeExceptions();
//        }
//        }

        if (customPlayer.getSkillLevel(skillType) < requiredLevel - 1) {
            guiItem = ItemStackUtils.makeItemGUIItem(new ItemStack(Material.BOOK), SkillLevel.Companion.getDisplayName(requiredLevel) + " Not Unlocked");
            ItemMeta meta = guiItem.getItem().getItemMeta();
            if (meta != null) {
                LoreUtils.setLore(meta, LoreUtils.createDescriptionLoreLine("You can't view recipes yet, you'll be able to see it once you're one level under the requirement (" + (requiredLevel - 1) + ")"));
            }
            return guiItem;
        } else if (customPlayer.getSkillLevel(skillType) == requiredLevel - 1) {
            guiItem = ItemStackUtils.makeItemGUIItem(new ItemStack(Material.BOOK), SkillLevel.Companion.getDisplayName(requiredLevel) + " Not Unlocked");
            ItemMeta meta = guiItem.getItem().getItemMeta();
            if (meta != null) {
                LoreUtils.setLore(meta, LoreUtils.createDescriptionLoreLine("Click to view recipes you'll unlock"));
            }
            guiItem.setOnClick(() -> {
                Set<NamespacedKey> stringHashSetPair = RecipeBlocker.INSTANCE.getRecipes(skillType, requiredLevel);
                ArrayList<ItemStack> itemStacks = new ArrayList<>(0);
                if (stringHashSetPair != null) {
                    for (NamespacedKey namespacedKey : stringHashSetPair) {
                        Material material = Registry.MATERIAL.get(namespacedKey);
                        if(material!=null) {
                            itemStacks.add(new ItemStack(material));
                        }
                    }
                    HashMap<GUIPlaceOption, Boolean> map = new HashMap<>(0);
                    map.putAll(Map.of(GUIPlaceOption.SHOULD_PLACE_EXIT, false, GUIPlaceOption.SHOULD_PLACE_BACK, true, GUIPlaceOption.SHOULD_PLACE_SEARCH, false));
//                    Debug.broadcast("recipe", "recipes!");
                    new ListGUI(Component.text("Recipes"), itemStacks, map).setParentGUI(this).open(Bukkit.getPlayer(customPlayer.getUuid()));
                }
            });
            return guiItem;
        }
        guiItem = ItemStackUtils.makeItemGUIItem(new ItemStack(Material.WRITABLE_BOOK), SkillLevel.Companion.getDisplayName(requiredLevel) + " Unlocked");
        ItemMeta meta = guiItem.getItem().getItemMeta();
        if (meta != null) {
            LoreUtils.setLore(meta, LoreUtils.createDescriptionLoreLine("Click to view recipes you've unlocked"));
        }
        guiItem.setOnClick(() -> {
            Set<NamespacedKey> stringHashSetPair = RecipeBlocker.INSTANCE.getRecipes(skillType, requiredLevel);
            ArrayList<ItemStack> itemStacks = new ArrayList<>(0);
            if (stringHashSetPair != null) {
                for (NamespacedKey namespacedKey : stringHashSetPair) {
                    if (namespacedKey == null) {
                        Debug.broadcast("recipe", "<red>Null NamespacedKey in recipe set");
                        continue;
                    }

                    ItemStack item = ItemStackUtils.getItemStack(namespacedKey);
                    if (item != null) {
                        itemStacks.add(item);
                    } else {
                        String keyString = namespacedKey.toString();
                        ItemStack stack = recipe_exceptions.get(keyString);

                        if (stack == null) {
                            stack = recipe_exceptions.get(namespacedKey.getKey());
                        }

                        if (stack != null) {
                            itemStacks.add(stack);
                        } else {
                            Debug.broadcast("recipe", "<red>Unknown recipe key:<white> " + namespacedKey);
                        }
                    }
                }
                new ListGUI(Component.text("Recipes"), itemStacks, Map.of(GUIPlaceOption.SHOULD_PLACE_EXIT, false, GUIPlaceOption.SHOULD_PLACE_BACK, true, GUIPlaceOption.SHOULD_PLACE_SEARCH, false)).setParentGUI(this).open(Bukkit.getPlayer(customPlayer.getUuid()));
            }
        });

        return guiItem;
    }

    Map<String, ItemStack> recipe_exceptions = generateRecipeExceptions();

    private Map<String, ItemStack> generateRecipeExceptions() {
        Map<String, ItemStack> recipemap = new HashMap<>();
        ItemStack mapitem = new ItemStack(Material.MAP);
        ItemMeta meta = mapitem.getItemMeta();
        LoreUtils.setItemDisplayName(meta,Component.text("Empty Map").color(NamedTextColor.WHITE));
        mapitem.setItemMeta(meta);
        recipemap.put("empty_map", mapitem);

// create shield and apply the lightning banner pattern to the shield
        ItemStack shield_item = new ItemStack(Material.SHIELD);
        BlockStateMeta shield_meta = (BlockStateMeta) shield_item.getItemMeta();
        Banner shield_banner = (Banner) shield_meta.getBlockState();

// black backdrop for the shield banner
        shield_banner.setBaseColor(DyeColor.BLACK);

// lightning bolt (layered zig-zag effect) applied to the shield's banner state
        shield_banner.addPattern(new Pattern(DyeColor.YELLOW, PatternType.STRIPE_DOWNLEFT));
        shield_banner.addPattern(new Pattern(DyeColor.YELLOW, PatternType.STRIPE_MIDDLE));
        shield_banner.addPattern(new Pattern(DyeColor.ORANGE, PatternType.STRIPE_DOWNRIGHT));
        shield_banner.addPattern(new Pattern(DyeColor.BLACK, PatternType.BORDER));

        shield_banner.update();
        shield_meta.setBlockState(shield_banner);

// set the visible item name for the shield (standard white text)
        LoreUtils.setItemDisplayName(shield_meta,Component.text("Decorated Shield").color(NamedTextColor.WHITE));
        shield_item.setItemMeta(shield_meta);
        recipemap.put("shield_decoration", shield_item);

// plain black banner (no patterns) with a white display name
        ItemStack banner_item = new ItemStack(Material.BLACK_BANNER);
        BannerMeta banner_meta = (BannerMeta) banner_item.getItemMeta();
        LoreUtils.setItemDisplayName(banner_meta,Component.text("Black Banner").color(NamedTextColor.WHITE));
        banner_item.setItemMeta(banner_meta);
        recipemap.put("banner_duplicate", banner_item);

        return recipemap;
    }


}
