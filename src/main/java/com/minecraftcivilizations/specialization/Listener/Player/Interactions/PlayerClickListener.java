package com.minecraftcivilizations.specialization.Listener.Player.Interactions;

import com.minecraftcivilizations.specialization.CustomItem.ability.AbilityCastEvent;
import com.minecraftcivilizations.specialization.CustomItem.ability.CustomAbility;
import com.minecraftcivilizations.specialization.CustomItem.CustomItem;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerClickListener implements Listener {

    private final Map<UUID, Long> lastClickTick = new HashMap<>();

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        ItemStack inHand = event.getPlayer().getInventory().getItemInMainHand();

        // Ignore air or non-custom items
        if (inHand.getType().isAir() || !CustomItem.isCustomItem(inHand)) {
            return;
        }

        UUID uuid = event.getPlayer().getUniqueId();
        long currentTick = event.getPlayer().getWorld().getFullTime();
        if (lastClickTick.getOrDefault(uuid, -1L) == currentTick) return; // prevent double fire
        lastClickTick.put(uuid, currentTick);

        CustomItem from = CustomItem.from(inHand);

        for (CustomAbility ability : from.getAbilities()) {
            AbilityCastEvent cast = ability.getCastEvent();

            // Check for left clicks (RIGHT_CLICK_AIR/BLOCK are right clicks, everything else is left)
            boolean isLeftClick = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
            boolean isRightClick = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
            boolean isSneaking = event.getPlayer().isSneaking();

            if (isLeftClick && !isSneaking && cast == AbilityCastEvent.LEFT_CLICK)
                ability.getAbilityFunction().accept(event.getPlayer());

            else if (isLeftClick && isSneaking && cast == AbilityCastEvent.SNEAK_LEFT_CLICK)
                ability.getAbilityFunction().accept(event.getPlayer());

            else if (isRightClick && !isSneaking && cast == AbilityCastEvent.RIGHT_CLICK)
                ability.getAbilityFunction().accept(event.getPlayer());

            else if (isRightClick && isSneaking && cast == AbilityCastEvent.SNEAK_RIGHT_CLICK)
                ability.getAbilityFunction().accept(event.getPlayer());
        }
    }
}