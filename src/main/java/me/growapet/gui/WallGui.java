package me.growapet.gui;

import me.growapet.GrowAPet;
import me.growapet.utils.Messages;
import me.growapet.zones.Zone;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Viewer-safe purchase prompt for a client-side zone wall. */
public final class WallGui extends Menu {
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);
    private final GrowAPet plugin;
    private final Zone zone;

    public WallGui(GrowAPet plugin, Player viewer, Zone zone) {
        super(viewer, Messages.parse("<gold><bold>UNLOCK ZONE</bold></gold>"), InventoryType.DISPENSER);
        this.plugin = plugin;
        this.zone = zone;
    }

    @Override public void build() {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot=0;slot<9;slot++) setItem(slot,pane,null);
        List<String> lore = new ArrayList<>();
        lore.add("<gray>• Destination → <white>" + safe(zone.getDisplayName()) + "</white></gray>");
        if (zone.getReqLevel()>0) lore.add("<gray>• Required level → <yellow>"+zone.getReqLevel()+"</yellow></gray>");
        if (zone.getCost()>0) lore.add("<gray>• Coins → <yellow>"+NUMBER.format(zone.getCost())+"</yellow></gray>");
        if (zone.getGemCost()>0) lore.add("<gray>• Gems → <aqua>"+NUMBER.format(zone.getGemCost())+"</aqua></gray>");
        lore.add(""); lore.add("<green><bold>CLICK → UNLOCK</bold></green>");
        setItem(4,item(Material.EMERALD,"<green><bold>"+safe(zone.getDisplayName()).toUpperCase(Locale.ROOT)+"</bold></green>",lore),event->{
            if(plugin.getZoneManager().unlock(viewer,zone.getId())){viewer.closeInventory();plugin.getWallManager().playBreakCutscene(viewer,zone);}
        });
    }

    private static String safe(String value){return value.replace("<","‹").replace(">","›");}
    private static ItemStack item(Material material,String name,List<String> lore){return new ItemBuilder(material).name(Messages.parse(name)).loreComponents(lore.stream().map(Messages::parse).toList()).build();}
}
