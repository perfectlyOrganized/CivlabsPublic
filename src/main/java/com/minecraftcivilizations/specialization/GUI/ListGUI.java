package com.minecraftcivilizations.specialization.GUI;

import com.minecraftcivilizations.specialization.util.ComponentUtils;
import com.minecraftcivilizations.specialization.util.ItemStackUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ListGUI extends GUI {
    List<ItemStack> show = new ArrayList<>();

    public ListGUI(Component title, ArrayList<ItemStack> items) {
        super(title, 54, Map.of(GUIPlaceOption.SHOULD_PLACE_EXIT, true, GUIPlaceOption.SHOULD_PLACE_SEARCH, true));
        show.addAll(items);
        int itemCount = 0;
        for (int placementIndex = 10; placementIndex < 44; placementIndex++) {
            if (placementIndex % 9 != 0 && placementIndex % 9 != 8 && itemCount < items.size()) {
                if(items.get(itemCount).getType().isItem() && items.get(itemCount).getType() != Material.AIR) {
                    this.getItems().put(placementIndex, ItemStackUtils.makeItemGUIItem(items.get(itemCount), ItemStackUtils.getFriendlyName(items.get(itemCount).getType())));
                }
                itemCount++;
            }
        }
        if (items.size() > getSize() - (18 + (getSize()/9 - 2) * 2)) {
            this.getItems().put(getSize() - 1, new GUIItem(ItemStackUtils.makeGUIItemOfType(Material.ARROW, "Next").getItem(), () -> next((Player) getInventory().getViewers().get(0))));
        }
    }

    public ListGUI(Component title, ArrayList<ItemStack> items, Map<GUIPlaceOption, Boolean> options) {
        super(title, 54, options);
        show.addAll(items);
        int itemCount = 0;
        for (int placementIndex = 10; placementIndex < 44; placementIndex++) {
            if (placementIndex % 9 != 0 && placementIndex % 9 != 8 && itemCount < items.size()) {
                ItemStack item = items.get(itemCount);
                if(item.getType().isItem() && item.getType() != Material.AIR) {
                    String friendlyName = null;
                    if(item.hasItemMeta() && item.getItemMeta().getDisplayName()!=null) {
                        friendlyName = item.getItemMeta().getDisplayName(); //converts the item's display name to a uniform style
                    }else{
                        friendlyName = ItemStackUtils.getFriendlyName(item.getType()); // If an item already has a name, pass it in as null. it will be handled.
                    }
                    this.getItems().put(placementIndex, ItemStackUtils.makeItemGUIItem(item, friendlyName));
                }
                itemCount++;
            }
        }
        if (items.size() > getSize() - (18 + (getSize()/9 - 2) * 2)) {
            this.getItems().put(getSize() - 1, new GUIItem(ItemStackUtils.makeGUIItemOfType(Material.ARROW, "Next").getItem(), () -> next((Player) getInventory().getViewers().get(0))));
        }
    }

    @Override
    public void open(Player player) {
        if (getParentGUI() != null) {
            if (getParentGUI() instanceof ListGUI && (getOptions().get(GUIPlaceOption.SHOULD_PLACE_BACK) == null || !getOptions().get(GUIPlaceOption.SHOULD_PLACE_BACK))) {
                this.getItems().put(getSize() - 9, new GUIItem(ItemStackUtils.makeGUIItemOfType(Material.ARROW, "Back").getItem(), () -> getParentGUI().open((Player) getInventory().getViewers().get(0))));
            } else {
                getOptions().put(GUIPlaceOption.SHOULD_PLACE_BACK, true);
            }
        }
        super.open(player);
    }

    public void next(Player player) {
        ArrayList<ItemStack> items = new ArrayList<>();
        for (int i = getSize() - (18 + (getSize()/9 - 2) * 2); i < show.size(); i++) {
            items.add(show.get(i));
//            if (i > getSize() - (18 + (getSize()/9 - 2) * 2)) {
//            }
        }
        new ListGUI(Component.text("Search Results"), items, getOptions()).setParentGUI(this).open(player);
    }
}
