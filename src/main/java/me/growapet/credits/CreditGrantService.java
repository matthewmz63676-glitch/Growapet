package me.growapet.credits;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Idempotent receipt boundary for gameplay, web-store, and administrative Credit grants. */
public final class CreditGrantService {
    private final GrowAPet plugin;
    public CreditGrantService(GrowAPet plugin) { this.plugin = plugin; }

    public CompletableFuture<Boolean> grant(String receiptId, UUID playerId, long credits, String source) {
        if (receiptId == null || !receiptId.matches("[A-Za-z0-9._:-]{1,128}") || playerId == null || credits <= 0 || credits > 1_000_000_000L)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid Credit grant"));
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Runnable begin = () -> beginGrant(receiptId, playerId, credits, source, result);
        if (Bukkit.isPrimaryThread()) begin.run();
        else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, begin);
        else result.completeExceptionally(new IllegalStateException("Plugin is stopping"));
        return result;
    }

    private void beginGrant(String receiptId, UUID playerId, long credits, String source, CompletableFuture<Boolean> result) {
        PlayerData data = plugin.getPlayerManager().get(playerId);
        if (data != null && !data.tryLockEconomy()) {
            if (plugin.isEnabled()) Bukkit.getScheduler().runTaskLater(plugin,
                    () -> beginGrant(receiptId, playerId, credits, source, result), 1L);
            else result.completeExceptionally(new IllegalStateException("Plugin is stopping"));
            return;
        }
        AtomicBoolean granted = new AtomicBoolean();
        plugin.getDatabase().transaction(connection -> {
            granted.set(grantOnce(connection, receiptId, playerId, credits, source, System.currentTimeMillis()));
            return null;
        }).whenComplete((ignored, error) -> {
            if (!plugin.isEnabled()) {
                if (error != null) result.completeExceptionally(error); else result.complete(granted.get());
                return;
            }
            completeOnMain(() -> {
            if (data != null) data.unlockEconomy();
            if (error != null) { result.completeExceptionally(error); return; }
            if (granted.get() && data != null && plugin.getPlayerManager().get(playerId) == data) data.addCredits(credits);
            result.complete(granted.get());
            });
        });
    }

    private void completeOnMain(Runnable action) {
        if (Bukkit.isPrimaryThread()) action.run();
        else Bukkit.getScheduler().runTask(plugin, action);
    }

    static boolean grantOnce(Connection connection, String receiptId, UUID playerId, long credits, String source, long now) throws Exception {
        try (PreparedStatement receipt = connection.prepareStatement("INSERT OR IGNORE INTO credit_grants(receipt_id,player_uuid,credits,source,granted_at)VALUES(?,?,?,?,?)")) {
            receipt.setString(1, receiptId); receipt.setString(2, playerId.toString()); receipt.setLong(3, credits);
            receipt.setString(4, source == null ? "UNKNOWN" : source); receipt.setLong(5, now);
            if (receipt.executeUpdate() != 1) return false;
        }
        try (PreparedStatement reward = connection.prepareStatement("UPDATE players SET credits=MIN(9223372036854775807,credits+?) WHERE uuid=?")) {
            reward.setLong(1, credits); reward.setString(2, playerId.toString());
            if (reward.executeUpdate() != 1) throw new IllegalStateException("Player row missing");
        }
        return true;
    }
}
