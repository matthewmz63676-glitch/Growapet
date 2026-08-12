package me.growapet.gui;

import me.growapet.GrowAPet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Deny-by-default inventory boundary for every GrowAPet menu. */
public final class MenuListener implements Listener {
    private final GrowAPet plugin;
    private final Map<UUID, UUID> sessions = new ConcurrentHashMap<>();
    public MenuListener(GrowAPet plugin) { this.plugin = plugin; }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu && event.getPlayer() == menu.getViewer()) {
            sessions.put(event.getPlayer().getUniqueId(), menu.getSessionId());
        }
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || player != menu.getViewer()) return;
        if (!menu.isActive() || !menu.getSessionId().equals(sessions.get(player.getUniqueId()))) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;
        if (!isDispatchable(event.getClick(),event.getAction())) return;
        menu.handleClick(event);
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) return;
        sessions.remove(event.getPlayer().getUniqueId(), menu.getSessionId());
        menu.invalidate();
        if (event.getPlayer() instanceof Player player) Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    static boolean isDispatchable(ClickType click,InventoryAction action) {
        return (click==ClickType.LEFT||click==ClickType.RIGHT)
                && action!=InventoryAction.COLLECT_TO_CURSOR
                && action!=InventoryAction.MOVE_TO_OTHER_INVENTORY;
    }
}
