/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 */
package me.growapet.models;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.Generated;

public class PlayerData {
    private final UUID uuid;
    private String name;
    private long coins;
    private long gems;
    private long credits;
    private int level = 1;
    private long exp = 0L;
    private double expMultiplier = 1.0;
    private double coinMultiplier = 1.0;
    private double gemMultiplier = 1.0;
    private double damageMultiplier = 1.0;
    private double criticalChance = 0.0;
    private double criticalDamage = 1.5;
    private long mobKills = 0L;
    private long bossKills = 0L;
    private long eggsHatched = 0L;
    private long petsCollected = 0L;
    private long trades = 0L;
    private long questsCompleted = 0L;
    private long playtimeSeconds = 0L;
    private String activePetUuid;
    private final Set<String> unlockedZones = new LinkedHashSet<String>();
    private final Map<String, Integer> shopLevels = new LinkedHashMap<String, Integer>();
    private boolean dirty = false;

    public boolean hasUnlockedZone(String zoneId) {
        return this.unlockedZones.contains(zoneId);
    }

    public void unlockZone(String zoneId) {
        this.unlockedZones.add(zoneId);
        this.dirty = true;
    }

    public int getShopLevel(String upgradeId) {
        return this.shopLevels.getOrDefault(upgradeId, 0);
    }

    public void setShopLevel(String upgradeId, int level) {
        this.shopLevels.put(upgradeId, level);
        this.dirty = true;
    }

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public static long expToLevelUp(int level) {
        return Math.round(50.0 * Math.pow(level, 1.5)) + 100L;
    }

    public double getExpProgress() {
        long required = PlayerData.expToLevelUp(this.level);
        if (required <= 0L) {
            return 0.0;
        }
        return Math.min(1.0, (double)this.exp / (double)required);
    }

    public void addExp(long amount) {
        if (amount <= 0L) {
            return;
        }
        this.exp += Math.round((double)amount * this.expMultiplier);
        this.dirty = true;
        long required = PlayerData.expToLevelUp(this.level);
        while (this.exp >= required) {
            this.exp -= required;
            ++this.level;
            required = PlayerData.expToLevelUp(this.level);
        }
    }

    public void addCoins(long amount) {
        if (amount <= 0L) {
            return;
        }
        this.coins += Math.round((double)amount * this.coinMultiplier);
        this.dirty = true;
    }

    public void addGems(long amount) {
        if (amount <= 0L) {
            return;
        }
        this.gems += Math.round((double)amount * this.gemMultiplier);
        this.dirty = true;
    }

    public boolean removeCoins(long amount) {
        if (this.coins < amount) {
            return false;
        }
        this.coins -= amount;
        this.dirty = true;
        return true;
    }

    public boolean removeGems(long amount) {
        if (this.gems < amount) {
            return false;
        }
        this.gems -= amount;
        this.dirty = true;
        return true;
    }

    @Generated
    public UUID getUuid() {
        return this.uuid;
    }

    @Generated
    public String getName() {
        return this.name;
    }

    @Generated
    public long getCoins() {
        return this.coins;
    }

    @Generated
    public long getGems() {
        return this.gems;
    }

    @Generated
    public long getCredits() {
        return this.credits;
    }

    @Generated
    public int getLevel() {
        return this.level;
    }

    @Generated
    public long getExp() {
        return this.exp;
    }

    @Generated
    public double getExpMultiplier() {
        return this.expMultiplier;
    }

    @Generated
    public double getCoinMultiplier() {
        return this.coinMultiplier;
    }

    @Generated
    public double getGemMultiplier() {
        return this.gemMultiplier;
    }

    @Generated
    public double getDamageMultiplier() {
        return this.damageMultiplier;
    }

    @Generated
    public double getCriticalChance() {
        return this.criticalChance;
    }

    @Generated
    public double getCriticalDamage() {
        return this.criticalDamage;
    }

    @Generated
    public long getMobKills() {
        return this.mobKills;
    }

    @Generated
    public long getBossKills() {
        return this.bossKills;
    }

    @Generated
    public long getEggsHatched() {
        return this.eggsHatched;
    }

    @Generated
    public long getPetsCollected() {
        return this.petsCollected;
    }

    @Generated
    public long getTrades() {
        return this.trades;
    }

    @Generated
    public long getQuestsCompleted() {
        return this.questsCompleted;
    }

    @Generated
    public long getPlaytimeSeconds() {
        return this.playtimeSeconds;
    }

    @Generated
    public String getActivePetUuid() {
        return this.activePetUuid;
    }

    @Generated
    public Set<String> getUnlockedZones() {
        return this.unlockedZones;
    }

    @Generated
    public Map<String, Integer> getShopLevels() {
        return this.shopLevels;
    }

    @Generated
    public boolean isDirty() {
        return this.dirty;
    }

    @Generated
    public void setName(String name) {
        this.name = name;
    }

    @Generated
    public void setCoins(long coins) {
        this.coins = coins;
    }

    @Generated
    public void setGems(long gems) {
        this.gems = gems;
    }

    @Generated
    public void setCredits(long credits) {
        this.credits = credits;
    }

    @Generated
    public void setLevel(int level) {
        this.level = level;
    }

    @Generated
    public void setExp(long exp) {
        this.exp = exp;
    }

    @Generated
    public void setExpMultiplier(double expMultiplier) {
        this.expMultiplier = expMultiplier;
    }

    @Generated
    public void setCoinMultiplier(double coinMultiplier) {
        this.coinMultiplier = coinMultiplier;
    }

    @Generated
    public void setGemMultiplier(double gemMultiplier) {
        this.gemMultiplier = gemMultiplier;
    }

    @Generated
    public void setDamageMultiplier(double damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }

    @Generated
    public void setCriticalChance(double criticalChance) {
        this.criticalChance = criticalChance;
    }

    @Generated
    public void setCriticalDamage(double criticalDamage) {
        this.criticalDamage = criticalDamage;
    }

    @Generated
    public void setMobKills(long mobKills) {
        this.mobKills = mobKills;
    }

    @Generated
    public void setBossKills(long bossKills) {
        this.bossKills = bossKills;
    }

    @Generated
    public void setEggsHatched(long eggsHatched) {
        this.eggsHatched = eggsHatched;
    }

    @Generated
    public void setPetsCollected(long petsCollected) {
        this.petsCollected = petsCollected;
    }

    @Generated
    public void setTrades(long trades) {
        this.trades = trades;
    }

    @Generated
    public void setQuestsCompleted(long questsCompleted) {
        this.questsCompleted = questsCompleted;
    }

    @Generated
    public void setPlaytimeSeconds(long playtimeSeconds) {
        this.playtimeSeconds = playtimeSeconds;
    }

    @Generated
    public void setActivePetUuid(String activePetUuid) {
        this.activePetUuid = activePetUuid;
    }

    @Generated
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }
}

