package me.growapet.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;

public abstract class Menu
implements InventoryHolder {
    protected final Player viewer;
    private final Component componentTitle;
    private final int size;
    private final InventoryType type;
    private Inventory inventory;
    private final UUID sessionId = UUID.randomUUID();
    private boolean active;
    private final Map<Integer, ClickHandler> handlers = new HashMap<Integer, ClickHandler>();

    protected Menu(Player viewer, Component title, int size) {
        this.viewer = viewer;
        this.componentTitle = title;
        this.size = size;
        this.type = null;
    }

    protected Menu(Player viewer, Component title, InventoryType type) {
        this.viewer = viewer;
        this.componentTitle = title;
        this.size = type.getDefaultSize();
        this.type = type;
    }

    public abstract void build();

    public void open() {
        this.inventory = this.type != null
                ? Bukkit.createInventory(this, this.type, this.componentTitle)
                : Bukkit.createInventory(this, this.size, this.componentTitle);
        this.build();
        this.active = true;
        this.viewer.openInventory(this.inventory);
    }

    public void refresh() {
        this.handlers.clear();
        if (this.inventory != null) {
            this.inventory.clear();
        }
        this.build();
    }

    protected void setItem(int slot, ItemStack item, ClickHandler handler) {
        this.inventory.setItem(slot, item);
        if (handler != null) {
            this.handlers.put(slot, handler);
        }
    }

    void handleClick(InventoryClickEvent event) {
        if (!this.active || event.getWhoClicked() != this.viewer || event.getView().getTopInventory() != this.inventory) return;
        ClickHandler handler = this.handlers.get(event.getRawSlot());
        if (handler != null) {
            handler.onClick(event);
        }
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    public UUID getSessionId() { return this.sessionId; }
    public Player getViewer() { return this.viewer; }
    public boolean isActive() { return this.active; }
    void invalidate() { this.active = false; this.handlers.clear(); }

    @FunctionalInterface
    public static interface ClickHandler {
        public void onClick(InventoryClickEvent var1);
    }
}
