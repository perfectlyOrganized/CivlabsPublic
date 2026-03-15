package com.minecraftcivilizations.specialization.GUI

import com.minecraftcivilizations.specialization.Config.SpecializationConfig
import com.minecraftcivilizations.specialization.Skill.SkillType
import net.kyori.adventure.text.Component
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.function.Consumer

class BooleanSelectionGUI : GUI {

    // Secondary constructor as the main entry point
    constructor(title: Component, options: MutableMap<GUIPlaceOption, Boolean>, callback: Consumer<Boolean>) :
            super(title, 9) {
        val yes = ItemStack(Material.GREEN_WOOL)
        val yesMeta = yes.itemMeta ?: return
        yesMeta.setDisplayName(ChatColor.RED.toString() + "Accept")
        yes.itemMeta = yesMeta

        items[3] = GUIItem(yes) {
            callback.accept(true)
            closeGUI()
        }
        val no = ItemStack(Material.RED_WOOL)
        val noMeta = no.itemMeta ?: return
        noMeta.setDisplayName(ChatColor.RED.toString() + "Decline")
        no.itemMeta = noMeta
        items[5] = GUIItem(no) {
            callback.accept(false)
            closeGUI()
        }

    }
}