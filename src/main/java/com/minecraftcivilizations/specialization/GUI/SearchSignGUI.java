package com.minecraftcivilizations.specialization.GUI;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.WrappedRegistrable;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;
import com.comphenix.protocol.wrappers.nbt.NbtList;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


public class SearchSignGUI {
    // Track players who have opened custom search signs
    private static final Set<UUID> playersWithActiveSearchSign = new HashSet<>();

    public static void openSearch(Player player) {
        // Mark this player as having an active search sign
        playersWithActiveSearchSign.add(player.getUniqueId());

        // Define a dummy block position (does not exist in the world)
        BlockPosition blockPosition = new BlockPosition((int) player.getLocation().getX(), (int) player.getLocation().getY() - 2, (int) player.getLocation().getZ());

        // Create the packet

        player.sendBlockChange(blockPosition.toLocation(player.getWorld()), Material.OAK_SIGN.createBlockData());

        PacketContainer openSign = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.OPEN_SIGN_EDITOR);
        PacketContainer signData = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.TILE_ENTITY_DATA);

        openSign.getBlockPositionModifier().write(0, blockPosition);

        NbtCompound signNBT = (NbtCompound) signData.getNbtModifier().read(0);
        NbtCompound frontText = NbtFactory.ofCompound("front_text");
        NbtCompound value = NbtFactory.ofCompound("");
        NbtList<String> messages = NbtFactory.ofList("messages");
        messages.add("");
        messages.add("");
        messages.add("Enter search term:");
        messages.add("----------------");
        value.put(messages);
        frontText.put(messages);
        signNBT.put(frontText);

        signData.getBlockPositionModifier().write(0, blockPosition);
        signData.getBlockEntityTypeModifier().write(0, WrappedRegistrable.blockEntityType("sign"));
        signData.getNbtModifier().write(0, signNBT);

        // Send the "open sign editor" packet
        PacketContainer openSignPacket = ProtocolLibrary.getProtocolManager().createPacket(PacketType.Play.Server.OPEN_SIGN_EDITOR);
        openSignPacket.getBlockPositionModifier().write(0, blockPosition);
        openSignPacket.getBooleans().write(0, true);

        ProtocolLibrary.getProtocolManager().sendServerPacket(player, signData);
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, openSignPacket);
    }

    public static ArrayList<ItemStack> searchItems(String search) {
        ArrayList<ItemStack> materials = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isItem() && material != Material.AIR) {
                materials.add(new ItemStack(material));
            }
        }
        return materials;
    }

    public static ArrayList<ItemStack> searchItemsAndBlocks(String search) {
        ArrayList<ItemStack> materials = new ArrayList<>();
        for (Material material : Material.values()) {
            if ((material.isItem() || (material.isBlock() && material.isItem()))
                    && material != Material.AIR
                    && material.name().toUpperCase().contains(search.toUpperCase())) {
                materials.add(new ItemStack(material));
            }
        }
        return materials;
    }
//    public static ArrayList<ItemStack> searchMobs(String search) {
//        ArrayList<ItemStack> materials = new ArrayList<>();
//        for (EntityType material : EntityType.values()) {
//            if (material.isAlive() && material != EntityType.PLAYER) {
//                materials.add(ItemUtils.makeGUIItemOfType(Material.NAME_TAG, MobUtils.getFriendlyName(material)));
//            }
//        }
//        return materials;
//    }

    public static ArrayList<ItemStack> searchBlocks(String search) {
        ArrayList<ItemStack> materials = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isItem() && material.isBlock() && material != Material.AIR) {
                materials.add(new ItemStack(material));
            }
        }
        return materials;
    }

    /**
     * Check if a player has an active search sign
     */
    public static boolean hasActiveSearchSign(UUID playerUUID) {
        return playersWithActiveSearchSign.contains(playerUUID);
    }

    /**
     * Remove a player from the active search sign tracking
     */
    public static void clearActiveSearchSign(UUID playerUUID) {
        playersWithActiveSearchSign.remove(playerUUID);
    }
}
