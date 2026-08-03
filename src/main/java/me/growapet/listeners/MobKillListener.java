/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.entity.EntityDeathEvent
 */
package me.growapet.listeners;

import me.growapet.GrowAPet;
import me.growapet.mobs.MobRewards;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class MobKillListener
implements Listener {
    private final GrowAPet plugin;

    public MobKillListener(GrowAPet plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        if (this.plugin.getMobManager().isTracked(entity.getUniqueId())) {
            return;
        }
        MobRewards.grant(this.plugin, killer, entity.getType());
    }
}

