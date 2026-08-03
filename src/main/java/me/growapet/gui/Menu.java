/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryType
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 */
package me.growapet.gui;

import java.util.HashMap;
import java.util.Map;
import me.growapet.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public abstract class Menu
implements InventoryHolder {
    protected final Player viewer;
    private final String title;
    private final int size;
    private final InventoryType type;
    private Inventory inventory;
    private final Map<Integer, ClickHandler> handlers = new HashMap<Integer, ClickHandler>();

    protected Menu(Player viewer, String title, int size) {
        this.viewer = viewer;
        this.title = Utils.colorize(title);
        this.size = size;
        this.type = null;
    }

    protected Menu(Player viewer, String title, InventoryType type) {
        this.viewer = viewer;
        this.title = Utils.colorize(title);
        this.size = type.getDefaultSize();
        this.type = type;
    }

    public abstract void build();

    public void open() {
        this.inventory = this.type != null ? Bukkit.createInventory((InventoryHolder)this, (InventoryType)this.type, (String)this.title) : Bukkit.createInventory((InventoryHolder)this, (int)this.size, (String)this.title);
        this.build();
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
        ClickHandler handler = this.handlers.get(event.getRawSlot());
        if (handler != null) {
            handler.onClick(event);
        }
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    @FunctionalInterface
    public static interface ClickHandler {
        public void onClick(InventoryClickEvent var1);
    }
}

