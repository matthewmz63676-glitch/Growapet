package me.growapet.gui;

import me.growapet.GrowAPet;
import me.growapet.daily.DailyManager;
import me.growapet.utils.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.List;

public final class DailyMenu extends Menu {
    private final GrowAPet plugin;
    private DailyManager.Status status;

    public DailyMenu(GrowAPet plugin, Player viewer, DailyManager.Status status) {
        super(viewer, Messages.parse("<light_purple><bold>DAILY REWARD</bold></light_purple>"), 27);
        this.plugin = plugin; this.status = status;
    }

    @Override public void build() {
        ItemStack pane = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < 27; slot++) setItem(slot, pane, null);
        boolean ready = status.ready(System.currentTimeMillis());
        setItem(13, item(ready ? Material.CHEST_MINECART : Material.MINECART,
                ready ? "<light_purple><bold>CLAIM REWARD</bold></light_purple>" : "<yellow><bold>REWARD ON COOLDOWN</bold></yellow>", List.of(
                        "<gray>• Reward → <white>1 Credit</white></gray>",
                        "<gray>• Claims completed → <white>" + status.claimCount() + "</white></gray>",
                        "<gray>• Availability → <white>" + (ready ? "Ready now" : remaining(status.remainingMillis(System.currentTimeMillis()))) + "</white></gray>",
                        "", ready ? "<light_purple>Click → claim your reward</light_purple>" : "<dark_gray>Return when the timer expires.</dark_gray>")),
                ready ? event -> claim() : null);
        setItem(22, item(Material.BARRIER, "<red><bold>CLOSE</bold></red>", List.of("<gray>• Click → close this menu</gray>")), event -> viewer.closeInventory());
    }

    private void claim() {
        plugin.getDailyManager().claim(viewer).thenCompose(ignored -> plugin.getDailyManager().status(viewer.getUniqueId()))
                .thenAccept(updated -> org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    status = updated;
                    if (isActive() && viewer.getOpenInventory().getTopInventory() == getInventory()) refresh();
                }));
    }

    private static String remaining(long millis) {
        Duration value = Duration.ofMillis(Math.max(0, millis));
        long hours = value.toHours(), minutes = value.minusHours(hours).toMinutes();
        return hours + "h " + minutes + "m";
    }
    private static ItemStack item(Material material, String name, List<String> lore) {
        return new ItemBuilder(material).name(Messages.parse(name)).loreComponents(lore.stream().map(Messages::parse).toList()).build();
    }
}
