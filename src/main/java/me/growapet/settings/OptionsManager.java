package me.growapet.settings;

import me.growapet.GrowAPet;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player preferences with an explicit load future for command/UI gates. */
public final class OptionsManager {
    private final GrowAPet plugin;
    private final Map<UUID, Map<String, String>> values = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> loading = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> loaded = ConcurrentHashMap.newKeySet();

    public OptionsManager(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Void> load(UUID id) {
        loaded.remove(id);
        CompletableFuture<Void> future = plugin.getDatabase().async(connection -> {
            Map<String, String> result = new ConcurrentHashMap<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT setting_key,setting_value FROM settings WHERE player_uuid=?")) {
                statement.setString(1, id.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) result.put(rows.getString(1), rows.getString(2));
                }
            }
            return result;
        }).thenAccept(result -> {
            values.merge(id, result, (existing, fromDatabase) -> {
                fromDatabase.putAll(existing);
                return fromDatabase;
            });
            loaded.add(id);
        });
        loading.put(id, future);
        future.whenComplete((ignored, error) -> loading.remove(id, future));
        return future;
    }

    /** Returns the current load operation, starting one if the join hook has not done so yet. */
    public CompletableFuture<Void> awaitLoaded(UUID id) {
        if (loaded.contains(id)) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> future = loading.get(id);
        return future != null ? future : load(id);
    }

    public boolean enabled(UUID id, String key, boolean fallback) {
        return Boolean.parseBoolean(values.getOrDefault(id, Map.of())
                .getOrDefault(key, String.valueOf(fallback)));
    }

    public String get(UUID id, String key, String fallback) {
        return values.getOrDefault(id, Map.of()).getOrDefault(key, fallback);
    }

    public CompletableFuture<Void> set(UUID id, String key, String value) {
        Map<String, String> playerValues = values.computeIfAbsent(id, ignored -> new ConcurrentHashMap<>());
        String previous = playerValues.put(key, value);
        return plugin.getDatabase().<Void>async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO settings(player_uuid,setting_key,setting_value)VALUES(?,?,?) " +
                            "ON CONFLICT(player_uuid,setting_key)DO UPDATE SET setting_value=excluded.setting_value")) {
                statement.setString(1, id.toString());
                statement.setString(2, key);
                statement.setString(3, value);
                statement.executeUpdate();
            }
            return null;
        }).whenComplete((ignored, error) -> {
            if (error == null) return;
            if (previous == null) playerValues.remove(key, value);
            else playerValues.replace(key, value, previous);
            plugin.getLogger().severe("Failed to persist setting " + key + " for " + id + ": " + error.getMessage());
        });
    }

    public boolean isLoaded(UUID id) {
        return loaded.contains(id);
    }

    public void cache(UUID id, String key, String value) {
        values.computeIfAbsent(id, ignored -> new ConcurrentHashMap<>()).put(key, value);
    }

    public void unload(UUID id) {
        loaded.remove(id);
        loading.remove(id);
        values.remove(id);
    }
}
