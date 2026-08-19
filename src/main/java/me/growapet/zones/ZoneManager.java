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

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import me.growapet.utils.LocationSafety;
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
    private final Set<String> invalidRegions = new HashSet<>();
    private final Map<String, String> regionProblems = new LinkedHashMap<>();

    public ZoneManager(GrowAPet plugin) {
        this.plugin = plugin;
        this.load();
    }

    public void load() {
        this.zones.clear();
        this.invalidRegions.clear();
        this.regionProblems.clear();
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
            String regionId = sec.getString("region", "growapet_" + id);
            boolean free = "spawn".equals(id) || sec.getBoolean("free", false);
            Zone zone = new Zone(id, sec.getString("display-name", id), sec.getInt("order", 0), sec.getLong("cost", 0L), sec.getLong("gem-cost", 0L), sec.getInt("req-level", 0), loc, wall, regionId, free);
            this.zones.put(id, zone);
            validateRegion(zone);
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

    /** Returns a stable snapshot of configuration/WorldGuard problems for diagnostics. */
    public Map<String, String> validationProblems() {
        return Map.copyOf(regionProblems);
    }

    public boolean isRegionUsable(String zoneId) {
        return zoneId != null && zones.containsKey(zoneId) && !invalidRegions.contains(zoneId);
    }

    public Zone getZoneAt(Location location) {
        if (location == null || location.getWorld() == null) return null;
        BlockVector3 point = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        for (Zone zone : getZonesInOrder()) {
            ProtectedCuboidRegion region = cuboid(zone);
            if (region != null && region.contains(point)) return zone;
        }
        return null;
    }

    public boolean isPlayerInZone(Player player, String zoneId) {
        if (player == null || zoneId == null) return false;
        Zone expected = zones.get(zoneId);
        if (expected == null) return false;
        Zone actual = getZoneAt(player.getLocation());
        return actual != null && actual.getId().equals(expected.getId());
    }

    public boolean isLocationInZone(Location location, String zoneId) {
        if (location == null || zoneId == null) return false;
        Zone actual = getZoneAt(location);
        return actual != null && actual.getId().equals(zoneId);
    }

    public ProtectedCuboidRegion cuboid(Zone zone) {
        if (zone == null || invalidRegions.contains(zone.getId()) || zone.getWarp().getWorld() == null) return null;
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(zone.getWarp().getWorld()));
        if (manager == null) return null;
        ProtectedRegion region = manager.getRegion(zone.getRegionId());
        return region instanceof ProtectedCuboidRegion cuboid ? cuboid : null;
    }

    /** Exact mathematical center of the configured cuboid WorldGuard region. */
    public Location center(Zone zone) {
        ProtectedCuboidRegion region = cuboid(zone);
        if (region == null || zone.getWarp().getWorld() == null) return null;
        BlockVector3 min = region.getMinimumPoint(), max = region.getMaximumPoint();
        return new Location(zone.getWarp().getWorld(),
                (min.x() + max.x() + 1) / 2.0,
                (min.y() + max.y() + 1) / 2.0,
                (min.z() + max.z() + 1) / 2.0);
    }

    private void validateRegion(Zone zone) {
        if (zone.getWarp().getWorld() == null) {
            invalidRegions.add(zone.getId());
            String message = "Zone '" + zone.getId() + "' references an unloaded world.";
            regionProblems.put(zone.getId(), message);
            plugin.getLogger().severe(message);
            return;
        }
        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(zone.getWarp().getWorld()));
        ProtectedRegion region = manager == null ? null : manager.getRegion(zone.getRegionId());
        if (!(region instanceof ProtectedCuboidRegion)) {
            invalidRegions.add(zone.getId());
            String message = "Zone '" + zone.getId() + "' requires cuboid WorldGuard region '" + zone.getRegionId() + "'. Zone-bound features are disabled for it.";
            regionProblems.put(zone.getId(), message);
            plugin.getLogger().severe(message);
        }
    }

    public boolean isUnlocked(Player player, String zoneId) {
        Zone zone = this.zones.get(zoneId);
        if (zone != null && zone.isFree()) {
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
        if (!data.tryLockEconomy()) {
            player.sendMessage("§cAnother transaction is already in progress.");
            return false;
        }
        Zone previous = this.getZonesInOrder().stream().filter(candidate -> candidate.getOrder() == zone.getOrder() - 1).findFirst().orElse(null);
        if (previous != null && !this.isUnlocked(player, previous.getId())) {
            data.unlockEconomy();
            player.sendMessage("§cUnlock §e" + previous.getDisplayName() + " §cfirst.");
            return false;
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
            data.unlockEconomy();
            return false;
        }
        data.removeCoins(zone.getCost());
        data.removeGems(zone.getGemCost());
        data.unlockZone(zoneId);
        UUID playerId = player.getUniqueId();
        this.plugin.getPlayerManager().saveTransaction(data, UUID.randomUUID().toString(), "ZONE:" + zoneId,
                -zone.getCost(), -zone.getGemCost(), 0).whenComplete((ignored, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    data.unlockEconomy();
                    if (error != null) {
                        data.addCoinsRaw(zone.getCost());
                        data.addGemsRaw(zone.getGemCost());
                        data.revokeZone(zoneId);
                        plugin.getLogger().severe("Failed to persist zone unlock for " + playerId + ": " + error.getMessage());
                        Player online = Bukkit.getPlayer(playerId);
                        if (online != null) { online.sendMessage("§cZone unlock failed safely; your payment was restored."); plugin.getWallManager().sendWalls(online); }
                        return;
                    }
                    plugin.getQuestManager().record(playerId, me.growapet.quests.QuestType.ZONE_UNLOCK, 1, zoneId);
                    Player online = Bukkit.getPlayer(playerId);
                    if (online != null) online.sendMessage("§aUnlocked zone: §e" + zone.getDisplayName() + "§a!");
                }));
        return true;
    }

    public boolean teleport(Player player, String zoneId) {
        Zone zone = this.zones.get(zoneId);
        if (zone == null) {
            player.sendMessage("\u00a7cThat zone's warp location isn't configured yet.");
            return false;
        }
        if (!this.isUnlocked(player, zoneId)) {
            player.sendMessage("§cYou have not unlocked that zone.");
            return false;
        }
        Location destination = LocationSafety.prepareForUse(zone.getWarp(), "Zone '" + zone.getId() + "' warp");
        if (destination == null) {
            player.sendMessage("\u00a7cThat zone's warp is unavailable or unsafe. An admin must fix it before players can travel there.");
            return false;
        }
        player.teleportAsync(destination).thenAccept(success -> {
            if(success)me.growapet.utils.Messages.send(player,"<green>Warped to <yellow><zone></yellow>.</green>",me.growapet.utils.Messages.value("zone",zone.getDisplayName()));
            else me.growapet.utils.Messages.send(player,"<red>That warp could not be completed safely.</red>");
        });
        return true;
    }
}
