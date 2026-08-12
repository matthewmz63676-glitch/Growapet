package me.growapet.models;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Mutable cached player state. All mutations are performed on the server thread. */
public final class PlayerData {
    private final UUID uuid;
    private String name;
    private long coins;
    private long gems;
    private long credits;
    private int level = 1;
    private long exp;
    private double expMultiplier = 1.0;
    private double coinMultiplier = 1.0;
    private double gemMultiplier = 1.0;
    private double damageMultiplier = 1.0;
    private double criticalChance;
    private double criticalDamage = 1.5;
    private double petPower;
    private long mobKills;
    private long bossKills;
    private double bossDamage;
    private long eggsHatched;
    private long petsCollected;
    private long trades;
    private long questsCompleted;
    private long playtimeSeconds;
    private int highestPetLevel;
    private String activePetUuid;
    private final Set<String> unlockedZones = new LinkedHashSet<>();
    private final Map<String, Integer> shopLevels = new LinkedHashMap<>();
    private boolean dirty;
    private long revision;
    private boolean economyLocked;

    public PlayerData(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public static long expToLevelUp(int level) {
        return Math.round(50.0 * Math.pow(Math.max(1, level), 1.5)) + 100L;
    }

    public double getExpProgress() {
        return Math.min(1.0, Math.max(0.0, (double) exp / expToLevelUp(level)));
    }

    public void addExp(long amount) {
        long adjusted = scaledPositive(amount, expMultiplier);
        if (adjusted == 0L) return;
        exp = safeAdd(exp, adjusted);
        while (exp >= expToLevelUp(level) && level < Integer.MAX_VALUE) {
            exp -= expToLevelUp(level);
            level++;
        }
        touch();
    }

    public void normalizeExperience() {
        while (exp >= expToLevelUp(level) && level < Integer.MAX_VALUE) { exp -= expToLevelUp(level); level++; touch(); }
    }

    public void addCoins(long amount) { addCoinsRaw(scaledPositive(amount, coinMultiplier)); }
    public void addGems(long amount) { addGemsRaw(scaledPositive(amount, gemMultiplier)); }
    public void addCredits(long amount) { if (amount > 0) { credits = safeAdd(credits, amount); touch(); } }
    public void addCoinsRaw(long amount) { if (amount > 0) { coins = safeAdd(coins, amount); touch(); } }
    public void addGemsRaw(long amount) { if (amount > 0) { gems = safeAdd(gems, amount); touch(); } }

    public boolean removeCoins(long amount) {
        if (amount < 0 || coins < amount) return false;
        coins -= amount;
        touch();
        return true;
    }

    public boolean removeGems(long amount) {
        if (amount < 0 || gems < amount) return false;
        gems -= amount;
        touch();
        return true;
    }

    public boolean removeCredits(long amount) {
        if (amount < 0 || credits < amount) return false;
        credits -= amount;
        touch();
        return true;
    }

    public boolean hasUnlockedZone(String zoneId) { return unlockedZones.contains(zoneId); }
    public void unlockZone(String zoneId) { if (zoneId != null && unlockedZones.add(zoneId)) touch(); }
    public void revokeZone(String zoneId){if(unlockedZones.remove(zoneId))touch();}
    public boolean tryLockEconomy(){if(economyLocked)return false;economyLocked=true;return true;}public void unlockEconomy(){economyLocked=false;}public boolean isEconomyLocked(){return economyLocked;}
    public int getShopLevel(String id) { return shopLevels.getOrDefault(id, 0); }
    public void setShopLevel(String id, int value) { shopLevels.put(id, Math.max(0, value)); touch(); }
    public void incrementMobKills() { mobKills = safeAdd(mobKills, 1); touch(); }
    public void incrementBossKills() { bossKills = safeAdd(bossKills, 1); touch(); }
    public void incrementEggsHatched() { eggsHatched = safeAdd(eggsHatched, 1); touch(); }
    public void incrementPetsCollected() { petsCollected = safeAdd(petsCollected, 1); touch(); }
    public void incrementTrades() { trades = safeAdd(trades, 1); touch(); }
    public void incrementQuestsCompleted() { questsCompleted = safeAdd(questsCompleted, 1); touch(); }
    public void addBossDamage(double amount) { if (Double.isFinite(amount) && amount > 0) { bossDamage += amount; touch(); } }
    public void addPlaytime(long seconds) { if (seconds > 0) { playtimeSeconds = safeAdd(playtimeSeconds, seconds); touch(); } }

    private static long scaledPositive(long amount, double multiplier) {
        if (amount <= 0 || !Double.isFinite(multiplier) || multiplier <= 0) return 0;
        double scaled = amount * multiplier;
        return scaled >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, Math.round(scaled));
    }

    private static long safeAdd(long current, long amount) {
        if (amount <= 0) return current;
        return current > Long.MAX_VALUE - amount ? Long.MAX_VALUE : current + amount;
    }

    private void touch() { dirty = true; revision++; }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public long getCoins() { return coins; }
    public long getGems() { return gems; }
    public long getCredits() { return credits; }
    public int getLevel() { return level; }
    public long getExp() { return exp; }
    public double getExpMultiplier() { return expMultiplier; }
    public double getCoinMultiplier() { return coinMultiplier; }
    public double getGemMultiplier() { return gemMultiplier; }
    public double getDamageMultiplier() { return damageMultiplier; }
    public double getCriticalChance() { return criticalChance; }
    public double getCriticalDamage() { return criticalDamage; }
    public double getPetPower() { return petPower; }
    public long getMobKills() { return mobKills; }
    public long getBossKills() { return bossKills; }
    public double getBossDamage() { return bossDamage; }
    public long getEggsHatched() { return eggsHatched; }
    public long getPetsCollected() { return petsCollected; }
    public long getTrades() { return trades; }
    public long getQuestsCompleted() { return questsCompleted; }
    public long getPlaytimeSeconds() { return playtimeSeconds; }
    public int getHighestPetLevel() { return highestPetLevel; }
    public String getActivePetUuid() { return activePetUuid; }
    public Set<String> getUnlockedZones() { return unlockedZones; }
    public Map<String, Integer> getShopLevels() { return shopLevels; }
    public boolean isDirty() { return dirty; }
    public long getRevision() { return revision; }

    public void setName(String value) { if (value != null && !value.equals(name)) { name = value; touch(); } }
    public void setCoins(long value) { coins = Math.max(0, value); touch(); }
    public void setGems(long value) { gems = Math.max(0, value); touch(); }
    public void setCredits(long value) { credits = Math.max(0, value); touch(); }
    public void setLevel(int value) { level = Math.max(1, value); touch(); }
    public void setExp(long value) { exp = Math.max(0, value); touch(); }
    public void setExpMultiplier(double value) { expMultiplier = saneMultiplier(value); touch(); }
    public void setCoinMultiplier(double value) { coinMultiplier = saneMultiplier(value); touch(); }
    public void setGemMultiplier(double value) { gemMultiplier = saneMultiplier(value); touch(); }
    public void setDamageMultiplier(double value) { damageMultiplier = saneMultiplier(value); touch(); }
    public void setCriticalChance(double value) { criticalChance = Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; touch(); }
    public void setCriticalDamage(double value) { criticalDamage = saneMultiplier(value); touch(); }
    public void setPetPower(double value) { petPower = Double.isFinite(value) ? Math.max(0, value) : 0; touch(); }
    public void setMobKills(long value) { mobKills = Math.max(0, value); touch(); }
    public void setBossKills(long value) { bossKills = Math.max(0, value); touch(); }
    public void setBossDamage(double value) { bossDamage = Double.isFinite(value) ? Math.max(0, value) : 0; touch(); }
    public void setEggsHatched(long value) { eggsHatched = Math.max(0, value); touch(); }
    public void setPetsCollected(long value) { petsCollected = Math.max(0, value); touch(); }
    public void setTrades(long value) { trades = Math.max(0, value); touch(); }
    public void setQuestsCompleted(long value) { questsCompleted = Math.max(0, value); touch(); }
    public void setPlaytimeSeconds(long value) { playtimeSeconds = Math.max(0, value); touch(); }
    public void setHighestPetLevel(int value) { highestPetLevel = Math.max(0, value); touch(); }
    public void setActivePetUuid(String value) { activePetUuid = value; touch(); }
    public void setDirty(boolean value) { dirty = value; }

    private static double saneMultiplier(double value) {
        return Double.isFinite(value) ? Math.max(0, value) : 1.0;
    }
}
