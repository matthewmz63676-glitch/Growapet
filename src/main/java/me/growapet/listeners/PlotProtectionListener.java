/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Location
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.Cancellable
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.block.BlockPlaceEvent
 *  org.bukkit.event.hanging.HangingBreakByEntityEvent
 *  org.bukkit.event.player.PlayerBucketEmptyEvent
 *  org.bukkit.event.player.PlayerBucketFillEvent
 */
package me.growapet.listeners;

import me.growapet.GrowAPet;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

public class PlotProtectionListener
implements Listener {
    private final GrowAPet plugin;

    public PlotProtectionListener(GrowAPet plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled=true)
    public void onBreak(BlockBreakEvent event) {
        this.guard(event.getPlayer(), event.getBlock().getLocation(), (Cancellable)event);
    }

    @EventHandler(ignoreCancelled=true)
    public void onPlace(BlockPlaceEvent event) {
        this.guard(event.getPlayer(), event.getBlock().getLocation(), (Cancellable)event);
    }

    @EventHandler(ignoreCancelled=true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        this.guard(event.getPlayer(), event.getBlock().getLocation(), (Cancellable)event);
    }

    @EventHandler(ignoreCancelled=true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        this.guard(event.getPlayer(), event.getBlock().getLocation(), (Cancellable)event);
    }

    @EventHandler(ignoreCancelled=true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Entity entity = event.getRemover();
        if (entity instanceof Player) {
            Player player = (Player)entity;
            this.guard(player, event.getEntity().getLocation(), (Cancellable)event);
        }
    }

    private void guard(Player player, Location location, Cancellable event) {
        if (player.hasPermission("growapet.admin")) {
            return;
        }
        if (!this.plugin.getPlotManager().isWithinAnyPlot(location)) {
            return;
        }
        if (this.plugin.getPlotManager().isWithinOwnPlot(player.getUniqueId(), location)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage("\u00a7cYou can't modify another player's plot!");
    }
}

