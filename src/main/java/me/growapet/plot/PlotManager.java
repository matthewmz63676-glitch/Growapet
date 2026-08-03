/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Difficulty
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.World$Environment
 *  org.bukkit.WorldCreator
 *  org.bukkit.WorldType
 */
package me.growapet.plot;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import me.growapet.GrowAPet;
import me.growapet.models.Plot;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

public class PlotManager {
    private final GrowAPet plugin;
    private final Map<UUID, Plot> plots = new ConcurrentHashMap<UUID, Plot>();
    private final AtomicInteger nextId = new AtomicInteger(0);
    private static final int SPACING = 64;
    private static final String PLOT_WORLD_NAME = "growapet_plots";

    public PlotManager(GrowAPet plugin) {
        this.plugin = plugin;
        this.ensurePlotWorld();
    }

    private void ensurePlotWorld() {
        World world = Bukkit.getWorld((String)PLOT_WORLD_NAME);
        if (world != null) {
            return;
        }
        WorldCreator creator = new WorldCreator(PLOT_WORLD_NAME);
        creator.type(WorldType.FLAT);
        creator.generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:plains\"}");
        creator.environment(World.Environment.NORMAL);
        world = Bukkit.createWorld((WorldCreator)creator);
        if (world != null) {
            world.setDifficulty(Difficulty.PEACEFUL);
            world.setSpawnFlags(false, false);
            world.setAutoSave(true);
        } else {
            this.plugin.getLogger().severe("Failed to create the dedicated plot world 'growapet_plots'.");
        }
    }

    public void loadAll() {
        this.plugin.getDatabase().async(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM plots");
                 ResultSet rs = ps.executeQuery();){
                while (rs.next()) {
                    UUID owner = UUID.fromString(rs.getString("owner"));
                    World world = Bukkit.getWorld((String)rs.getString("world"));
                    if (world == null) continue;
                    Location center = new Location(world, rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"));
                    Plot plot = new Plot(owner, rs.getInt("id"), center);
                    plot.setSize(rs.getInt("size"));
                    plot.setPetLimit(rs.getInt("pet_limit"));
                    plot.setEggLimit(rs.getInt("egg_limit"));
                    this.plots.put(owner, plot);
                    this.nextId.updateAndGet(current -> Math.max(current, plot.getId()));
                }
            }
            return null;
        });
    }

    public Plot getPlot(UUID owner) {
        return this.plots.get(owner);
    }

    public boolean hasPlot(UUID owner) {
        return this.plots.containsKey(owner);
    }

    public boolean isWithinOwnPlot(UUID owner, Location location) {
        Plot plot = this.plots.get(owner);
        return plot != null && plot.contains(location);
    }

    public boolean isWithinAnyPlot(Location location) {
        for (Plot plot : this.plots.values()) {
            if (!plot.contains(location)) continue;
            return true;
        }
        return false;
    }

    public Plot createPlot(UUID owner) {
        this.ensurePlotWorld();
        World world = Bukkit.getWorld((String)PLOT_WORLD_NAME);
        if (world == null) {
            world = (World)Bukkit.getWorlds().get(0);
        }
        int id = this.nextId.incrementAndGet();
        int gridX = id % 50;
        int gridZ = id / 50;
        Location center = new Location(world, (double)(gridX * 64), 100.0, (double)(gridZ * 64));
        Plot plot = new Plot(owner, id, center);
        this.plots.put(owner, plot);
        World w = world;
        this.plugin.getDatabase().async(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("INSERT OR REPLACE INTO plots (owner, id, world, x, y, z, size, pet_limit, egg_limit)\nVALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)\n");){
                ps.setString(1, owner.toString());
                ps.setInt(2, plot.getId());
                ps.setString(3, w.getName());
                ps.setDouble(4, center.getX());
                ps.setDouble(5, center.getY());
                ps.setDouble(6, center.getZ());
                ps.setInt(7, plot.getSize());
                ps.setInt(8, plot.getPetLimit());
                ps.setInt(9, plot.getEggLimit());
                ps.executeUpdate();
            }
            return null;
        });
        return plot;
    }
}

