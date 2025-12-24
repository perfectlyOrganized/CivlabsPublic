package com.minecraftcivilizations.specialization.SmartEntity;


import com.minecraftcivilizations.specialization.Specialization;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles scheduling and management of SmartEntity
 * @Author Alectriciti
 */
public class SmartEntityManager implements Listener{

    Specialization specialization;

    public final List<SmartEntity> entities = new ArrayList<SmartEntity>();
    public final List<SmartEntity> entities_to_add = new ArrayList<SmartEntity>();
    public final List<SmartEntity> entities_to_remove = new ArrayList<SmartEntity>();

    boolean entities_exist;
    boolean dirty; // requires cleaning

    public SmartEntityManager(Specialization specialization){
        SmartEntity.manager = this;
        this.specialization = specialization;
        this.specialization.getServer().getPluginManager().registerEvents(this, specialization);
        task.runTaskTimer(specialization, 1L, 1L);
    }

    BukkitRunnable task = new BukkitRunnable() {
        @Override
        public void run() {
            updateAll();
        }
    };

    public void shutdown(){
        this.specialization.getServer().getScheduler().cancelTask(task.getTaskId());
    }

    public void updateAll(){
        if(entities_exist) {
            for (SmartEntity entity : entities) {
                entity.always_update();
            }
        }
        if (dirty) {
            if(!entities_to_add.isEmpty()) {
                entities.addAll(entities_to_add);
                entities_to_add.clear();
            }
            if(!entities_to_remove.isEmpty()) {
                entities.removeAll(entities_to_remove);
                entities_to_remove.clear();
            }
            if(entities.isEmpty()){
                entities_exist = false;
            }
            dirty = false;
        }
    }

    void registerEntity(SmartEntity entity){
        entities_to_add.add(entity);
//        Debug.broadcast("smartentities", ChatColor.YELLOW+"Smart Entity created by "+(entity.owner!=null?entity.owner.getName():"null")+" at "+entity.location.toString());
        dirty = true;
        entities_exist = true;
    }

    void unregisterEntity(SmartEntity entity){
        entities_to_remove.add(entity);
//        Debug.broadcast("smartentities", ChatColor.RED+"Smart Entity died");
        dirty = true;
    }




}
