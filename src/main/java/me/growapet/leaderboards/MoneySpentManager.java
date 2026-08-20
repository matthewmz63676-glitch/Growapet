package me.growapet.leaderboards;

import me.growapet.GrowAPet;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/** UUID-keyed, cents-precise manual real-money ledger used by TOPMONEYSPENT. */
public final class MoneySpentManager {
    private static final long MAX_CENTS = 100_000_000_000L;
    private final GrowAPet plugin;
    private final AtomicLong refreshSequence = new AtomicLong();
    private volatile List<Entry> cache = List.of();
    private volatile boolean running;

    public MoneySpentManager(GrowAPet plugin) { this.plugin = plugin; }
    public void start() { running = true; refresh(); }
    public void stop() { running = false; refreshSequence.incrementAndGet(); cache = List.of(); }

    public CompletableFuture<Target> resolve(String input) {
        if (input == null || input.isBlank()) return CompletableFuture.completedFuture(null);
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) return CompletableFuture.completedFuture(new Target(online.getUniqueId(), online.getName()));
        try {
            UUID id = UUID.fromString(input);
            return plugin.getDatabase().async(connection -> {
                try (PreparedStatement statement = connection.prepareStatement("SELECT name FROM players WHERE uuid=?")) {
                    statement.setString(1, id.toString());
                    try (ResultSet row = statement.executeQuery()) {
                        return row.next() ? new Target(id, safeName(row.getString(1))) : null;
                    }
                }
            });
        } catch (IllegalArgumentException ignored) { }
        return plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT uuid,name FROM players WHERE name=? COLLATE NOCASE LIMIT 1")) {
                statement.setString(1, input);
                try (ResultSet row = statement.executeQuery()) {
                    return row.next() ? new Target(UUID.fromString(row.getString(1)), safeName(row.getString(2))) : null;
                }
            }
        });
    }

    public CompletableFuture<Entry> find(Target target) {
        if (target == null) return CompletableFuture.completedFuture(null);
        return plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT player_uuid,player_name,amount_cents FROM money_spent WHERE player_uuid=?")) {
                statement.setString(1, target.uuid().toString());
                try (ResultSet row = statement.executeQuery()) {
                    return row.next() ? new Entry(UUID.fromString(row.getString(1)), safeName(row.getString(2)), row.getLong(3)) : null;
                }
            }
        });
    }

    public CompletableFuture<Long> add(Target target, BigDecimal amount) {
        if (target == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Player is required"));
        long cents = toCents(amount);
        return plugin.getDatabase().transaction(connection -> {
            long current = 0;
            try (PreparedStatement lookup = connection.prepareStatement("SELECT amount_cents FROM money_spent WHERE player_uuid=?")) {
                lookup.setString(1, target.uuid().toString());
                try (ResultSet row = lookup.executeQuery()) { if (row.next()) current = row.getLong(1); }
            }
            long updated = Math.addExact(current, cents);
            if (updated > MAX_CENTS) throw new IllegalArgumentException("Lifetime total exceeds the safety limit");
            try (PreparedStatement upsert = connection.prepareStatement("INSERT INTO money_spent(player_uuid,player_name,amount_cents,updated_at) VALUES(?,?,?,?) ON CONFLICT(player_uuid) DO UPDATE SET player_name=excluded.player_name,amount_cents=excluded.amount_cents,updated_at=excluded.updated_at")) {
                upsert.setString(1, target.uuid().toString()); upsert.setString(2, safeName(target.name()));
                upsert.setLong(3, updated); upsert.setLong(4, System.currentTimeMillis()); upsert.executeUpdate();
            }
            audit(connection, "TOPMONEYSPENT_ADD", target.uuid().toString(), format(cents));
            return updated;
        }).whenComplete((ignored, error) -> { if (error == null) refresh(); });
    }

    public CompletableFuture<Boolean> clear(Target target) {
        if (target == null) return CompletableFuture.completedFuture(false);
        return plugin.getDatabase().transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM money_spent WHERE player_uuid=?")) {
                statement.setString(1, target.uuid().toString());
                boolean removed = statement.executeUpdate() == 1;
                audit(connection, "TOPMONEYSPENT_CLEAR", target.uuid().toString(), target.name());
                return removed;
            }
        }).whenComplete((ignored, error) -> { if (error == null) refresh(); });
    }

    public CompletableFuture<Integer> clearAll() {
        return plugin.getDatabase().transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM money_spent")) {
                int count = statement.executeUpdate();
                audit(connection, "TOPMONEYSPENT_CLEAR_ALL", "", String.valueOf(count));
                return count;
            }
        }).whenComplete((ignored, error) -> { if (error == null) refresh(); });
    }

    public CompletableFuture<List<Entry>> top(int limit) {
        int bounded = Math.max(1, Math.min(100, limit));
        return plugin.getDatabase().async(connection -> {
            List<Entry> entries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT player_uuid,player_name,amount_cents FROM money_spent ORDER BY amount_cents DESC,player_name COLLATE NOCASE ASC LIMIT ?")) {
                statement.setInt(1, bounded);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) entries.add(new Entry(UUID.fromString(rows.getString(1)), safeName(rows.getString(2)), rows.getLong(3)));
                }
            }
            return List.copyOf(entries);
        });
    }

    public void refresh() {
        if (!running) return;
        long sequence = refreshSequence.incrementAndGet();
        top(100).thenAccept(entries -> { if (running && sequence == refreshSequence.get()) cache = entries; })
                .exceptionally(error -> { if (running && sequence == refreshSequence.get()) plugin.getLogger().warning("TOPMONEYSPENT refresh failed: " + error.getMessage()); return null; });
    }
    public Entry cached(int rank) { List<Entry> snapshot = cache; return rank < 1 || rank > snapshot.size() ? null : snapshot.get(rank - 1); }

    public static long toCents(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Amount must be positive");
        BigDecimal cents;
        try { cents = amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("Amount has more than two decimal places", error); }
        if (cents.compareTo(BigDecimal.valueOf(MAX_CENTS)) > 0) throw new IllegalArgumentException("Amount exceeds the safety limit");
        return cents.longValueExact();
    }

    public static String format(long cents) { return String.format(Locale.US, "$%,.2f", cents / 100.0); }

    private static void audit(Connection connection, String action, String target, String details) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO admin_audit(actor,action,target,details,created_at) VALUES(?,?,?,?,?)")) {
            statement.setString(1, "SYSTEM"); statement.setString(2, action); statement.setString(3, target); statement.setString(4, details); statement.setLong(5, System.currentTimeMillis()); statement.executeUpdate();
        }
    }

    private static String safeName(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String clean = value.replaceAll("[^A-Za-z0-9_]", "");
        return clean.isBlank() ? "Unknown" : clean.substring(0, Math.min(16, clean.length()));
    }

    public record Target(UUID uuid, String name) { }
    public record Entry(UUID uuid, String name, long cents) { }
}
