package com.minecraftcivilizations.specialization.Reinforcement;

import org.bukkit.Material;
import org.bukkit.util.Vector;

public record Reinforcement(Vector location, ReinforcementType type, Material plankMaterial, long expirationTick) {

    // Constructor for backwards compatibility with existing light/heavy reinforcements
    public Reinforcement(Vector location, boolean isHeavy) {
        this(location, isHeavy ? ReinforcementType.HEAVY : ReinforcementType.LIGHT, null, -1L);
    }

    // Constructor for wooden reinforcements
    public Reinforcement(Vector location, Material plankMaterial, long expirationTick) {
        this(location, ReinforcementType.WOODEN, plankMaterial, expirationTick);
    }

    public boolean isHeavy() {
        return type == ReinforcementType.HEAVY;
    }

    public boolean isLight() {
        return type == ReinforcementType.LIGHT;
    }

    public boolean isWooden() {
        return type == ReinforcementType.WOODEN;
    }

    public boolean isExpired(long currentTick) {
        return type == ReinforcementType.WOODEN && expirationTick > 0 && currentTick >= expirationTick;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reinforcement r)) return false;
        return location.getBlockX() == r.location.getBlockX() &&
               location.getBlockY() == r.location.getBlockY() &&
               location.getBlockZ() == r.location.getBlockZ();
    }

    @Override
    public int hashCode() {
        return location.hashCode();
    }
}
