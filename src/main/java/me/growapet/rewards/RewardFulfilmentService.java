package me.growapet.rewards;

import me.growapet.GrowAPet;
import me.growapet.models.PlayerData;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Exactly-once reward boundary. Every external origin supplies an idempotency key;
 * the database receipt is inserted before any balance, entitlement, or delivery row.
 */
public final class RewardFulfilmentService {
    private final GrowAPet plugin;

    public RewardFulfilmentService(GrowAPet plugin) { this.plugin = plugin; }

    public CompletableFuture<Boolean> fulfil(String receiptId, String origin, UUID playerId, RewardBundle bundle) {
        return fulfilInternal(receiptId, origin, playerId, bundle, null, null);
    }

    /** Applies a caller-owned conditional claim in the same transaction as the reward receipt. */
    public CompletableFuture<Boolean> fulfilGuarded(String receiptId, String origin, UUID playerId, RewardBundle bundle, Guard guard) {
        return fulfilInternal(receiptId, origin, playerId, bundle, null, guard);
    }

    /**
     * Commerce entry point. The provider receipt and every reward row are committed in
     * the same SQLite transaction so a retry can never grant only part of a package.
     */
    public CompletableFuture<Boolean> fulfilCommerce(String receiptId, String transactionId, String packageId,
                                                      UUID playerId, int quantity, RewardBundle bundle) {
        if (transactionId == null || packageId == null || quantity < 1)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid commerce receipt"));
        return fulfilInternal(receiptId, "TEBEX:" + packageId, playerId, bundle,
                new CommerceReceipt(transactionId, packageId, quantity), null);
    }

    /**
     * Reverses a non-provider reward receipt exactly once. Provider-specific
     * refunds add debt handling in CommerceFulfilmentService; this method is the
     * shared audited boundary for seasonal, Discord, quest, and admin rewards.
     */
    public CompletableFuture<Boolean> reverse(String receiptId, String reason) {
        if (receiptId == null || !receiptId.matches("[A-Za-z0-9._:-]{1,192}"))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid reward receipt"));
        String safeReason = reason == null || reason.isBlank() ? "reversal" : reason.substring(0, Math.min(160, reason.length()));
        return plugin.getDatabase().transaction(connection -> reverseOnce(connection, receiptId, safeReason))
                .thenCompose(reversed -> reversed ? findPlayer(receiptId).thenCompose(player -> player == null
                        ? CompletableFuture.completedFuture(true)
                        : plugin.getEntitlementService().load(player).thenApply(ignored -> true))
                        : CompletableFuture.completedFuture(false));
    }

    private CompletableFuture<Boolean> fulfilInternal(String receiptId, String origin, UUID playerId,
                                                       RewardBundle bundle, CommerceReceipt commerce, Guard guard) {
        if (receiptId == null || !receiptId.matches("[A-Za-z0-9._:-]{1,192}"))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid reward receipt"));
        if (origin == null || origin.isBlank() || playerId == null || bundle == null)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Incomplete reward request"));
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Runnable begin = () -> begin(receiptId, origin, playerId, bundle, commerce, guard, result);
        if (Bukkit.isPrimaryThread()) begin.run();
        else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, begin);
        else result.completeExceptionally(new IllegalStateException("Plugin is stopping"));
        return result;
    }

    private void begin(String receiptId, String origin, UUID playerId, RewardBundle bundle, CommerceReceipt commerce, Guard guard, CompletableFuture<Boolean> result) {
        PlayerData data = plugin.getPlayerManager() == null ? null : plugin.getPlayerManager().get(playerId);
        if (data != null && !data.tryLockEconomy()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> begin(receiptId, origin, playerId, bundle, commerce, guard, result), 1L);
            return;
        }
        AtomicBoolean applied = new AtomicBoolean();
        plugin.getDatabase().transaction(connection -> {
            applied.set(applyOnce(connection, receiptId, origin, playerId, bundle, System.currentTimeMillis(), commerce, guard));
            return null;
        }).whenComplete((ignored, error) -> {
            Runnable finish = () -> {
                if (data != null) data.unlockEconomy();
                if (error != null) { result.completeExceptionally(error); return; }
                if (applied.get()) {
                    if (data != null && plugin.getPlayerManager().get(playerId) == data) {
                        data.addCoinsRaw(bundle.coins());
                        data.addGemsRaw(bundle.gems());
                        data.addCredits(bundle.credits());
                    }
                    for (RewardBundle.Entitlement entitlement : bundle.entitlements())
                        plugin.getEntitlementService().cache(playerId, entitlement.id());
                }
                if (applied.get() && plugin.getTradeManager() != null) plugin.getTradeManager().deliverPending(playerId);
                result.complete(applied.get());
            };
            if (Bukkit.isPrimaryThread()) finish.run(); else Bukkit.getScheduler().runTask(plugin, finish);
        });
    }

    static boolean applyOnce(Connection connection, String receiptId, String origin, UUID playerId,
                             RewardBundle bundle, long now) throws Exception {
        return applyOnce(connection, receiptId, origin, playerId, bundle, now, null, null);
    }

    static boolean applyOnce(Connection connection, String receiptId, String origin, UUID playerId,
                             RewardBundle bundle, long now, CommerceReceipt commerce) throws Exception {
        return applyOnce(connection, receiptId, origin, playerId, bundle, now, commerce, null);
    }

    static boolean applyOnce(Connection connection, String receiptId, String origin, UUID playerId,
                             RewardBundle bundle, long now, CommerceReceipt commerce, Guard guard) throws Exception {
        if (guard != null && !guard.apply(connection)) return false;
        if (commerce != null && !insertCommerceReceipt(connection, receiptId, commerce, playerId, now)) return false;
        try (PreparedStatement receipt = connection.prepareStatement(
                "INSERT OR IGNORE INTO reward_receipts(receipt_id,origin,player_uuid,bundle_version,status,created_at) VALUES(?,?,?,?,?,?)")) {
            receipt.setString(1, receiptId); receipt.setString(2, origin); receipt.setString(3, playerId.toString());
            receipt.setString(4, bundle.version()); receipt.setString(5, "APPLIED"); receipt.setLong(6, now);
            if (receipt.executeUpdate() != 1) return false;
        }
        try (PreparedStatement player = connection.prepareStatement(
                "UPDATE players SET coins=CASE WHEN coins>? THEN 9223372036854775807 ELSE coins+? END,gems=CASE WHEN gems>? THEN 9223372036854775807 ELSE gems+? END,credits=CASE WHEN credits>? THEN 9223372036854775807 ELSE credits+? END WHERE uuid=?")) {
            player.setLong(1, Long.MAX_VALUE - bundle.coins()); player.setLong(2, bundle.coins());
            player.setLong(3, Long.MAX_VALUE - bundle.gems()); player.setLong(4, bundle.gems());
            player.setLong(5, Long.MAX_VALUE - bundle.credits()); player.setLong(6, bundle.credits()); player.setString(7, playerId.toString());
            if (player.executeUpdate() != 1) throw new IllegalStateException("Player row missing");
        }
        if (bundle.coins() != 0 || bundle.gems() != 0 || bundle.credits() != 0) {
            try (PreparedStatement audit = connection.prepareStatement(
                    "INSERT INTO economy_transactions(id,player_uuid,kind,coins_delta,gems_delta,credits_delta,created_at) VALUES(?,?,?,?,?,?,?)")) {
                audit.setString(1, receiptId); audit.setString(2, playerId.toString()); audit.setString(3, "REWARD:" + origin);
                audit.setLong(4, bundle.coins()); audit.setLong(5, bundle.gems()); audit.setLong(6, bundle.credits()); audit.setLong(7, now); audit.executeUpdate();
            }
        }
        int itemIndex = 0;
        if (commerce != null) {
            itemIndex = insertCommerceItems(connection, receiptId, bundle, itemIndex);
        }
        for (RewardBundle.Entitlement entitlement : bundle.entitlements()) {
            try (PreparedStatement source = connection.prepareStatement(
                    "INSERT OR IGNORE INTO entitlement_sources(receipt_id,player_uuid,entitlement_id,kind,value,created_at) VALUES(?,?,?,?,?,?)")) {
                source.setString(1, receiptId); source.setString(2, playerId.toString()); source.setString(3, entitlement.id()); source.setString(4, entitlement.kind()); source.setString(5, entitlement.value()); source.setLong(6, now);
                if (source.executeUpdate() == 1) {
                    try (PreparedStatement owned = connection.prepareStatement(
                            "INSERT INTO player_entitlements(player_uuid,entitlement_id,kind,value,active,created_at) VALUES(?,?,?,?,1,?) ON CONFLICT(player_uuid,entitlement_id) DO UPDATE SET active=1,value=excluded.value")) {
                        owned.setString(1, playerId.toString()); owned.setString(2, entitlement.id()); owned.setString(3, entitlement.kind()); owned.setString(4, entitlement.value()); owned.setLong(5, now); owned.executeUpdate();
                    }
                }
            }
        }
        for (RewardBundle.BoostReward boost : bundle.boosts()) {
            String deliveryId = receiptId + ":boost:" + boost.id();
            try (PreparedStatement delivery = connection.prepareStatement(
                    "INSERT OR IGNORE INTO item_deliveries(id,player_uuid,item_type,item_data,created_at) VALUES(?,?,?,?,?)")) {
                delivery.setString(1, deliveryId); delivery.setString(2, playerId.toString()); delivery.setString(3, "GROWAPET_BOOST");
                delivery.setString(4, boost.id() + "|" + boost.type().name() + "|" + boost.bonus() + "|" + boost.durationMinutes()); delivery.setLong(5, now); delivery.executeUpdate();
            }
            if (commerce != null) insertCommerceItem(connection, receiptId, itemIndex++, "BOOST", 0,
                    boost.id() + "|" + boost.type().name() + "|" + boost.bonus() + "|" + boost.durationMinutes());
        }
        return true;
    }

    private static boolean insertCommerceReceipt(Connection connection, String receiptId, CommerceReceipt commerce,
                                                  UUID playerId, long now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO commerce_receipts(receipt_id,provider,transaction_id,package_id,player_uuid,quantity,status,verified_at) VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(receipt_id) DO UPDATE SET status='FULFILLED',verified_at=excluded.verified_at WHERE commerce_receipts.status='VERIFIED_PENDING'")) {
            statement.setString(1, receiptId); statement.setString(2, "TEBEX");
            statement.setString(3, commerce.transactionId()); statement.setString(4, commerce.packageId());
            statement.setString(5, playerId.toString()); statement.setInt(6, commerce.quantity());
            statement.setString(7, "FULFILLED"); statement.setLong(8, now);
            return statement.executeUpdate() == 1;
        }
    }

    private static int insertCommerceItems(Connection connection, String receiptId, RewardBundle bundle, int index) throws Exception {
        if (bundle.coins() != 0) insertCommerceItem(connection, receiptId, index++, "COINS", bundle.coins(), "");
        if (bundle.gems() != 0) insertCommerceItem(connection, receiptId, index++, "GEMS", bundle.gems(), "");
        if (bundle.credits() != 0) insertCommerceItem(connection, receiptId, index++, "CREDITS", bundle.credits(), "");
        for (RewardBundle.Entitlement entitlement : bundle.entitlements())
            insertCommerceItem(connection, receiptId, index++, "ENTITLEMENT", 0, entitlement.id() + "|" + entitlement.kind() + "|" + entitlement.value());
        return index;
    }

    private static void insertCommerceItem(Connection connection, String receiptId, int index, String kind,
                                           long amount, String value) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO commerce_receipt_items(receipt_id,item_index,kind,amount,value) VALUES(?,?,?,?,?)")) {
            statement.setString(1, receiptId); statement.setInt(2, index); statement.setString(3, kind);
            statement.setLong(4, amount); statement.setString(5, value); statement.executeUpdate();
        }
    }

    private static Boolean reverseOnce(Connection connection, String receiptId, String reason) throws Exception {
        String playerId;
        try (PreparedStatement lookup = connection.prepareStatement("SELECT player_uuid,status FROM reward_receipts WHERE receipt_id=?")) {
            lookup.setString(1, receiptId);
            try (ResultSet row = lookup.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("Reward receipt does not exist");
                playerId = row.getString(1);
                if ("REVERSED".equalsIgnoreCase(row.getString(2))) return false;
            }
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT OR IGNORE INTO reward_reversals(receipt_id,reason,created_at) VALUES(?,?,?)")) {
            insert.setString(1, receiptId); insert.setString(2, reason); insert.setLong(3, System.currentTimeMillis());
            if (insert.executeUpdate() != 1) return false;
        }
        long[] amounts = new long[3];
        try (PreparedStatement audit = connection.prepareStatement("SELECT coins_delta,gems_delta,credits_delta FROM economy_transactions WHERE id=?")) {
            audit.setString(1, receiptId);
            try (ResultSet row = audit.executeQuery()) {
                if (row.next()) { amounts[0] = Math.max(0, row.getLong(1)); amounts[1] = Math.max(0, row.getLong(2)); amounts[2] = Math.max(0, row.getLong(3)); }
            }
        }
        removeBalance(connection, playerId, "coins", amounts[0]);
        removeBalance(connection, playerId, "gems", amounts[1]);
        removeBalance(connection, playerId, "credits", amounts[2]);
        try (PreparedStatement source = connection.prepareStatement("SELECT entitlement_id FROM entitlement_sources WHERE receipt_id=? AND revoked_at IS NULL")) {
            source.setString(1, receiptId);
            try (ResultSet rows = source.executeQuery()) {
                while (rows.next()) {
                    String id = rows.getString(1);
                    try (PreparedStatement revoke = connection.prepareStatement("UPDATE entitlement_sources SET revoked_at=? WHERE receipt_id=? AND entitlement_id=? AND revoked_at IS NULL")) {
                        revoke.setLong(1, System.currentTimeMillis()); revoke.setString(2, receiptId); revoke.setString(3, id); revoke.executeUpdate();
                    }
                    try (PreparedStatement owned = connection.prepareStatement("UPDATE player_entitlements SET active=CASE WHEN EXISTS(SELECT 1 FROM entitlement_sources WHERE player_uuid=? AND entitlement_id=? AND revoked_at IS NULL) THEN 1 ELSE 0 END WHERE player_uuid=? AND entitlement_id=?")) {
                        owned.setString(1, playerId); owned.setString(2, id); owned.setString(3, playerId); owned.setString(4, id); owned.executeUpdate();
                    }
                }
            }
        }
        try (PreparedStatement delivery = connection.prepareStatement("DELETE FROM item_deliveries WHERE player_uuid=? AND id LIKE ?")) {
            delivery.setString(1, playerId); delivery.setString(2, receiptId + ":boost:%"); delivery.executeUpdate();
        }
        try (PreparedStatement state = connection.prepareStatement("UPDATE reward_receipts SET status='REVERSED',reversed_at=? WHERE receipt_id=?")) {
            state.setLong(1, System.currentTimeMillis()); state.setString(2, receiptId); state.executeUpdate();
        }
        if (amounts[0] != 0 || amounts[1] != 0 || amounts[2] != 0) {
            try (PreparedStatement reversalAudit = connection.prepareStatement("INSERT OR IGNORE INTO economy_transactions(id,player_uuid,kind,coins_delta,gems_delta,credits_delta,created_at) VALUES(?,?,?,?,?,?,?)")) {
                reversalAudit.setString(1, "refund:" + receiptId); reversalAudit.setString(2, playerId); reversalAudit.setString(3, "REWARD_REVERSAL");
                reversalAudit.setLong(4, -amounts[0]); reversalAudit.setLong(5, -amounts[1]); reversalAudit.setLong(6, -amounts[2]); reversalAudit.setLong(7, System.currentTimeMillis()); reversalAudit.executeUpdate();
            }
        }
        return true;
    }

    private static void removeBalance(Connection connection, String playerId, String column, long amount) throws Exception {
        if (amount <= 0) return;
        try (PreparedStatement update = connection.prepareStatement("UPDATE players SET " + column + "=MAX(0," + column + "-?) WHERE uuid=?")) {
            update.setLong(1, amount); update.setString(2, playerId); update.executeUpdate();
        }
    }

    private CompletableFuture<UUID> findPlayer(String receiptId) {
        return plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT player_uuid FROM reward_receipts WHERE receipt_id=?")) {
                statement.setString(1, receiptId);
                try (ResultSet row = statement.executeQuery()) { return row.next() ? UUID.fromString(row.getString(1)) : null; }
            }
        });
    }

    public record CommerceReceipt(String transactionId, String packageId, int quantity) { }

    @FunctionalInterface
    public interface Guard { boolean apply(Connection connection) throws Exception; }
}
