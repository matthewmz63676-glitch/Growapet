package me.growapet.plot;

import me.growapet.GrowAPet;
import me.growapet.models.Plot;
import me.growapet.models.PlayerData;
import me.growapet.utils.LocationSafety;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
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

public final class PlotManager {
    private final GrowAPet plugin;
    private final Map<UUID, Plot> plots = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger();
    private final String worldName;
    private final int spacing;
    private final java.util.Set<UUID> pendingUpgrades=ConcurrentHashMap.newKeySet();

    public PlotManager(GrowAPet plugin) {
        this.plugin = plugin;
        this.worldName = plugin.getConfigManager().config().getString("plot.world", "growapet_plots");
        this.spacing = Math.max(48, plugin.getConfigManager().config().getInt("plot.spacing", 96));
        ensurePlotWorld();
    }

    private void ensurePlotWorld() {
        requireMainThread();
        if (Bukkit.getWorld(worldName) != null) return;
        WorldCreator creator = new WorldCreator(worldName);
        creator.type(WorldType.FLAT);
        creator.generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:plains\"}");
        creator.environment(World.Environment.NORMAL);
        World world = Bukkit.createWorld(creator);
        if (world == null) throw new IllegalStateException("Failed to create plot world " + worldName);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setSpawnFlags(false, false);
        world.setAutoSave(true);
    }

    public CompletableFuture<Void> loadAll() {
        return plugin.getDatabase().async(connection -> {
            List<PlotRow> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM plots");
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) rows.add(new PlotRow(UUID.fromString(result.getString("owner")), result.getInt("id"),
                        result.getString("world"), result.getDouble("x"), result.getDouble("y"), result.getDouble("z"),
                        result.getInt("size"), result.getInt("pet_limit"), result.getInt("egg_limit")));
            }
            return rows;
        }).thenCompose(rows -> onMain(() -> {
            plots.clear();
            for (PlotRow row : rows) {
                World world = Bukkit.getWorld(row.world());
                if (world == null) {
                    plugin.getLogger().warning("Skipping plot " + row.id() + ": missing world " + row.world());
                    continue;
                }
                Plot plot = new Plot(row.owner(), row.id(), new Location(world, row.x(), row.y(), row.z()));
                plot.setSize(Math.max(1, row.size())); plot.setPetLimit(Math.max(1, row.petLimit())); plot.setEggLimit(Math.max(1, row.eggLimit()));
                plots.put(row.owner(), plot); nextId.accumulateAndGet(row.id(), Math::max);
            }
        }));
    }

    public Plot createPlot(UUID owner) {
        requireMainThread();
        Plot existing = plots.get(owner);
        if (existing != null) return existing;
        World world = Bukkit.getWorld(worldName);
        if (world == null) throw new IllegalStateException("Plot world unavailable");
        int id = nextId.incrementAndGet();
        int gridWidth = Math.max(10, plugin.getConfigManager().config().getInt("plot.grid-width", 50));
        int[] coordinates = gridCoordinates(id, gridWidth, spacing);
        Location center = new Location(world, coordinates[0], 100, coordinates[1]);
        Plot plot = new Plot(owner, id, center);
        plot.setSize(Math.max(8, plugin.getConfigManager().config().getInt("plot.default-size", 32)));
        plot.setPetLimit(Math.max(1, plugin.getConfigManager().config().getInt("plot.default-pet-limit", 5)));
        plot.setEggLimit(Math.max(1, plugin.getConfigManager().config().getInt("plot.default-egg-limit", 3)));
        plots.put(owner, plot);
        save(plot).exceptionally(error -> { plugin.getLogger().severe("Failed to persist plot " + id + ": " + error.getMessage()); return null; });
        return plot;
    }

    public CompletableFuture<Void> save(Plot plot) {
        PlotRow row = PlotRow.capture(plot);
        return plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO plots(owner,id,world,x,y,z,size,pet_limit,egg_limit) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(owner) DO UPDATE SET id=excluded.id,world=excluded.world,x=excluded.x,y=excluded.y,z=excluded.z,size=excluded.size,pet_limit=excluded.pet_limit,egg_limit=excluded.egg_limit")) {
                statement.setString(1, row.owner().toString()); statement.setInt(2, row.id()); statement.setString(3, row.world());
                statement.setDouble(4, row.x()); statement.setDouble(5, row.y()); statement.setDouble(6, row.z());
                statement.setInt(7, row.size()); statement.setInt(8, row.petLimit()); statement.setInt(9, row.eggLimit()); statement.executeUpdate();
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
        int step = switch (upgrade) { case SIZE -> 8; default -> 1; };
        int level = Math.max(0, (current - base) / step);
        long cost = Math.multiplyExact(upgrade.baseCost, 1L << Math.min(level, 20));
        if (data.getCoins()<cost) { pendingUpgrades.remove(player.getUniqueId());data.unlockEconomy();player.sendMessage("§cYou need §e" + cost + " coins§c."); return false; }
        data.removeCoins(cost);
        UUID playerId=player.getUniqueId();String column=switch(upgrade){case SIZE->"size";case PET_SLOTS->"pet_limit";case EGG_SLOTS->"egg_limit";};
        plugin.getDatabase().transaction(connection->{try(PreparedStatement debit=connection.prepareStatement("UPDATE players SET coins=coins-? WHERE uuid=? AND coins>=?")){debit.setLong(1,cost);debit.setString(2,playerId.toString());debit.setLong(3,cost);if(debit.executeUpdate()!=1)throw new IllegalStateException("Insufficient coins");}try(PreparedStatement update=connection.prepareStatement("UPDATE plots SET "+column+"="+column+"+? WHERE owner=?")){update.setInt(1,step);update.setString(2,playerId.toString());if(update.executeUpdate()!=1)throw new IllegalStateException("Plot missing");}return null;}).whenComplete((ignored,error)->Bukkit.getScheduler().runTask(plugin,()->{pendingUpgrades.remove(playerId);data.unlockEconomy();if(error!=null){data.addCoinsRaw(cost);if(player.isOnline())player.sendMessage("§cPlot upgrade failed safely.");return;}switch(upgrade){case SIZE->plot.setSize(current+step);case PET_SLOTS->plot.setPetLimit(current+1);case EGG_SLOTS->plot.setEggLimit(current+1);}if(player.isOnline()){player.closeInventory();player.sendMessage("§aPlot upgrade purchased for §e"+cost+" coins§a.");}}));
        return true;
    }

    public long upgradeCost(Plot plot, Upgrade upgrade) {
        int current = currentValue(plot, upgrade), step = upgrade == Upgrade.SIZE ? 8 : 1;
        int level = Math.max(0, (current - baseValue(upgrade)) / step);
        return Math.multiplyExact(upgrade.baseCost, 1L << Math.min(level, 20));
    }

    private static int currentValue(Plot plot, Upgrade upgrade) { return switch(upgrade){case SIZE->plot.getSize();case PET_SLOTS->plot.getPetLimit();case EGG_SLOTS->plot.getEggLimit();}; }
    private static int baseValue(Upgrade upgrade) { return switch(upgrade){case SIZE->32;case PET_SLOTS->5;case EGG_SLOTS->3;}; }

    public Plot getPlot(UUID owner) { return plots.get(owner); }
    public boolean hasPlot(UUID owner) { return plots.containsKey(owner); }
    public boolean isWithinOwnPlot(UUID owner, Location location) { Plot plot = plots.get(owner); return plot != null && plot.contains(location); }
    public boolean isWithinAnyPlot(Location location) { return plots.values().stream().anyMatch(plot -> plot.contains(location)); }
    public Plot plotAt(Location location) { return plots.values().stream().filter(plot -> plot.contains(location)).findFirst().orElse(null); }

    /**
     * Resolves a safe standing position near the persisted plot center without ever relocating the plot.
     * Existing coordinates remain authoritative; only the player's feet position is adjusted.
     */
    public Location homeLocation(Plot plot) {
        requireMainThread();
        Location center = plot.getCenter();
        World world = center.getWorld();
        if (world == null) throw new IllegalStateException("Plot world unavailable");
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

    public CompletableFuture<Boolean> teleportHome(Player player, Plot plot) {
        requireMainThread();
        Location destination = LocationSafety.prepareForUse(homeLocation(plot), "plot home");
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

    static int[] gridCoordinates(int plotId, int gridWidth, int spacing) {
        if (plotId < 1 || gridWidth < 1 || spacing < 1) throw new IllegalArgumentException("Invalid plot grid coordinate");
        int index = plotId - 1;
        return new int[]{Math.multiplyExact(index % gridWidth, spacing), Math.multiplyExact(index / gridWidth, spacing)};
    }

    private CompletableFuture<Void> onMain(Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> { try { runnable.run(); future.complete(null); } catch (Throwable t) { future.completeExceptionally(t); } });
        return future;
    }

    private static void requireMainThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Bukkit operation off server thread"); }
    public enum Upgrade { SIZE(5_000), PET_SLOTS(10_000), EGG_SLOTS(7_500); final long baseCost; Upgrade(long baseCost) { this.baseCost = baseCost; } }
    private record PlotRow(UUID owner,int id,String world,double x,double y,double z,int size,int petLimit,int eggLimit) {
        static PlotRow capture(Plot plot) { Location c=plot.getCenter(); return new PlotRow(plot.getOwner(),plot.getId(),c.getWorld().getName(),c.getX(),c.getY(),c.getZ(),plot.getSize(),plot.getPetLimit(),plot.getEggLimit()); }
    }
}
