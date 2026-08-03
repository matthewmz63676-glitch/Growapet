/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Cancellable
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerMoveEvent
 *  org.bukkit.event.player.PlayerTeleportEvent
 *  org.bukkit.plugin.Plugin
 */
package me.growapet.listeners;

import me.growapet.GrowAPet;
import me.growapet.gui.WallGui;
import me.growapet.zones.Zone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;

public class WallListener
implements Listener {
    private final GrowAPet plugin;

    public WallListener(GrowAPet plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> this.plugin.getWallManager().sendWalls(event.getPlayer()), 5L);
    }

    @EventHandler(ignoreCancelled=true)
    public void onMove(PlayerMoveEvent event) {
        if (this.blockChanged(event.getFrom(), event.getTo())) {
            this.guard(event.getPlayer(), event.getTo(), event.getFrom(), (Cancellable)event);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onTeleport(PlayerTeleportEvent event) {
        this.guard(event.getPlayer(), event.getTo(), event.getFrom(), (Cancellable)event);
    }

    private boolean blockChanged(Location from, Location to) {
        return to == null || from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ();
    }

    private void guard(Player player, Location to, Location from, Cancellable event) {
        if (to == null || player.hasPermission("growapet.admin")) {
            return;
        }
        if (this.plugin.getWallManager().isInsideLockedWall(player, to)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void onBreak(BlockBreakEvent event) {
        if (this.plugin.getWallManager().isInsideLockedWall(event.getPlayer(), event.getBlock().getLocation()) && !event.getPlayer().hasPermission("growapet.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Player player = event.getPlayer();
        Location loc = event.getClickedBlock().getLocation();
        for (Zone zone : this.plugin.getZoneManager().getZonesInOrder()) {
            if (!zone.hasWall() || this.plugin.getZoneManager().isUnlocked(player, zone.getId()) || !zone.getWall().contains(loc)) continue;
            event.setCancelled(true);
            new WallGui(this.plugin, player, zone).open();
            return;
        }
    }
}

