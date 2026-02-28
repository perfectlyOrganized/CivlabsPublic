package com.minecraftcivilizations.specialization.Combat;

import com.minecraftcivilizations.specialization.util.CooldownManager;
import com.minecraftcivilizations.specialization.util.PlayerUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;

public class ExplosionDamage implements Listener {


    private final CombatManager combatManager;

    public ExplosionDamage(CombatManager combatManager) {
        this.combatManager = combatManager;
        this.combatManager.plugin.getServer().getPluginManager().registerEvents(this, combatManager.plugin);
    }

    private final Map<Location, Material> lastExplosions = new HashMap<Location, Material>();

    @EventHandler
    public void onExplosion(EntityDamageEvent event){
//        if(event.getCause()== EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) {
//            if(event.getDamageSource().getDamageType()== DamageType.BAD_RESPAWN_POINT){
////                event.setDamage(BASE, event.get);
//            }
//            String modifiers = "";
//
//            for (EntityDamageEvent.DamageModifier m : EntityDamageEvent.DamageModifier.values()) {
////            if(event.getDamage(m)!=0)
//                if(event.isApplicable(m))
//                    modifiers += "\n<gray>"+m.name()+"</gray>: "+Debug.formatDecimal(event.getDamage(m));
//            }
//            Debug.broadcast("damage", "<gold>Explosion at "+   ": "+event.getDamage()+"</gold>", modifiers);
//            Debug.broadcast("explosion", "Block Explosion Damage cause: "+event.getDamageSource().getDamageType().toString());
//
//        }

    }

    @EventHandler
    public void onBlockBreak(BlockPlaceEvent event){
        Material mat = event.getBlockPlaced().getType();
        if(mat == Material.RESPAWN_ANCHOR){
            event.getPlayer().setCooldown(Material.GLOWSTONE, 60);
            event.getPlayer().getWorld().playSound(event.getPlayer().getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1, 1);
        }else if(mat.name().contains("_BED")){
            Player player = event.getPlayer();
            if(player.getWorld().getEnvironment() != World.Environment.NORMAL){
                CooldownManager.INSTANCE.setCooldown(player,"bed_place", 30);
                player.setCooldown(mat, 30);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event){
        if(event.getAction()== Action.RIGHT_CLICK_BLOCK){
            Material type = event.getClickedBlock().getType();
            if(type.equals(Material.RESPAWN_ANCHOR)){
                if(event.getPlayer().getCooldown(Material.GLOWSTONE)>0){
                    event.setCancelled(true);
                }
                Player player = event.getPlayer();
                if(CooldownManager.INSTANCE.isOnCooldown(player,"respawn_anchor")) {
                    event.setCancelled(true);
                }else{
                    CooldownManager.INSTANCE.setCooldown(player,"respawn_anchor", 8);
                }
            }else if(type.name().contains("_BED")){

                Player player = event.getPlayer();
                if(CooldownManager.INSTANCE.isOnCooldown(player,"bed_place")) {
                    event.setCancelled(true);
                }
            }
        }
    }
}
