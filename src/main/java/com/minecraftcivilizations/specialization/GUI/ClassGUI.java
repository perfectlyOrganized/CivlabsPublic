package com.minecraftcivilizations.specialization.GUI;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.OpenLab;
import com.minecraftcivilizations.specialization.player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.Skill;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import com.minecraftcivilizations.specialization.util.LoreUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;


public class ClassGUI extends GUI {

    public ClassGUI() {
        super(Component.text("Your Specialization Stats"), 54);
    }

    @Override
    public void open(Player player) {
        CustomPlayer customPlayer = CustomPlayerManager.INSTANCE.getCustomPlayerOrThrow(player);
        this.getItems().clear();

        if (customPlayer.isAdvancedClassesGUIEnabled()) {
            advancedClassGUI(customPlayer);
        } else {
            defaultClassGUI(customPlayer);
        }

        this.getItems().put(4, makeUserItem(customPlayer.getName()));
        this.getItems().put(45, makeRecipesItem(player));
        this.getItems().put(53, makeSettingsItem(player));

        player.openInventory(Bukkit.createInventory(player, 54));
        super.open(player);
    }

    private GUIItem makeSettingsItem(Player player){
        ItemStack settings = new ItemStack(Material.BOOK);
        ItemMeta settingsItemMeta = settings.getItemMeta();
        settingsItemMeta.addItemFlags(ItemFlag.values());
        LoreUtils.setItemDisplayName(settingsItemMeta,Component.text("Settings").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.WHITE));
        settings.setItemMeta(settingsItemMeta);
        return new GUIItem(settings, () -> new SettingsGUI().setParentGUI(this).open(player));
    }

    private GUIItem makeRecipesItem(Player player){
        ItemStack recipes = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta recipesItemMeta = recipes.getItemMeta();
        recipesItemMeta.addItemFlags(ItemFlag.values());
        LoreUtils.setItemDisplayName(recipesItemMeta,Component.text("Unlocked").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.WHITE));
        recipes.setItemMeta(recipesItemMeta);
        return new GUIItem(recipes, () -> new RecipesGUI(CustomPlayerManager.INSTANCE.getCustomPlayer(player.getUniqueId()), null).open(player));
    }

    private GUIItem makeUserItem(Component name){
        ItemStack user = new ItemStack(Material.EMERALD);
        ItemMeta userItemMeta = user.getItemMeta();
        userItemMeta.addItemFlags(ItemFlag.values());
        LoreUtils.setItemDisplayName(userItemMeta,name.decoration(TextDecoration.ITALIC, false).color(NamedTextColor.WHITE));
        user.setItemMeta(userItemMeta);
        return new GUIItem(user, null);
    }

    private GUIItem makeGlassDistributionPaneAdvanced(String name, Material material, double percent) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.addItemFlags(ItemFlag.values());
        LoreUtils.setItemDisplayName(itemMeta,Component.text(name).decoration(TextDecoration.ITALIC, false).color(NamedTextColor.WHITE));
        LoreUtils.setLore(itemMeta, new ArrayList<>() {
            {
                add(Component.text("Holds " + Math.round(percent * 100) / 100 + "% of your total xp").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.WHITE));
                add(Component.empty());
                add(Component.text("Awesome!").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY));
            }
        });
        itemStack.setItemMeta(itemMeta);
        return new GUIItem(itemStack, null);
    }

    private GUIItem makeGlassDistributionPaneSimple(String name, Material material, double percent) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta itemMeta = itemStack.getItemMeta();
        itemMeta.addItemFlags(ItemFlag.values());
        LoreUtils.setItemDisplayName(itemMeta,Component.text(name).decoration(TextDecoration.ITALIC, false).color(NamedTextColor.WHITE));
        LoreUtils.setLore(itemMeta,new ArrayList<>() {
            {
                add(Component.text("You are " + Math.round(percent * 100) / 100 + "% progressed through this tier").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.WHITE));
                add(Component.empty());
                add(Component.text("Awesome!").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY));
            }
        });
        itemStack.setItemMeta(itemMeta);
        return new GUIItem(itemStack, null);
    }

    private static final List<String> COLOR_PALETTE = Arrays.asList(
            "WHITE", "RED", "ORANGE", "YELLOW", "LIME", "PURPLE", "BLACK", "MAGENTA"
    );

    private Material getPaneMaterial(int tier, int type){
        int maxTier = Skill.Companion.getMAX_LEVEL();
        int safeTier = Math.max(0, Math.min(tier, maxTier));

        // Calculate color index based on position in gradient
        double progress = (double) safeTier / maxTier;
        int colorIndex = (int) Math.round(progress * (COLOR_PALETTE.size() - 1));

        String color = COLOR_PALETTE.get(colorIndex);
        String materialType = switch (type){
            case 0 -> "CARPET";
            case 1 -> "DYE";
            case 2 -> "STAINED_GLASS_PANE";
            default-> "BANNER";
        };
        return Objects.requireNonNull(Material.getMaterial(color + "_" + materialType));
    }
    private void advancedClassGUI(CustomPlayer customPlayer) {
        int i = 37;

        for (Skill skill : customPlayer.getSkills()) {
            int temp = i;
            for (int score = 0; score < 3; score++) {
                double diff = customPlayer.getGUIDistributionOfTotalSkills(skill.getSkillType()) - score;

                if (diff >= 1) {
                    this.getItems().put(temp-=9, makeGlassDistributionPaneAdvanced(SkillType.getDisplayName(skill.getSkillType()), Material.GREEN_STAINED_GLASS_PANE, customPlayer.getPercentOfTotal(skill.getSkillType())));
                } else if (diff >= 0.75) {
                    this.getItems().put(temp-=9, makeGlassDistributionPaneAdvanced(SkillType.getDisplayName(skill.getSkillType()), Material.LIME_STAINED_GLASS_PANE, customPlayer.getPercentOfTotal(skill.getSkillType())));
                } else if (diff >= 0.5) {
                    this.getItems().put(temp-=9, makeGlassDistributionPaneAdvanced(SkillType.getDisplayName(skill.getSkillType()), Material.YELLOW_STAINED_GLASS_PANE, customPlayer.getPercentOfTotal(skill.getSkillType())));
                } else if (diff >= 0.25) {
                    this.getItems().put(temp-=9, makeGlassDistributionPaneAdvanced(SkillType.getDisplayName(skill.getSkillType()), Material.ORANGE_STAINED_GLASS_PANE, customPlayer.getPercentOfTotal(skill.getSkillType())));
                } else {
                    this.getItems().put(temp-=9, makeGlassDistributionPaneAdvanced(SkillType.getDisplayName(skill.getSkillType()), Material.RED_STAINED_GLASS_PANE, customPlayer.getPercentOfTotal(skill.getSkillType())));
                }
            }

            int currentSkillLevel = customPlayer.getSkillLevel(skill.getSkillType());

            if (currentSkillLevel < SkillLevel.Companion.getValues().size()) {
                double currentXp = Math.round(skill.getXp() * 100) / 100D;
                double xpToNextLevel = Math.round((Skill.Companion.getXPNeededForLevel(currentSkillLevel + 1) - skill.getXp()) * 100) / 100D ;
                double percentOfTotalForNextLevel = Math.round(SkillLevel.Companion.getSkillLevelFromInt(currentSkillLevel + 1).getXpRequirement() * 100) / 100D;

                ItemStack itemStack = new ItemStack(skill.getSkillType().getSkillWorkstation());
                ItemMeta itemMeta = itemStack.getItemMeta();
                itemMeta.addItemFlags(ItemFlag.values());
                LoreUtils.setItemDisplayName(itemMeta,Component.text(SkillType.getDisplayName(skill.getSkillType()))
                        .decoration(TextDecoration.ITALIC, false)
                        .color(NamedTextColor.WHITE));
                LoreUtils.setLore(itemMeta,new ArrayList<>() {
                    {
                        add(Component.text(SkillLevel.Companion.getDisplayName(currentSkillLevel))
                                .decoration(TextDecoration.ITALIC, false)
                                .color(NamedTextColor.WHITE)
                                .append(Component.text("(lvl " + currentSkillLevel + ")"))
                                .color(NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false));
                        add(Component.text("Current xp: " + (int) Math.round(skill.getXp())).decoration(TextDecoration.ITALIC, false).color(NamedTextColor.WHITE));
                        add(Component.empty());
                        add(Component.text("Requirements for level " + (currentSkillLevel + 1) + ":").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.WHITE));
                        add(Component.text((int) Math.round(Skill.Companion.getXPNeededForLevel(currentSkillLevel + 1)) + "xp").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.WHITE));
                        add(Component.text(percentOfTotalForNextLevel + "% of your total xp to level up").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.WHITE));
                        add(Component.empty());
                        add(Component.text("You are missing: ").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY));
                        if (xpToNextLevel > 0) {
                            add(Component.text(xpToNextLevel + "xp to have enough xp").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY));
                        }
                        if (percentOfTotalForNextLevel > customPlayer.getPercentOfTotal(skill.getSkillType())) {
                            add(Component.text(Math.round((percentOfTotalForNextLevel - customPlayer.getPercentOfTotal(skill.getSkillType())) * 100) / 100.0 + "% more to level up")
                                    .color(NamedTextColor.WHITE)
                                    .decoration(TextDecoration.ITALIC, false)
                                    .append(Component.text(" (" + (int) ((percentOfTotalForNextLevel / 100 * customPlayer.getTotalXp() - currentXp) / (1.0 - percentOfTotalForNextLevel / 100)) + "xp)"))
                                    .color(NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false));
                        }
                        add(Component.empty());
                        add(Component.text("Pretty awesome if you ask me!").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY));
                    }
                });
                itemStack.setItemMeta(itemMeta);
                this.getItems().put(i++, new GUIItem(itemStack, () -> {
                    new RecipesGUI(customPlayer, skill.getSkillType()).open(Bukkit.getPlayer(customPlayer.getUuid()));
                }));
            } else if (currentSkillLevel == SkillLevel.Companion.getValues().size()-1) {
                double currentXp = Math.round(skill.getXp() * 100) / 100D;
                ItemStack itemStack = new ItemStack(skill.getSkillType().getSkillWorkstation());
                ItemMeta itemMeta = itemStack.getItemMeta();
                itemMeta.addItemFlags(ItemFlag.values());
                LoreUtils.setItemDisplayName(itemMeta,Component.text(SkillType.getDisplayName(skill.getSkillType()))
                        .decoration(TextDecoration.ITALIC, false)
                        .color(NamedTextColor.WHITE));
                LoreUtils.setLore(itemMeta,new ArrayList<>() {
                    {
                        add(Component.text("A").decorations(Map.of(TextDecoration.OBFUSCATED, TextDecoration.State.TRUE, TextDecoration.ITALIC, TextDecoration.State.FALSE, TextDecoration.BOLD, TextDecoration.State.TRUE))
                                .color(NamedTextColor.WHITE)
                                .append(Component.text(SkillLevel.Companion.getDisplayName(currentSkillLevel))
                                .decorations(Map.of(TextDecoration.OBFUSCATED, TextDecoration.State.FALSE, TextDecoration.ITALIC, TextDecoration.State.FALSE, TextDecoration.BOLD, TextDecoration.State.TRUE))
                                .color(NamedTextColor.WHITE))
                                .append(Component.text("A").decorations(Map.of(TextDecoration.OBFUSCATED, TextDecoration.State.TRUE, TextDecoration.ITALIC, TextDecoration.State.FALSE, TextDecoration.BOLD, TextDecoration.State.TRUE))
                                        .color(NamedTextColor.WHITE))

                        );
                        add(Component.text("Current xp: " + (int) Math.round(currentXp)).decoration(TextDecoration.ITALIC, false).color(NamedTextColor.WHITE));
                        add(Component.empty());
                        add(Component.text("You did it!").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY));
                    }
                });
                itemStack.setItemMeta(itemMeta);
                this.getItems().put(i++, new GUIItem(itemStack, () -> {
                    new RecipesGUI(customPlayer, skill.getSkillType()).open(Bukkit.getPlayer(customPlayer.getUuid()));
                }));
            }

        }
    }

    private void defaultClassGUI(CustomPlayer customPlayer) {
        int i = 37;
        for (Skill skill : customPlayer.getSkills()) {
            int temp = i;
            double distribution = customPlayer.getGUIDistributionOfTotalLevels(skill.getSkillType());
            int currentSkillLevel = Math.min(customPlayer.getSkillLevel(skill.getSkillType()), SkillLevel.Companion.getValues().size()-1);
            for (int score = 0; score < 3; score++) {
                if((distribution * .03 - score) < 0 ) break;
                int type = Math.min((int) (distribution * .09 - score * 3), 2);
                this.getItems().put(temp-=9, makeGlassDistributionPaneSimple(SkillType.getDisplayName(skill.getSkillType()), getPaneMaterial(currentSkillLevel, type), distribution));
            }

            double currentXp = Math.round(skill.getXp() * 100) / 100D;
            double percentOfTotalForNextLevel = Math.round(SkillLevel.Companion.getSkillLevelFromInt(currentSkillLevel + 1).getXpRequirement() * 100) / 100D;
            double xpToNextLevel = Math.max(
                    Math.round((Skill.Companion.getXPNeededForLevel(currentSkillLevel + 1) - skill.getXp()) * 100) / 100D,
                    Math.round((percentOfTotalForNextLevel / 100 * customPlayer.getTotalXp() - currentXp) / (1.0 - percentOfTotalForNextLevel / 100))
            );

            ItemStack itemStack = new ItemStack(skill.getSkillType().getSkillWorkstation());
            ItemMeta itemMeta = itemStack.getItemMeta();
            itemMeta.addItemFlags(ItemFlag.values());
            LoreUtils.setItemDisplayName(itemMeta,Component.text(SkillType.getDisplayName(skill.getSkillType()))
                    .decoration(TextDecoration.ITALIC, false)
                    .color(NamedTextColor.WHITE));
            LoreUtils.setLore(itemMeta,new ArrayList<>() {
                {
                    add(Component.text(SkillLevel.Companion.getDisplayName(currentSkillLevel))
                            .decoration(TextDecoration.ITALIC, false)
                            .color(NamedTextColor.WHITE)
                            .append(Component.text("(lvl " + currentSkillLevel + ")"))
                            .color(NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    add(Component.text("Current xp: " + (int) Math.round(skill.getXp())).decoration(TextDecoration.ITALIC, false).color(NamedTextColor.WHITE));
                    add(Component.empty());
                    if(currentSkillLevel < Skill.Companion.getMAX_LEVEL()-1) {
                        add(Component.text("You are missing: ").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY));
                        if (xpToNextLevel > 0) {
                            add(Component.text(xpToNextLevel + "xp to level up").decoration(TextDecoration.ITALIC, false).color(NamedTextColor.GRAY));
                        }
                    }else add(Component.text("You are maximum level in this class."));
                }
            });
            itemStack.setItemMeta(itemMeta);
            this.getItems().put(i++, new GUIItem(itemStack, () -> {
                new RecipesGUI(customPlayer, skill.getSkillType()).open(Bukkit.getPlayer(customPlayer.getUuid()));
            }));
        }
    }
}