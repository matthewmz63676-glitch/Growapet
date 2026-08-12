package me.growapet.listeners;

import me.growapet.GrowAPet;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataType;

public final class MobDamageListener implements Listener {
    private final GrowAPet plugin;
    private final NamespacedKey projectileDamageKey;

    public MobDamageListener(GrowAPet plugin) {
        this.plugin = plugin;
        this.projectileDamageKey = new NamespacedKey(plugin, "projectile_damage");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) return;
        event.getEntity().getPersistentDataContainer().set(
                projectileDamageKey, PersistentDataType.DOUBLE, plugin.getMobManager().weaponDamage(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)
                || !plugin.getMobManager().isTracked(target.getUniqueId())) return;

        boolean deniedByAnotherPlugin = event.isCancelled();
        event.setCancelled(true);
        event.setDamage(0.0);
        target.setNoDamageTicks(0);
        if (deniedByAnotherPlugin) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) return;
        double damage = event.getDamager() instanceof Projectile projectile
                ? projectile.getPersistentDataContainer().getOrDefault(
                        projectileDamageKey, PersistentDataType.DOUBLE, plugin.getMobManager().weaponDamage(attacker))
                : plugin.getMobManager().weaponDamage(attacker);
        plugin.getMobManager().applyCustomDamage(target, attacker, damage);
        target.setNoDamageTicks(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOtherDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;
        if (event.getEntity() instanceof LivingEntity target
                && plugin.getMobManager().isTracked(target.getUniqueId())) {
            event.setCancelled(true);
            event.setDamage(0.0);
            target.setNoDamageTicks(0);
        }
    }

    private static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
}
