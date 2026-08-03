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

public enum ToolTier {
    WOOD(1, "Wooden Tool", Material.WOODEN_SWORD, false, Rarity.COMMON, 800L, 0L, 0.05),
    STONE(2, "Stone Tool", Material.STONE_SWORD, false, Rarity.COMMON, 3000L, 0L, 0.05),
    IRON(3, "Iron Tool", Material.IRON_SWORD, false, Rarity.UNCOMMON, 12000L, 0L, 0.05),
    GOLD(4, "Golden Tool", Material.GOLDEN_SWORD, false, Rarity.RARE, 40000L, 0L, 0.05),
    DIAMOND(5, "Diamond Tool", Material.DIAMOND_SWORD, false, Rarity.EPIC, 120000L, 50L, 0.05),
    NETHERITE(6, "Netherite Tool", Material.NETHERITE_SWORD, false, Rarity.LEGENDARY, 350000L, 150L, 0.05),
    MYTHIC(7, "Mythic Blade", Material.NETHERITE_SWORD, true, Rarity.MYTHIC, 800000L, 400L, 0.05),
    DIVINE(8, "Divine Blade", Material.NETHERITE_SWORD, true, Rarity.DIVINE, 2000000L, 1000L, 0.05);

    private final int tier;
    private final String displayName;
    private final Material icon;
    private final boolean glow;
    private final Rarity rarity;
    private final long coinCost;
    private final long gemCost;
    private final double damageMultiplierBonus;

    private ToolTier(int tier, String displayName, Material icon, boolean glow, Rarity rarity, long coinCost, long gemCost, double damageMultiplierBonus) {
        this.tier = tier;
        this.displayName = displayName;
        this.icon = icon;
        this.glow = glow;
        this.rarity = rarity;
        this.coinCost = coinCost;
        this.gemCost = gemCost;
        this.damageMultiplierBonus = damageMultiplierBonus;
    }

    public static ToolTier byTier(int tier) {
        for (ToolTier tool : ToolTier.values()) {
            if (tool.tier != tier) continue;
            return tool;
        }
        return null;
    }

    public static double cumulativeBonus(int ownedTiers) {
        double total = 0.0;
        for (ToolTier tool : ToolTier.values()) {
            if (tool.tier > ownedTiers) continue;
            total += tool.damageMultiplierBonus;
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
    public double getDamageMultiplierBonus() {
        return this.damageMultiplierBonus;
    }
}

