package com.minecraftcivilizations.specialization.Player;


import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.NamespacedKey;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class CustomPlayerBase {
    @Getter
    @Setter
    private UUID uuid;
    @Getter
    @Setter
    private UUID currentGUI;
    @Getter
    @Setter
    private UUID nextGUI;
    private String name;
    @Getter
    @Setter
    private Map<NamespacedKey, Long> abilitiesCastHistory = new HashMap<>();

    public CustomPlayerBase(UUID uuid) {
        this.uuid = uuid;
    }

    public Component getName() {
        return GsonComponentSerializer.gson().deserialize(name);
    }

    public void setName(Component name) {
        assert name != null;
        this.name = GsonComponentSerializer.gson().serialize(name);
    }

}