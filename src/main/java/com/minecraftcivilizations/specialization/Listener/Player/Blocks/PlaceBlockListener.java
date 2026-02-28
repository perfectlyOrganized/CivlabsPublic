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
    private final Map<UUID, LinkedList<String>> recentPlacements = new HashMap<>();

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Block block = event.getBlock();
        String locationKey = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();

        LinkedList<String> history = recentPlacements.getOrDefault(playerId, new LinkedList<>());
        if (history.contains(locationKey)) {
            return;
        }

        history.addFirst(locationKey);
        if (history.size() > 10) {
            history.removeLast();
        }
        recentPlacements.put(playerId, history);

        String name = ItemGetterUtil.getItemId(event);
        Pair<SkillType, Double> pair = SkillType.getSkillXpFromConfig(SpecializationConfig.getXpGainFromPlacingConfig(), name);
        CustomPlayer customPlayer = CustomPlayerManager.INSTANCE.getCustomPlayer(playerId);
        customPlayer.addSkillXp(pair.key(), pair.value());
    }

}
