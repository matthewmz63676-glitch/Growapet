package me.growapet.rewards;

import me.growapet.GrowAPet;
import me.growapet.boosts.BoostType;
import me.growapet.events.EventType;
import me.growapet.models.PlayerData;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Main-thread cache of durable cosmetic and progression entitlements. */
public final class EntitlementService {
    private final GrowAPet plugin;
    private final Map<UUID, Set<String>> owned = new ConcurrentHashMap<>();

    public EntitlementService(GrowAPet plugin) { this.plugin = plugin; }

    public CompletableFuture<Void> load(UUID playerId) {
        return plugin.getDatabase().async(connection -> {
            Set<String> loaded = new HashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT entitlement_id FROM player_entitlements WHERE player_uuid=? AND active=1")) {
                statement.setString(1, playerId.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) loaded.add(rows.getString(1));
                }
            }
            return loaded;
        }).thenAccept(loaded -> {
            Set<String> values = ConcurrentHashMap.newKeySet();
            values.addAll(loaded);
            owned.put(playerId, values);
        });
    }

    public boolean has(UUID playerId, String entitlementId) {
        return owned.getOrDefault(playerId, Set.of()).contains(entitlementId);
    }

    public void cache(UUID playerId, String entitlementId) {
        owned.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(entitlementId);
    }

    public void revoke(UUID playerId, String entitlementId) {
        Set<String> values = owned.get(playerId);
        if (values != null) values.remove(entitlementId);
    }

    public void unload(UUID playerId) { owned.remove(playerId); }

    public double expMultiplier(UUID playerId) {
        double multiplier = has(playerId, "discord.link.exp5") ? 1.05 : 1.0;
        if (plugin.getPlotBoostManager() != null) multiplier *= plugin.getPlotBoostManager().multiplier(playerId, BoostType.MOB_EXP);
        return Math.min(100.0, multiplier);
    }

    /** Recomputes effective channels from durable sources instead of persisting a compounded multiplier. */
    public Map<String, Double> effectiveModifiers(UUID playerId) {
        PlayerData data = plugin.getPlayerManager() == null ? null : plugin.getPlayerManager().get(playerId);
        double coins = data == null ? 1.0 : data.getCoinMultiplier();
        double gems = data == null ? 1.0 : data.getGemMultiplier();
        double exp = data == null ? 1.0 : data.getExpMultiplier();
        if (has(playerId, "discord.link.exp5")) exp *= 1.05;
        if (plugin.getPlotBoostManager() != null) {
            coins *= plugin.getPlotBoostManager().multiplier(playerId, BoostType.COINS);
            gems *= plugin.getPlotBoostManager().multiplier(playerId, BoostType.GEMS);
            exp *= plugin.getPlotBoostManager().multiplier(playerId, BoostType.MOB_EXP);
        }
        if (plugin.getEventManager() != null) {
            coins *= plugin.getEventManager().multiplier(EventType.DOUBLE_COINS);
            gems *= plugin.getEventManager().multiplier(EventType.DOUBLE_GEMS);
            exp *= plugin.getEventManager().multiplier(EventType.DOUBLE_EXP);
        }
        return Map.of("COINS", cap(coins), "GEMS", cap(gems), "MOB_EXP", cap(exp),
                "PET_EXP", cap(plugin.getPlotBoostManager() == null ? 1.0 : plugin.getPlotBoostManager().multiplier(playerId, BoostType.PET_EXP)),
                "HATCH_SPEED", cap(plugin.getPlotBoostManager() == null ? 1.0 : plugin.getPlotBoostManager().multiplier(playerId, BoostType.HATCH_SPEED)));
    }

    private static double cap(double value) { return Double.isFinite(value) ? Math.max(1.0, Math.min(100.0, value)) : 1.0; }
}
