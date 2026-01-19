package com.minecraftcivilizations.specialization.CraftEngine;

import com.minecraftcivilizations.specialization.Config.SpecializationConfig;
import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import com.minecraftcivilizations.specialization.Specialization;
import com.minecraftcivilizations.specialization.util.CoreUtil;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.bukkit.block.entity.SimpleStorageBlockEntity;
import net.momirealms.craftengine.bukkit.nms.FastNMS;
import net.momirealms.craftengine.bukkit.util.BlockStateUtils;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.entity.BlockEntity;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.item.CustomItem;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.item.context.UseOnContext;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.CEWorld;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Path;
import java.util.*;

public class MortarAndPestleBehavior extends ItemBehavior  {
    public static final Factory FACTORY = new Factory();
    private final int cooldownTime;
    public static class Factory implements ItemBehaviorFactory {
        @Override
        public ItemBehavior create(Pack pack, Path path, String node, Key key, Map<String, Object> arguments) {
            int cooldownTime = (int) arguments.getOrDefault("cooldown-time", 20);
            return new MortarAndPestleBehavior(cooldownTime);
        }
    }
    public MortarAndPestleBehavior(int cooldownTime) {
        this.cooldownTime = cooldownTime;
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
        Item<?> pestle = context.getItem();
        if (!(pestle.getItem() instanceof ItemStack pestleStack)) return InteractionResult.PASS;
        // Check if it's specifically a mortar block
        ImmutableBlockState customState = optionalCustomState.get();

        if (!customState.owner().value().id().equals(Key.of("specialization:mortar"))) return InteractionResult.PASS;
        CEWorld world = context.getLevel().storageWorld();
        BlockPos blockPos = context.getClickedPos();
        BlockEntity blockEntity = world.getBlockEntityAtIfLoaded(blockPos);

        if (!(blockEntity instanceof SimpleStorageBlockEntity mortarEntity && mortarEntity.inventory() != null)) return InteractionResult.PASS;

        Inventory inventory = mortarEntity.inventory();

        CustomPlayer customPlayer = CoreUtil.getPlayer(context.getPlayer().uuid());
        Player player = (Player) context.getPlayer().platformPlayer();
        if (player.hasCooldown(pestleStack)) return InteractionResult.PASS;
        // Check all 9 slots for transformable items
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            for (SkillType skill : SkillType.values()) {
                Config conversionTypes = SpecializationConfig.getGrindConfig().getObject(skill.name());
                if (!conversionTypes.hasPath(getItemId(item))) continue;


                List<? extends ConfigObject> conversions = conversionTypes.getObjectList(getItemId(item));
                Config conversion;
                for (ConfigObject conversionObject : conversions) {
                    conversion = conversionObject.toConfig();

                    Key id = Key.of(conversion.getString("item"));
                    double foodCost = conversion.hasPath("foodCost") ? conversion.getDouble("foodCost") : 1;
                    double chance = conversion.hasPath("chance") ? conversion.getDouble("chance") : 1;
                    int amount = conversion.hasPath("amount") ? conversion.getInt("amount") : 1;
                    int xp = conversion.hasPath("xp") ? conversion.getInt("xp") : 0;
                    int level = conversion.hasPath("level") ? conversion.getInt("level") : 0;

                    if (customPlayer.getSkillLevel(skill) < level) continue;
                    boolean isSuccess = Math.random() < chance;
                    player.setCooldown(pestleStack.getType(), (int) (cooldownTime * Math.min(2.0 / customPlayer.getSkillLevel(skill), 1.0)));

                    if (!isSuccess) continue;
                    pestle.hurtAndBreak(1, null, null);

                    ItemStack originalItem = inventory.getItem(slot);
                    if (originalItem == null) continue;

                    double foodCostDecimals = foodCost % 1;
                    int foodCostResult = Math.floor(foodCost) + Math.random() > foodCostDecimals ? 0 : 1;

                    int newAmount = originalItem.getAmount() - 1;
                    inventory.removeItemAnySlot(originalItem);
                    if (newAmount <= 0) {
                        inventory.setItem(slot, null);
                    } else {
                        originalItem.setAmount(newAmount);
                        inventory.setItem(slot, originalItem);
                    }

                    Optional<ItemStack> itemStack = getItem(id);
                    if (itemStack.isEmpty()) continue;

                    ItemStack newItem = itemStack.get().clone();
                    newItem.setAmount(amount);
                    boolean added = inventory.addItem(newItem).isEmpty();

                    if (!added) {
                        inventory.setItem(slot, originalItem);
                        return InteractionResult.PASS;
                    }

                    Bukkit.getScheduler().runTaskLater(Specialization.getInstance(), () -> {
                        if (player.isOnline()) {
                            player.setFoodLevel(player.getFoodLevel() - foodCostResult);
                            customPlayer.addSkillXp(skill, xp);
                        }
                    }, 1L);
                }

            }
        }
        return InteractionResult.SUCCESS;
    }
}

