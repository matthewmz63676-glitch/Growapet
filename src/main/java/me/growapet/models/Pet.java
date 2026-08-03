/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  org.bukkit.entity.EntityType
 */
package me.growapet.models;

import java.util.UUID;
import lombok.Generated;
import org.bukkit.entity.EntityType;

public class Pet {
    private final UUID uuid;
    private final UUID owner;
    private EntityType entityType;
    private String displayName;
    private Rarity rarity;
    private int size;
    private int level = 1;
    private long exp = 0L;
    private double damageMultiplier = 1.0;
    private double coinMultiplier = 1.0;
    private double gemMultiplier = 1.0;
    private String skin;
    private boolean equipped = false;
    private boolean placed = false;

    public Pet(UUID uuid, UUID owner, EntityType entityType, Rarity rarity, int size) {
        this.uuid = uuid;
        this.owner = owner;
        this.entityType = entityType;
        this.rarity = rarity;
        this.size = size;
        this.displayName = rarity.name() + " " + entityType.name();
    }

    public static long expToLevelUp(int level) {
        return Math.round(30.0 * Math.pow(level, 1.4)) + 50L;
    }

    public void addExp(long amount) {
        if (amount <= 0L) {
            return;
        }
        this.exp += amount;
        long required = Pet.expToLevelUp(this.level);
        while (this.exp >= required) {
            this.exp -= required;
            ++this.level;
            this.damageMultiplier += 0.01;
            this.coinMultiplier += 0.005;
            this.gemMultiplier += 0.005;
            required = Pet.expToLevelUp(this.level);
        }
    }

    public static String sizeTierName(int size) {
        if (size < 10) {
            return "Tiny";
        }
        if (size < 25) {
            return "Small";
        }
        if (size < 60) {
            return "Normal";
        }
        if (size < 120) {
            return "Large";
        }
        if (size < 250) {
            return "Huge";
        }
        if (size < 400) {
            return "Massive";
        }
        if (size < 600) {
            return "Titan";
        }
        if (size < 800) {
            return "Colossal";
        }
        return "Mythical";
    }

    @Generated
    public UUID getUuid() {
        return this.uuid;
    }

    @Generated
    public UUID getOwner() {
        return this.owner;
    }

    @Generated
    public EntityType getEntityType() {
        return this.entityType;
    }

    @Generated
    public String getDisplayName() {
        return this.displayName;
    }

    @Generated
    public Rarity getRarity() {
        return this.rarity;
    }

    @Generated
    public int getSize() {
        return this.size;
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
    public double getDamageMultiplier() {
        return this.damageMultiplier;
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
    public String getSkin() {
        return this.skin;
    }

    @Generated
    public boolean isEquipped() {
        return this.equipped;
    }

    @Generated
    public boolean isPlaced() {
        return this.placed;
    }

    @Generated
    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    @Generated
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Generated
    public void setRarity(Rarity rarity) {
        this.rarity = rarity;
    }

    @Generated
    public void setSize(int size) {
        this.size = size;
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
    public void setDamageMultiplier(double damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
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
    public void setSkin(String skin) {
        this.skin = skin;
    }

    @Generated
    public void setEquipped(boolean equipped) {
        this.equipped = equipped;
    }

    @Generated
    public void setPlaced(boolean placed) {
        this.placed = placed;
    }

    public static enum Rarity {
        COMMON,
        UNCOMMON,
        RARE,
        EPIC,
        LEGENDARY,
        MYTHIC,
        DIVINE,
        SECRET,
        EXCLUSIVE;

    }
}

