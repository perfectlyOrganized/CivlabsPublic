package com.minecraftcivilizations.specialization.Listener.Player.Blocks;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.CraftEngine.ItemGetterUtil;
import com.minecraftcivilizations.specialization.Data.Pair;
import com.minecraftcivilizations.specialization.player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.OpenLab;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class PlaceBlockListener implements Listener {

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Pair<SkillType, Double> pair = SkillType.getSkillXpFromConfig(SpecializationConfig.getXpGainFromPlacingConfig(), ItemGetterUtil.getItemId(event));

        if (!event.getBlock().getType().isBlock()) return;
        CustomPlayer customPlayer = CustomPlayerManager.INSTANCE.getCustomPlayer(event.getPlayer().getUniqueId());
        customPlayer.addSkillXp(pair.key(), pair.value());
    }

}
