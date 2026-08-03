/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryType
 *  org.bukkit.inventory.ItemStack
 */
package me.growapet.gui;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;
import me.growapet.GrowAPet;
import me.growapet.gui.ItemBuilder;
import me.growapet.gui.Menu;
import me.growapet.zones.Zone;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

public class WallGui
extends Menu {
    private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private final GrowAPet plugin;
    private final Zone zone;

    public WallGui(GrowAPet plugin, Player viewer, Zone zone) {
        super(viewer, "&8Unlock Zone", InventoryType.DISPENSER);
        this.plugin = plugin;
        this.zone = zone;
    }

    @Override
    public void build() {
        ArrayList<String> lore = new ArrayList<String>();
        lore.add("&7Requirements:");
        if (this.zone.getReqLevel() > 0) {
            lore.add("&7- Level: &e" + this.zone.getReqLevel());
        }
        if (this.zone.getCost() > 0L) {
            lore.add("&7- Coins: &e" + FORMAT.format(this.zone.getCost()));
        }
        if (this.zone.getGemCost() > 0L) {
            lore.add("&7- Gems: &e" + FORMAT.format(this.zone.getGemCost()));
        }
        lore.add("");
        lore.add("&eClick to unlock!");
        ItemStack item = new ItemBuilder(Material.EMERALD).name("&aUnlock " + this.zone.getDisplayName()).lore(lore).build();
        this.setItem(4, item, e -> {
            if (this.plugin.getZoneManager().unlock(this.viewer, this.zone.getId())) {
                this.viewer.closeInventory();
                this.plugin.getWallManager().playBreakCutscene(this.viewer, this.zone);
            }
        });
    }
}

