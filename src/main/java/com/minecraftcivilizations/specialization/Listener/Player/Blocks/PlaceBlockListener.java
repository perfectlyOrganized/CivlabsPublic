package com.minecraftcivilizations.specialization.Listener.Player.Blocks;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.CraftEngine.ItemGetterUtil;
import com.minecraftcivilizations.specialization.Data.Pair;
import com.minecraftcivilizations.specialization.player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.OpenLab;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;

public class PlaceBlockListener implements Listener {
    private static final LinkedList<String> recentPlacements = new LinkedList<>();

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Block block = event.getBlock();
        String locationKey = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();

        recentPlacements.remove(locationKey);
        recentPlacements.addFirst(locationKey);

        if (recentPlacements.size() > 50) {
            recentPlacements.removeLast();
        }

        String name = ItemGetterUtil.getItemId(event);
        Pair<SkillType, Double> pair = SkillType.getSkillXpFromConfig(SpecializationConfig.getXpGainFromPlacingConfig(), name);
        CustomPlayer customPlayer = CustomPlayerManager.INSTANCE.getCustomPlayer(playerId);
        customPlayer.addSkillXp(pair.key(), pair.value());
    }

    public static boolean wasRecentlyPlaced(Block block) {
        String locationKey = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
        return recentPlacements.remove(locationKey);
    }

}
