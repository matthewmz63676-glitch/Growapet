package me.growapet.boosts;

import me.growapet.GrowAPet;
import me.growapet.display.VirtualTextDisplayService;
import me.growapet.models.Plot;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Persisted permanent/temporary plot boosts and their packet-only status displays. */
public final class PlotBoostManager {
    private final GrowAPet plugin;
    private final Map<UUID, List<Boost>> boosts = new ConcurrentHashMap<>();
    private final Map<UUID, VirtualTextDisplayService.Handle> displays = new ConcurrentHashMap<>();
    private BukkitTask task;
    public PlotBoostManager(GrowAPet plugin) { this.plugin = plugin; }

    public CompletableFuture<Void> loadAll() {
        return plugin.getDatabase().async(connection -> {
            Map<UUID, List<Boost>> loaded = new java.util.HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM plot_boosts"); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    try {
                        Boost boost = new Boost(rows.getString("boost_id"), UUID.fromString(rows.getString("owner_uuid")),
                                BoostType.valueOf(rows.getString("boost_type")), Math.max(0, rows.getDouble("bonus")),
                                rows.getLong("starts_at"), (Long) rows.getObject("expires_at"), rows.getString("source"));
                        loaded.computeIfAbsent(boost.owner, ignored -> new ArrayList<>()).add(boost);
                    } catch (IllegalArgumentException error) { plugin.getLogger().warning("Skipping invalid plot boost row: " + error.getMessage()); }
                }
            }
            return loaded;
        }).thenAccept(loaded -> { boosts.clear(); boosts.putAll(loaded); });
    }

    public void start() {
        for (UUID owner : boosts.keySet()) refreshDisplay(owner);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, 200L);
    }

    public void stop() {
        if (task != null) task.cancel(); task = null;
        displays.values().forEach(plugin.getVirtualTextDisplays()::remove); displays.clear();
    }

    public double multiplier(UUID owner, BoostType type) {
        long now = System.currentTimeMillis();
        double bonus = boosts.getOrDefault(owner, List.of()).stream()
                .filter(boost -> boost.type == type && boost.active(now)).mapToDouble(Boost::bonus).sum();
        return Math.min(100, 1 + Math.max(0, bonus));
    }

    public CompletableFuture<Void> grant(UUID owner, BoostType type, double bonus, Long expiresAt, String source) {
        if (!Double.isFinite(bonus) || bonus <= 0) return CompletableFuture.failedFuture(new IllegalArgumentException("Boost bonus must be positive"));
        Boost boost = new Boost(UUID.randomUUID().toString(), owner, type, Math.min(99, bonus), System.currentTimeMillis(), expiresAt, source);
        return plugin.getDatabase().transaction(connection -> { insert(connection, boost); return null; })
                .thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> cache(boost)));
    }

    /**
     * Atomically consumes an owner-bound delivery id and creates its boost. A
     * copied stack can therefore never activate the same paid delivery twice.
     */
    public CompletableFuture<Boolean> grantFromDelivery(UUID owner, String deliveryId, BoostType type,
                                                         double bonus, long expiresAt, String source) {
        if (owner == null || deliveryId == null || deliveryId.isBlank() || type == null
                || !Double.isFinite(bonus) || bonus <= 0 || bonus > 9 || expiresAt <= System.currentTimeMillis())
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid boost delivery"));
        String boostId = "item:" + deliveryId;
        return plugin.getDatabase().transaction(connection -> {
            try (PreparedStatement claim = connection.prepareStatement(
                    "INSERT OR IGNORE INTO boost_item_claims(delivery_id,player_uuid,boost_id,activated_at) VALUES(?,?,?,?)")) {
                claim.setString(1, deliveryId); claim.setString(2, owner.toString()); claim.setString(3, boostId); claim.setLong(4, System.currentTimeMillis());
                if (claim.executeUpdate() != 1) return false;
            }
            insert(connection, new Boost(boostId, owner, type, Math.min(99, bonus), System.currentTimeMillis(), expiresAt, source));
            return true;
        }).thenApply(claimed -> {
            if (Boolean.TRUE.equals(claimed)) Bukkit.getScheduler().runTask(plugin, () -> {
                Boost boost = new Boost(boostId, owner, type, Math.min(99, bonus), System.currentTimeMillis(), expiresAt, source);
                cache(boost);
            });
            return claimed;
        });
    }

    public void insert(Connection connection, String id, UUID owner, BoostType type, double bonus, Long expiresAt, String source) throws Exception {
        insert(connection, new Boost(id, owner, type, bonus, System.currentTimeMillis(), expiresAt, source));
    }

    public void cache(String id, UUID owner, BoostType type, double bonus, Long expiresAt, String source) {
        cache(new Boost(id, owner, type, bonus, System.currentTimeMillis(), expiresAt, source));
    }

    private static void insert(Connection connection, Boost boost) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO plot_boosts(boost_id,owner_uuid,boost_type,bonus,starts_at,expires_at,source)VALUES(?,?,?,?,?,?,?)")) {
            statement.setString(1, boost.id); statement.setString(2, boost.owner.toString()); statement.setString(3, boost.type.name());
            statement.setDouble(4, boost.bonus); statement.setLong(5, boost.startsAt);
            if (boost.expiresAt == null) statement.setNull(6, java.sql.Types.BIGINT); else statement.setLong(6, boost.expiresAt);
            statement.setString(7, boost.source); statement.executeUpdate();
        }
    }

    private void cache(Boost boost) { boosts.computeIfAbsent(boost.owner, ignored -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(boost); refreshDisplay(boost.owner); }
    public void refreshDisplay(UUID owner) {
        if (!Bukkit.isPrimaryThread()) { Bukkit.getScheduler().runTask(plugin, () -> refreshDisplay(owner)); return; }
        Plot plot = plugin.getPlotManager().getPlot(owner); if (plot == null) return;
        VirtualTextDisplayService.Handle prior = displays.remove(owner); if (prior != null) plugin.getVirtualTextDisplays().remove(prior);
        Location location = plot.getCenter().clone().add(0.5, 2.8, 0.5);
        displays.put(owner, plugin.getVirtualTextDisplays().create(location, text(owner), viewer -> plot.contains(viewer.getLocation())));
    }

    private void refreshAll() {
        long now = System.currentTimeMillis();
        boosts.values().forEach(list -> list.removeIf(boost -> !boost.active(now)));
        for (UUID owner : new ArrayList<>(displays.keySet())) {
            VirtualTextDisplayService.Handle handle = displays.get(owner);
            if (handle != null) plugin.getVirtualTextDisplays().update(handle, text(owner));
        }
    }

    private net.kyori.adventure.text.Component text(UUID owner) {
        List<String> lines = new ArrayList<>(); lines.add("<aqua><bold>PLOT BOOSTS</bold></aqua>");
        for (BoostType type : BoostType.values()) {
            double bonus = multiplier(owner, type) - 1;
            if (bonus > 0) lines.add("<gray>• " + label(type) + " → <white>+" + Math.round(bonus * 100) + "%</white></gray>");
        }
        if (lines.size() == 1) lines.add("<gray>• No active boosts</gray>");
        return Messages.parse(String.join("\n", lines));
    }

    private static String label(BoostType type) { return switch (type) { case MOB_EXP -> "Mob EXP"; case PET_EXP -> "Pet EXP"; case HATCH_SPEED -> "Hatch Speed"; default -> type.name().substring(0,1)+type.name().substring(1).toLowerCase(); }; }
    private record Boost(String id, UUID owner, BoostType type, double bonus, long startsAt, Long expiresAt, String source) { boolean active(long now) { return expiresAt == null || expiresAt > now; } }
}
