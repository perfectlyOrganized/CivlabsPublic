package com.minecraftcivilizations.specialization.GUI

import com.minecraftcivilizations.specialization.Config.SpecializationConfig
import com.minecraftcivilizations.specialization.Skill.SkillType
import net.kyori.adventure.text.Component
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.function.Consumer

class ClassSelectionGUI : GUI {

    // Secondary constructor as the main entry point
    constructor(title: Component, options: MutableMap<GUIPlaceOption, Boolean>, callback: Consumer<SkillType>) :
            super(title, 9) {

        for ((index, skillType) in SkillType.entries.withIndex()) {
            val itemStack = ItemStack(skillType.skillWorkstation)
            val meta = itemStack.itemMeta ?: continue
            meta.setDisplayName(ChatColor.GOLD.toString() + skillType.displayName)
            itemStack.itemMeta = meta
            this.items[index + 1] = GUIItem(itemStack) {
                callback.accept(skillType)
                closeGUI()
            }
        }
    }
}