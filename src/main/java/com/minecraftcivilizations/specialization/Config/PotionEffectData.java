package com.minecraftcivilizations.specialization.Config;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

public record PotionEffectData(int duration, int amplifier) {
    public Map<String, Object> toMap() {
        return Map.of(
                "duration", duration,
                "amplifier", amplifier
        );
    }
    @Override
    public @NotNull String toString() {
        return String.format("{ duration: %d, amplifier: %d }", duration, amplifier);
    }
}
