/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  org.bukkit.Material
 */
package me.growapet.shop;

import lombok.Generated;
import me.growapet.shop.Rarity;
import org.bukkit.Material;

public enum GearTier {
    LEATHER(1, "Leather Gear", Material.LEATHER_CHESTPLATE, false, Rarity.COMMON, 1500L, 0L, 0.1),
    IRON(2, "Iron Gear", Material.IRON_CHESTPLATE, false, Rarity.RARE, 12000L, 0L, 0.1),
    DIAMOND(3, "Diamond Gear", Material.DIAMOND_CHESTPLATE, false, Rarity.EPIC, 60000L, 0L, 0.1),
    NETHERITE(4, "Netherite Gear", Material.NETHERITE_CHESTPLATE, true, Rarity.LEGENDARY, 300000L, 0L, 0.1);

    private final int tier;
    private final String displayName;
    private final Material icon;
    private final boolean glow;
    private final Rarity rarity;
    private final long coinCost;
    private final long gemCost;
    private final double coinMultiplierBonus;

    private GearTier(int tier, String displayName, Material icon, boolean glow, Rarity rarity, long coinCost, long gemCost, double coinMultiplierBonus) {
        this.tier = tier;
        this.displayName = displayName;
        this.icon = icon;
        this.glow = glow;
        this.rarity = rarity;
        this.coinCost = coinCost;
        this.gemCost = gemCost;
        this.coinMultiplierBonus = coinMultiplierBonus;
    }

    public static GearTier byTier(int tier) {
        for (GearTier gear : GearTier.values()) {
            if (gear.tier != tier) continue;
            return gear;
        }
        return null;
    }

    public static double cumulativeBonus(int ownedTiers) {
        double total = 0.0;
        for (GearTier gear : GearTier.values()) {
            if (gear.tier > ownedTiers) continue;
            total += gear.coinMultiplierBonus;
        }
        return total;
    }

    @Generated
    public int getTier() {
        return this.tier;
    }

    @Generated
    public String getDisplayName() {
        return this.displayName;
    }

    @Generated
    public Material getIcon() {
        return this.icon;
    }

    @Generated
    public boolean isGlow() {
        return this.glow;
    }

    @Generated
    public Rarity getRarity() {
        return this.rarity;
    }

    @Generated
    public long getCoinCost() {
        return this.coinCost;
    }

    @Generated
    public long getGemCost() {
        return this.gemCost;
    }

    @Generated
    public double getCoinMultiplierBonus() {
        return this.coinMultiplierBonus;
    }
}

