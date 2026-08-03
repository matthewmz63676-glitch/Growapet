/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  org.bukkit.Material
 */
package me.growapet.shop;

import java.util.function.BiConsumer;
import lombok.Generated;
import me.growapet.models.PlayerData;
import org.bukkit.Material;

public enum ShopUpgrade {
    COIN_BOOST("coin_boost", "&6Coin Boost", Material.GOLD_INGOT, Currency.COINS, 250L, 1.15, 0.02, 50, (data, value) -> data.setCoinMultiplier((double)value)),
    GEM_BOOST("gem_boost", "&aGem Boost", Material.EMERALD, Currency.GEMS, 15L, 1.15, 0.02, 50, (data, value) -> data.setGemMultiplier((double)value)),
    EXP_BOOST("exp_boost", "&bExperience Boost", Material.EXPERIENCE_BOTTLE, Currency.COINS, 200L, 1.15, 0.02, 50, (data, value) -> data.setExpMultiplier((double)value)),
    DAMAGE_BOOST("damage_boost", "&cDamage Boost", Material.IRON_SWORD, Currency.COINS, 400L, 1.18, 0.03, 40, (data, value) -> data.setDamageMultiplier((double)value)),
    CRIT_CHANCE("crit_chance", "&dCritical Chance", Material.SPECTRAL_ARROW, Currency.GEMS, 300L, 1.2, 0.01, 30, (data, value) -> data.setCriticalChance((double)value)),
    CRIT_DAMAGE("crit_damage", "&5Critical Damage", Material.NETHERITE_SWORD, Currency.GEMS, 500L, 1.2, 0.05, 20, (data, value) -> data.setCriticalDamage((double)value));

    private final String id;
    private final String displayName;
    private final Material icon;
    private final Currency currency;
    private final long baseCost;
    private final double costGrowth;
    private final double increment;
    private final int maxLevel;
    private final BiConsumer<PlayerData, Double> valueSetter;

    private ShopUpgrade(String id, String displayName, Material icon, Currency currency, long baseCost, double costGrowth, double increment, int maxLevel, BiConsumer<PlayerData, Double> valueSetter) {
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.currency = currency;
        this.baseCost = baseCost;
        this.costGrowth = costGrowth;
        this.increment = increment;
        this.maxLevel = maxLevel;
        this.valueSetter = valueSetter;
    }

    public double baseValue() {
        return this == CRIT_DAMAGE ? 1.5 : (this == CRIT_CHANCE ? 0.0 : 1.0);
    }

    public long costForLevel(int currentLevel) {
        return Math.round((double)this.baseCost * Math.pow(this.costGrowth, currentLevel));
    }

    public double valueAtLevel(int level) {
        return this.baseValue() + this.increment * (double)level;
    }

    public void apply(PlayerData data, int level) {
        this.valueSetter.accept(data, this.valueAtLevel(level));
    }

    @Generated
    public String getId() {
        return this.id;
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
    public Currency getCurrency() {
        return this.currency;
    }

    @Generated
    public long getBaseCost() {
        return this.baseCost;
    }

    @Generated
    public double getCostGrowth() {
        return this.costGrowth;
    }

    @Generated
    public double getIncrement() {
        return this.increment;
    }

    @Generated
    public int getMaxLevel() {
        return this.maxLevel;
    }

    @Generated
    public BiConsumer<PlayerData, Double> getValueSetter() {
        return this.valueSetter;
    }

    public static enum Currency {
        COINS,
        GEMS;

    }
}

