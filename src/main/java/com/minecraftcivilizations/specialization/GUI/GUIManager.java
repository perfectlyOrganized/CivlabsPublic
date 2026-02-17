package com.minecraftcivilizations.specialization.GUI;

import lombok.Getter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public class GUIManager implements Listener {
    private final ArrayList<GUI> GUIs = new ArrayList<>(0);

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        List<GUI> relevantGUIs = new ArrayList<>(0);
        for (GUI gui : GUIs) {
            // Add null check to prevent NullPointerException
            if (gui.getInventory() != null && gui.getInventory().equals(event.getClickedInventory())) {
                relevantGUIs.add(gui);
                event.setCancelled(true);
            }
            if(event.getView().getTopInventory().equals(gui.getInventory())) {
                if(event.getAction().equals(InventoryAction.MOVE_TO_OTHER_INVENTORY)){
                    event.setCancelled(true);
                }
            }
        }
        for (GUI gui : relevantGUIs) {
            gui.click(event.getSlot());
        }
    }

    public GUI findGUIById(UUID id) {
        for (GUI baseGUI : GUIs) {
            if (baseGUI.getId().equals(id)) {
                return baseGUI;
            }
        }
        return null;
    }

    public GUI findGUIById(String id) {
        return findGUIById(UUID.fromString(id));
    }
}
