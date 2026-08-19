package me.growapet.gui;

import me.growapet.GrowAPet;
import me.growapet.models.Plot;
import me.growapet.plot.PlotManager;
import me.growapet.utils.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/** Secure, live plot overview and upgrade storefront. */
public final class PlotSettingsMenu extends Menu {
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);
    private final GrowAPet plugin;

    public PlotSettingsMenu(GrowAPet plugin, Player viewer) {
        super(viewer, Messages.parse("<green><bold>PLOT SETTINGS</bold></green>"), 45);
        this.plugin = plugin;
    }

    @Override public void build() {
        fill();
        Plot plot = plugin.getPlotManager().getPlot(viewer.getUniqueId());
        if (plot == null) {
            setItem(22, item(Material.BARRIER, "<red><bold>PLOT UNAVAILABLE</bold></red>",
                    List.of("<gray>Your plot data is still loading.</gray>")), null);
            return;
        }
        int eggs = plugin.getEggIncubationManager().countActive(viewer.getUniqueId());
        setItem(4, item(Material.GRASS_BLOCK, "<green><bold>PLOT #" + plot.getId() + "</bold></green>", List.of(
                "<gray>• Pet slots → <white>" + plot.getPetLimit() + "</white></gray>",
                "<gray>• Incubators → <white>" + eggs + " / " + plot.getEggLimit() + "</white></gray>")), null);
        setItem(22, upgrade(Material.BONE, "PET CAPACITY", "Adds one active pet slot.",
                PlotManager.Upgrade.PET_SLOTS, plot), event -> buy(PlotManager.Upgrade.PET_SLOTS));
        setItem(24, upgrade(Material.TURTLE_EGG, "INCUBATOR CAPACITY", "Adds one incubation slot.",
                PlotManager.Upgrade.EGG_SLOTS, plot), event -> buy(PlotManager.Upgrade.EGG_SLOTS));
        setItem(36, item(Material.BARRIER, "<red><bold>CLOSE</bold></red>",
                List.of("<gray>• Click → close this menu</gray>")), event -> viewer.closeInventory());
        setItem(40, item(Material.ENDER_PEARL, "<aqua><bold>TELEPORT HOME</bold></aqua>",
                List.of("<gray>• Destination → <white>your safe plot spawn</white></gray>", "", "<aqua>Click → teleport</aqua>")), event -> {
            viewer.closeInventory();
            plugin.getPlotManager().teleportHome(viewer).thenAccept(success -> {
                if (success) Messages.send(viewer, "<green>Teleported to your plot.</green>");
                else Messages.send(viewer, "<red>Unable to teleport to your plot safely.</red>");
            });
        });
    }

    private ItemStack upgrade(Material material, String title, String description, PlotManager.Upgrade upgrade, Plot plot) {
        long cost = plugin.getPlotManager().upgradeCost(plot, upgrade);
        return item(material, "<gold><bold>" + title + "</bold></gold>", List.of(
                "<gray>• Benefit → <white>" + description + "</white></gray>",
                "<gray>• Cost → <yellow>" + NUMBER.format(cost) + " Coins</yellow></gray>", "", "<green>Click → purchase upgrade</green>"));
    }

    private void buy(PlotManager.Upgrade upgrade) { if (plugin.getPlotManager().purchaseUpgrade(viewer, upgrade)) refresh(); }
    private void fill() { ItemStack pane=item(Material.GRAY_STAINED_GLASS_PANE," ",List.of());for(int slot=0;slot<45;slot++)setItem(slot,pane,null); }
    private static ItemStack item(Material material,String name,List<String> lore){return new ItemBuilder(material).name(Messages.parse(name)).loreComponents(lore.stream().map(Messages::parse).toList()).build();}
}
