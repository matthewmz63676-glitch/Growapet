/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 */
package me.growapet.listeners;

import me.growapet.GrowAPet;
import me.growapet.models.Plot;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener
implements Listener {
    private final GrowAPet plugin;

    public PlayerListener(GrowAPet plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.plugin.getPlayerManager().load(event.getPlayer());
        if (!this.plugin.getPlotManager().hasPlot(event.getPlayer().getUniqueId())) {
            Plot plot = this.plugin.getPlotManager().createPlot(event.getPlayer().getUniqueId());
            event.getPlayer().sendMessage("\u00a7aA plot has been created for you! Use \u00a7e/plot home \u00a7ato visit it.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.plugin.getPlayerManager().unload(event.getPlayer());
    }
}

