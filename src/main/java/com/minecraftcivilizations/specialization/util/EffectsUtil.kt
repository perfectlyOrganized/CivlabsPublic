package com.minecraftcivilizations.specialization.util

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.type.Bed
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object EffectsUtil {
    fun playBlockBoundingBox(player: Player, block: Block, particle: Particle, step: Double) {
        val box: BoundingBox?

        val bedData = block.blockData as? Bed
        if (bedData != null) {
            val otherPart: Block? = getOtherBedPart(block, bedData)
            if (otherPart != null) {
                box = block.boundingBox.union(otherPart.boundingBox)
            } else {
                box = block.boundingBox
            }
        } else {
            box = block.boundingBox
        }

        val min = box.min
        val max = box.max

        // Edge vertices
        val starts = arrayOf<Vector>(
            Vector(min.getX(), min.getY(), min.getZ()),
            Vector(min.getX(), min.getY(), max.getZ()),
            Vector(max.getX(), min.getY(), min.getZ()),
            Vector(max.getX(), min.getY(), max.getZ()),
            Vector(min.getX(), max.getY(), min.getZ()),
            Vector(min.getX(), max.getY(), max.getZ()),
            Vector(max.getX(), max.getY(), min.getZ()),
            Vector(max.getX(), max.getY(), max.getZ())
        )

        val edges = arrayOf<IntArray>(
            intArrayOf(0, 1), intArrayOf(0, 2), intArrayOf(1, 3), intArrayOf(2, 3),
            intArrayOf(4, 5), intArrayOf(4, 6), intArrayOf(5, 7), intArrayOf(6, 7),
            intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7)
        )

        for (edge in edges) {
            val start = starts[edge[0]]
            val end = starts[edge[1]]
            val diff = end.clone().subtract(start)
            val length = diff.length()
            val stepVec = diff.clone().normalize().multiply(step)

            var d = 0.0
            while (d <= length) {
                val point = start.clone().add(stepVec.clone().multiply(d / step))
                player.spawnParticle(particle, point.getX(), point.getY(), point.getZ(), 1, 0.0, 0.0, 0.0, 0.0)
                d += step
            }
        }
    }

    private fun getOtherBedPart(block: Block, bedData: Bed): Block? {
        val other = block.getRelative(bedData.facing, if (bedData.part == Bed.Part.HEAD) -1 else 1)
        if (other.type == block.type && other.blockData is Bed) {
            return other
        }
        return null
    }

    fun spawnLootEffect(location: Location, seed: Long = System.currentTimeMillis()) {
        val world: World = location.getWorld() ?: return
        val random = Random(seed)

        // Determine effect variations based on seed
        val colorVariation = random.nextInt(3) // 0-2 for different color schemes
        var patternType = random.nextInt(4) // 0-3 for different patterns
        val heightOffset = 0.3 + random.nextDouble() * 0.7 // Random height between 0.3 and 1.0
        val radius = 1.0 + random.nextDouble() * 1.5 // Random radius between 1.0 and 2.5
        val particleCount = 20 + random.nextInt(20) // Random particle count between 20 and 40
        val speedMultiplier = 0.5 + random.nextDouble() * 1.0 // Random speed between 0.5 and 1.5

        // Play base sounds (with slight pitch variation based on seed)
        world.playSound(location, Sound.BLOCK_CHEST_OPEN, 1.0f, (1.0f + random.nextFloat() * 0.4f))
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.7f, (1.3f + random.nextFloat() * 0.4f))
        world.playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, (1.6f + random.nextFloat() * 0.4f))

        // Select particle types based on seed
        val primaryParticle = when (colorVariation) {
            0 -> Particle.END_ROD
            1 -> Particle.FIREWORKS_SPARK
            2 -> Particle.WAX_ON // or any other sparkly particle
            else -> Particle.END_ROD
        }

        val secondaryParticle = when (colorVariation) {
            0 -> Particle.VILLAGER_HAPPY
            1 -> Particle.WHITE_ASH
            2 -> Particle.TOTEM
            else -> Particle.VILLAGER_HAPPY
        }

        // Spawn particles with variations
        var i = 0
        while (i < 360) {
            val angle = Math.toRadians(i.toDouble())

            // Pattern variations
            val x = when (patternType) {
                0 -> cos(angle) * radius  // Standard circle
                1 -> cos(angle) * radius * 1.5  // Ellipse
                2 -> cos(angle) * radius * (0.8 + 0.4 * sin(angle))  // Flower-like
                else -> cos(angle) * radius * (1.0 + 0.3 * cos(angle * 2)) // Spiral-like
            }

            val z = when (patternType) {
                0 -> sin(angle) * radius
                1 -> sin(angle) * radius / 1.5
                2 -> sin(angle) * radius * (0.8 + 0.4 * cos(angle))
                else -> sin(angle) * radius * (1.0 + 0.3 * sin(angle * 2))
            }

            // Random vertical offset for each particle
            val yOffset = heightOffset + random.nextDouble() * 0.3

            val particleLoc: Location = location.clone().add(x, yOffset, z)

            // Vary particle speeds based on seed
            world.spawnParticle(primaryParticle, particleLoc, 0, 0.0, 0.1 * speedMultiplier, 0.0, 0.05 * speedMultiplier)
            world.spawnParticle(secondaryParticle, particleLoc, 0, 0.0, 0.1 * speedMultiplier, 0.0, 0.1 * speedMultiplier)

            i += when (random.nextInt(3)) {
                0 -> 10  // Denser particles
                1 -> 15  // Standard density
                else -> 20  // Sparse particles
            }
        }

        // Central burst with variations
        val centralBurstHeight = location.clone().add(0.0, 1.5 + random.nextDouble() * 0.5, 0.0)

        // Vary central particle effects
        world.spawnParticle(
            when (random.nextInt(3)) {
                0 -> Particle.FIREWORKS_SPARK
                1 -> Particle.END_ROD
                else -> Particle.TOTEM
            },
            centralBurstHeight,
            particleCount,
            0.5 * speedMultiplier,
            0.3 * speedMultiplier,
            0.5 * speedMultiplier,
            0.1 * speedMultiplier
        )

        world.spawnParticle(primaryParticle, centralBurstHeight, 20, 0.3, 0.2, 0.3, 0.05)

        // Play final sound with variation
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, (0.6f + random.nextFloat() * 0.4f))
    }
}