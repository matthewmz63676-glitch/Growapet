package me.growapet.listeners;

import me.growapet.GrowAPet;
import me.growapet.gui.StatsMenu;
import me.growapet.utils.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

/** Optional PETCORE-style right-click access to another online player's stats. */
public final class StatsInteractionListener implements Listener {
    private final GrowAPet plugin;
    public StatsInteractionListener(GrowAPet plugin) { this.plugin = plugin; }
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player target) || event.getPlayer().isSneaking()) return;
        Player viewer = event.getPlayer();
        if (!viewer.hasPermission("growapet.stats.others")) return;
        if (plugin.getPlayerManager().get(target) == null) return;
        event.setCancelled(true);
        new StatsMenu(plugin, viewer, target).open();
    }
}
