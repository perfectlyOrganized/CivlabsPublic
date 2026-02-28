package com.minecraftcivilizations.specialization.GUI;

import com.minecraftcivilizations.specialization.OpenLab;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.player.CustomPlayer;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import com.minecraftcivilizations.specialization.util.LoreUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;
import java.util.function.Consumer;

public class SettingsGUI extends GUI {
    public SettingsGUI() {
        super(Component.text("Settings").color(NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false), 27,
                Map.of(GUIPlaceOption.SHOULD_PLACE_EXIT, false, GUIPlaceOption.SHOULD_PLACE_BACK, true));

    }

    @Override
    public void open(Player player) {
        CustomPlayer customPlayer = CustomPlayerManager.INSTANCE.getCustomPlayerOrThrow(player.getUniqueId());

        addSettingItem(10, customPlayer.isAdvancedClassesGUIEnabled(),
                "Toggle Advanced Class GUI",
                () -> customPlayer.setAdvancedClassesGUIEnabled(!customPlayer.isAdvancedClassesGUIEnabled()), player);

        addSettingItem(11, customPlayer.isNewRecipeGUIIteration(),
                "Toggle The Different Recipes GUI",
                () -> customPlayer.setNewRecipeGUIIteration(!customPlayer.isNewRecipeGUIIteration()), player);

        addSettingItem(12, customPlayer.isSoundEnabled(),
                "Toggle XP gain sound",
                () -> customPlayer.setSoundEnabled(!customPlayer.isSoundEnabled()), player);
        if (customPlayer.getClassXPToggles( ) != null) {

        Boolean builderXP = customPlayer.getClassXPToggles().getOrDefault(SkillType.BUILDER, true);
        Boolean blacksmithXP = customPlayer.getClassXPToggles().getOrDefault(SkillType.BLACKSMITH, true);
        Boolean minerXP = customPlayer.getClassXPToggles().getOrDefault(SkillType.MINER, true);
        Boolean farmerXP = customPlayer.getClassXPToggles().getOrDefault(SkillType.FARMER, true);
        Boolean healerXP = customPlayer.getClassXPToggles().getOrDefault(SkillType.HEALER, true);
        Boolean guardsmanXP = customPlayer.getClassXPToggles().getOrDefault(SkillType.GUARDSMAN, true);

        addSettingItem(1, builderXP,
                "Toggle Builder XP",
                () -> customPlayer.setClassXPToggles(SkillType.BUILDER, !builderXP), player);

//        addSettingItem(2, blacksmithXP,
//                "Toggle Blacksmith XP",
//                () -> customPlayer.setClassXPToggles(SkillType.BLACKSMITH, !blacksmithXP), player);
//
//        addSettingItem(3, minerXP,
//                "Toggle Miner XP",
//                () -> customPlayer.setClassXPToggles(SkillType.MINER, !minerXP), player);
//
//        addSettingItem(5, farmerXP,
//                "Toggle Farmer XP",
//                () -> customPlayer.setClassXPToggles(SkillType.FARMER, !farmerXP), player);
//
//        addSettingItem(6, healerXP,
//                "Toggle Healer XP",
//                () -> customPlayer.setClassXPToggles(SkillType.HEALER, !healerXP), player);

        addSettingItem(7, guardsmanXP,
                "Toggle Guardsman XP",
                () -> customPlayer.setClassXPToggles(SkillType.GUARDSMAN, !guardsmanXP), player);

        }

        super.open(player);

    }

    private void addSettingItem(int slot, boolean enabled, String displayName, Runnable toggleAction, Player player) {
        ItemStack settingItem = new ItemStack(enabled ? Material.getMaterial("NATURALIST_VERDANT_FROGLASS_PANE") : Material.getMaterial("NATURALIST_CRIMSON_FROGLASS_PANE"));
        ItemMeta meta = settingItem.getItemMeta();

        meta.addItemFlags(ItemFlag.values());
        LoreUtils.setItemDisplayName(meta, Component.text(displayName)
                .decoration(TextDecoration.ITALIC, false)
                .color(NamedTextColor.WHITE));

        settingItem.setItemMeta(meta);

        this.getItems().put(slot, new GUIItem(settingItem, () -> {
            toggleAction.run();
            getItems().clear();
            SettingsGUI.this.open(player);
        }));
    }
}




