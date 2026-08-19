package me.growapet.plot;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import me.growapet.GrowAPet;
import me.growapet.models.Plot;
import me.growapet.models.PlayerData;
import me.growapet.utils.LocationSafety;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Every player's plot is the same single shared WorldGuard region ({@code plot.region}, default
 * {@code "plot"}) in the plugin's one world ({@code plot.world}) — there is no per-player physical
 * space any more. This manager only owns per-owner caps (pet/egg slot limits) and resolves that one
 * shared location; who currently sees whose placed pets/eggs at that location is
 * {@link PlotVisitManager}'s job.
 */
public final class PlotManager {
    private final GrowAPet plugin;
    private final Map<UUID, Plot> plots = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger();
    private final String worldName;
    private final String regionId;
    private final java.util.Set<UUID> pendingUpgrades = ConcurrentHashMap.newKeySet();

    public PlotManager(GrowAPet plugin) {
        this.plugin = plugin;
        this.worldName = plugin.getConfigManager().config().getString("plot.world", "world");
        this.regionId = plugin.getConfigManager().config().getString("plot.region", "plot");
    }

    public CompletableFuture<Void> loadAll() {
        return plugin.getDatabase().async(connection -> {
            List<PlotRow> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM plots");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) rows.add(new PlotRow(UUID.fromString(result.getString("owner")), result.getInt("id"),
                        result.getInt("pet_limit"), result.getInt("egg_limit")));
            }
            return rows;
        }).thenCompose(rows -> onMain(() -> {
            plots.clear();
            for (PlotRow row : rows) {
                Plot plot = new Plot(row.owner(), row.id());
                plot.setPetLimit(Math.max(1, row.petLimit())); plot.setEggLimit(Math.max(1, row.eggLimit()));
                plots.put(row.owner(), plot); nextId.accumulateAndGet(row.id(), Math::max);
            }
        }));
    }

    public Plot createPlot(UUID owner) {
        requireMainThread();
        Plot existing = plots.get(owner);
        if (existing != null) return existing;
        int id = nextId.incrementAndGet();
        Plot plot = new Plot(owner, id);
        plot.setPetLimit(Math.max(1, plugin.getConfigManager().config().getInt("plot.default-pet-limit", 5)));
        plot.setEggLimit(Math.max(1, plugin.getConfigManager().config().getInt("plot.default-egg-limit", 3)));
        plots.put(owner, plot);
        save(plot).exceptionally(error -> { plugin.getLogger().severe("Failed to persist plot " + id + ": " + error.getMessage()); return null; });
        return plot;
    }

    public CompletableFuture<Void> save(Plot plot) {
        PlotRow row = PlotRow.capture(plot);
        return plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO plots(owner,id,pet_limit,egg_limit) VALUES(?,?,?,?) ON CONFLICT(owner) DO UPDATE SET id=excluded.id,pet_limit=excluded.pet_limit,egg_limit=excluded.egg_limit")) {
                statement.setString(1, row.owner().toString()); statement.setInt(2, row.id());
                statement.setInt(3, row.petLimit()); statement.setInt(4, row.eggLimit()); statement.executeUpdate();
            }
            return null;
        });
    }

    public boolean purchaseUpgrade(Player player, Upgrade upgrade) {
        requireMainThread();
        Plot plot = getPlot(player.getUniqueId());
        PlayerData data = plugin.getPlayerManager().get(player);
        if (plot == null || data == null || !data.tryLockEconomy()) return false;
        if (!pendingUpgrades.add(player.getUniqueId())) { data.unlockEconomy(); return false; }
        int current = currentValue(plot, upgrade);
        int base = baseValue(upgrade);
        int level = Math.max(0, current - base);
        long cost = Math.multiplyExact(upgrade.baseCost, 1L << Math.min(level, 20));
        if (data.getCoins()<cost) { pendingUpgrades.remove(player.getUniqueId());data.unlockEconomy();player.sendMessage("§cYou need §e" + cost + " coins§c."); return false; }
        data.removeCoins(cost);
        UUID playerId=player.getUniqueId();String column=switch(upgrade){case PET_SLOTS->"pet_limit";case EGG_SLOTS->"egg_limit";};
        plugin.getDatabase().transaction(connection->{try(PreparedStatement debit=connection.prepareStatement("UPDATE players SET coins=coins-? WHERE uuid=? AND coins>=?")){debit.setLong(1,cost);debit.setString(2,playerId.toString());debit.setLong(3,cost);if(debit.executeUpdate()!=1)throw new IllegalStateException("Insufficient coins");}try(PreparedStatement update=connection.prepareStatement("UPDATE plots SET "+column+"="+column+"+1 WHERE owner=?")){update.setString(1,playerId.toString());if(update.executeUpdate()!=1)throw new IllegalStateException("Plot missing");}return null;}).whenComplete((ignored,error)->Bukkit.getScheduler().runTask(plugin,()->{pendingUpgrades.remove(playerId);data.unlockEconomy();if(error!=null){data.addCoinsRaw(cost);if(player.isOnline())player.sendMessage("§cPlot upgrade failed safely.");return;}switch(upgrade){case PET_SLOTS->plot.setPetLimit(current+1);case EGG_SLOTS->plot.setEggLimit(current+1);}if(player.isOnline()){player.closeInventory();player.sendMessage("§aPlot upgrade purchased for §e"+cost+" coins§a.");}}));
        return true;
    }

    public long upgradeCost(Plot plot, Upgrade upgrade) {
        int level = Math.max(0, currentValue(plot, upgrade) - baseValue(upgrade));
        return Math.multiplyExact(upgrade.baseCost, 1L << Math.min(level, 20));
    }

    private static int currentValue(Plot plot, Upgrade upgrade) { return switch(upgrade){case PET_SLOTS->plot.getPetLimit();case EGG_SLOTS->plot.getEggLimit();}; }
    private static int baseValue(Upgrade upgrade) { return switch(upgrade){case PET_SLOTS->5;case EGG_SLOTS->3;}; }

    public Plot getPlot(UUID owner) { return plots.get(owner); }
    public boolean hasPlot(UUID owner) { return plots.containsKey(owner); }

    /** True for any location inside the one shared plot region — ownership of what's placed there is
     *  tracked separately (see {@link PlotVisitManager}), not by physical space any more. */
    public boolean isPlotRegion(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().getName().equals(worldName)) return false;
        ProtectedCuboidRegion region = cuboid();
        if (region == null) return false;
        BlockVector3 point = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        return region.contains(point);
    }

    public boolean isRegionUsable() { return cuboid() != null; }

    /** Exact mathematical center of the shared plot WorldGuard region, or {@code null} if it isn't configured. */
    public Location plotCenter() {
        ProtectedCuboidRegion region = cuboid();
        World world = Bukkit.getWorld(worldName);
        if (region == null || world == null) return null;
        BlockVector3 min = region.getMinimumPoint(), max = region.getMaximumPoint();
        return new Location(world, (min.x() + max.x() + 1) / 2.0, (min.y() + max.y() + 1) / 2.0, (min.z() + max.z() + 1) / 2.0);
    }

    private ProtectedCuboidRegion cuboid() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        if (manager == null) return null;
        ProtectedRegion region = manager.getRegion(regionId);
        return region instanceof ProtectedCuboidRegion cuboid ? cuboid : null;
    }

    /**
     * Resolves a safe standing position near the shared plot center. The same for every player —
     * there is only one plot location.
     */
    public Location homeLocation() {
        requireMainThread();
        Location center = plotCenter();
        if (center == null) throw new IllegalStateException("Plot region unavailable");
        World world = center.getWorld();
        int x = center.getBlockX(), z = center.getBlockZ();
        int preferred = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 2, center.getBlockY()));
        for (int distance = 0; distance <= 24; distance++) {
            int above = preferred + distance;
            if (above < world.getMaxHeight() - 1 && isSafeFeet(world, x, above, z)) return centered(world, x, above, z, center);
            int below = preferred - distance;
            if (distance > 0 && below > world.getMinHeight() && isSafeFeet(world, x, below, z)) return centered(world, x, below, z, center);
        }
        return new Location(world, x + 0.5, center.getY(), z + 0.5, center.getYaw(), center.getPitch());
    }

    public CompletableFuture<Boolean> teleportHome(Player player) {
        requireMainThread();
        Location destination;
        try { destination = LocationSafety.prepareForUse(homeLocation(), "plot home"); }
        catch (IllegalStateException error) { return CompletableFuture.completedFuture(false); }
        if (destination == null) return CompletableFuture.completedFuture(false);
        return player.teleportAsync(destination);
    }

    private static boolean isSafeFeet(World world, int x, int y, int z) {
        return world.getBlockAt(x, y - 1, z).getType().isSolid()
                && world.getBlockAt(x, y, z).isPassable()
                && world.getBlockAt(x, y + 1, z).isPassable();
    }

    private static Location centered(World world, int x, int y, int z, Location orientation) {
        return new Location(world, x + 0.5, y, z + 0.5, orientation.getYaw(), orientation.getPitch());
    }

    private CompletableFuture<Void> onMain(Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> { try { runnable.run(); future.complete(null); } catch (Throwable t) { future.completeExceptionally(t); } });
        return future;
    }

    private static void requireMainThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Bukkit operation off server thread"); }
    public enum Upgrade { PET_SLOTS(10_000), EGG_SLOTS(7_500); final long baseCost; Upgrade(long baseCost) { this.baseCost = baseCost; } }
    private record PlotRow(UUID owner,int id,int petLimit,int eggLimit) {
        static PlotRow capture(Plot plot) { return new PlotRow(plot.getOwner(),plot.getId(),plot.getPetLimit(),plot.getEggLimit()); }
    }
}
