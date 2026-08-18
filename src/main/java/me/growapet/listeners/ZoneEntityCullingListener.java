package me.growapet.listeners;

import me.growapet.GrowAPet;
import me.growapet.zones.Zone;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/** Viewer-scoped culling for loaded mobs and item drops across zone boundaries. */
public final class ZoneEntityCullingListener implements Listener {
    private final GrowAPet plugin;
    public ZoneEntityCullingListener(GrowAPet plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Zone before = plugin.getZoneManager().getZoneAt(event.getFrom());
        Zone after = plugin.getZoneManager().getZoneAt(event.getTo());
        if (same(before, after)) return;
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity) && !(entity instanceof Item)) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) apply(viewer, entity);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (!(entity instanceof LivingEntity) && !(entity instanceof Item)) continue;
            for (Player viewer : Bukkit.getOnlinePlayers()) apply(viewer, entity);
        }
    }

    public void refresh(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;
        for (World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) {
            if (entity instanceof Player || (!(entity instanceof LivingEntity) && !(entity instanceof Item))) continue;
            apply(viewer, entity);
        }
    }

    private void apply(Player viewer, Entity entity) {
        if (!viewer.isOnline() || entity == viewer || !viewer.getWorld().equals(entity.getWorld())) return;
        Zone viewerZone = plugin.getZoneManager().getZoneAt(viewer.getLocation());
        Zone entityZone = plugin.getZoneManager().getZoneAt(entity.getLocation());
        if (entityZone != null && (viewerZone == null || !entityZone.getId().equals(viewerZone.getId()))) viewer.hideEntity(plugin, entity);
        else viewer.showEntity(plugin, entity);
    }

    private static boolean same(Zone left, Zone right) { return left == null ? right == null : right != null && left.getId().equals(right.getId()); }
}
