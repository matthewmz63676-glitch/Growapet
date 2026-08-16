package me.growapet.utils;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Main-thread location guard shared by teleports and world-bound spawns.
 *
 * <p>Configuration may outlive a world or a chunk.  Callers must validate
 * before handing a location to Bukkit so a stale YAML value can never turn
 * into a teleport to an unloaded world or an out-of-bounds coordinate.</p>
 */
public final class LocationSafety {
    private LocationSafety() {}

    public static String problem(Location location, String label, boolean requireLoadedChunk) {
        if (location == null) return label + " is not configured";
        World world = location.getWorld();
        if (world == null) return label + " references an unloaded world";
        if (!finite(location.getX()) || !finite(location.getY()) || !finite(location.getZ())
                || !finite(location.getYaw()) || !finite(location.getPitch())) {
            return label + " contains a non-finite coordinate";
        }
        if (location.getY() < world.getMinHeight() || location.getY() > world.getMaxHeight()) {
            return label + " is outside the world height limits";
        }
        if (!world.getWorldBorder().isInside(location)) return label + " is outside the world border";
        if (requireLoadedChunk && !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return label + " is in an unloaded chunk";
        }
        return null;
    }

    /** Loads the destination chunk only on the server thread, then validates it. */
    public static Location prepareForUse(Location configured, String label) {
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Location preparation must run on the server thread");
        }
        String problem = problem(configured, label, false);
        if (problem != null) return null;
        Location location = configured.clone();
        World world = location.getWorld();
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                && !world.getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4).load()) {
            return null;
        }
        return problem(location, label, true) == null ? location : null;
    }

    private static boolean finite(double value) { return Double.isFinite(value); }
}
