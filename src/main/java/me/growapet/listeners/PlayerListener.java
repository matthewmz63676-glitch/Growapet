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
        if (this.plugin.isReady()) this.plugin.getPlayerManager().load(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.plugin.getTutorialManager().forceStop(event.getPlayer());
        this.plugin.getActionBarManager().clear(event.getPlayer().getUniqueId());
        this.plugin.getLeaderboardManager().dismissPersonal(event.getPlayer().getUniqueId());
        java.util.concurrent.CompletableFuture.allOf(
                this.plugin.getTradeManager().cancel(event.getPlayer().getUniqueId(), "§cTrade cancelled: a player disconnected."),
                this.plugin.getQuestManager().awaitPendingClaim(event.getPlayer().getUniqueId())
        ).whenComplete((ignored,error) -> {
            if (this.plugin.isEnabled()) org.bukkit.Bukkit.getScheduler().runTask(this.plugin,
                    () -> this.plugin.getPlayerManager().unload(event.getPlayer()));
        });
        this.plugin.getOptionsManager().unload(event.getPlayer().getUniqueId());
    }
}
