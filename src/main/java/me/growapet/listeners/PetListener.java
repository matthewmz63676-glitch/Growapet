package me.growapet.listeners;

import me.growapet.GrowAPet;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetEvent;

public final class PetListener implements Listener {
    private final GrowAPet plugin;
    public PetListener(GrowAPet plugin) { this.plugin = plugin; }
    @EventHandler public void onDamage(EntityDamageEvent event) { if (plugin.getPetManager().isPet(event.getEntity())) event.setCancelled(true); }
    @EventHandler public void onTarget(EntityTargetEvent event) { if (plugin.getPetManager().isPet(event.getEntity()) || (event.getTarget()!=null && plugin.getPetManager().isPet(event.getTarget()))) event.setCancelled(true); }
    @EventHandler public void onExplode(EntityExplodeEvent event) { if (plugin.getPetManager().isPet(event.getEntity())) event.setCancelled(true); }
}
