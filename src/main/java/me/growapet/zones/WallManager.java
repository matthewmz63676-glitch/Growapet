/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.GameMode
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.Sound
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 */
package me.growapet.zones;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import me.growapet.GrowAPet;
import me.growapet.zones.WallRegion;
import me.growapet.zones.Zone;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class WallManager {
    private static final long MAX_CACHED_BLOCKS = 20000L;
    private final GrowAPet plugin;
    private final Map<String, List<Location>> blockCache = new HashMap<String, List<Location>>();
    private final Random random = new Random();

    public WallManager(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        this.blockCache.clear();
        for (Zone zone : this.plugin.getZoneManager().getZonesInOrder()) {
            if (!zone.hasWall()) continue;
            WallRegion wall = zone.getWall();
            if (wall.blockCount() > 20000L) {
                this.plugin.getLogger().warning("Zone '" + zone.getId() + "' wall region is " + wall.blockCount() + " blocks (max 20000) - skipping fake-wall rendering for it.");
                continue;
            }
            ArrayList<Location> blocks = new ArrayList<Location>();
            for (int x = wall.getMinX(); x <= wall.getMaxX(); ++x) {
                for (int y = wall.getMinY(); y <= wall.getMaxY(); ++y) {
                    for (int z = wall.getMinZ(); z <= wall.getMaxZ(); ++z) {
                        blocks.add(new Location(wall.getWorld(), (double)x, (double)y, (double)z));
                    }
                }
            }
            this.blockCache.put(zone.getId(), blocks);
        }
    }

    public void sendWalls(Player player) {
        for (Zone zone : this.plugin.getZoneManager().getZonesInOrder()) {
            if (!zone.hasWall() || this.plugin.getZoneManager().isUnlocked(player, zone.getId())) continue;
            this.sendFakeWall(player, zone.getId());
        }
    }

    private void sendFakeWall(Player player, String zoneId) {
        List<Location> blocks = this.blockCache.get(zoneId);
        if (blocks == null) {
            return;
        }
        BlockData glass = Material.BLACK_STAINED_GLASS.createBlockData();
        for (Location loc : blocks) {
            player.sendBlockChange(loc, glass);
        }
    }

    private void clearFakeWall(Player player, String zoneId) {
        List<Location> blocks = this.blockCache.get(zoneId);
        if (blocks == null) {
            return;
        }
        for (Location loc : blocks) {
            player.sendBlockChange(loc, loc.getBlock().getBlockData());
        }
    }

    public boolean isInsideLockedWall(Player player, Location loc) {
        for (Zone zone : this.plugin.getZoneManager().getZonesInOrder()) {
            if (!zone.hasWall() || this.plugin.getZoneManager().isUnlocked(player, zone.getId()) || !zone.getWall().contains(loc)) continue;
            return true;
        }
        return false;
    }

    public void playBreakCutscene(Player player, Zone zone) {
        WallRegion wall = zone.getWall();
        if (wall == null || wall.getCameraPose() == null) {
            this.clearFakeWall(player, zone.getId());
            player.sendMessage("\u00a7d\u00a7lWALL BROKEN! \u00a7r\u00a77The path to \u00a7e" + zone.getDisplayName() + " \u00a77is now open!");
            return;
        }
        GameMode previousMode = player.getGameMode();
        Location previousLocation = player.getLocation();
        boolean wasFlying = player.isFlying();
        boolean couldFly = player.getAllowFlight();
        player.setGameMode(GameMode.SPECTATOR);
        player.teleport(wall.getCameraPose());
        player.playSound(wall.getCameraPose(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.0f);
        player.playSound(wall.getCameraPose(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.0f);
        List blocks = this.blockCache.getOrDefault(zone.getId(), List.of());
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> player.playSound(wall.getCameraPose(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f), 10L);
        int waves = 8;
        for (Location block : blocks) {
            int wave = 1 + this.random.nextInt(waves);
            long delay = 10L + (long)wave * 8L;
            Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
                player.sendBlockChange(block, Material.AIR.createBlockData());
                player.playSound(block, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.7f + (float)wave * 0.05f);
            }, delay);
        }
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);
        }, 90L);
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            player.setGameMode(previousMode);
            player.teleport(previousLocation);
            player.setAllowFlight(couldFly);
            player.setFlying(wasFlying && couldFly);
            this.clearFakeWall(player, zone.getId());
            player.sendMessage("\u00a7d\u00a7lWALL BROKEN! \u00a7r\u00a77The path to \u00a7e" + zone.getDisplayName() + " \u00a77is now open!");
        }, 105L);
    }
}

