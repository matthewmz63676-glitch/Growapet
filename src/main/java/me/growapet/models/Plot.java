/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  org.bukkit.Location
 */
package me.growapet.models;

import java.util.UUID;
import lombok.Generated;
import org.bukkit.Location;

public class Plot {
    private final UUID owner;
    private final int id;
    private Location center;
    private int size = 32;
    private int petLimit = 5;
    private int eggLimit = 3;

    public Plot(UUID owner, int id, Location center) {
        this.owner = owner;
        this.id = id;
        this.center = center;
    }

    public boolean contains(Location loc) {
        if (loc == null || this.center == null) {
            return false;
        }
        if (loc.getWorld() == null || this.center.getWorld() == null) {
            return false;
        }
        if (!loc.getWorld().equals((Object)this.center.getWorld())) {
            return false;
        }
        double half = (double)this.size / 2.0;
        return Math.abs(loc.getX() - this.center.getX()) <= half && Math.abs(loc.getZ() - this.center.getZ()) <= half;
    }

    @Generated
    public UUID getOwner() {
        return this.owner;
    }

    @Generated
    public int getId() {
        return this.id;
    }

    @Generated
    public Location getCenter() {
        return this.center;
    }

    @Generated
    public int getSize() {
        return this.size;
    }

    @Generated
    public int getPetLimit() {
        return this.petLimit;
    }

    @Generated
    public int getEggLimit() {
        return this.eggLimit;
    }

    @Generated
    public void setCenter(Location center) {
        this.center = center;
    }

    @Generated
    public void setSize(int size) {
        this.size = size;
    }

    @Generated
    public void setPetLimit(int petLimit) {
        this.petLimit = petLimit;
    }

    @Generated
    public void setEggLimit(int eggLimit) {
        this.eggLimit = eggLimit;
    }
}

