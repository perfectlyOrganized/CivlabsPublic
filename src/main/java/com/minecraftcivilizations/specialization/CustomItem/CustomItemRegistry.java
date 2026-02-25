package com.minecraftcivilizations.specialization.CustomItem;

import lombok.Getter;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;

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
