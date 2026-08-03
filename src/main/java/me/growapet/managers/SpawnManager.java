/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.configuration.file.FileConfiguration
 */
package me.growapet.managers;

import me.growapet.GrowAPet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public class SpawnManager {
    private final GrowAPet plugin;

    public SpawnManager(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public Location getSpawn() {
        World world;
        FileConfiguration cfg = this.plugin.getConfigManager().config();
        if (cfg == null || !cfg.isConfigurationSection("spawn")) {
            return null;
        }
        String worldName = cfg.getString("spawn.world");
        World world2 = world = worldName != null ? Bukkit.getWorld((String)worldName) : null;
        if (world == null) {
            return null;
        }
        return new Location(world, cfg.getDouble("spawn.x"), cfg.getDouble("spawn.y"), cfg.getDouble("spawn.z"), (float)cfg.getDouble("spawn.yaw"), (float)cfg.getDouble("spawn.pitch"));
    }

    public void setSpawn(Location location) {
        FileConfiguration cfg = this.plugin.getConfigManager().config();
        if (cfg == null || location.getWorld() == null) {
            return;
        }
        cfg.set("spawn.world", (Object)location.getWorld().getName());
        cfg.set("spawn.x", (Object)location.getX());
        cfg.set("spawn.y", (Object)location.getY());
        cfg.set("spawn.z", (Object)location.getZ());
        cfg.set("spawn.yaw", (Object)location.getYaw());
        cfg.set("spawn.pitch", (Object)location.getPitch());
        this.plugin.getConfigManager().save("config.yml");
    }
}

