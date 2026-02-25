package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import com.minecraftcivilizations.specialization.GUI.RecipesGUI;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import org.bukkit.entity.Player;

@CommandAlias("recipes")
public class RecipesCommand extends BaseCommand {

    @Default
    public void onSendCommand(Player sender) {
        new RecipesGUI(CustomPlayerManager.INSTANCE.getCustomPlayer(sender), null).open(sender);
    }

}
