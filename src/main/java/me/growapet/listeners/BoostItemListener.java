package me.growapet.listeners;

import me.growapet.GrowAPet;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class BoostItemListener implements Listener {
    private final GrowAPet plugin;
    public BoostItemListener(GrowAPet plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!plugin.getBoostItemManager().isBoost(event.getItem())) return;
        event.setCancelled(true);
        plugin.getBoostItemManager().activate(event.getPlayer(), event.getItem());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!plugin.getBoostItemManager().isBoost(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        me.growapet.utils.Messages.send(event.getPlayer(), "<red>Bound boost items cannot be dropped or traded.</red>");
    }
}
