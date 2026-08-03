/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 */
package me.growapet.gui;

import me.growapet.GrowAPet;
import me.growapet.gui.ItemBuilder;
import me.growapet.gui.Menu;
import me.growapet.models.Plot;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class PlotSettingsMenu
extends Menu {
    private final GrowAPet plugin;

    public PlotSettingsMenu(GrowAPet plugin, Player viewer) {
        super(viewer, "&8Plot Settings", 27);
        this.plugin = plugin;
    }

    @Override
    public void build() {
        Plot plot = this.plugin.getPlotManager().getPlot(this.viewer.getUniqueId());
        if (plot == null) {
            ItemStack item = new ItemBuilder(Material.BARRIER).name("&cNo plot found").lore("&7You don't have a plot yet.").build();
            this.setItem(13, item, null);
            return;
        }
        int eggsActive = this.plugin.getEggIncubationManager().countActive(this.viewer.getUniqueId());
        ItemStack info = new ItemBuilder(Material.GRASS_BLOCK).name("&aPlot #" + plot.getId()).lore("&7Size: &e" + plot.getSize() + "x" + plot.getSize(), "&7Pet limit: &e" + plot.getPetLimit(), "&7Eggs incubating: &e" + eggsActive + "/" + plot.getEggLimit()).build();
        this.setItem(11, info, null);
        ItemStack teleport = new ItemBuilder(Material.ENDER_PEARL).name("&bTeleport Home").lore("&7Click to warp to your plot.").build();
        this.setItem(15, teleport, e -> {
            this.viewer.closeInventory();
            this.viewer.teleport(plot.getCenter());
            this.viewer.sendMessage("\u00a7aTeleported to your plot.");
        });
    }
}

