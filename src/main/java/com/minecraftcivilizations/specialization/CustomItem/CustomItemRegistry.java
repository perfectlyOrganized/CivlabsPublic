package com.minecraftcivilizations.specialization.CustomItem;

import com.minecraftcivilizations.specialization.CustomItem.CustomItem;
import lombok.Getter;
import minecraftcivilizations.com.minecraftCivilizationsCore.Ability.CustomAbility;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CustomItemRegistry {
    @Getter
    private static final Map<NamespacedKey, CustomItem> items = new HashMap<>(0);


    public static void register(NamespacedKey namespacedKey, CustomItem item) {
        items.put(namespacedKey, item);
    }

    public static CustomItem getItem(NamespacedKey namespacedKey) {
        return items.get(namespacedKey);
    }
}
