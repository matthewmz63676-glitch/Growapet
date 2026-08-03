/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 */
package me.growapet.shop;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import me.growapet.shop.GearTier;
import me.growapet.shop.ShopUpgrade;
import me.growapet.shop.ToolTier;
import org.bukkit.entity.Player;

public class ShopManager {
    private static final String GEAR_KEY = "gear_tier";
    private static final String TOOL_KEY = "tool_tier";
    private final GrowAPet plugin;

    public ShopManager(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public void applyAll(PlayerData data) {
        for (ShopUpgrade upgrade : ShopUpgrade.values()) {
            if (upgrade == ShopUpgrade.COIN_BOOST || upgrade == ShopUpgrade.DAMAGE_BOOST) continue;
            upgrade.apply(data, data.getShopLevel(upgrade.getId()));
        }
        this.recomputeDerivedMultipliers(data);
    }

    private void recomputeDerivedMultipliers(PlayerData data) {
        int coinBoostLevel = data.getShopLevel(ShopUpgrade.COIN_BOOST.getId());
        int gearTier = data.getShopLevel(GEAR_KEY);
        data.setCoinMultiplier(ShopUpgrade.COIN_BOOST.valueAtLevel(coinBoostLevel) + GearTier.cumulativeBonus(gearTier));
        int damageBoostLevel = data.getShopLevel(ShopUpgrade.DAMAGE_BOOST.getId());
        int toolTier = data.getShopLevel(TOOL_KEY);
        data.setDamageMultiplier(ShopUpgrade.DAMAGE_BOOST.valueAtLevel(damageBoostLevel) + ToolTier.cumulativeBonus(toolTier));
    }

    public boolean canAfford(PlayerData data, ShopUpgrade upgrade) {
        long cost = upgrade.costForLevel(data.getShopLevel(upgrade.getId()));
        return upgrade.getCurrency() == ShopUpgrade.Currency.COINS ? data.getCoins() >= cost : data.getGems() >= cost;
    }

    public boolean purchase(Player player, ShopUpgrade upgrade) {
        boolean paid;
        PlayerData data = this.plugin.getPlayerManager().get(player);
        if (data == null) {
            return false;
        }
        int level = data.getShopLevel(upgrade.getId());
        if (level >= upgrade.getMaxLevel()) {
            player.sendMessage("\u00a7c" + upgrade.getDisplayName() + " \u00a7cis already at its maximum level!");
            return false;
        }
        long cost = upgrade.costForLevel(level);
        boolean bl = paid = upgrade.getCurrency() == ShopUpgrade.Currency.COINS ? data.removeCoins(cost) : data.removeGems(cost);
        if (!paid) {
            String currencyName = upgrade.getCurrency() == ShopUpgrade.Currency.COINS ? "coins" : "gems";
            player.sendMessage("\u00a7cYou need \u00a7e" + cost + " " + currencyName + " \u00a7cfor the next level of " + upgrade.getDisplayName() + "\u00a7c!");
            return false;
        }
        data.setShopLevel(upgrade.getId(), level + 1);
        if (upgrade == ShopUpgrade.COIN_BOOST || upgrade == ShopUpgrade.DAMAGE_BOOST) {
            this.recomputeDerivedMultipliers(data);
        } else {
            upgrade.apply(data, level + 1);
        }
        player.sendMessage("\u00a7aPurchased \u00a7e" + upgrade.getDisplayName() + " \u00a7alevel \u00a7e" + (level + 1) + "\u00a7a!");
        return true;
    }

    public int getGearTier(PlayerData data) {
        return data.getShopLevel(GEAR_KEY);
    }

    public int getToolTier(PlayerData data) {
        return data.getShopLevel(TOOL_KEY);
    }

    public boolean canAffordGear(PlayerData data, GearTier gear) {
        return data.getCoins() >= gear.getCoinCost() && data.getGems() >= gear.getGemCost();
    }

    public boolean canAffordTool(PlayerData data, ToolTier tool) {
        return data.getCoins() >= tool.getCoinCost() && data.getGems() >= tool.getGemCost();
    }

    public boolean purchaseGear(Player player, GearTier gear) {
        PlayerData data = this.plugin.getPlayerManager().get(player);
        if (data == null) {
            return false;
        }
        int owned = this.getGearTier(data);
        if (gear.getTier() != owned + 1) {
            player.sendMessage("\u00a7cYou need to own the previous Gear tier first!");
            return false;
        }
        if (!this.canAffordGear(data, gear)) {
            player.sendMessage("\u00a7cYou need \u00a7e" + gear.getCoinCost() + " coins" + (String)(gear.getGemCost() > 0L ? " &cand \u00a7e" + gear.getGemCost() + " gems" : "") + " \u00a7cfor " + gear.getDisplayName() + "\u00a7c!");
            return false;
        }
        data.removeCoins(gear.getCoinCost());
        data.removeGems(gear.getGemCost());
        data.setShopLevel(GEAR_KEY, gear.getTier());
        this.recomputeDerivedMultipliers(data);
        player.sendMessage("\u00a7aPurchased \u00a7e" + gear.getDisplayName() + "\u00a7a!");
        return true;
    }

    public boolean purchaseTool(Player player, ToolTier tool) {
        PlayerData data = this.plugin.getPlayerManager().get(player);
        if (data == null) {
            return false;
        }
        int owned = this.getToolTier(data);
        if (tool.getTier() != owned + 1) {
            player.sendMessage("\u00a7cYou need to own the previous Tool tier first!");
            return false;
        }
        if (!this.canAffordTool(data, tool)) {
            player.sendMessage("\u00a7cYou need \u00a7e" + tool.getCoinCost() + " coins" + (String)(tool.getGemCost() > 0L ? " &cand \u00a7e" + tool.getGemCost() + " gems" : "") + " \u00a7cfor " + tool.getDisplayName() + "\u00a7c!");
            return false;
        }
        data.removeCoins(tool.getCoinCost());
        data.removeGems(tool.getGemCost());
        data.setShopLevel(TOOL_KEY, tool.getTier());
        this.recomputeDerivedMultipliers(data);
        player.sendMessage("\u00a7aPurchased \u00a7e" + tool.getDisplayName() + "\u00a7a!");
        return true;
    }
}

