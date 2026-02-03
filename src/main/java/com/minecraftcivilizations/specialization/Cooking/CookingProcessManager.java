package com.minecraftcivilizations.specialization.Cooking;

import com.minecraftcivilizations.specialization.Specialization;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Campfire;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Handles the cooking process (starting, stopping, timing, burning).
 */
public class CookingProcessManager {

    /**
     * Start cooking for a session.
     */
    public static void startCooking(CookingItemData session, org.bukkit.entity.Player starter) {
        if (session == null) return;
        if (session.food == null) return;
        if (session.cookingInProgress || session.cookTask != null) return;

        int cookSeconds = session.cookTimeSeconds > 0 ? session.cookTimeSeconds : 10;
        final int READY_TICKS = cookSeconds * 20;
        final int BURN_TICKS = (int) Math.ceil(READY_TICKS * 1.2);

        session.cookingInProgress = true;
        session.viewer = (starter == null ? null : starter.getUniqueId());

        CookingVisuals.playStartEffects(session);

        // Start flame particle task
        if (session.flameTask != null) {
            session.flameTask.cancel();
            session.flameTask = null;
        }
        session.flameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (session.destroyed || session.cooked || !session.cookingInProgress) {
                    cancel();
                    return;
                }
                CookingVisuals.spawnFlameParticles(session);
            }
        }.runTaskTimer(Specialization.getInstance(), 0L, 10L);

        // Cook completion task
        session.cookTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (session.destroyed) return;
                session.cooked = true;
                session.cookingInProgress = false;

                // Clear campfire slots
                if (session.campfireLocation != null) {
                    Block b = session.campfireLocation.getBlock();
                    if (b.getType() == Material.CAMPFIRE) {
                        Campfire cf = (Campfire) b.getState();
                        for (int i = 0; i < 4; i++) {
                            cf.setItem(i, null);
                        }
                        cf.update(true);
                    }
                }

                if (session.recipientDisplay != null) {
                    session.recipientDisplay.remove();
                    session.recipientDisplay = null;
                }

                session.ingredients.clear();
                session.ingredientIds.clear();
                session.recipient = null;
                session.recipientId = null;

                CookingVisuals.markReady(session);
            }
        }.runTaskLater(Specialization.getInstance(), READY_TICKS);

        // Burn task
        if (session.burnTask != null) session.burnTask.cancel();
        session.burnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (session.destroyed) return;
                if (!session.cooked) return;
                session.burnt = true;
                CookingRecipeManager.resolveResultItem(session, "specialization:burnt_food");
                CookingVisuals.spawnOrUpdateDisplay(session);
                CookingVisuals.markReady(session);
            }
        }.runTaskLater(Specialization.getInstance(), BURN_TICKS);
    }

    /**
     * Stop all cooking processes for a session.
     */
    public static void stopCookingProcesses(CookingItemData data) {
        if (data == null) return;

        CookingVisuals.stopProgressBar(data);
        CookingVisuals.stopAmbient(data);

        if (data.progressTask != null) {
            data.progressTask.cancel();
            data.progressTask = null;
        }
        if (data.cookTask != null) {
            data.cookTask.cancel();
            data.cookTask = null;
        }
        if (data.maintenanceTask != null) {
            data.maintenanceTask.cancel();
            data.maintenanceTask = null;
        }
        if (data.burnTask != null) {
            data.burnTask.cancel();
            data.burnTask = null;
        }
        if (data.flameTask != null) {
            data.flameTask.cancel();
            data.flameTask = null;
        }
        if (data.holdTask != null) {
            data.holdTask.cancel();
            data.holdTask = null;
        }

        data.cookingInProgress = false;
        data.cooked = false;
        data.burnt = false;
        data.viewer = null;
    }

    /**
     * Check if a session is actively cooking (not yet finished).
     */
    public static boolean isActiveCooking(CookingItemData d) {
        return d != null && !d.cooked && (d.cookingInProgress || d.cookTask != null);
    }

    /**
     * Check if a meal is present (cooking, ready, or burnt).
     */
    public static boolean hasMealInside(CookingItemData d) {
        return d != null && (d.cookingInProgress || d.cooked || d.burnt);
    }
}
