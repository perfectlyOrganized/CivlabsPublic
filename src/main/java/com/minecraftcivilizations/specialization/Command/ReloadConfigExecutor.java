package com.minecraftcivilizations.specialization.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import com.minecraftcivilizations.specialization.Config.ConfigFilesManager;
import org.bukkit.entity.Player;

@CommandAlias("reloadconfig")
@CommandPermission("core.reload")
public class ReloadConfigExecutor extends BaseCommand {

    @Default
    @CommandPermission("core.reload")
    public void onReloadConfig(Player player) {
        if(player.isOp()) {
            ConfigFilesManager.reloadConfigFiles();
        }
    }
}
