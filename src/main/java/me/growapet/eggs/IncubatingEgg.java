/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  org.bukkit.Location
 *  org.bukkit.entity.EntityType
 *  org.bukkit.scheduler.BukkitTask
 */
package me.growapet.eggs;

import java.util.UUID;
import lombok.Generated;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.scheduler.BukkitTask;

public class IncubatingEgg {
    private final UUID owner;
    private final EntityType entityType;
    private final Location location;
    private final int totalSeconds;
    private int secondsRemaining;
    private BukkitTask task;

    public IncubatingEgg(UUID owner, EntityType entityType, Location location, int totalSeconds) {
        this.owner = owner;
        this.entityType = entityType;
        this.location = location;
        this.totalSeconds = totalSeconds;
        this.secondsRemaining = totalSeconds;
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
    public Location getLocation() {
        return this.location;
    }

    @Generated
    public int getTotalSeconds() {
        return this.totalSeconds;
    }

    @Generated
    public int getSecondsRemaining() {
        return this.secondsRemaining;
    }

    @Generated
    public BukkitTask getTask() {
        return this.task;
    }

    @Generated
    public void setSecondsRemaining(int secondsRemaining) {
        this.secondsRemaining = secondsRemaining;
    }

    @Generated
    public void setTask(BukkitTask task) {
        this.task = task;
    }
}

