package com.minecraftcivilizations.specialization.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class ComponentUtils {

    public static String serializeComponent(final Component component) {
        return GsonComponentSerializer.gson().serialize(component);
    }

    public static Component deserializeComponent(final String serializedComponent) {
        return GsonComponentSerializer.gson().deserialize(serializedComponent);
    }

    public static String serializeComponentAsStringWithStrip(final Component component) {
        return serializeComponentAsString(component).replaceAll("§[0-9a-fklmnor]", "");
    }
    public static String serializeComponentAsString(final Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    public static Component deserializeStringAsComponent(final String serializedComponent) {
        return LegacyComponentSerializer.legacySection().deserialize(serializedComponent);
    }
}
