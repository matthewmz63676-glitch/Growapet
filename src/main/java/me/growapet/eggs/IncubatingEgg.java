package me.growapet.eggs;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.UUID;
import java.time.Clock;

public final class IncubatingEgg {
    private final UUID owner;
    private final EntityType entityType;
    private final Location location;
    private final int totalSeconds;
    private final long hatchAt;

    public IncubatingEgg(UUID owner, EntityType entityType, Location location, int totalSeconds, long hatchAt) {
        this.owner=owner; this.entityType=entityType; this.location=location.toBlockLocation();
        this.totalSeconds=Math.max(1,totalSeconds); this.hatchAt=hatchAt;
    }
    public UUID getOwner(){return owner;} public EntityType getEntityType(){return entityType;}
    public Location getLocation(){return location;} public int getTotalSeconds(){return totalSeconds;}
    public long getHatchAt(){return hatchAt;}
    public int getSecondsRemaining(){return getSecondsRemaining(System.currentTimeMillis());}
    public int getSecondsRemaining(Clock clock){return getSecondsRemaining(clock.millis());}
    int getSecondsRemaining(long now){return Math.max(0,(int)Math.ceil((hatchAt-now)/1000.0));}
}
