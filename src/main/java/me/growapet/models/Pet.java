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
    private UUID owner;
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
    private UUID entityUuid;
    private String world;
    private double x;
    private double y;
    private double z;

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
        this.exp = this.exp > Long.MAX_VALUE - amount ? Long.MAX_VALUE : this.exp + amount;
        long required = Pet.expToLevelUp(this.level);
        while (this.exp >= required && this.level < Integer.MAX_VALUE) {
            this.exp -= required;
            ++this.level;
            this.damageMultiplier = Math.min(1_000_000.0, this.damageMultiplier + 0.01);
            this.coinMultiplier = Math.min(1_000_000.0, this.coinMultiplier + 0.005);
            this.gemMultiplier = Math.min(1_000_000.0, this.gemMultiplier + 0.005);
            required = Pet.expToLevelUp(this.level);
        }
    }

    public UUID getEntityUuid() { return entityUuid; }
    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public void setEntityUuid(UUID entityUuid) { this.entityUuid = entityUuid; }
    public void setLocation(String world, double x, double y, double z) { this.world = world; this.x = x; this.y = y; this.z = z; }

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

    public void setOwner(UUID owner) { this.owner = owner; }

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
        this.level = Math.max(1, level);
    }

    @Generated
    public void setExp(long exp) {
        this.exp = Math.max(0, exp);
    }

    @Generated
    public void setDamageMultiplier(double damageMultiplier) {
        this.damageMultiplier = saneMultiplier(damageMultiplier);
    }

    @Generated
    public void setCoinMultiplier(double coinMultiplier) {
        this.coinMultiplier = saneMultiplier(coinMultiplier);
    }

    @Generated
    public void setGemMultiplier(double gemMultiplier) {
        this.gemMultiplier = saneMultiplier(gemMultiplier);
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

    private static double saneMultiplier(double value) { return Double.isFinite(value) ? Math.max(0.0, Math.min(1_000_000.0, value)) : 1.0; }
}
