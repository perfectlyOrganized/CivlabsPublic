package com.minecraftcivilizations.specialization.GUI

import com.minecraftcivilizations.specialization.Config.SpecializationConfig
import com.minecraftcivilizations.specialization.Skill.SkillType
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.function.Consumer

class ClassSelectionGUI : GUI {

    // Secondary constructor as the main entry point
    constructor(title: Component, options: MutableMap<GUIPlaceOption, Boolean>, callback: Consumer<SkillType>) :
            super(title, 9) {

        for ((index, skillType) in SkillType.entries.withIndex()) {
            val classType = SpecializationConfig.skillsConfig.getString(skillType.name.uppercase() + "_WORKSTATION")
            val itemStack = ItemStack(Material.valueOf(classType!!))

            this.items[index + 2] = GUIItem(itemStack) {
                callback.accept(skillType)
                closeGUI()
            }
        }
    }
}