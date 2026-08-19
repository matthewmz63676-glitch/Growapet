/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  org.bukkit.Location
 */
package me.growapet.zones;

import lombok.Generated;
import me.growapet.zones.WallRegion;
import org.bukkit.Location;

public class Zone {
    private final String id;
    private final String displayName;
    private final int order;
    private final long cost;
    private final long gemCost;
    private final int reqLevel;
    private final Location warp;
    private final WallRegion wall;
    private final String regionId;
    private final boolean free;

    public Zone(String id, String displayName, int order, long cost, long gemCost, int reqLevel, Location warp, WallRegion wall, String regionId, boolean free) {
        this.id = id;
        this.displayName = displayName;
        this.order = order;
        this.cost = cost;
        this.gemCost = gemCost;
        this.reqLevel = reqLevel;
        this.warp = warp;
        this.wall = wall;
        this.regionId = regionId;
        this.free = free;
    }

    public boolean hasWall() {
        return this.wall != null;
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
    public int getOrder() {
        return this.order;
    }

    @Generated
    public long getCost() {
        return this.cost;
    }

    @Generated
    public long getGemCost() {
        return this.gemCost;
    }

    @Generated
    public int getReqLevel() {
        return this.reqLevel;
    }

    @Generated
    public Location getWarp() {
        return this.warp;
    }

    @Generated
    public WallRegion getWall() {
        return this.wall;
    }

    public String getRegionId() {
        return this.regionId;
    }

    /** True for zones players can use immediately without unlocking (e.g. the starter zone). */
    public boolean isFree() {
        return this.free;
    }
}
