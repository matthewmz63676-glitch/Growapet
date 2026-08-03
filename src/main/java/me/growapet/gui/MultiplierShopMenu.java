/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Material
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 */
package me.growapet.gui;

import java.text.NumberFormat;
import java.util.Locale;
import me.growapet.GrowAPet;
import me.growapet.gui.ItemBuilder;
import me.growapet.gui.Menu;
import me.growapet.gui.ShopMenu;
import me.growapet.models.PlayerData;
import me.growapet.shop.ShopUpgrade;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class MultiplierShopMenu
extends Menu {
    private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private final GrowAPet plugin;

    public MultiplierShopMenu(GrowAPet plugin, Player viewer) {
        super(viewer, "&8Shop &7- Multipliers", 27);
        this.plugin = plugin;
    }

    @Override
    public void build() {
        PlayerData data = this.plugin.getPlayerManager().get(this.viewer);
        if (data == null) {
            return;
        }
        ShopUpgrade[] upgrades = ShopUpgrade.values();
        int slot = 10;
        for (ShopUpgrade upgrade : upgrades) {
            ItemBuilder builder;
            String currency;
            int level;
            if (slot % 9 == 8) {
                slot += 2;
            }
            boolean maxed = (level = data.getShopLevel(upgrade.getId())) >= upgrade.getMaxLevel();
            double currentValue = upgrade.valueAtLevel(level);
            String string = currency = upgrade.getCurrency() == ShopUpgrade.Currency.COINS ? "coins" : "gems";
            if (maxed) {
                builder = new ItemBuilder(upgrade.getIcon()).name(upgrade.getDisplayName() + " &7(&aMAX&7)").lore("&7Level: &e" + level + "&7/&e" + upgrade.getMaxLevel(), "&7Current: &f" + this.format(upgrade, currentValue), "", "&aMaximum level reached!").glow(true);
            } else {
                long cost = upgrade.costForLevel(level);
                double nextValue = upgrade.valueAtLevel(level + 1);
                boolean afford = this.plugin.getShopManager().canAfford(data, upgrade);
                builder = new ItemBuilder(upgrade.getIcon()).name(upgrade.getDisplayName()).lore("&7Level: &e" + level + "&7/&e" + upgrade.getMaxLevel(), "&7Current: &f" + this.format(upgrade, currentValue) + " &8-> &a" + this.format(upgrade, nextValue), "", "&7Cost: " + (afford ? "&e" : "&c") + FORMAT.format(cost) + " " + currency, afford ? "&aClick to purchase!" : "&cYou can't afford this yet.");
            }
            this.setItem(slot, builder.build(), e -> {
                if (this.plugin.getShopManager().purchase(this.viewer, upgrade)) {
                    this.refresh();
                }
            });
            ++slot;
        }
        ItemStack back = new ItemBuilder(Material.ARROW).name("&7\u00ab Back to Shop").build();
        this.setItem(22, back, e -> new ShopMenu(this.plugin, this.viewer).open());
        ItemStack balance = new ItemBuilder(Material.PLAYER_HEAD).name("&fYour Balance").lore("&7Coins: &e" + FORMAT.format(data.getCoins()), "&7Gems: &a" + FORMAT.format(data.getGems())).build();
        this.setItem(4, balance, null);
    }

    private String format(ShopUpgrade upgrade, double value) {
        if (upgrade == ShopUpgrade.CRIT_CHANCE) {
            return String.format(Locale.US, "%.0f%%", value * 100.0);
        }
        return String.format(Locale.US, "%.2fx", value);
    }
}

