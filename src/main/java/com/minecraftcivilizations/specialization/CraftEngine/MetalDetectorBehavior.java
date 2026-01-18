package com.minecraftcivilizations.specialization.CraftEngine;

import com.minecraftcivilizations.specialization.Player.CustomPlayer;
import com.minecraftcivilizations.specialization.Skill.SkillType;
import net.momirealms.craftengine.bukkit.api.BukkitAdaptors;
import net.momirealms.craftengine.bukkit.world.BukkitExistingBlock;
import net.momirealms.craftengine.core.entity.player.InteractionResult;
import net.momirealms.craftengine.core.item.Item;
import net.momirealms.craftengine.core.item.behavior.ItemBehavior;
import net.momirealms.craftengine.core.item.behavior.ItemBehaviorFactory;
import net.momirealms.craftengine.core.item.context.UseOnContext;
import net.momirealms.craftengine.core.pack.Pack;
import net.momirealms.craftengine.core.sound.SoundSource;
import net.momirealms.craftengine.core.util.Direction;
import net.momirealms.craftengine.core.util.Key;
import net.momirealms.craftengine.core.world.BlockPos;
import net.momirealms.craftengine.core.world.World;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Path;
import java.util.Map;

public class MetalDetectorBehavior extends ItemBehavior implements Listener {
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
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player targetPlayer)) {
            return;
        }

        net.momirealms.craftengine.core.entity.player.Player player = BukkitAdaptors.adapt(event.getPlayer());
        if (player == null) return;
        World world = player.world();
        ItemStack[] items = targetPlayer.getInventory().getContents();
        boolean isLuxury = false;
        boolean isNormal = false;

        for (ItemStack item : items) {

            if (isLuxuryMetal(item.getType())) {
                isLuxury = true;
                break;
            }
            if (isNormalMetal(item.getType())) isNormal = true;
        }

        if (isLuxury) {
            world.playSound(
                    player.position(),
                    DETECTOR_SOUND_2,
                    0.8f + (float) Math.random() * 0.4f,
                    1.0f,
                    SoundSource.PLAYER
            );
            return;
        }

        if (isNormal) {
            world.playSound(
                    player.position(),
                    DETECTOR_SOUND_1,
                    0.8f + (float) Math.random() * 0.4f,
                    1.0f,
                    SoundSource.PLAYER
            );
            return;
        }
        world.playSound(
                player.position(),
                DETECTOR_SOUND_FAIL,
                0.8f + (float) Math.random() * 0.4f,
                1.0f,
                SoundSource.PLAYER
        );
    }
    public InteractionResult useOnBlock(UseOnContext context) {
        net.momirealms.craftengine.core.entity.player.Player cePlayer = context.getPlayer();
        if (cePlayer == null) return InteractionResult.PASS;
        Player bukkitPlayer = (Player) cePlayer.platformPlayer();
        CustomPlayer customPlayer = CustomPlayer.getCustomPlayer(bukkitPlayer);
        BlockPos startPos = context.getClickedPos();
        Direction direction = context.getClickedFace();
        if (direction == null) return InteractionResult.PASS;
        Item<?> item = context.getItem();
        if (!(item.getItem() instanceof ItemStack itemStack)) return InteractionResult.PASS;
        if (bukkitPlayer.hasCooldown(itemStack) || customPlayer.getSkillLevel(SkillType.MINER) < 1) return InteractionResult.PASS;
        boolean isLuxury = false;
        boolean isNormal = false;
        // Check the next 5 blocks in the clicked direction
        for (int f = 0; f < range; f++) {
            BlockPos checkPos = startPos.relative(direction, -f);
            BukkitExistingBlock block = (BukkitExistingBlock) context.getLevel().getBlock(checkPos);
            if (isLuxuryMetal(block.block().getType())) {
                isLuxury = true;
                break;
            }
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
        return material.name().contains("IRON") || material.name().contains("GOLD") || material.name().contains("MINECART")
                || material == Material.HOPPER
                || material == Material.BUCKET
                || material == Material.CAULDRON
                || material == Material.ANVIL
                || material.name().contains("CHAINMAIL")
                || material.name().contains("RAIL")
                || material == Material.FLINT_AND_STEEL;
    }
    private boolean isNormalMetal(Material material) {
        return material.name().contains("COPPER");
    }
}
