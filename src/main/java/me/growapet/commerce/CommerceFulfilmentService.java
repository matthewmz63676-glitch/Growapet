package me.growapet.commerce;

import me.growapet.GrowAPet;
import me.growapet.boosts.BoostType;
import me.growapet.rewards.RewardBundle;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Verified Tebex fulfilment and auditable reversal boundary. Console commands only
 * provide a transaction trigger; the provider API and immutable local SKU decide
 * what may be delivered.
 */
public final class CommerceFulfilmentService {
    private final GrowAPet plugin;
    private final CommerceVerifier verifier;

    public CommerceFulfilmentService(GrowAPet plugin) {
        this.plugin = plugin;
        this.verifier = new CommerceVerifier(plugin);
    }

    public CompletableFuture<Boolean> fulfil(String transaction, UUID playerId, String packageId, int quantity) {
        if (!plugin.getConfigManager().commerce().getBoolean("enabled", false))
            return CompletableFuture.failedFuture(new IllegalStateException("Commerce is disabled"));
        if (!plugin.getConfigManager().commerce().getBoolean("paid-fulfilment-enabled", false))
            return CompletableFuture.failedFuture(new IllegalStateException("Paid fulfilment is disabled until the economy gate is approved"));
        int maxQuantity = Math.max(1, plugin.getConfigManager().commerce().getInt("max-quantity", 20));
        if (quantity < 1 || quantity > maxQuantity)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Quantity exceeds configured cap"));
        RewardBundle unit = bundle(packageId);
        if (unit == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown commerce package"));
        RewardBundle total;
        try {
            total = multiply(unit, quantity);
            validateCaps(total);
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
        return providerAvailable().thenCompose(provider -> {
            if (!provider) return CompletableFuture.failedFuture(new IllegalStateException("The official Tebex plugin is not installed"));
            return verifier.verify(transaction, playerId, packageId, quantity).thenCompose(payment -> hasOpenDebt(playerId).thenCompose(locked -> {
            String receipt = "tebex:" + transaction + ":" + packageId;
            if (locked) return recordPending(receipt, transaction, packageId, playerId, quantity, total);
            return plugin.getRewardFulfilmentService().fulfilCommerce(receipt, transaction, packageId, playerId, quantity, total);
            }));
        });
    }

    /** Re-verifies the provider status, then atomically revokes available value and records debt. */
    public CompletableFuture<Boolean> reverse(String transaction, String packageId, UUID playerId, String reason) {
        if (!plugin.getConfigManager().commerce().getBoolean("enabled", false))
            return CompletableFuture.failedFuture(new IllegalStateException("Commerce is disabled"));
        return verifier.verifyStatus(transaction).thenCompose(status -> {
            if (!status.valid()) return CompletableFuture.failedFuture(new IllegalStateException(status.failure()));
            String receiptId = "tebex:" + transaction + ":" + packageId;
            return plugin.getDatabase().transaction(connection -> reverseOnce(connection, receiptId, packageId, playerId, reason, status.status()))
                    .thenApply(reversed -> {
                        if (Boolean.TRUE.equals(reversed)) {
                            plugin.getEntitlementService().load(playerId);
                            refreshCachedBalances(playerId);
                        }
                        return reversed;
                    });
        });
    }

    public CompletableFuture<Boolean> hasOpenDebt(UUID playerId) {
        return plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM commerce_debts WHERE player_uuid=? AND status='OPEN' LIMIT 1")) {
                statement.setString(1, playerId.toString());
                try (ResultSet rows = statement.executeQuery()) { return rows.next(); }
            }
        });
    }

    public boolean providerInstalled() {
        String configured = plugin.getConfigManager().commerce().getString("provider-plugin-name", "Tebex");
        return configured.isBlank() || Bukkit.getPluginManager().getPlugin(configured) != null;
    }

    private CompletableFuture<Boolean> providerAvailable() {
        if (Bukkit.isPrimaryThread()) return CompletableFuture.completedFuture(providerInstalled());
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        try { Bukkit.getScheduler().runTask(plugin, () -> result.complete(providerInstalled())); }
        catch (RuntimeException error) { result.completeExceptionally(error); }
        return result;
    }

    /** Re-verifies debt-blocked receipts one at a time and fulfils only verified rows. */
    public CompletableFuture<Integer> reconcilePending() {
        return plugin.getDatabase().async(connection -> {
            List<PendingReceipt> pending = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT transaction_id,package_id,player_uuid,quantity FROM commerce_receipts WHERE status='VERIFIED_PENDING' ORDER BY verified_at")) {
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        try { pending.add(new PendingReceipt(rows.getString(1), rows.getString(2), UUID.fromString(rows.getString(3)), rows.getInt(4))); }
                        catch (IllegalArgumentException ignored) { plugin.getLogger().warning("Skipping malformed pending commerce receipt"); }
                    }
                }
            }
            return pending;
        }).thenCompose(pending -> {
            CompletableFuture<Integer> count = CompletableFuture.completedFuture(0);
            for (PendingReceipt receipt : pending) count = count.thenCompose(value -> fulfil(receipt.transaction(), receipt.playerId(), receipt.packageId(), receipt.quantity())
                    .handle((applied, error) -> value + (error == null && Boolean.TRUE.equals(applied) ? 1 : 0)));
            return count;
        });
    }

    public CompletableFuture<Integer> pendingCount() {
        return plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM commerce_receipts WHERE status='VERIFIED_PENDING'")) {
                try (ResultSet row = statement.executeQuery()) { return row.next() ? row.getInt(1) : 0; }
            }
        });
    }

    public CompletableFuture<CommerceVerifier.ProviderStatus> status(String transaction) {
        return verifier.verifyStatus(transaction);
    }

    /** Audited administrative debt resolution; it never grants value by itself. */
    public CompletableFuture<Boolean> resolveDebt(UUID playerId, String assetKind, String actor, String reason) {
        if (playerId == null || assetKind == null || !assetKind.matches("COINS|GEMS|CREDITS|BOOST_ITEM"))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid debt asset"));
        return plugin.getDatabase().transaction(connection -> {
            int updated;
            try (PreparedStatement statement = connection.prepareStatement("UPDATE commerce_debts SET status='RESOLVED',resolved_at=? WHERE player_uuid=? AND asset_kind=? AND status='OPEN'")) {
                statement.setLong(1, System.currentTimeMillis()); statement.setString(2, playerId.toString()); statement.setString(3, assetKind); updated = statement.executeUpdate();
            }
            if (updated > 0) try (PreparedStatement audit = connection.prepareStatement("INSERT INTO admin_audit(actor,action,target,details,created_at) VALUES(?,?,?,?,?)")) {
                audit.setString(1, actor == null ? "CONSOLE" : actor); audit.setString(2, "commerce_resolve_debt"); audit.setString(3, playerId.toString()); audit.setString(4, assetKind + ":" + (reason == null ? "" : reason.substring(0, Math.min(160, reason.length())))); audit.setLong(5, System.currentTimeMillis()); audit.executeUpdate();
            }
            return updated > 0;
        });
    }

    private record PendingReceipt(String transaction, String packageId, UUID playerId, int quantity) { }

    private CompletableFuture<Boolean> recordPending(String receipt, String transaction, String packageId, UUID playerId,
                                                      int quantity, RewardBundle bundle) {
        return plugin.getDatabase().transaction(connection -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT OR IGNORE INTO commerce_receipts(receipt_id,provider,transaction_id,package_id,player_uuid,quantity,status,verified_at) VALUES(?,?,?,?,?,?,?,?)")) {
                insert.setString(1, receipt); insert.setString(2, "TEBEX"); insert.setString(3, transaction);
                insert.setString(4, packageId); insert.setString(5, playerId.toString()); insert.setInt(6, quantity);
                insert.setString(7, "VERIFIED_PENDING"); insert.setLong(8, System.currentTimeMillis());
                if (insert.executeUpdate() != 1) return false;
            }
            insertItems(connection, receipt, bundle);
            return true;
        });
    }

    private Boolean reverseOnce(java.sql.Connection connection, String receiptId, String packageId, UUID playerId,
                                String reason, String providerStatus) throws Exception {
        String actualPlayer;
        try (PreparedStatement receipt = connection.prepareStatement("SELECT player_uuid FROM commerce_receipts WHERE receipt_id=? AND package_id=?")) {
            receipt.setString(1, receiptId); receipt.setString(2, packageId);
            try (ResultSet row = receipt.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("No verified receipt exists for that package");
                actualPlayer = row.getString(1);
            }
        }
        if (!playerId.toString().equalsIgnoreCase(actualPlayer)) throw new IllegalStateException("Receipt player mismatch");
        try (PreparedStatement reversal = connection.prepareStatement(
                "INSERT OR IGNORE INTO commerce_reversals(receipt_id,reason,provider_status,created_at) VALUES(?,?,?,?)")) {
            reversal.setString(1, receiptId); reversal.setString(2, reason == null || reason.isBlank() ? "provider-reversal" : reason.substring(0, Math.min(160, reason.length())));
            reversal.setString(3, providerStatus); reversal.setLong(4, System.currentTimeMillis());
            if (reversal.executeUpdate() != 1) return false;
        }
        List<String> revoked = new ArrayList<>();
        try (PreparedStatement items = connection.prepareStatement("SELECT kind,amount,value FROM commerce_receipt_items WHERE receipt_id=? ORDER BY item_index")) {
            items.setString(1, receiptId);
            try (ResultSet rows = items.executeQuery()) {
                while (rows.next()) {
                    String kind = rows.getString(1); long amount = rows.getLong(2); String value = rows.getString(3);
                    switch (kind) {
                        case "COINS", "GEMS", "CREDITS" -> removeBalanceOrDebt(connection, playerId, kind, amount, receiptId);
                        case "ENTITLEMENT" -> {
                            String[] parts = value.split("\\|", 3);
                            if (parts.length > 0) {
                                revoked.add(parts[0]);
                                try (PreparedStatement revoke = connection.prepareStatement("UPDATE entitlement_sources SET revoked_at=? WHERE receipt_id=? AND entitlement_id=? AND revoked_at IS NULL")) {
                                    revoke.setLong(1, System.currentTimeMillis()); revoke.setString(2, receiptId); revoke.setString(3, parts[0]); revoke.executeUpdate();
                                }
                            }
                        }
                        case "BOOST" -> {
                            String boostId = value == null ? "" : value.split("\\|", 2)[0];
                            try (PreparedStatement delivery = connection.prepareStatement("DELETE FROM item_deliveries WHERE player_uuid=? AND id=?")) {
                                delivery.setString(1, playerId.toString()); delivery.setString(2, receiptId + ":boost:" + boostId);
                                if (delivery.executeUpdate() == 0) addDebt(connection, playerId, "BOOST_ITEM", 1, receiptId);
                            }
                        }
                        default -> { }
                    }
                }
            }
        }
        for (String entitlement : revoked) {
            try (PreparedStatement owned = connection.prepareStatement(
                    "UPDATE player_entitlements SET active=CASE WHEN EXISTS(SELECT 1 FROM entitlement_sources WHERE player_uuid=? AND entitlement_id=? AND revoked_at IS NULL) THEN 1 ELSE 0 END WHERE player_uuid=? AND entitlement_id=?")) {
                owned.setString(1, playerId.toString()); owned.setString(2, entitlement); owned.setString(3, playerId.toString()); owned.setString(4, entitlement); owned.executeUpdate();
            }
        }
        try (PreparedStatement state = connection.prepareStatement("UPDATE commerce_receipts SET status='REVERSED',reversed_at=? WHERE receipt_id=? AND status<>'REVERSED'")) {
            state.setLong(1, System.currentTimeMillis()); state.setString(2, receiptId); state.executeUpdate();
        }
        try (PreparedStatement reward = connection.prepareStatement("UPDATE reward_receipts SET status='REVERSED',reversed_at=? WHERE receipt_id=? AND status<>'REVERSED'")) {
            reward.setLong(1, System.currentTimeMillis()); reward.setString(2, receiptId); reward.executeUpdate();
        }
        return true;
    }

    private void removeBalanceOrDebt(java.sql.Connection connection, UUID playerId, String kind, long requested, String receiptId) throws Exception {
        if (requested <= 0) return;
        String column = switch (kind) { case "COINS" -> "coins"; case "GEMS" -> "gems"; default -> "credits"; };
        long available;
        try (PreparedStatement select = connection.prepareStatement("SELECT " + column + " FROM players WHERE uuid=?")) {
            select.setString(1, playerId.toString()); try (ResultSet row = select.executeQuery()) { if (!row.next()) throw new IllegalStateException("Missing player row"); available = Math.max(0, row.getLong(1)); }
        }
        long removed = Math.min(available, requested);
        try (PreparedStatement update = connection.prepareStatement("UPDATE players SET " + column + "=? WHERE uuid=?")) { update.setLong(1, available - removed); update.setString(2, playerId.toString()); update.executeUpdate(); }
        if (requested > removed) addDebt(connection, playerId, kind, requested - removed, receiptId);
        try (PreparedStatement audit = connection.prepareStatement("INSERT INTO economy_transactions(id,player_uuid,kind,coins_delta,gems_delta,credits_delta,created_at) VALUES(?,?,?,?,?,?,?)")) {
            audit.setString(1, "refund:" + receiptId + ":" + kind); audit.setString(2, playerId.toString()); audit.setString(3, "TEBEX_REVERSAL");
            audit.setLong(4, "COINS".equals(kind) ? -removed : 0); audit.setLong(5, "GEMS".equals(kind) ? -removed : 0); audit.setLong(6, "CREDITS".equals(kind) ? -removed : 0); audit.setLong(7, System.currentTimeMillis()); audit.executeUpdate();
        }
    }

    private static void addDebt(java.sql.Connection connection, UUID playerId, String kind, long amount, String receiptId) throws Exception {
        if (amount <= 0) return;
        try (PreparedStatement debt = connection.prepareStatement("INSERT INTO commerce_debts(player_uuid,asset_kind,amount,source_receipt,status,created_at) VALUES(?,?,?,?, 'OPEN',?) ON CONFLICT(player_uuid,asset_kind,source_receipt) DO UPDATE SET amount=commerce_debts.amount+excluded.amount,status='OPEN'")) {
            debt.setString(1, playerId.toString()); debt.setString(2, kind); debt.setLong(3, amount); debt.setString(4, receiptId); debt.setLong(5, System.currentTimeMillis()); debt.executeUpdate();
        }
    }

    private RewardBundle bundle(String packageId) {
        if (packageId == null || !packageId.matches("[A-Za-z0-9._:-]{1,128}")) return null;
        ConfigurationSection section = plugin.getConfigManager().commerce().getConfigurationSection("sku-bundles." + packageId);
        if (section == null) return null;
        List<RewardBundle.Entitlement> entitlements = new ArrayList<>();
        for (String id : section.getStringList("entitlements")) entitlements.add(new RewardBundle.Entitlement(id, "COSMETIC", id));
        List<RewardBundle.BoostReward> boosts = new ArrayList<>();
        ConfigurationSection boostRoot = section.getConfigurationSection("boosts");
        if (boostRoot != null) for (String id : boostRoot.getKeys(false)) {
            ConfigurationSection boost = boostRoot.getConfigurationSection(id); if (boost == null) continue;
            try { boosts.add(new RewardBundle.BoostReward(id, BoostType.valueOf(boost.getString("type", "MOB_EXP").toUpperCase()), boost.getDouble("bonus", 0.25), boost.getLong("duration-minutes", 30))); }
            catch (IllegalArgumentException ignored) { plugin.getLogger().warning("Invalid commerce boost " + packageId + "." + id); }
        }
        try {
            return new RewardBundle(section.getString("version", "1"), Math.max(0, section.getLong("coins", 0)), Math.max(0, section.getLong("gems", 0)), Math.max(0, section.getLong("credits", 0)), entitlements, boosts);
        } catch (IllegalArgumentException invalid) {
            plugin.getLogger().warning("Invalid commerce SKU " + packageId + ": " + invalid.getMessage());
            return null;
        }
    }

    private void validateCaps(RewardBundle bundle) {
        long maxCoins = Math.max(0, plugin.getConfigManager().commerce().getLong("max-coins-per-receipt", 1_000_000_000L));
        long maxGems = Math.max(0, plugin.getConfigManager().commerce().getLong("max-gems-per-receipt", 1_000_000L));
        long maxCredits = Math.max(0, plugin.getConfigManager().commerce().getLong("max-credits-per-receipt", 1_000_000L));
        if (bundle.coins() > maxCoins || bundle.gems() > maxGems || bundle.credits() > maxCredits)
            throw new IllegalArgumentException("Package rewards exceed configured caps");
    }

    private static RewardBundle multiply(RewardBundle unit, int quantity) {
        List<RewardBundle.Entitlement> entitlements = new ArrayList<>(unit.entitlements());
        List<RewardBundle.BoostReward> boosts = new ArrayList<>();
        for (int i = 0; i < quantity; i++) for (RewardBundle.BoostReward boost : unit.boosts())
            boosts.add(new RewardBundle.BoostReward(boost.id() + "-" + (i + 1), boost.type(), boost.bonus(), boost.durationMinutes()));
        return new RewardBundle(unit.version(), Math.multiplyExact(unit.coins(), quantity), Math.multiplyExact(unit.gems(), quantity), Math.multiplyExact(unit.credits(), quantity), entitlements, boosts);
    }

    private static void insertItems(java.sql.Connection connection, String receipt, RewardBundle bundle) throws Exception {
        int index = 0;
        if (bundle.coins() != 0) index = insertItem(connection, receipt, index, "COINS", bundle.coins(), "");
        if (bundle.gems() != 0) index = insertItem(connection, receipt, index, "GEMS", bundle.gems(), "");
        if (bundle.credits() != 0) index = insertItem(connection, receipt, index, "CREDITS", bundle.credits(), "");
        for (RewardBundle.Entitlement entitlement : bundle.entitlements()) index = insertItem(connection, receipt, index, "ENTITLEMENT", 0, entitlement.id() + "|" + entitlement.kind() + "|" + entitlement.value());
        for (RewardBundle.BoostReward boost : bundle.boosts()) index = insertItem(connection, receipt, index, "BOOST", 0, boost.id() + "|" + boost.type().name() + "|" + boost.bonus() + "|" + boost.durationMinutes());
    }

    private static int insertItem(java.sql.Connection connection, String receipt, int index, String kind, long amount, String value) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO commerce_receipt_items(receipt_id,item_index,kind,amount,value) VALUES(?,?,?,?,?)")) {
            statement.setString(1, receipt); statement.setInt(2, index); statement.setString(3, kind); statement.setLong(4, amount); statement.setString(5, value); statement.executeUpdate();
        }
        return index + 1;
    }

    private void refreshCachedBalances(UUID playerId) {
        plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT coins,gems,credits FROM players WHERE uuid=?")) {
                statement.setString(1, playerId.toString());
                try (ResultSet row = statement.executeQuery()) {
                    return row.next() ? new long[]{row.getLong(1), row.getLong(2), row.getLong(3)} : null;
                }
            }
        }).thenAccept(values -> {
            if (values == null || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                me.growapet.models.PlayerData data = plugin.getPlayerManager().get(playerId);
                if (data == null) return;
                data.setCoins(values[0]); data.setGems(values[1]); data.setCredits(values[2]);
            });
        });
    }
}
