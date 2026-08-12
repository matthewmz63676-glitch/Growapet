package me.growapet.gui;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import me.growapet.shop.ShopUpgrade;
import me.growapet.utils.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public final class MultiplierShopMenu extends Menu {
    private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);
    private final GrowAPet plugin;

    public MultiplierShopMenu(GrowAPet plugin, Player viewer) {
        super(viewer, Messages.parse("<light_purple><bold>MULTIPLIER SHOP</bold></light_purple>"), 36);
        this.plugin = plugin;
    }

    @Override public void build() {
        PlayerData data = plugin.getPlayerManager().get(viewer);
        if (data == null) return;
        ItemStack pane = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(Messages.parse(" ")).build();
        for (int slot = 0; slot < 36; slot++) setItem(slot, pane, null);
        int[] slots = {10, 11, 12, 14, 15, 16};
        ShopUpgrade[] upgrades = ShopUpgrade.values();
        for (int index = 0; index < upgrades.length; index++) {
            ShopUpgrade upgrade = upgrades[index];
            int level = data.getShopLevel(upgrade.getId());
            boolean maxed = level >= upgrade.getMaxLevel();
            long cost = maxed ? 0 : upgrade.costForLevel(level);
            boolean affordable = maxed || plugin.getShopManager().canAfford(data, upgrade);
            String currency = upgrade.getCurrency() == ShopUpgrade.Currency.COINS ? "Coins" : "Gems";
            ItemBuilder item = new ItemBuilder(upgrade.getIcon()).name(Messages.parse("<light_purple><bold><name></bold></light_purple>", Messages.value("name", plain(upgrade.getDisplayName()))))
                    .loreComponents(List.of(
                            Messages.parse("<gray>• Level → <white><level>/<max></white></gray>", Messages.value("level", level), Messages.value("max", upgrade.getMaxLevel())),
                            Messages.parse("<gray>• Current → <white><value></white></gray>", Messages.value("value", format(upgrade, upgrade.valueAtLevel(level)))),
                            Messages.parse(maxed ? "<gray>• Next → <green>Maximum reached</green></gray>" : "<gray>• Next → <green><value></green></gray>", Messages.value("value", format(upgrade, upgrade.valueAtLevel(Math.min(upgrade.getMaxLevel(), level + 1))))),
                            Messages.parse(""),
                            Messages.parse(maxed ? "<green>Upgrade complete</green>" : "<gray>• Cost → <yellow><cost> <currency></yellow></gray>", Messages.value("cost", NUMBER.format(cost)), Messages.value("currency", currency)),
                            Messages.parse(maxed ? "<dark_gray>No further levels are available.</dark_gray>" : affordable ? "<green>Click → purchase</green>" : "<red>Insufficient balance</red>")));
            setItem(slots[index], item.glow(maxed).build(), event -> { if (!maxed && plugin.getShopManager().purchase(viewer, upgrade)) refresh(); });
        }
        setItem(4, new ItemBuilder(Material.PLAYER_HEAD).name(Messages.parse("<white><bold>YOUR BALANCE</bold></white>"))
                .loreComponents(List.of(Messages.parse("<gray>• Coins → <yellow><coins></yellow></gray>", Messages.value("coins", NUMBER.format(data.getCoins()))), Messages.parse("<gray>• Gems → <aqua><gems></aqua></gray>", Messages.value("gems", NUMBER.format(data.getGems()))))).build(), null);
        setItem(31, new ItemBuilder(Material.ARROW).name(Messages.parse("<red><bold>BACK</bold></red>"))
                .loreComponents(List.of(Messages.parse("<gray>• Click → return to the gear shop</gray>"))).build(), event -> new ShopMenu(plugin, viewer).open());
    }

    private static String format(ShopUpgrade upgrade, double value) {
        return upgrade == ShopUpgrade.CRIT_CHANCE ? String.format(Locale.US, "%.0f%%", value * 100) : String.format(Locale.US, "%.2fx", value);
    }

    private static String plain(String legacy) { return legacy.replaceAll("&[0-9A-FK-ORa-fk-or]", ""); }
}
