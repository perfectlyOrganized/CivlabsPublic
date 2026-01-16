package com.minecraftcivilizations.specialization.CraftEngine;

import net.momirealms.craftengine.bukkit.api.BukkitAdaptors;
import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.bukkit.util.LocationUtils;
import net.momirealms.craftengine.bukkit.world.BukkitExistingBlock;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.item.CustomItem;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.item.context.UseOnContext;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.ExistingBlock;
import net.momirealms.craftengine.core.world.Vec3d;
import net.momirealms.craftengine.libraries.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.nio.file.Path;
import java.util.Map;

public class MetalDetectorBehavior extends ItemBehavior {
    public static final Factory FACTORY = new Factory();
    private static final Key DETECTOR_SOUND_1 = Key.of("specialization:metal_detector_success_1");
    private static final Key DETECTOR_SOUND_2 = Key.of("specialization:metal_detector_success_2");
    private static final Key DETECTOR_SOUND_FAIL = Key.of("specialization:metal_detector_fail");
    private final int range;
    private final int cooldownTime;

    public static class Factory implements ItemBehaviorFactory {
        @Override
        public ItemBehavior create(Pack pack, Path path, String node, Key key, Map<String, Object> arguments) {
            int range = (int) arguments.getOrDefault("range", 5);
            int cooldownTime = (int) arguments.getOrDefault("cooldown-time", 60);

            return new MetalDetectorBehavior(range, cooldownTime);
        }
    }
    public MetalDetectorBehavior(int range, int cooldownTime) {
        this.range = range;
        this.cooldownTime = cooldownTime;
    }
    public InteractionResult useOnBlock(UseOnContext context) {
        net.momirealms.craftengine.core.entity.player.Player cePlayer = context.getPlayer();
        if (cePlayer == null) return InteractionResult.PASS;
        Player bukkitPlayer = (Player) cePlayer.platformPlayer();
        BlockPos startPos = context.getClickedPos();
        Direction direction = context.getClickedFace();
        if (direction == null) return InteractionResult.PASS;
        Item<?> item = context.getItem();
        if (!(item.getItem() instanceof ItemStack itemStack)) return InteractionResult.PASS;
        if (bukkitPlayer.hasCooldown(itemStack)) return InteractionResult.PASS;

        boolean isLuxury = false;
        boolean isNormal = false;
        // Check the next 5 blocks in the clicked direction
        for (int f = 0; f < range; f++) {
            BlockPos checkPos = startPos.relative(direction, -f);
            BukkitExistingBlock block = (BukkitExistingBlock) context.getLevel().getBlock(checkPos);
            if (isLuxuryMetal(block.block().getType())) {
                isLuxury = true;
                break;
            };
            if (isNormalMetal(block.block().getType())) isNormal = true;
        }
        item.hurtAndBreak(1, null, null);
        bukkitPlayer.setCooldown(itemStack.getType(), this.cooldownTime);
        cePlayer.swingHand(context.getHand());
        if (isLuxury) {
            context.getLevel().playBlockSound(
                    cePlayer.position(),
                    DETECTOR_SOUND_2,
                    0.8f + (float) Math.random() * 0.4f,
                    1.0f
            );
            return InteractionResult.SUCCESS;
        }

        if (isNormal) {
            context.getLevel().playBlockSound(
                    cePlayer.position(),
                    DETECTOR_SOUND_1,
                    0.8f + (float) Math.random() * 0.4f,
                    1.0f
            );
            return InteractionResult.SUCCESS;
        }
        context.getLevel().playBlockSound(
                cePlayer.position(),
                DETECTOR_SOUND_FAIL,
                0.8f + (float) Math.random() * 0.4f,
                1.0f
        );

        return InteractionResult.SUCCESS;
    }
    private boolean isLuxuryMetal(Material material) {
        return material == Material.IRON_ORE || material == Material.DEEPSLATE_IRON_ORE || material == Material.GOLD_ORE || material == Material.DEEPSLATE_GOLD_ORE || material == Material.NETHER_GOLD_ORE;
    }
    private boolean isNormalMetal(Material material) {
        return  material == Material.COPPER_ORE || material == Material.DEEPSLATE_COPPER_ORE;
    }
}
