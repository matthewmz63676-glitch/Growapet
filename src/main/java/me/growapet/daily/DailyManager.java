package me.growapet.daily;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import me.growapet.utils.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Connection;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Durable 24-hour reward claims. Database state is the source of truth. */
public final class DailyManager {
    public static final long COOLDOWN_MILLIS = Duration.ofHours(24).toMillis();
    private final GrowAPet plugin;
    private final java.util.Set<UUID> pending = ConcurrentHashMap.newKeySet();

    public DailyManager(GrowAPet plugin) { this.plugin = plugin; }

    public CompletableFuture<Status> status(UUID playerId) {
        return plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT last_claim_at,claim_count FROM daily_claims WHERE player_uuid=?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet rows = statement.executeQuery()) {
                    if (!rows.next()) return new Status(0, 0);
                    return new Status(rows.getLong(1), rows.getLong(2));
                }
            }
        });
    }

    public CompletableFuture<Boolean> claim(Player player) {
        UUID playerId = player.getUniqueId();
        PlayerData data = plugin.getPlayerManager().get(playerId);
        if (data == null || !pending.add(playerId) || !data.tryLockEconomy()) {
            pending.remove(playerId);
            Messages.send(player, "<red>Another reward transaction is already in progress.</red>");
            return CompletableFuture.completedFuture(false);
        }
        long now = System.currentTimeMillis();
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        java.util.concurrent.atomic.AtomicBoolean claimed = new java.util.concurrent.atomic.AtomicBoolean();
        plugin.getDatabase().transaction(connection -> {
            if (claimOnce(connection, playerId, now)) claimed.set(true);
            return null;
        }).whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                data.unlockEconomy();
                if (error != null) {
                    plugin.getLogger().severe("Daily claim failed for " + playerId + ": " + error.getMessage());
                    if (player.isOnline()) Messages.send(player, "<red>Your daily reward could not be claimed. Nothing was consumed.</red>");
                    result.complete(false);
                } else if (!claimed.get()) {
                    if (player.isOnline()) Messages.send(player, "<yellow>Your daily reward is still on cooldown.</yellow>");
                    result.complete(false);
                } else {
                    if (plugin.getPlayerManager().get(playerId) == data) data.addCredits(1);
                    if (player.isOnline()) Messages.send(player, "<light_purple><bold>DAILY REWARD</bold></light_purple> <dark_gray>•</dark_gray> <gray>You received <white>1 Credit</white>.</gray>");
                    result.complete(true);
                }
            } finally { pending.remove(playerId); }
        }));
        return result;
    }

    static boolean claimOnce(Connection connection, UUID playerId, long now) throws Exception {
        long cutoff = now - COOLDOWN_MILLIS;
        int won;
            try (PreparedStatement claim = connection.prepareStatement(
                    "INSERT INTO daily_claims(player_uuid,last_claim_at,claim_count) VALUES(?,?,1) " +
                            "ON CONFLICT(player_uuid) DO UPDATE SET last_claim_at=excluded.last_claim_at,claim_count=daily_claims.claim_count+1 " +
                            "WHERE daily_claims.last_claim_at<=?")) {
                claim.setString(1, playerId.toString()); claim.setLong(2, now); claim.setLong(3, cutoff);
                won = claim.executeUpdate();
            }
            if (won != 1) return false;
            try (PreparedStatement reward = connection.prepareStatement(
                    "UPDATE players SET credits=CASE WHEN credits=9223372036854775807 THEN credits ELSE credits+1 END WHERE uuid=?")) {
                reward.setString(1, playerId.toString());
                if (reward.executeUpdate() != 1) throw new IllegalStateException("Player row missing");
            }
        return true;
    }

    public record Status(long lastClaimAt, long claimCount) {
        public long availableAt() { return lastClaimAt == 0 ? 0 : lastClaimAt + COOLDOWN_MILLIS; }
        public boolean ready(long now) { return lastClaimAt == 0 || now >= availableAt(); }
        public long remainingMillis(long now) { return Math.max(0, availableAt() - now); }
    }
}
