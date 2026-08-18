package me.growapet.tutorial;

import me.growapet.GrowAPet;
import me.growapet.utils.Messages;
import me.growapet.utils.LocationSafety;
import me.growapet.display.VirtualTextDisplayService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Durable, action-gated onboarding adapted from the supplied Petcore NPC route. */
public final class TutorialManager {
    private final GrowAPet plugin;
    private final Map<UUID, TutorialNpcSession> sessions = new HashMap<>();
    private final Set<UUID> transitions = ConcurrentHashMap.newKeySet();
    private final Set<UUID> starting = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Set<VirtualTextDisplayService.Handle>> previews = new HashMap<>();

    public TutorialManager(GrowAPet plugin) { this.plugin = plugin; }

    public void start(Player target, boolean administratorRequested) {
        requireMain();
        UUID playerId = target.getUniqueId();
        if (!enabled()) { Messages.send(target, "<red>The tutorial is disabled.</red>"); return; }
        if (sessions.containsKey(playerId) || !starting.add(playerId)) {
            Messages.send(target, "<yellow>Your tutorial is already running.</yellow>"); return;
        }
        Route route = route();
        if (route == null) { starting.remove(playerId); Messages.send(target, "<red>The tutorial route is not configured in a loaded world.</red>"); return; }
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement insert = connection.prepareStatement("INSERT OR IGNORE INTO tutorial_progress(player_uuid,stage,updated_at) VALUES(?,'MOB',?)")) {
                insert.setString(1, playerId.toString()); insert.setLong(2, System.currentTimeMillis()); insert.executeUpdate();
            }
            try (PreparedStatement select = connection.prepareStatement("SELECT stage FROM tutorial_progress WHERE player_uuid=?")) {
                select.setString(1, playerId.toString());
                try (ResultSet result = select.executeQuery()) { return result.next() ? TutorialStage.parse(result.getString(1)) : TutorialStage.MOB; }
            }
        }).whenComplete((stage, error) -> onMain(() -> {
            starting.remove(playerId);
            Player online = Bukkit.getPlayer(playerId);
            if (online == null) return;
            if (error != null) { plugin.getLogger().warning("Failed to load tutorial state for " + playerId + ": " + error.getMessage()); Messages.send(online, "<red>Your tutorial progress could not be loaded safely.</red>"); return; }
            if (stage == TutorialStage.COMPLETE && !administratorRequested && !plugin.getConfigManager().tutorial().getBoolean("tutorial.allow-replay", false)) {
                Messages.send(online, "<green>You have already completed the GrowAPet tutorial.</green>"); return;
            }
            if (stage == TutorialStage.COMPLETE) { resetThenStart(online); return; }
            begin(online, route, stage);
        }));
    }

    private void resetThenStart(Player player) {
        UUID playerId = player.getUniqueId();
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE tutorial_progress SET stage='MOB',updated_at=? WHERE player_uuid=?")) {
                statement.setLong(1, System.currentTimeMillis()); statement.setString(2, playerId.toString()); statement.executeUpdate();
            }
            return null;
        }).whenComplete((ignored, error) -> onMain(() -> {
            if (error != null) Messages.send(player, "<red>The tutorial could not be reset safely.</red>");
            else { Route current = route(); if (current != null && player.isOnline()) begin(player, current, TutorialStage.MOB); }
        }));
    }

    private void begin(Player player, Route route, TutorialStage stage) {
        org.bukkit.Location sessionStart = startFor(route, stage);
        org.bukkit.Location safeStart = LocationSafety.prepareForUse(sessionStart, "tutorial route start");
        if (safeStart == null) { Messages.send(player, "<red>The tutorial start point is unavailable or unsafe. An admin must run <white>/tutorial validate</white>.</red>"); return; }
        Runnable spawn = () -> {
            if (!player.isOnline() || sessions.containsKey(player.getUniqueId())) return;
            try {
                TutorialNpcSession session = new TutorialNpcSession(plugin, player, safeStart, route.speed, stage);
                sessions.put(player.getUniqueId(), session);
                session.spawn();
                session.swingArm();
                session.say("<aqua><bold>GROWAPET GUIDE</bold></aqua><newline><white>Welcome, <player>!</white>", Messages.value("player", player.getName()));
                Bukkit.getScheduler().runTaskLater(plugin, () -> showStage(session, route), 30L);
                Messages.send(player, "<aqua><bold>TUTORIAL STARTED</bold></aqua> <dark_gray>•</dark_gray> <gray>Follow your private guide.</gray>");
            } catch (RuntimeException error) {
                sessions.remove(player.getUniqueId());
                plugin.getLogger().warning("Failed to spawn tutorial NPC for " + player.getUniqueId() + ": " + error.getMessage());
                Messages.send(player, "<red>The tutorial NPC could not be created. Check PacketEvents and the route configuration.</red>");
            }
        };
        if (route.teleportPlayer && (!player.getWorld().equals(safeStart.getWorld()) || player.getLocation().distanceSquared(safeStart) > 256.0D)) {
            player.teleportAsync(safeStart).thenAccept(success -> onMain(() -> { if (success) spawn.run(); else Messages.send(player, "<red>Could not teleport to the tutorial route.</red>"); }));
        } else if (!player.getWorld().equals(safeStart.getWorld())) {
            Messages.send(player, "<red>Travel to <white><world></white> before starting the tutorial.</red>", Messages.value("world", safeStart.getWorld().getName()));
        } else spawn.run();
    }

    public void notifyAction(Player player, TutorialAction action) {
        notifyAction(player.getUniqueId(), action);
    }

    /** Persists trusted actions even if the player disconnects while the gameplay transaction commits. */
    public void notifyAction(UUID playerId, TutorialAction action) {
        TutorialStage previous = switch (action) { case MOB_KILL -> TutorialStage.MOB; case SHOP_PURCHASE -> TutorialStage.SHOP; case EGG_HATCH -> TutorialStage.EGG; };
        TutorialStage next = previous.next();
        if (!transitions.add(playerId)) return;
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("UPDATE tutorial_progress SET stage=?,updated_at=? WHERE player_uuid=? AND stage=?")) {
                statement.setString(1, next.name()); statement.setLong(2, System.currentTimeMillis());
                statement.setString(3, playerId.toString()); statement.setString(4, previous.name());
                return statement.executeUpdate();
            }
        }).whenComplete((changed, error) -> onMain(() -> {
            transitions.remove(playerId);
            if (error != null || changed == null || changed != 1) {
                if (error != null) {
                    plugin.getLogger().warning("Tutorial transition failed for " + playerId + ": " + error.getMessage());
                    Player online = Bukkit.getPlayer(playerId);
                    if (online != null) Messages.send(online, "<red>Tutorial progress could not be saved; the step was not advanced.</red>");
                }
                return;
            }
            TutorialNpcSession session = sessions.get(playerId);
            if (session != null && !session.ended() && session.stage() == previous) {
                session.stage(next);
                showStage(session, route());
            }
        }));
    }

    private void showStage(TutorialNpcSession session, Route route) {
        if (route == null || session.ended() || sessions.get(session.target().getUniqueId()) != session) return;
        switch (session.stage()) {
            case MOB -> walk(session, route.mob, "<yellow><bold>STEP 1</bold></yellow><newline><white>Defeat one GrowAPet mob.</white>",
                    "<gray>• Objective → <white>Kill one tracked mob</white></gray>");
            case SHOP -> walk(session, route.shop, "<yellow><bold>STEP 2</bold></yellow><newline><white>Purchase gear, a tool, or an upgrade.</white>",
                    "<gray>• Objective → <white>Complete one shop purchase</white></gray>");
            case EGG -> walk(session, route.egg, "<yellow><bold>STEP 3</bold></yellow><newline><white>Place and hatch a pet egg.</white>",
                    "<gray>• Objective → <white>Hatch one pet egg</white></gray>");
            case COMPLETE -> {
                session.say("<green><bold>TUTORIAL COMPLETE</bold></green><newline><white>Your GrowAPet journey begins!</white>");
                Messages.send(session.target(), "<green><bold>TUTORIAL COMPLETE</bold></green> <dark_gray>•</dark_gray> <gray>Your progress has been saved.</gray>");
                Bukkit.getScheduler().runTaskLater(plugin, () -> forceStop(session.target()), 60L);
            }
        }
    }

    private void walk(TutorialNpcSession session, org.bukkit.Location destination, String speech, String objective) {
        session.say("<aqua>Follow me →</aqua>");
        session.walkTo(destination, () -> {
            if (session.ended() || sessions.get(session.target().getUniqueId()) != session) return;
            session.say(speech);
            Messages.send(session.target(), "<aqua><bold>TUTORIAL</bold></aqua> <dark_gray>•</dark_gray> " + objective);
        });
    }

    public void stop(Player player) { forceStop(player); Messages.send(player, "<yellow>Tutorial visuals stopped.</yellow> <gray>Your saved progress was kept.</gray>"); }
    public void forceStop(Player player) { requireMain(); TutorialNpcSession session = sessions.remove(player.getUniqueId()); transitions.remove(player.getUniqueId()); starting.remove(player.getUniqueId()); if (session != null) session.despawn(); clearPreview(player.getUniqueId()); }
    public void shutdown() { requireMain(); for (TutorialNpcSession session : sessions.values()) session.despawn(); sessions.clear(); for (UUID playerId : Set.copyOf(previews.keySet())) clearPreview(playerId); transitions.clear(); starting.clear(); }
    public void onPlayerReady(Player player) { if (plugin.getConfigManager().tutorial().getBoolean("tutorial.auto-start", false)) start(player, false); }
    public boolean isRunning(UUID playerId) { return sessions.containsKey(playerId); }

    public void reset(Player target) {
        forceStop(target); UUID id = target.getUniqueId();
        plugin.getDatabase().async(connection -> { try (PreparedStatement statement = connection.prepareStatement("DELETE FROM tutorial_progress WHERE player_uuid=?")) { statement.setString(1, id.toString()); statement.executeUpdate(); } return null; })
                .whenComplete((ignored, error) -> onMain(() -> Messages.send(target, error == null ? "<green>Tutorial progress reset.</green>" : "<red>Tutorial progress could not be reset.</red>")));
    }

    /** Saves one route point from an admin's current location. */
    public boolean setPoint(String point, org.bukkit.Location location) {
        requireMain();
        if (!List.of("start", "mob", "shop", "egg").contains(point) || location == null || location.getWorld() == null) return false;
        String problem = LocationSafety.problem(location, "tutorial route " + point, true);
        if (problem != null) return false;
        ConfigurationSection root = plugin.getConfigManager().tutorial().getConfigurationSection("tutorial");
        if (root == null) return false;
        root.set("world", location.getWorld().getName());
        root.set("route." + point + ".x", location.getX()); root.set("route." + point + ".y", location.getY()); root.set("route." + point + ".z", location.getZ());
        root.set("route." + point + ".yaw", location.getYaw()); root.set("route." + point + ".pitch", location.getPitch());
        plugin.getConfigManager().save("tutorial.yml");
        return true;
    }

    /** Returns actionable route errors without changing state or loading chunks. */
    public java.util.List<String> validateRoute() {
        ConfigurationSection root = plugin.getConfigManager().tutorial().getConfigurationSection("tutorial");
        if (root == null || !root.getBoolean("enabled", true)) return List.of("tutorial is disabled");
        World world = Bukkit.getWorld(root.getString("world", ""));
        if (world == null) return List.of("tutorial world '" + root.getString("world", "") + "' is not loaded");
        java.util.List<String> problems = new java.util.ArrayList<>();
        for (String point : List.of("start", "mob", "shop", "egg")) {
            double x = root.getDouble("route." + point + ".x", Double.NaN), y = root.getDouble("route." + point + ".y", Double.NaN), z = root.getDouble("route." + point + ".z", Double.NaN);
            String problem = LocationSafety.problem(new org.bukkit.Location(world, x, y, z), "tutorial route " + point, false);
            if (problem != null) problems.add(problem);
        }
        return List.copyOf(problems);
    }

    /** Shows temporary packet-only labels at each route point for the requesting admin. */
    public boolean preview(Player player) {
        requireMain();
        if (player == null) return false;
        java.util.List<String> problems = validateRoute();
        if (!problems.isEmpty()) return false;
        clearPreview(player.getUniqueId());
        ConfigurationSection root = plugin.getConfigManager().tutorial().getConfigurationSection("tutorial");
        World world = Bukkit.getWorld(root.getString("world"));
        Set<VirtualTextDisplayService.Handle> handles = new java.util.HashSet<>();
        for (String point : List.of("start", "mob", "shop", "egg")) {
            org.bukkit.Location location = location(world, root, "route." + point).add(0, 1.6, 0);
            handles.add(plugin.getVirtualTextDisplays().create(location, Messages.parse("<aqua><bold>" + point.toUpperCase(java.util.Locale.ROOT) + " ROUTE POINT</bold></aqua>"), viewer -> viewer.getUniqueId().equals(player.getUniqueId())));
        }
        previews.put(player.getUniqueId(), handles);
        Bukkit.getScheduler().runTaskLater(plugin, () -> clearPreview(player.getUniqueId()), 200L);
        Messages.send(player, "<aqua><bold>TUTORIAL PREVIEW</bold></aqua> <dark_gray>•</dark_gray> <gray>Route labels are visible only to you for 10 seconds.</gray>");
        return true;
    }

    private void clearPreview(UUID playerId) {
        Set<VirtualTextDisplayService.Handle> handles = previews.remove(playerId);
        if (handles == null) return;
        for (VirtualTextDisplayService.Handle handle : handles) plugin.getVirtualTextDisplays().remove(handle);
    }

    private boolean enabled() { return plugin.getConfigManager().tutorial().getBoolean("tutorial.enabled", true); }
    private Route route() {
        ConfigurationSection root = plugin.getConfigManager().tutorial().getConfigurationSection("tutorial");
        if (root == null) return null;
        if (!validateRoute().isEmpty()) return null;
        World world = Bukkit.getWorld(root.getString("world", "world")); if (world == null) return null;
        return new Route(location(world, root, "route.start"), location(world, root, "route.mob"),
                location(world, root, "route.shop"), location(world, root, "route.egg"),
                Math.max(0.05D, Math.min(1.0D, root.getDouble("walk-blocks-per-tick", 0.19D))),
                root.getBoolean("teleport-player", true));
    }

    private static org.bukkit.Location location(World world, ConfigurationSection root, String path) {
        return new org.bukkit.Location(world, root.getDouble(path + ".x"), root.getDouble(path + ".y"), root.getDouble(path + ".z"),
                (float) root.getDouble(path + ".yaw", 0), (float) root.getDouble(path + ".pitch", 0));
    }
    private static org.bukkit.Location startFor(Route route, TutorialStage stage) { return switch (stage) { case MOB -> route.start; case SHOP -> route.mob; case EGG, COMPLETE -> route.shop; }; }
    private void onMain(Runnable action) { if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, action); }
    private static void requireMain() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Tutorial lifecycle must run on the server thread"); }
    private record Route(org.bukkit.Location start, org.bukkit.Location mob, org.bukkit.Location shop, org.bukkit.Location egg, double speed, boolean teleportPlayer) {}
}
