package me.growapet.shop;

import me.growapet.GrowAPet;
import org.bukkit.configuration.ConfigurationSection;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Native, reversible 10-zone shop migration. It never renames legacy IDs in place. */
public final class ShopMigrationService {
    private static final List<String> NEW_ORDER = List.of("plains", "spruce_forest", "savanna", "desert", "ice_spikes", "cherry_grove", "flower_forest", "cave", "ocean", "mushroom_island");
    private static final Map<String, String> ZONE_ALIASES = Map.ofEntries(
            Map.entry("forest", "plains"), Map.entry("plains", "spruce_forest"), Map.entry("mushroom", "savanna"), Map.entry("desert", "desert"),
            Map.entry("badlands", "ice_spikes"), Map.entry("snow", "cherry_grove"), Map.entry("deep_caves", "flower_forest"), Map.entry("nether", "cave"),
            Map.entry("end", "ocean"), Map.entry("ancient_realm", "mushroom_island"));
    private final GrowAPet plugin;

    public ShopMigrationService(GrowAPet plugin) { this.plugin = plugin; }

    public List<String> zones() { return NEW_ORDER; }
    public Map<String, String> aliases() { return ZONE_ALIASES; }

    public String catalogChecksum() {
        ConfigurationSection catalog = plugin.getConfigManager().shopCatalog().getConfigurationSection("catalogs.zones-v1");
        StringBuilder canonical = new StringBuilder("zones-v1|");
        if (catalog != null) {
            canonical.append(catalog.getString("display-name", "")).append('|').append(String.join(",", catalog.getStringList("order")));
        }
        ConfigurationSection aliases = plugin.getConfigManager().shopCatalog().getConfigurationSection("aliases");
        if (aliases != null) aliases.getKeys(false).stream().sorted().forEach(key -> canonical.append('|').append(key).append('=').append(aliases.getString(key, "")));
        String raw = canonical.toString();
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("Could not checksum shop catalog", error); }
    }

    public CompletableFuture<List<Analysis>> analyze() {
        return plugin.getDatabase().async(connection -> {
            List<Analysis> result = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT uuid,name,coins,gems,shop_levels,unlocked_zones FROM players ORDER BY uuid"); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) result.add(analyzeRow(rows.getString(1), rows.getString(2), rows.getLong(3), rows.getLong(4), rows.getString(5), rows.getString(6)));
            }
            return result;
        });
    }

    public CompletableFuture<String> dryRun() {
        String batchId = UUID.randomUUID().toString();
        return analyze().thenCompose(entries -> plugin.getDatabase().transaction(connection -> {
            insertBatch(connection, batchId, "legacy", "zones-v1", catalogChecksum(), "DRY_RUN");
            for (Analysis entry : entries) insertEntry(connection, batchId, entry, "DRY_RUN");
            return batchId;
        }));
    }

    public CompletableFuture<Boolean> apply(String batchId, String confirmationChecksum) {
        String expected = catalogChecksum();
        if (!expected.equalsIgnoreCase(confirmationChecksum)) return CompletableFuture.failedFuture(new IllegalArgumentException("Catalog checksum does not match"));
        if (!plugin.getConfigManager().config().getBoolean("shop-migration.maintenance-mode", false))
            return CompletableFuture.failedFuture(new IllegalStateException("Maintenance mode is required for shop migration"));
        if (!"zones-v1".equalsIgnoreCase(plugin.getConfigManager().shopCatalog().getString("active-catalog", "legacy")))
            return CompletableFuture.failedFuture(new IllegalStateException("zones-v1 must be the active catalog before apply"));
        if (!plugin.getConfigManager().shopCatalog().getBoolean("catalogs.zones-v1.enabled", false))
            return CompletableFuture.failedFuture(new IllegalStateException("zones-v1 catalog is not enabled"));
        return plugin.getDatabase().createVerifiedBackup("shop-migration-" + batchId).thenCompose(ignored -> analyze().thenCompose(entries -> plugin.getDatabase().transaction(connection -> {
            if (!batchInState(connection, batchId, "DRY_RUN")) throw new IllegalStateException("Migration batch is missing or already applied");
            for (Analysis entry : entries) {
                insertEntry(connection, batchId, entry, "APPLIED");
                for (Purchase purchase : entry.purchases()) {
                    try (PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO shop_purchases(catalog_id,player_uuid,zone_id,chain_id,tier_id,source,purchased_at) VALUES('zones-v1',?,?,?,?,?,?)")) {
                        statement.setString(1, entry.playerId().toString()); statement.setString(2, purchase.zoneId()); statement.setString(3, purchase.chain()); statement.setString(4, purchase.tierId()); statement.setString(5, "MIGRATION:" + batchId); statement.setLong(6, System.currentTimeMillis()); statement.executeUpdate();
                    }
                }
                try (PreparedStatement balance = connection.prepareStatement("UPDATE players SET coins=?,gems=? WHERE uuid=?")) {
                    balance.setLong(1, entry.coinRemainder()); balance.setLong(2, entry.gemRemainder()); balance.setString(3, entry.playerId().toString()); balance.executeUpdate();
                }
                for (String zone : entry.unlockedNewZones()) try (PreparedStatement zoneRow = connection.prepareStatement("INSERT OR IGNORE INTO player_zones(player_uuid,zone_id,unlocked_at) VALUES(?,?,?)")) { zoneRow.setString(1, entry.playerId().toString()); zoneRow.setString(2, zone); zoneRow.setLong(3, System.currentTimeMillis()); zoneRow.executeUpdate(); }
            }
            try (PreparedStatement state = connection.prepareStatement("UPDATE shop_migration_batches SET state='APPLIED',applied_at=? WHERE batch_id=? AND state IN ('DRY_RUN','APPLIED')")) { state.setLong(1, System.currentTimeMillis()); state.setString(2, batchId); state.executeUpdate(); }
            return true;
        })));
    }

    public CompletableFuture<Verification> verify(String batchId) {
        return plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT state,COUNT(*) FROM shop_migration_batches b LEFT JOIN shop_migration_entries e ON e.batch_id=b.batch_id WHERE b.batch_id=? GROUP BY b.state")) {
                statement.setString(1, batchId); try (ResultSet row = statement.executeQuery()) { return row.next() ? new Verification(batchId, row.getString(1), row.getInt(2), true) : new Verification(batchId, "MISSING", 0, false); }
            }
        });
    }

    public CompletableFuture<Boolean> rollback(String batchId) {
        if (!plugin.getConfigManager().config().getBoolean("shop-migration.maintenance-mode", false)) return CompletableFuture.failedFuture(new IllegalStateException("Maintenance mode is required for shop migration"));
        return plugin.getDatabase().createVerifiedBackup("shop-migration-rollback-" + batchId).thenCompose(ignored -> plugin.getDatabase().transaction(connection -> {
            if (!batchInState(connection, batchId, "APPLIED")) return false;
            try (PreparedStatement entries = connection.prepareStatement("SELECT player_uuid,legacy_data FROM shop_migration_entries WHERE batch_id=? AND state='APPLIED'")) {
                entries.setString(1, batchId); try (ResultSet rows = entries.executeQuery()) { while (rows.next()) {
                    UUID player = UUID.fromString(rows.getString(1));
                    String[] original = rows.getString(2).split(":", 2);
                    long originalCoins = Long.parseLong(original[0]), originalGems = Long.parseLong(original[1]);
                    try (PreparedStatement balance = connection.prepareStatement("UPDATE players SET coins=?,gems=? WHERE uuid=?")) { balance.setLong(1, originalCoins); balance.setLong(2, originalGems); balance.setString(3, player.toString()); balance.executeUpdate(); }
                }}
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM shop_purchases WHERE source=?")) { delete.setString(1, "MIGRATION:" + batchId); delete.executeUpdate(); }
            try (PreparedStatement state = connection.prepareStatement("UPDATE shop_migration_batches SET state='ROLLED_BACK',rolled_back_at=? WHERE batch_id=? AND state='APPLIED'")) { state.setLong(1, System.currentTimeMillis()); state.setString(2, batchId); state.executeUpdate(); }
            return true;
        }));
    }

    private Analysis analyzeRow(String uuid, String name, long coins, long gems, String shopLevels, String unlockedZones) {
        UUID player = UUID.fromString(uuid); Map<String, Integer> levels = parseLevels(shopLevels);
        long virtualCoins = coins, virtualGems = gems;
        List<Purchase> purchases = new ArrayList<>();
        int gear = Math.max(0, levels.getOrDefault("gear_tier", 0)); int tool = Math.max(0, levels.getOrDefault("tool_tier", 0));
        for (int tier = 1; tier <= gear; tier++) { GearTier value = GearTier.byTier(tier); if (value != null) { virtualCoins = safeAdd(virtualCoins, value.getCoinCost()); virtualGems = safeAdd(virtualGems, value.getGemCost()); } }
        for (int tier = 1; tier <= tool; tier++) { ToolTier value = ToolTier.byTier(tier); if (value != null) { virtualCoins = safeAdd(virtualCoins, value.getCoinCost()); virtualGems = safeAdd(virtualGems, value.getGemCost()); } }
        int highest = highestLegacyZone(unlockedZones);
        List<String> unlocked = NEW_ORDER.subList(0, Math.min(NEW_ORDER.size(), Math.max(0, highest + 1)));
        for (String zone : unlocked) {
            for (int tier = 1; tier <= GearTier.values().length; tier++) {
                GearTier cost = GearTier.byTier(tier); if (cost == null || virtualCoins < cost.getCoinCost() || virtualGems < cost.getGemCost()) break;
                virtualCoins -= cost.getCoinCost(); virtualGems -= cost.getGemCost(); purchases.add(new Purchase(zone, "gear", "tier-" + tier));
            }
            for (int tier = 1; tier <= ToolTier.values().length; tier++) {
                ToolTier cost = ToolTier.byTier(tier); if (cost == null || virtualCoins < cost.getCoinCost() || virtualGems < cost.getGemCost()) break;
                virtualCoins -= cost.getCoinCost(); virtualGems -= cost.getGemCost(); purchases.add(new Purchase(zone, "tool", "tier-" + tier));
            }
        }
        return new Analysis(player, name, coins, gems, Math.max(0, virtualCoins), Math.max(0, virtualGems), purchases, unlocked);
    }

    private int highestLegacyZone(String raw) { int highest = -1; if (raw == null) return highest; for (String token : raw.split(",")) { String id = token.trim().toLowerCase(); String mapped = ZONE_ALIASES.get(id); if (mapped != null) highest = Math.max(highest, NEW_ORDER.indexOf(mapped)); } return highest; }
    private static long safeAdd(long left, long right) { return left > Long.MAX_VALUE - Math.max(0, right) ? Long.MAX_VALUE : left + Math.max(0, right); }
    private static Map<String, Integer> parseLevels(String raw) { Map<String, Integer> result = new LinkedHashMap<>(); if (raw == null) return result; for (String token : raw.split(",")) { String[] parts = token.split(":", 2); if (parts.length != 2) continue; try { result.put(parts[0], Math.max(0, Integer.parseInt(parts[1]))); } catch (NumberFormatException ignored) { } } return result; }
    private static void insertBatch(Connection connection, String batchId, String from, String to, String checksum, String state) throws Exception { try (PreparedStatement statement = connection.prepareStatement("INSERT OR IGNORE INTO shop_migration_batches(batch_id,from_catalog,to_catalog,checksum,state,created_at) VALUES(?,?,?,?,?,?)")) { statement.setString(1, batchId); statement.setString(2, from); statement.setString(3, to); statement.setString(4, checksum); statement.setString(5, state); statement.setLong(6, System.currentTimeMillis()); statement.executeUpdate(); } }
    private static void insertEntry(Connection connection, String batchId, Analysis entry, String state) throws Exception { String generated = entry.purchases().stream().map(value -> value.zoneId() + ":" + value.chain() + ":" + value.tierId()).reduce((a,b) -> a + "," + b).orElse(""); try (PreparedStatement statement = connection.prepareStatement("INSERT OR REPLACE INTO shop_migration_entries(batch_id,player_uuid,legacy_data,generated_data,coin_remainder,gem_remainder,state) VALUES(?,?,?,?,?,?,?)")) { statement.setString(1,batchId);statement.setString(2,entry.playerId().toString());statement.setString(3,entry.originalCoins()+":"+entry.originalGems());statement.setString(4,generated);statement.setLong(5,entry.coinRemainder());statement.setLong(6,entry.gemRemainder());statement.setString(7,state);statement.executeUpdate(); } }
    private static boolean batchInState(Connection connection, String batchId, String state) throws Exception { try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM shop_migration_batches WHERE batch_id=? AND state=?")) { statement.setString(1,batchId); statement.setString(2,state); try(ResultSet row=statement.executeQuery()){return row.next();} } }

    public record Purchase(String zoneId, String chain, String tierId) { }
    public record Analysis(UUID playerId, String name, long originalCoins, long originalGems, long coinRemainder, long gemRemainder, List<Purchase> purchases, List<String> unlockedNewZones) { public Analysis { purchases=List.copyOf(purchases);unlockedNewZones=List.copyOf(unlockedNewZones); } }
    public record Verification(String batchId, String state, int entries, boolean exists) { }
}
