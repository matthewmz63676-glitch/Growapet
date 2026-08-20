package me.growapet.gui;

import me.growapet.GrowAPet;
import me.growapet.tags.TagService;
import me.growapet.utils.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Protected, ownership-aware tag selector. */
public final class TagMenu extends Menu {
    private final GrowAPet plugin;
    public TagMenu(GrowAPet plugin, Player viewer) { super(viewer, Messages.parse(plugin.getConfigManager().cosmetics().getString("tag-menu-title", "<aqua><bold>TAGS</bold></aqua>")), 45); this.plugin = plugin; }

    @Override public void build() {
        fill();
        String selected = plugin.getTagService().selected(viewer.getUniqueId());
        setItem(4, item(Material.NAME_TAG, "<aqua><bold>YOUR TAG</bold></aqua>", List.of(selected.isBlank() ? "<gray>No tag selected.</gray>" : "<white>" + selected + "</white>", "", "<yellow>Click a tag to equip it.</yellow>")), null);
        int slot = 10;
        for (TagService.Definition definition : plugin.getTagService().all()) {
            if (slot >= 35) break;
            boolean owned = plugin.getTagService().owns(viewer.getUniqueId(), definition.id());
            boolean active = definition.id().equalsIgnoreCase(selected);
            String color = active ? "<green>" : owned ? "<white>" : "<dark_gray>";
            List<String> lore = owned ? List.of("<gray>Preview → " + definition.markup() + "</gray>", active ? "<green>Equipped</green>" : "<yellow>Click → equip</yellow>") : List.of("<gray>Locked</gray>");
            setItem(slot++, item(owned ? Material.NAME_TAG : Material.IRON_BARS, color + "<bold>" + safe(definition.displayName()) + "</bold>" + (active ? "</green>" : owned ? "</white>" : "</dark_gray>"), lore), owned ? event -> select(definition.id()) : null);
        }
        setItem(36, item(Material.BARRIER, "<red><bold>CLEAR</bold></red>", List.of("<gray>Remove your selected tag.</gray>")), event -> plugin.getTagService().clear(viewer.getUniqueId()).whenComplete((success, error) -> refreshMain()));
        setItem(40, item(Material.BARRIER, "<red><bold>CLOSE</bold></red>", List.of("<gray>Close this menu.</gray>")), event -> viewer.closeInventory());
    }

    private void select(String id) { plugin.getTagService().select(viewer.getUniqueId(), id).whenComplete((success, error) -> refreshMain()); }
    private void refreshMain() { if (plugin.isEnabled()) org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> { if (viewer.isOnline() && isActive()) refresh(); }); }
    private void fill() { ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of()); for (int i = 0; i < 45; i++) setItem(i, pane, null); }
    private static ItemStack item(Material material, String name, List<String> lore) { return new ItemBuilder(material).name(Messages.parse(name)).loreComponents(lore.stream().map(Messages::parse).toList()).build(); }
    private static String safe(String value) { return value == null ? "" : value.replace("<", "‹").replace(">", "›"); }
}
