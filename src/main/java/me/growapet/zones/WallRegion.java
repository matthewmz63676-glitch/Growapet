/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  org.bukkit.Location
 *  org.bukkit.World
 */
package me.growapet.zones;

import lombok.Generated;
import org.bukkit.Location;
import org.bukkit.World;

public class WallRegion {
    private final World world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final Location cameraPose;

    public WallRegion(World world, int x1, int y1, int z1, int x2, int y2, int z2, Location cameraPose) {
        this.world = world;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
        this.cameraPose = cameraPose;
    }

    public long blockCount() {
        return (long)(this.maxX - this.minX + 1) * (long)(this.maxY - this.minY + 1) * (long)(this.maxZ - this.minZ + 1);
    }

    public boolean contains(Location loc) {
        if (loc == null || loc.getWorld() == null || !loc.getWorld().equals((Object)this.world)) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
    }

    @Generated
    public World getWorld() {
        return this.world;
    }

    @Generated
    public int getMinX() {
        return this.minX;
    }

    @Generated
    public int getMinY() {
        return this.minY;
    }

    @Generated
    public int getMinZ() {
        return this.minZ;
    }

    @Generated
    public int getMaxX() {
        return this.maxX;
    }

    @Generated
    public int getMaxY() {
        return this.maxY;
    }

    @Generated
    public int getMaxZ() {
        return this.maxZ;
    }

    @Generated
    public Location getCameraPose() {
        return this.cameraPose;
    }
}

