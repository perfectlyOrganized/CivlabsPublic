package com.minecraftcivilizations.specialization.CustomItem.ability;

import lombok.Getter;
import net.kyori.adventure.key.Namespaced;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

public class CustomItemAbilityRegistry {
    @Getter
    private static final Map<NamespacedKey, CustomAbility> abilities = new HashMap<>(0);

    public static void register(@NotNull NamespacedKey namespacedKey, @NotNull CustomAbility ability) {
        abilities.put(namespacedKey, ability);
    }

    public static CustomAbility getAbility(@NotNull NamespacedKey namespacedKey) {
        return abilities.get(namespacedKey);
    }
}
