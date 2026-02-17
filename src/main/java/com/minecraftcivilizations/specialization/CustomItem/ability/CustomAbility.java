package com.minecraftcivilizations.specialization.CustomItem.ability;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

@Getter
@Setter
@NoArgsConstructor
public class CustomAbility {
    private String name;
    private String description;
    private int cooldown;
    private Consumer<Player> abilityFunction;
    private AbilityCastEvent castEvent;
}
