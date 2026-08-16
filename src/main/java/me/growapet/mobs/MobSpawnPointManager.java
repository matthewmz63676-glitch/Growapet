package me.growapet.mobs;

import me.growapet.GrowAPet;
import me.growapet.utils.LocationSafety;
import me.growapet.zones.Zone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent, bounded mob population points managed entirely on the server thread. */
public final class MobSpawnPointManager {
    private static final int MAX_ID_LENGTH = 64;
    private final GrowAPet plugin;
    private final Map<String, MobSpawnPoint> points = new ConcurrentHashMap<>();
    private BukkitTask task;

    public MobSpawnPointManager(GrowAPet plugin) { this.plugin = plugin; }

    public CompletableFuture<Void> loadAll() {
        return plugin.getDatabase().async(connection -> {
            List<MobSpawnPoint> loaded = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT spawn_id,mob_id,world,x,y,z,yaw,pitch,zone_id,max_count,enabled,created_at FROM mob_spawn_points"); ResultSet result = statement.executeQuery()) {
                while (result.next()) loaded.add(new MobSpawnPoint(result.getString("spawn_id"), result.getString("mob_id"), result.getString("world"), result.getDouble("x"), result.getDouble("y"), result.getDouble("z"), result.getFloat("yaw"), result.getFloat("pitch"), result.getString("zone_id"), result.getInt("max_count"), result.getBoolean("enabled"), result.getLong("created_at")));
            }
            return loaded;
        }).thenAccept(loaded -> { points.clear(); for (MobSpawnPoint point : loaded) points.put(point.id(), point); });
    }

    public void start() {
        if (task != null) return;
        long period = Math.max(20L, plugin.getConfigManager().config().getLong("mob-spawns.tick-period-ticks", 100L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
        points.clear();
    }

    public List<MobSpawnPoint> points() {
        return points.values().stream().sorted(Comparator.comparing(MobSpawnPoint::id, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public MobSpawnPoint get(String id) { return id == null ? null : points.get(id.toLowerCase(Locale.ROOT)); }

    public CompletableFuture<Void> upsert(String id, String mobId, Location location, int maxCount) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Mob spawn points must be changed on the server thread");
        String normalizedId = normalizeId(id);
        if (!isValidId(normalizedId)) return CompletableFuture.failedFuture(new IllegalArgumentException("Spawn ID must contain only letters, numbers, '_' or '-' and be at most 64 characters."));
        if (location == null || location.getWorld() == null) return CompletableFuture.failedFuture(new IllegalArgumentException("The placement location has no loaded world."));
        if (maxCount < 1 || maxCount > 100) return CompletableFuture.failedFuture(new IllegalArgumentException("Maximum population must be between 1 and 100."));
        String locationProblem = LocationSafety.problem(location, "mob spawn point", true);
        if (locationProblem != null) return CompletableFuture.failedFuture(new IllegalArgumentException(locationProblem));
        String normalizedMob = mobId == null ? "" : mobId.toUpperCase(Locale.ROOT);
        ConfigurationSection mob = plugin.getMobManager().getMobConfig(normalizedMob);
        if (mob == null || !mob.getBoolean("enabled", true)) return CompletableFuture.failedFuture(new IllegalArgumentException("That mob is missing or disabled in mobs.yml."));
        String configuredZone = mob.getString("zone", "");
        Zone actualZone = plugin.getZoneManager().getZoneAt(location);
        if (!configuredZone.isBlank() && (actualZone == null || !configuredZone.equals(actualZone.getId()))) return CompletableFuture.failedFuture(new IllegalArgumentException("That mob must be placed inside its configured zone '" + configuredZone + "'."));
        String zoneId = actualZone == null ? "" : actualZone.getId();
        if (!zoneId.isBlank() && !plugin.getZoneManager().isRegionUsable(zoneId)) return CompletableFuture.failedFuture(new IllegalArgumentException("The target zone has no usable WorldGuard cuboid."));
        MobSpawnPoint point = new MobSpawnPoint(normalizedId, normalizedMob, location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(), zoneId, maxCount, true, System.currentTimeMillis());
        return plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO mob_spawn_points(spawn_id,mob_id,world,x,y,z,yaw,pitch,zone_id,max_count,enabled,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,1,?) ON CONFLICT(spawn_id) DO UPDATE SET mob_id=excluded.mob_id,world=excluded.world,x=excluded.x,y=excluded.y,z=excluded.z,yaw=excluded.yaw,pitch=excluded.pitch,zone_id=excluded.zone_id,max_count=excluded.max_count,enabled=1")) {
                statement.setString(1, point.id()); statement.setString(2, point.mobId()); statement.setString(3, point.world()); statement.setDouble(4, point.x()); statement.setDouble(5, point.y()); statement.setDouble(6, point.z()); statement.setFloat(7, point.yaw()); statement.setFloat(8, point.pitch()); statement.setString(9, point.zoneId()); statement.setInt(10, point.maxCount()); statement.setLong(11, point.createdAt()); statement.executeUpdate();
            }
            return null;
        }).thenRun(() -> points.put(point.id(), point));
    }

    public CompletableFuture<Void> remove(String id) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Mob spawn points must be changed on the server thread");
        String normalizedId = normalizeId(id);
        if (!isValidId(normalizedId)) return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid spawn-point ID."));
        return plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM mob_spawn_points WHERE spawn_id=?")) { statement.setString(1, normalizedId); statement.executeUpdate(); }
            return null;
        }).thenRun(() -> { points.remove(normalizedId); plugin.getMobManager().removeSpawnPointEntities(normalizedId); plugin.getMobManager().removeSpawnPointRespawns(normalizedId); });
    }

    private void tick() {
        int globalCap = Math.max(1, plugin.getConfigManager().config().getInt("mob-spawns.max-tracked-mobs", 5000));
        int burst = Math.max(1, Math.min(10, plugin.getConfigManager().config().getInt("mob-spawns.spawn-burst-per-tick", 2)));
        if (plugin.getMobManager().getTrackedCount() >= globalCap) return;
        for (MobSpawnPoint point : points()) {
            if (!point.enabled() || plugin.getMobManager().getTrackedCount() >= globalCap) break;
            World world = Bukkit.getWorld(point.world());
            if (world == null || !plugin.getZoneManager().isRegionUsable(point.zoneId()) && !point.zoneId().isBlank()) continue;
            Location location = point.location(world);
            if (LocationSafety.problem(location, "mob spawn point " + point.id(), false) != null) continue;
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) continue;
            int missing = point.maxCount() - plugin.getMobManager().countSpawnPoint(point.id()) - plugin.getMobManager().countPendingSpawnPoint(point.id());
            for (int i = 0; i < Math.min(missing, burst) && plugin.getMobManager().getTrackedCount() < globalCap; i++) {
                MobManager.SpawnResult result = plugin.getMobManager().spawnFromPoint(point.mobId(), location, point.id());
                if (!result.successful()) { plugin.getLogger().warning("Spawn point '" + point.id() + "' could not spawn: " + result.detail()); break; }
            }
        }
    }

    public static boolean isValidId(String id) { return id != null && id.length() <= MAX_ID_LENGTH && id.matches("[A-Za-z0-9_-]+"); }
    private static String normalizeId(String id) { return id == null ? "" : id.trim().toLowerCase(Locale.ROOT); }

    public record MobSpawnPoint(String id, String mobId, String world, double x, double y, double z, float yaw, float pitch, String zoneId, int maxCount, boolean enabled, long createdAt) {
        public MobSpawnPoint {
            id = id == null ? "" : id.toLowerCase(Locale.ROOT);
            mobId = mobId == null ? "" : mobId.toUpperCase(Locale.ROOT);
            world = world == null ? "" : world;
            zoneId = zoneId == null ? "" : zoneId;
            maxCount = Math.max(1, Math.min(100, maxCount));
        }
        public Location location(World loadedWorld) { return new Location(loadedWorld, x, y, z, yaw, pitch); }
    }
}
