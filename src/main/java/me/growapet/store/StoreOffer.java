/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  org.bukkit.Material
 */
package me.growapet.store;

import lombok.Generated;
import org.bukkit.Material;

public enum StoreOffer {
    COIN_SURGE("Coin Surge", Material.GOLD_INGOT, 10L, "&6+5,000 Coins"),
    GEM_SURGE("Gem Surge", Material.DIAMOND, 20L, "&a+250 Gems"),
    MYSTERY_CRATE("Mystery Crate", Material.EMERALD, 50L, "&dRandom bonus of Coins & Gems");

    private final String displayName;
    private final Material icon;
    private final long creditPrice;
    private final String rewardLabel;

    private StoreOffer(String displayName, Material icon, long creditPrice, String rewardLabel) {
        this.displayName = displayName;
        this.icon = icon;
        this.creditPrice = creditPrice;
        this.rewardLabel = rewardLabel;
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
    public long getCreditPrice() {
        return this.creditPrice;
    }

    @Generated
    public String getRewardLabel() {
        return this.rewardLabel;
    }
}

