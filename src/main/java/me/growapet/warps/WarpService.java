package me.growapet.warps;

import me.growapet.GrowAPet;
import me.growapet.utils.LocationSafety;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Small safe overlay for administrator-defined warps; zones remain ZoneManager-owned. */
public final class WarpService {
    private final GrowAPet plugin;
    private final File file;
    private final Map<String, Location> custom = new ConcurrentHashMap<>();
    public WarpService(GrowAPet plugin) { this.plugin = plugin; this.file = new File(plugin.getDataFolder(), "warps.yml"); reload(); }

    public void reload() {
        custom.clear();
        if (!file.isFile()) return;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = data.getConfigurationSection("warps");
        if (root == null) return;
        for (String raw : root.getKeys(false)) {
            String id = normalize(raw); org.bukkit.World world = Bukkit.getWorld(root.getString(raw + ".world", ""));
            if (world == null) continue;
            Location location = new Location(world, root.getDouble(raw + ".x"), root.getDouble(raw + ".y"), root.getDouble(raw + ".z"), (float) root.getDouble(raw + ".yaw"), (float) root.getDouble(raw + ".pitch"));
            if (LocationSafety.problem(location, "warp " + id, false) == null) custom.put(id, location);
        }
    }

    public List<String> customIds() { return custom.keySet().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(); }
    public boolean set(String id, Location location) {
        String normalized = normalize(id);
        if (normalized.isBlank() || location == null || LocationSafety.problem(location, "warp " + normalized, true) != null) return false;
        custom.put(normalized, location.clone()); save(); return true;
    }
    public boolean remove(String id) { boolean removed = custom.remove(normalize(id)) != null; if (removed) save(); return removed; }
    public Location get(String id) { Location location = custom.get(normalize(id)); return location == null ? null : location.clone(); }

    public boolean teleport(Player player, String id, boolean bypass) {
        Location destination = get(id);
        if (destination == null) return false;
        if (!bypass && !player.hasPermission("growapet.warp")) return false;
        Location safe = LocationSafety.prepareForUse(destination, "warp " + id);
        if (safe == null) { Messages.send(player, "<red>That warp is unsafe or unavailable.</red>"); return true; }
        player.teleportAsync(safe).thenAccept(success -> { if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> Messages.send(player, success ? "<green>Warped to <yellow><name></yellow>.</green>" : "<red>That warp could not be completed safely.</red>", Messages.value("name", id))); });
        return true;
    }

    private void save() { YamlConfiguration data = new YamlConfiguration(); for (Map.Entry<String, Location> entry : custom.entrySet()) { String path = "warps." + entry.getKey(); Location location = entry.getValue(); data.set(path + ".world", location.getWorld().getName()); data.set(path + ".x", location.getX()); data.set(path + ".y", location.getY()); data.set(path + ".z", location.getZ()); data.set(path + ".yaw", location.getYaw()); data.set(path + ".pitch", location.getPitch()); } try { data.save(file); } catch (IOException error) { plugin.getLogger().warning("Could not save warps.yml: " + error.getMessage()); } }
    private static String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "").substring(0, Math.min(32, value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "").length())); }
}
