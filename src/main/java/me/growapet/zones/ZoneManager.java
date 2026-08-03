/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.World
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.entity.Player
 */
package me.growapet.zones;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import me.growapet.zones.WallRegion;
import me.growapet.zones.Zone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class ZoneManager {
    private final GrowAPet plugin;
    private final Map<String, Zone> zones = new LinkedHashMap<String, Zone>();

    public ZoneManager(GrowAPet plugin) {
        this.plugin = plugin;
        this.load();
    }

    public void load() {
        this.zones.clear();
        ConfigurationSection root = this.plugin.getConfigManager().zones().getConfigurationSection("zones");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            World wallWorld;
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;
            String worldName = sec.getString("world", "world");
            World world = Bukkit.getWorld((String)worldName);
            Location loc = new Location(world, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"));
            WallRegion wall = null;
            ConfigurationSection wallSec = sec.getConfigurationSection("wall");
            if (wallSec != null && (wallWorld = Bukkit.getWorld((String)wallSec.getString("world", worldName))) != null) {
                Location cam = null;
                if (wallSec.isSet("cam-x")) {
                    cam = new Location(wallWorld, wallSec.getDouble("cam-x"), wallSec.getDouble("cam-y"), wallSec.getDouble("cam-z"), (float)wallSec.getDouble("cam-yaw"), (float)wallSec.getDouble("cam-pitch"));
                }
                wall = new WallRegion(wallWorld, wallSec.getInt("x1"), wallSec.getInt("y1"), wallSec.getInt("z1"), wallSec.getInt("x2"), wallSec.getInt("y2"), wallSec.getInt("z2"), cam);
            }
            Zone zone = new Zone(id, sec.getString("display-name", id), sec.getInt("order", 0), sec.getLong("cost", 0L), sec.getLong("gem-cost", 0L), sec.getInt("req-level", 0), loc, wall);
            this.zones.put(id, zone);
        }
    }

    public List<Zone> getZonesInOrder() {
        ArrayList<Zone> list = new ArrayList<Zone>(this.zones.values());
        list.sort((a, b) -> Integer.compare(a.getOrder(), b.getOrder()));
        return list;
    }

    public Zone getZone(String id) {
        return this.zones.get(id);
    }

    public boolean isUnlocked(Player player, String zoneId) {
        if ("spawn".equals(zoneId)) {
            return true;
        }
        PlayerData data = this.plugin.getPlayerManager().get(player);
        return data != null && data.hasUnlockedZone(zoneId);
    }

    public boolean unlock(Player player, String zoneId) {
        Zone zone = this.zones.get(zoneId);
        if (zone == null) {
            return false;
        }
        PlayerData data = this.plugin.getPlayerManager().get(player);
        if (data == null) {
            return false;
        }
        if (this.isUnlocked(player, zoneId)) {
            return true;
        }
        boolean missing = false;
        if (data.getLevel() < zone.getReqLevel()) {
            player.sendMessage("\u00a7cYou need to be \u00a7elevel " + zone.getReqLevel() + " \u00a7cto unlock " + zone.getDisplayName() + "!");
            missing = true;
        }
        if (data.getCoins() < zone.getCost()) {
            player.sendMessage("\u00a7cYou need \u00a7e" + zone.getCost() + " coins \u00a7cto unlock " + zone.getDisplayName() + "!");
            missing = true;
        }
        if (data.getGems() < zone.getGemCost()) {
            player.sendMessage("\u00a7cYou need \u00a7e" + zone.getGemCost() + " gems \u00a7cto unlock " + zone.getDisplayName() + "!");
            missing = true;
        }
        if (missing) {
            return false;
        }
        data.removeCoins(zone.getCost());
        data.removeGems(zone.getGemCost());
        data.unlockZone(zoneId);
        player.sendMessage("\u00a7aUnlocked zone: \u00a7e" + zone.getDisplayName() + "\u00a7a!");
        return true;
    }

    public boolean teleport(Player player, String zoneId) {
        Zone zone = this.zones.get(zoneId);
        if (zone == null || zone.getWarp().getWorld() == null) {
            player.sendMessage("\u00a7cThat zone's warp location isn't configured yet.");
            return false;
        }
        player.teleport(zone.getWarp());
        player.sendMessage("\u00a7aWarped to \u00a7e" + zone.getDisplayName() + "\u00a7a.");
        return true;
    }
}

