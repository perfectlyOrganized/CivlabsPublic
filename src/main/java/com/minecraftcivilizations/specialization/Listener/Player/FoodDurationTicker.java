package com.minecraftcivilizations.specialization.Listener.Player;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Listener.Player.Interactions.FoodInteractionListener;
import com.minecraftcivilizations.specialization.Specialization;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class FoodDurationTicker implements Listener {
    private final boolean enabled = false;;

    public FoodDurationTicker() {

    }

    private int getCurrentTime() {
        World world = Bukkit.getWorlds().get(0);
        return (int) (world.getFullTime());
    }
    private final NamespacedKey CREATED_AT_KEY = new NamespacedKey(Specialization.getInstance(), "created_at");
    private final NamespacedKey EXPIRATION_KEY = new NamespacedKey(Specialization.getInstance(), "expiration");
    private boolean isValid(ItemStack item) {
        return item != null && item.getType() != Material.AIR && !FoodInteractionListener.isBlessedFood(item);
    }





    private boolean isAir(ItemStack item) {
        return item == null || item.getType().isAir();
    }

}

