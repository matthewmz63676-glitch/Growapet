/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.LivingEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.Projectile
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.entity.EntityDamageByEntityEvent
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.projectiles.ProjectileSource
 */
package me.growapet.listeners;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.growapet.GrowAPet;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

public class MobDamageListener
implements Listener {
    private static final double BASE_HIT_DAMAGE = 1.0;
    private static final long HIT_COOLDOWN_MS = 1000L;
    private final GrowAPet plugin;
    private final Map<UUID, Long> lastHitAt = new ConcurrentHashMap<UUID, Long>();

    public MobDamageListener(GrowAPet plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled=true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        LivingEntity target = (LivingEntity)entity;
        if (!this.plugin.getMobManager().isTracked(target.getUniqueId())) {
            return;
        }
        event.setDamage(0.0);
        Player attacker = this.resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = this.lastHitAt.get(attacker.getUniqueId());
        if (last != null && now - last < 1000L) {
            return;
        }
        this.lastHitAt.put(attacker.getUniqueId(), now);
        this.plugin.getMobManager().applyCustomDamage(target, attacker, 1.0);
    }

    @EventHandler(ignoreCancelled=true)
    public void onDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity)) {
            return;
        }
        LivingEntity target = (LivingEntity)entity;
        if (!this.plugin.getMobManager().isTracked(target.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
    }

    private Player resolveAttacker(Entity damager) {
        Projectile projectile;
        ProjectileSource shooter;
        if (damager instanceof Player) {
            Player player = (Player)damager;
            return player;
        }
        if (damager instanceof Projectile && (shooter = (projectile = (Projectile)damager).getShooter()) instanceof Player) {
            Player player = (Player)shooter;
            return player;
        }
        return null;
    }
}

