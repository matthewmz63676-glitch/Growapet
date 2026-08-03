/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 */
package me.growapet.gui;

import java.util.List;
import me.growapet.GrowAPet;
import me.growapet.gui.ItemBuilder;
import me.growapet.gui.Menu;
import me.growapet.zones.Zone;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class WarpMenu
extends Menu {
    private final GrowAPet plugin;

    public WarpMenu(GrowAPet plugin, Player viewer) {
        super(viewer, "&8Warps", 27);
        this.plugin = plugin;
    }

    @Override
    public void build() {
        List<Zone> zones = this.plugin.getZoneManager().getZonesInOrder();
        int slot = 0;
        for (Zone zone : zones) {
            if (slot >= 27) break;
            boolean unlocked = this.plugin.getZoneManager().isUnlocked(this.viewer, zone.getId());
            if (unlocked) {
                item = new ItemBuilder(Material.PAPER).name("&a" + zone.getDisplayName()).lore("&7Click to warp!").build();
                this.setItem(slot, item, e -> {
                    this.viewer.closeInventory();
                    this.plugin.getZoneManager().teleport(this.viewer, zone.getId());
                });
            } else {
                item = new ItemBuilder(Material.BARRIER).name("&c" + zone.getDisplayName() + " &7(Locked)").lore("&7Cost: &e" + zone.getCost() + " coins", "&7Click to purchase & warp.").build();
                this.setItem(slot, item, e -> {
                    if (this.plugin.getZoneManager().unlock(this.viewer, zone.getId())) {
                        this.viewer.closeInventory();
                        this.plugin.getZoneManager().teleport(this.viewer, zone.getId());
                    }
                });
            }
            ++slot;
        }
    }
}

