package com.minecraftcivilizations.specialization.GUI;

import com.minecraftcivilizations.specialization.OpenLab;
import com.minecraftcivilizations.specialization.player.CustomPlayerManager;
import com.minecraftcivilizations.specialization.util.ComponentUtils;
import com.minecraftcivilizations.specialization.util.ItemStackUtils;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

@Getter
public abstract class GUI {
    private final Map<Integer, GUIItem> items = new HashMap<>();
    private final Map<GUIPlaceOption, Boolean> options = new HashMap<>();
    private final Component title;
    private final UUID id = UUID.randomUUID();
    private final int size;
    private GUI parentGUI;
    private GUI childGUI;
    private Inventory inventory;

    public GUI(Component title, int size, Map<Integer, GUIItem> items, Map<GUIPlaceOption, Boolean> options) {
        this.title = title;
        this.size = size;
        this.items.putAll(items);
        this.options.putAll(options);
        if (items.size() == size) return;
        fillEmptySlots();
        OpenLab.guiManager.getGUIs().add(this);
    }

    public GUI(Component title, int size, Map<GUIPlaceOption, Boolean> options) {
        this.title = title;
        this.size = size;
        this.options.putAll(options);
        if (items.size() == size) return;
        fillEmptySlots();
        OpenLab.guiManager.getGUIs().add(this);
    }

    public GUI(Component title, int size) {
        this.title = title;
        this.size = size;
        if (size == 0) return;
        fillEmptySlots();
        OpenLab.guiManager.getGUIs().add(this);
    }

    public void open(Player player) {
        if (!options.isEmpty()) {
            for (Map.Entry<GUIPlaceOption, Boolean> option : options.entrySet()) {
                placeOption(option);
            }
        }
        inventory = Bukkit.createInventory(null, size, ComponentUtils.serializeComponentAsString(title));
        items.forEach((key, value) -> {
            if (value != null) {
                inventory.setItem(key, value.getItem());
            }
        });
        player.openInventory(inventory);
    }

    public void closeGUI() {
        removeGUI();
        inventory.getViewers().get(0).closeInventory();
    }

    private void removeGUI() {
        if (getParentGUI() != null) {
            getParentGUI().removeGUI();
        }
        if (getChildGUI() != null) {
            getChildGUI().removeGUI();
        }
    }

    public void click(int index) {
        if (items.get(index) == null || items.get(index).getOnClick() == null) return;
        items.get(index).getOnClick().run();
    }

    private void fillEmptySlots() {
        for (int i = 0; i < size; i++) {
            if (!this.items.containsKey(i)) {
                this.items.put(i, ItemStackUtils.makeGUIItemOfType(Material.BLACK_STAINED_GLASS_PANE, ""));
            }
        }
    }

    private void placeOption(Map.Entry<GUIPlaceOption, Boolean> option) {
        if (option.getKey() == GUIPlaceOption.SHOULD_PLACE_BACK && option.getValue()) {
            if (this.parentGUI == null) return;
            GUIItem back = ItemStackUtils.makeGUIItemOfType(Material.ARROW, "Back");
            back.setOnClick(new Runnable() {
                @Override
                public void run() {
                    parentGUI.open((Player) inventory.getViewers().get(0));
                }
            });
            this.items.put(this.size - 6, back);
        } else if (option.getKey() == GUIPlaceOption.SHOULD_PLACE_EXIT && option.getValue()) {
            GUIItem exit = ItemStackUtils.makeGUIItemOfType(Material.valueOf("CREATEDECO_DECAL_CROSS"), "Exit");
            exit.setOnClick(new Runnable() {
                @Override
                public void run() {
                    closeGUI();
                }
            });
            this.items.put(8, exit);
        } else if (option.getKey() == GUIPlaceOption.SHOULD_PLACE_SEARCH && option.getValue()) {
            GUIItem search = ItemStackUtils.makeGUIItemOfType(Material.OAK_SIGN, "Search");
            search.setOnClick(() -> {
                CustomPlayerManager.INSTANCE.getCustomPlayer((inventory.getViewers().get(0)).getUniqueId()).setCurrentGUI(GUI.this.id);
                SearchSignGUI.openSearch((Player) inventory.getViewers().get(0));
            });
            this.items.put(4, search);
        } else if (option.getKey() == GUIPlaceOption.SHOULD_PLACE_NEXT && option.getValue()) {
            GUIItem next = ItemStackUtils.makeGUIItemOfType(Material.ARROW, "Next");
            next.setOnClick(() -> childGUI.open((Player) inventory.getViewers().get(0)));
            this.items.put(this.size - 1, next);
        }
    }

    public GUI setParentGUI(GUI parentGUI) {
        this.parentGUI = parentGUI;
        return this;
    }

    public GUI setChildGUI(GUI childGUI) {
        this.childGUI = childGUI;
        return this;
    }
}