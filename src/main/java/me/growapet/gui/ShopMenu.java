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
import java.util.ArrayList;
import java.util.Locale;
import me.growapet.GrowAPet;
import me.growapet.gui.ItemBuilder;
import me.growapet.gui.Menu;
import me.growapet.gui.MultiplierShopMenu;
import me.growapet.models.PlayerData;
import me.growapet.shop.GearTier;
import me.growapet.shop.ToolTier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ShopMenu
extends Menu {
    private static final NumberFormat FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private final GrowAPet plugin;

    public ShopMenu(GrowAPet plugin, Player viewer) {
        super(viewer, "&8Shop &7- Gear & Tools", 54);
        this.plugin = plugin;
    }

    @Override
    public void build() {
        PlayerData data = this.plugin.getPlayerManager().get(this.viewer);
        if (data == null) {
            return;
        }
        this.fillBorder();
        int ownedGear = this.plugin.getShopManager().getGearTier(data);
        int[] gearSlots = new int[]{10, 19, 28, 37};
        for (GearTier gear : GearTier.values()) {
            this.setItem(gearSlots[gear.getTier() - 1], this.gearItem(data, gear, ownedGear).build(), e -> {
                if (this.plugin.getShopManager().purchaseGear(this.viewer, gear)) {
                    this.refresh();
                }
            });
        }
        int ownedTools = this.plugin.getShopManager().getToolTier(data);
        int[] toolSlots = new int[]{15, 16, 24, 25, 33, 34, 42, 43};
        for (ToolTier tool : ToolTier.values()) {
            this.setItem(toolSlots[tool.getTier() - 1], this.toolItem(data, tool, ownedTools).build(), e -> {
                if (this.plugin.getShopManager().purchaseTool(this.viewer, tool)) {
                    this.refresh();
                }
            });
        }
        ItemStack multipliers = new ItemBuilder(Material.NETHER_STAR).name("&d&lMultiplier Shop").lore("&7Coin / Gem / Exp / Damage / Crit", "&7percentage upgrades.", "", "&aClick to open!").glow(true).build();
        this.setItem(49, multipliers, e -> new MultiplierShopMenu(this.plugin, this.viewer).open());
        ItemStack balance = new ItemBuilder(Material.PLAYER_HEAD).name("&fYour Balance").lore("&7Coins: &e" + FORMAT.format(data.getCoins()), "&7Gems: &a" + FORMAT.format(data.getGems()), "", "&7Gear bonus: &6+" + String.format(Locale.US, "%.0f%%", GearTier.cumulativeBonus(ownedGear) * 100.0) + " coins", "&7Tool bonus: &c+" + String.format(Locale.US, "%.0f%%", ToolTier.cumulativeBonus(ownedTools) * 100.0) + " damage").build();
        this.setItem(4, balance, null);
    }

    private void fillBorder() {
        int i;
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (i = 0; i < 9; ++i) {
            this.setItem(i, filler, null);
        }
        for (i = 45; i < 54; ++i) {
            this.setItem(i, filler, null);
        }
        for (int row = 1; row < 5; ++row) {
            this.setItem(row * 9, filler, null);
            this.setItem(row * 9 + 8, filler, null);
        }
        ItemStack divider = new ItemBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int row = 1; row < 5; ++row) {
            for (int col : new int[]{2, 3, 4, 5}) {
                this.setItem(row * 9 + col, divider, null);
            }
        }
    }

    private ItemBuilder gearItem(PlayerData data, GearTier gear, int owned) {
        if (gear.getTier() <= owned) {
            return new ItemBuilder(gear.getIcon()).name("&a" + gear.getDisplayName() + " &7(&aOwned&7)").lore("&7Tier " + gear.getTier() + "/4 - " + gear.getRarity().getLabel(), "&7Bonus: &6+" + (int)(gear.getCoinMultiplierBonus() * 100.0) + "% coins").glow(true);
        }
        if (gear.getTier() > owned + 1) {
            return new ItemBuilder(Material.GRAY_DYE).name("&8" + gear.getDisplayName() + " &7(&8Locked&7)").lore("&7Buy Gear tier " + (owned + 1) + " first.");
        }
        boolean afford = this.plugin.getShopManager().canAffordGear(data, gear);
        return new ItemBuilder(gear.getIcon()).name(gear.getRarity().getColor() + gear.getDisplayName()).lore("&7Tier " + gear.getTier() + "/4 - " + gear.getRarity().getLabel(), "&7Bonus: &6+" + (int)(gear.getCoinMultiplierBonus() * 100.0) + "% coins", "", "&7Cost: " + (afford ? "&e" : "&c") + FORMAT.format(gear.getCoinCost()) + " coins", afford ? "&aClick to purchase!" : "&cYou can't afford this yet.");
    }

    private ItemBuilder toolItem(PlayerData data, ToolTier tool, int owned) {
        if (tool.getTier() <= owned) {
            ItemBuilder builder = new ItemBuilder(tool.getIcon()).name("&a" + tool.getDisplayName() + " &7(&aOwned&7)").lore("&7Tier " + tool.getTier() + "/8 - " + tool.getRarity().getLabel(), "&7Bonus: &c+" + (int)(tool.getDamageMultiplierBonus() * 100.0) + "% damage");
            return builder.glow(true);
        }
        if (tool.getTier() > owned + 1) {
            return new ItemBuilder(Material.GRAY_DYE).name("&8" + tool.getDisplayName() + " &7(&8Locked&7)").lore("&7Buy Tool tier " + (owned + 1) + " first.");
        }
        boolean afford = this.plugin.getShopManager().canAffordTool(data, tool);
        ArrayList<String> lore = new ArrayList<String>();
        lore.add("&7Tier " + tool.getTier() + "/8 - " + tool.getRarity().getLabel());
        lore.add("&7Bonus: &c+" + (int)(tool.getDamageMultiplierBonus() * 100.0) + "% damage");
        lore.add("");
        lore.add("&7Cost: " + (afford ? "&e" : "&c") + FORMAT.format(tool.getCoinCost()) + " coins" + (String)(tool.getGemCost() > 0L ? " &7+ " + (afford ? "&b" : "&c") + FORMAT.format(tool.getGemCost()) + " gems" : ""));
        lore.add(afford ? "&aClick to purchase!" : "&cYou can't afford this yet.");
        return new ItemBuilder(tool.getIcon()).name(tool.getRarity().getColor() + tool.getDisplayName()).lore(lore).glow(tool.isGlow());
    }
}

