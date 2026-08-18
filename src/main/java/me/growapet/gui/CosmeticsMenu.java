package me.growapet.gui;

import me.growapet.GrowAPet;
import me.growapet.utils.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Compact, ownership-aware cosmetics selector. */
public final class CosmeticsMenu extends Menu {
    private final GrowAPet plugin;
    public CosmeticsMenu(GrowAPet plugin, Player viewer) { super(viewer, Messages.parse("<aqua><bold>COSMETICS</bold></aqua>"), 45); this.plugin = plugin; }

    @Override public void build() {
        fill();
        setItem(4, item(Material.NAME_TAG, "<aqua><bold>YOUR STYLE</bold></aqua>", List.of("<gray>• Ownership is separate from selection.</gray>", "<gray>• Rewards remain safe across relogs.</gray>")), null);
        int slot = 10;
        for (String id : plugin.getCosmeticService().available(viewer.getUniqueId(), "tag")) {
            if (slot >= 26) break;
            String selected = plugin.getCosmeticService().selected(viewer.getUniqueId(), "tag");
            setItem(slot++, item(Material.NAME_TAG, (id.equals(selected) ? "<green>" : "<white>") + "<bold>TAG · " + id + "</bold></" + (id.equals(selected) ? "green" : "white") + ">", List.of("<gray>• Preview → <white>" + id + "</white></gray>", id.equals(selected) ? "<green>Selected</green>" : "<yellow>Click → select</yellow>")), event -> select("tag", id));
        }
        slot = 28;
        for (String id : plugin.getCosmeticService().available(viewer.getUniqueId(), "color")) {
            if (slot >= 44) break;
            String selected = plugin.getCosmeticService().selected(viewer.getUniqueId(), "color");
            setItem(slot++, item(Material.LEATHER_CHESTPLATE, (id.equals(selected) ? "<green>" : "<white>") + "<bold>COLOR · " + id + "</bold></" + (id.equals(selected) ? "green" : "white") + ">", List.of("<gray>• Applies to → <white>chat</white></gray>", id.equals(selected) ? "<green>Selected</green>" : "<yellow>Click → select</yellow>")), event -> select("color", id));
        }
        setItem(36, item(Material.BARRIER, "<red><bold>BACK</bold></red>", List.of("<gray>• Click → close</gray>")), event -> viewer.closeInventory());
        setItem(40, item(Material.COMPASS, "<aqua><bold>REFRESH</bold></aqua>", List.of("<gray>• Click → refresh ownership</gray>")), event -> refresh());
    }

    private void select(String kind, String value) {
        plugin.getCosmeticService().select(viewer.getUniqueId(), kind, value).whenComplete((success, error) -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            if (viewer.isOnline()) Messages.send(viewer, error == null && Boolean.TRUE.equals(success) ? "<green>Cosmetic selected.</green>" : "<red>You do not own that cosmetic.</red>");
            if (isActive()) refresh();
        }));
    }

    private void fill() { ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()); for (int i = 0; i < 45; i++) setItem(i, pane, null); }
    private static ItemStack item(Material material, String name, List<String> lore) { return new ItemBuilder(material).name(Messages.parse(name)).loreComponents(lore.stream().map(Messages::parse).toList()).build(); }
}
