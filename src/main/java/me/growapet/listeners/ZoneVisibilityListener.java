package me.growapet.listeners;

import me.growapet.GrowAPet;
import me.growapet.zones.Zone;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Objects;

/** Applies viewer-scoped zone isolation immediately when a player crosses a zone boundary. */
public final class ZoneVisibilityListener implements Listener {
    private final GrowAPet plugin;
    public ZoneVisibilityListener(GrowAPet plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom(), to = event.getTo();
        if (to == null || (from.getWorld() == to.getWorld() && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ())) return;
        Zone before = plugin.getZoneManager().getZoneAt(from), after = plugin.getZoneManager().getZoneAt(to);
        if (Objects.equals(before == null ? null : before.getId(), after == null ? null : after.getId())) return;
        plugin.getMobManager().refreshViewer(event.getPlayer());
        plugin.getBossManager().refreshViewer(event.getPlayer());
        plugin.getRelicManager().refreshViewer(event.getPlayer());
    }
}
