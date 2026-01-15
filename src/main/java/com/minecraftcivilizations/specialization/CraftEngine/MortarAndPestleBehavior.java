package com.minecraftcivilizations.specialization.CraftEngine;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillLevel;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.typesafe.config.Config;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptors;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.block.entity.SimpleStorageBlockEntity;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.nms.FastNMS;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.item.BuildableItem;
import net.momirealms.craftengine.core.item.CustomItem;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.ItemBuildContext;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.item.context.UseOnContext;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import net.momirealms.craftengine.core.world.World;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.nio.file.Path;
import java.util.*;

public class MortarAndPestleBehavior extends ItemBehavior  {
    public static final Factory FACTORY = new Factory();
    public static class Factory implements ItemBehaviorFactory {
        @Override
        public ItemBehavior create(Pack pack, Path path, String node, Key key, Map<String, Object> arguments) {
            return new MortarAndPestleBehavior();
        }
    }
    private Optional<ItemStack> getItem(Key itemId) {
        if (Objects.equals(itemId.namespace(), "minecraft")) {
            Material material = Material.valueOf(itemId.value().toUpperCase());
            return Optional.of(new ItemStack(material));
        }
        CustomItem<ItemStack> item = CraftEngineItems.byId(itemId);
        if (item != null) return Optional.empty();
        return Optional.of(item.buildItemStack());
    }
    private String getItemId(ItemStack item) {
        if (CraftEngineItems.isCustomItem(item)) {
            var ceItem = CraftEngineItems.getCustomItemId(item);
            if (ceItem != null) return ceItem.toString().toUpperCase(Locale.ROOT).replace(":","_");
        }
        return item.getType().key().value().toUpperCase(Locale.ROOT);
    }
    public InteractionResult useOnBlock(UseOnContext context) {
        Object blockState = FastNMS.INSTANCE.method$BlockGetter$getBlockState(
                context.getLevel().serverWorld(),
                LocationUtils.toBlockPos(context.getClickedPos())
        );
        // Check if it's a custom block
        Optional<ImmutableBlockState> optionalCustomState = BlockStateUtils.getOptionalCustomBlockState(blockState);
        if (optionalCustomState.isEmpty()) {
            return InteractionResult.PASS; // Not a custom block
        }

        // Check if it's specifically a mortar block
        ImmutableBlockState customState = optionalCustomState.get();
        if (customState.owner().value().id().equals(Key.of("specialization:mortar"))) {
            CEWorld world = context.getLevel().storageWorld();
            BlockPos blockPos = context.getClickedPos();
            BlockEntity blockEntity = world.getBlockEntityAtIfLoaded(blockPos);

            if (!(blockEntity instanceof SimpleStorageBlockEntity mortarEntity)) {
                return InteractionResult.PASS;
            }

            Inventory inventory = mortarEntity.inventory();
            if (inventory == null) {
                return InteractionResult.PASS;
            }
            CustomPlayer customPlayer = CoreUtil.getPlayer(context.getPlayer().uuid());
            Player player = (Player) context.getPlayer().platformPlayer();
            // Check all 9 slots for transformable items
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                ItemStack item = inventory.getItem(slot);
                if (item != null && !item.getType().isAir()) {
                    for (SkillType skill : SkillType.values()) {
                        Config conversions = SpecializationConfig.getGrindConfig().getObject(skill.name());
                        if (!conversions.hasPath(getItemId(item))) continue;
                        Config conversion = conversions.getObject(getItemId(item)).toConfig();
                        Key id = Key.of(conversion.getString("item"));
                        int foodCost = conversion.getInt("foodCost");
                        int amount = conversion.getInt("amount");
                        int xp = conversion.getInt("xp");
                        int level = conversion.getInt("level");

                        if (customPlayer.getSkillLevel(skill) < level) continue;
                        // Transform the item
                        Optional<ItemStack> itemStack = getItem(id);
                        if (itemStack.isPresent()) {
                            ItemStack originalItem = inventory.getItem(slot);
                            int newAmount = originalItem.getAmount() - 1;
                            inventory.removeItemAnySlot(originalItem);
                            if (newAmount <= 0) {
                                inventory.setItem(slot, null);
                            } else {
                                originalItem.setAmount(newAmount);
                                inventory.setItem(slot, originalItem);
                            }

                            ItemStack newItem = itemStack.get().clone();
                            newItem.setAmount(amount);
                            boolean added = inventory.addItem(newItem).isEmpty();

                            if (!added) {
                                inventory.setItem(slot, originalItem);
                                return InteractionResult.PASS;
                            }

                            if (xp != 0) {
                                int finalReduction = Math.max(foodCost, 1);
                                Bukkit.getScheduler().runTaskLater(Specialization.getInstance(), () -> {
                                    if (player.isOnline()) {
                                        player.setFoodLevel(player.getFoodLevel() - finalReduction);
                                        customPlayer.addSkillXp(skill, xp);
                                    }
                                }, 1L);
                            }
                            return InteractionResult.SUCCESS;
                        }
                    }

                }
            }
        }

        return InteractionResult.PASS; // Not a mortar block
    }
}
