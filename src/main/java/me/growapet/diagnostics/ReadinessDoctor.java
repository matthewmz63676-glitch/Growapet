package me.growapet.diagnostics;

import me.growapet.GrowAPet;
import me.growapet.utils.LocationSafety;
import me.growapet.zones.Zone;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Read-only launch diagnostics. It never writes player or configuration data. */
public final class ReadinessDoctor {
    private final GrowAPet plugin;

    public ReadinessDoctor(GrowAPet plugin) { this.plugin = plugin; }

    public CompletableFuture<Report> run() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("GrowAPet diagnostics must start on the server thread");
        }
        List<Check> checks = new ArrayList<>();
        checkDependency(checks, "PacketEvents", "packetevents", true);
        checkDependency(checks, "WorldEdit", "WorldEdit", true);
        checkDependency(checks, "WorldGuard", "WorldGuard", true);
        checkDependency(checks, "PlaceholderAPI", "PlaceholderAPI", false);
        checkDependency(checks, "LuckPerms", "LuckPerms", false);
        checkConfiguration(checks);
        checkWorldsAndZones(checks);
        checkSpawn(checks);
        checkTutorial(checks);
        checkMobDefinitions(checks);
        checkResourcePack(checks);
        checkDiscord(checks);
        checkCommerce(checks);
        checkSeasons(checks);
        checkShopCatalog(checks);

        CompletableFuture<List<Check>> database = plugin.getDatabase().async(connection -> {
            List<Check> databaseChecks = new ArrayList<>();
            try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
                String value = result.next() ? result.getString(1) : "no result";
                databaseChecks.add("ok".equalsIgnoreCase(value)
                        ? new Check("SQLite", Status.OK, "database connection and integrity check passed")
                        : new Check("SQLite", Status.FAIL, "PRAGMA integrity_check returned '" + value + "'"));
            }
            if (plugin.getConfigManager().commerce().getBoolean("enabled", false)) {
                try (Statement statement = connection.createStatement(); ResultSet row = statement.executeQuery("SELECT (SELECT COUNT(*) FROM commerce_receipts WHERE status='VERIFIED_PENDING'),(SELECT COUNT(*) FROM commerce_debts WHERE status='OPEN')")) {
                    if (row.next()) {
                        int pending = row.getInt(1), debts = row.getInt(2);
                        databaseChecks.add(pending == 0 ? new Check("Tebex pending receipts", Status.OK, "no verified receipts are waiting") : new Check("Tebex pending receipts", Status.WARN, pending + " receipt(s) await reconciliation"));
                        databaseChecks.add(debts == 0 ? new Check("Commerce debt", Status.OK, "no open refund debt") : new Check("Commerce debt", Status.FAIL, debts + " open debt row(s) lock paid fulfilment"));
                    }
                }
            }
            return databaseChecks;
        }).exceptionally(error -> List.of(new Check("SQLite", Status.FAIL, "read-only integrity check failed: " + rootMessage(error))));
        return database.thenApply(dbChecks -> {
            checks.addAll(dbChecks);
            checks.sort(Comparator.comparing(Check::name, String.CASE_INSENSITIVE_ORDER));
            return new Report(List.copyOf(checks));
        });
    }

    private void checkDependency(List<Check> checks, String label, String pluginName, boolean required) {
        Plugin dependency = Bukkit.getPluginManager().getPlugin(pluginName);
        if (dependency != null && dependency.isEnabled()) {
            checks.add(new Check(label, Status.OK, "enabled (" + dependency.getDescription().getVersion() + ")"));
        } else if (required) {
            checks.add(new Check(label, Status.FAIL, "required plugin is missing or disabled"));
        } else {
            checks.add(new Check(label, Status.WARN, "optional plugin is not installed; related integrations are disabled"));
        }
    }

    private void checkConfiguration(List<Check> checks) {
        checks.add(new Check("Configuration", plugin.getConfigManager().config() != null ? Status.OK : Status.FAIL,
                plugin.getConfigManager().config() == null ? "config.yml is unavailable" : "validated configuration snapshot is loaded"));
        if (plugin.getConfigManager().zones() == null || plugin.getConfigManager().mobs() == null || plugin.getConfigManager().bosses() == null) {
            checks.add(new Check("Gameplay YAML", Status.FAIL, "zones.yml, mobs.yml, and bosses.yml must all be present"));
        } else {
            checks.add(new Check("Gameplay YAML", Status.OK, "zones, mobs, bosses, eggs, quests, menus, and messages are loaded"));
        }
    }

    private void checkWorldsAndZones(List<Check> checks) {
        String plotWorld = plugin.getConfigManager().config().getString("plot.world", "");
        World plots = plotWorld.isBlank() ? null : Bukkit.getWorld(plotWorld);
        checks.add(plots == null
                ? new Check("Plot world", Status.FAIL, "configured plot world '" + plotWorld + "' is not loaded")
                : new Check("Plot world", Status.OK, "loaded: " + plots.getName()));
        checks.add(plugin.getPlotManager().isRegionUsable()
                ? new Check("Plot region", Status.OK, "shared plot WorldGuard region is a usable cuboid")
                : new Check("Plot region", Status.FAIL, "missing/non-cuboid WorldGuard region '" + plugin.getConfigManager().config().getString("plot.region", "plot") + "'; egg placement and placed pets/eggs are disabled"));
        for (Zone zone : plugin.getZoneManager().getZonesInOrder()) {
            String regionProblem = plugin.getZoneManager().validationProblems().get(zone.getId());
            if (regionProblem != null) {
                checks.add(new Check("Zone " + zone.getId(), Status.FAIL, regionProblem));
                continue;
            }
            String locationProblem = LocationSafety.problem(zone.getWarp(), "zone " + zone.getId() + " warp", false);
            if (locationProblem != null) {
                checks.add(new Check("Zone " + zone.getId(), Status.FAIL, locationProblem));
            } else if (!zone.getWarp().getWorld().isChunkLoaded(zone.getWarp().getBlockX() >> 4, zone.getWarp().getBlockZ() >> 4)) {
                checks.add(new Check("Zone " + zone.getId(), Status.WARN, "WorldGuard region is valid but its warp chunk is not loaded yet"));
            } else {
                checks.add(new Check("Zone " + zone.getId(), Status.OK, "warp and cuboid region '" + zone.getRegionId() + "' are available"));
            }
        }
    }

    private void checkSpawn(List<Check> checks) {
        Location spawn = plugin.getSpawnManager().getSpawn();
        String problem = LocationSafety.problem(spawn, "server spawn", false);
        if (problem != null) checks.add(new Check("Server spawn", Status.FAIL, problem + "; run /setspawn"));
        else if (!spawn.getWorld().isChunkLoaded(spawn.getBlockX() >> 4, spawn.getBlockZ() >> 4)) checks.add(new Check("Server spawn", Status.WARN, "configured spawn chunk is not loaded yet"));
        else checks.add(new Check("Server spawn", Status.OK, "configured and loaded"));
    }

    private void checkTutorial(List<Check> checks) {
        ConfigurationSection root = plugin.getConfigManager().tutorial().getConfigurationSection("tutorial");
        if (root == null || !root.getBoolean("enabled", true)) {
            checks.add(new Check("Tutorial", Status.WARN, "tutorial is disabled"));
            return;
        }
        String worldName = root.getString("world", "");
        World world = worldName.isBlank() ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            checks.add(new Check("Tutorial", Status.FAIL, "tutorial world '" + worldName + "' is not loaded"));
            return;
        }
        List<String> problems = new ArrayList<>();
        for (String point : List.of("start", "mob", "shop", "egg")) {
            double x = root.getDouble("route." + point + ".x", Double.NaN);
            double y = root.getDouble("route." + point + ".y", Double.NaN);
            double z = root.getDouble("route." + point + ".z", Double.NaN);
            String problem = LocationSafety.problem(new Location(world, x, y, z), "tutorial route " + point, false);
            if (problem != null) problems.add(problem);
        }
        checks.add(problems.isEmpty()
                ? new Check("Tutorial", Status.OK, "world and four route points are valid")
                : new Check("Tutorial", Status.FAIL, String.join("; ", problems)));
    }

    private void checkMobDefinitions(List<Check> checks) {
        ConfigurationSection mobs = plugin.getConfigManager().mobs().getConfigurationSection("mobs");
        if (mobs == null || mobs.getKeys(false).isEmpty()) {
            checks.add(new Check("Mob definitions", Status.FAIL, "mobs.yml has no mob definitions"));
            return;
        }
        List<String> problems = new ArrayList<>();
        for (String id : mobs.getKeys(false)) {
            ConfigurationSection section = mobs.getConfigurationSection(id);
            if (section == null || !section.getBoolean("enabled", true)) continue;
            String zoneId = section.getString("zone", "");
            if (!zoneId.isBlank() && !plugin.getZoneManager().isRegionUsable(zoneId)) problems.add(id + " references an unusable zone '" + zoneId + "'");
        }
        checks.add(problems.isEmpty()
                ? new Check("Mob definitions", Status.OK, mobs.getKeys(false).size() + " configured entries are available")
                : new Check("Mob definitions", Status.FAIL, String.join("; ", problems)));
    }

    private void checkResourcePack(List<Check> checks) {
        ConfigurationSection root = plugin.getConfigManager().hud().getConfigurationSection("resource-pack");
        if (root == null || !root.getBoolean("enabled", false)) {
            checks.add(new Check("HUD resource pack", Status.WARN, "disabled; Unicode actionbar fallback will be used"));
            return;
        }
        String url = root.getString("url", "");
        String sha1 = root.getString("sha1", "").replace(" ", "");
        if (!url.startsWith("https://")) {
            checks.add(new Check("HUD resource pack", Status.FAIL, "enabled resource pack URL must use HTTPS"));
        } else if (!sha1.matches("(?i)[0-9a-f]{40}")) {
            checks.add(new Check("HUD resource pack", Status.FAIL, "enabled resource pack requires a 40-character SHA-1 hash"));
        } else {
            int online = Bukkit.getOnlinePlayers().size();
            int loaded = plugin.getActionBarManager() == null ? 0 : plugin.getActionBarManager().readySpritePlayers();
            Status status = online > 0 && loaded < online ? Status.WARN : Status.OK;
            checks.add(new Check("HUD resource pack", status, "HTTPS URL and SHA-1 are configured; " + loaded + "/" + online + " online player(s) reported a successful load"));
        }
    }

    private void checkDiscord(List<Check> checks) {
        if (!plugin.getConfigManager().discord().getBoolean("enabled", false)) { checks.add(new Check("Discord", Status.WARN, "disabled; account linking and relay are off")); return; }
        String guild = plugin.getConfigManager().discord().getString("guild-id", "");
        String channel = plugin.getConfigManager().discord().getString("channel-id", "");
        String role = plugin.getConfigManager().discord().getString("linked-role-id", "");
        if (!guild.matches("\\d{5,32}") || !channel.matches("\\d{5,32}") || !role.matches("\\d{5,32}")) {
            checks.add(new Check("Discord configuration", Status.FAIL, "enabled integration requires numeric guild-id, channel-id, and linked-role-id"));
            return;
        }
        String env = plugin.getConfigManager().discord().getString("token-env", "GROWAPET_DISCORD_TOKEN");
        if (System.getenv(env) == null || System.getenv(env).isBlank()) checks.add(new Check("Discord", Status.FAIL, env + " is not configured"));
        else if (!plugin.getDiscordIntegration().isAvailable()) checks.add(new Check("Discord", Status.WARN, "token is present but the gateway is not currently connected"));
        else checks.add(new Check("Discord", Status.OK, "gateway connected; verify privileged intents in the Discord developer portal"));
    }

    private void checkCommerce(List<Check> checks) {
        if (!plugin.getConfigManager().commerce().getBoolean("enabled", false)) { checks.add(new Check("Tebex commerce", Status.WARN, "disabled; no paid fulfilment is possible")); return; }
        String env = plugin.getConfigManager().commerce().getString("secret-env", "GROWAPET_TEBEX_SECRET");
        boolean pluginPresent = plugin.getCommerceFulfilmentService().providerInstalled();
        if (!pluginPresent) checks.add(new Check("Tebex plugin", Status.FAIL, "official Tebex plugin is missing or disabled")); else checks.add(new Check("Tebex plugin", Status.OK, "official Tebex plugin is enabled"));
        if (System.getenv(env) == null || System.getenv(env).isBlank()) checks.add(new Check("Tebex secret", Status.FAIL, env + " is not configured")); else checks.add(new Check("Tebex secret", Status.OK, "provider secret is available from the environment"));
        if (!plugin.getConfigManager().commerce().getBoolean("paid-fulfilment-enabled", false)) checks.add(new Check("Paid fulfilment gate", Status.WARN, "disabled until economy simulation and catalog checksum are approved"));
    }

    private void checkSeasons(List<Check> checks) {
        if (plugin.getConfigManager().seasons() == null) { checks.add(new Check("Season definitions", Status.FAIL, "seasons.yml is unavailable")); return; }
        List<String> invalid = plugin.getSeasonService().ids().stream().filter(id -> !plugin.getSeasonService().validate(id)).toList();
        if (plugin.getSeasonService().ids().isEmpty()) checks.add(new Check("Season definitions", Status.WARN, "no season definitions are configured"));
        else checks.add(invalid.isEmpty() ? new Check("Season definitions", Status.OK, plugin.getSeasonService().ids().size() + " validated definition(s)") : new Check("Season definitions", Status.FAIL, "invalid definitions: " + String.join(", ", invalid)));
    }

    private void checkShopCatalog(List<Check> checks) {
        String active = plugin.getConfigManager().shopCatalog().getString("active-catalog", "legacy");
        boolean known = plugin.getConfigManager().shopCatalog().isConfigurationSection("catalogs." + active);
        checks.add(known ? new Check("Shop catalog", Status.OK, "active catalog: " + active + "; zones-v1 remains opt-in") : new Check("Shop catalog", Status.FAIL, "active catalog '" + active + "' is not defined"));
    }

    public record Report(List<Check> checks) {
        public boolean healthy() { return checks.stream().noneMatch(check -> check.status() == Status.FAIL); }
        public long failures() { return checks.stream().filter(check -> check.status() == Status.FAIL).count(); }
        public long warnings() { return checks.stream().filter(check -> check.status() == Status.WARN).count(); }
    }

    public record Check(String name, Status status, String detail) {
        public NamedTextColor color() { return status == Status.OK ? NamedTextColor.GREEN : status == Status.WARN ? NamedTextColor.YELLOW : NamedTextColor.RED; }
        public String symbol() { return status == Status.OK ? "✓" : status == Status.WARN ? "!" : "✗"; }
    }

    public enum Status { OK, WARN, FAIL }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
