package me.growapet.tags;

import me.growapet.GrowAPet;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Static and durable custom chat tags backed by GrowAPet entitlements/options. */
public final class TagService {
    private final GrowAPet plugin;
    private final Map<String, Definition> custom = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public TagService(GrowAPet plugin) { this.plugin = plugin; }

    public CompletableFuture<Void> load() {
        return plugin.getDatabase().async(connection -> {
            Map<String, Definition> loaded = new java.util.HashMap<>();
            try (PreparedStatement statement = connection.prepareStatement("SELECT tag_id,display_name,markup FROM custom_tags"); ResultSet rows = statement.executeQuery()) {
                while (rows.next()) loaded.put(rows.getString(1), new Definition(rows.getString(1), rows.getString(2), rows.getString(3), true));
            }
            return loaded;
        }).thenAccept(loaded -> { custom.clear(); custom.putAll(loaded); });
    }

    public List<Definition> all() {
        Map<String, Definition> result = new java.util.LinkedHashMap<>();
        ConfigurationSection root = plugin.getConfigManager().cosmetics().getConfigurationSection("tags");
        if (root != null) for (String id : root.getKeys(false)) {
            String value = root.getString(id + ".value", root.getString(id + ".text", ""));
            result.put(id.toLowerCase(Locale.ROOT), new Definition(id, root.getString(id + ".display-name", id), value, false));
        }
        custom.forEach((id, definition) -> result.put(id, definition));
        return result.values().stream().filter(definition -> safeId(definition.id())).sorted(java.util.Comparator.comparing(Definition::id, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public Definition find(String id) { return all().stream().filter(definition -> definition.id().equalsIgnoreCase(id)).findFirst().orElse(null); }

    public boolean owns(UUID player, String id) { return player != null && find(id) != null && plugin.getEntitlementService().has(player, entitlementId(id)); }
    public String selected(UUID player) { String id = plugin.getOptionsManager().get(player, "cosmetic.selected.tag", ""); return owns(player, id) ? id : ""; }

    public CompletableFuture<Boolean> select(UUID player, String id) {
        if (!owns(player, id)) return CompletableFuture.completedFuture(false);
        return plugin.getOptionsManager().set(player, "cosmetic.selected.tag", id).thenApply(ignored -> true);
    }

    public CompletableFuture<Boolean> clear(UUID player) { return plugin.getOptionsManager().set(player, "cosmetic.selected.tag", "").thenApply(ignored -> true); }

    public String render(String id, String fallback) { Definition definition = find(id); return definition == null || definition.markup().isBlank() ? fallback : definition.markup(); }

    public CompletableFuture<Boolean> create(String id, String displayName, String markup, String actor) {
        if (!safeId(id) || !safeMarkup(markup) || displayName == null || displayName.isBlank()) return CompletableFuture.completedFuture(false);
        String normalized = id.toLowerCase(Locale.ROOT);
        String safeDisplay = displayName.substring(0, Math.min(64, displayName.length()));
        return plugin.getDatabase().transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO custom_tags(tag_id,display_name,markup,created_by,created_at) VALUES(?,?,?,?,?)")) {
                statement.setString(1, normalized); statement.setString(2, safeDisplay); statement.setString(3, markup); statement.setString(4, actor == null ? "unknown" : actor.substring(0, Math.min(64, actor.length()))); statement.setLong(5, System.currentTimeMillis()); statement.executeUpdate();
            }
            return true;
        }).whenComplete((success, error) -> { if (error == null && Boolean.TRUE.equals(success)) custom.put(normalized, new Definition(normalized, safeDisplay, markup, true)); }).exceptionally(error -> false);
    }

    public CompletableFuture<Boolean> delete(String id) {
        String normalized = id == null ? "" : id.toLowerCase(Locale.ROOT);
        Definition definition = custom.get(normalized);
        if (definition == null) return CompletableFuture.completedFuture(false);
        return plugin.getDatabase().transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM custom_tags WHERE tag_id=?")) { statement.setString(1, normalized); statement.executeUpdate(); }
            try (PreparedStatement revoke = connection.prepareStatement("UPDATE entitlement_sources SET revoked_at=? WHERE entitlement_id=? AND revoked_at IS NULL")) { revoke.setLong(1, System.currentTimeMillis()); revoke.setString(2, entitlementId(normalized)); revoke.executeUpdate(); }
            try (PreparedStatement revoke = connection.prepareStatement("UPDATE player_entitlements SET active=0 WHERE entitlement_id=? AND NOT EXISTS (SELECT 1 FROM entitlement_sources s WHERE s.player_uuid=player_entitlements.player_uuid AND s.entitlement_id=player_entitlements.entitlement_id AND s.revoked_at IS NULL)")) { revoke.setString(1, entitlementId(normalized)); revoke.executeUpdate(); }
            return true;
        }).whenComplete((success, error) -> { if (error == null && Boolean.TRUE.equals(success)) custom.remove(normalized); }).exceptionally(error -> false);
    }

    public CompletableFuture<Boolean> grant(UUID player, String id) {
        if (player == null || find(id) == null) return CompletableFuture.completedFuture(false);
        String receipt = "tag-admin:" + UUID.randomUUID();
        return plugin.getDatabase().transaction(connection -> {
            long now = System.currentTimeMillis();
            try (PreparedStatement receiptInsert = connection.prepareStatement("INSERT INTO reward_receipts(receipt_id,origin,player_uuid,bundle_version,status,created_at) VALUES(?,?,?,?,?,?)")) {
                receiptInsert.setString(1, receipt); receiptInsert.setString(2, "TAG_ADMIN"); receiptInsert.setString(3, player.toString()); receiptInsert.setString(4, "tag-v1"); receiptInsert.setString(5, "APPLIED"); receiptInsert.setLong(6, now); receiptInsert.executeUpdate();
            }
            try (PreparedStatement source = connection.prepareStatement("INSERT INTO entitlement_sources(receipt_id,player_uuid,entitlement_id,kind,value,created_at) VALUES(?,?,?,?,?,?)")) {
                source.setString(1, receipt); source.setString(2, player.toString()); source.setString(3, entitlementId(id)); source.setString(4, "CHAT_TAG"); source.setString(5, id); source.setLong(6, now); source.executeUpdate();
            }
            try (PreparedStatement owned = connection.prepareStatement("INSERT INTO player_entitlements(player_uuid,entitlement_id,kind,value,active,created_at) VALUES(?,?,?,?,1,?) ON CONFLICT(player_uuid,entitlement_id) DO UPDATE SET active=1,value=excluded.value")) {
                owned.setString(1, player.toString()); owned.setString(2, entitlementId(id)); owned.setString(3, "CHAT_TAG"); owned.setString(4, id); owned.setLong(5, now); owned.executeUpdate();
            }
            return true;
        }).thenApply(success -> { if (success) plugin.getEntitlementService().cache(player, entitlementId(id)); return success; });
    }

    public CompletableFuture<Boolean> revoke(UUID player, String id) {
        if (player == null || find(id) == null) return CompletableFuture.completedFuture(false);
        return plugin.getDatabase().transaction(connection -> {
            String receipt = null;
            try (PreparedStatement source = connection.prepareStatement("SELECT s.receipt_id FROM entitlement_sources s JOIN reward_receipts r ON r.receipt_id=s.receipt_id WHERE s.player_uuid=? AND s.entitlement_id=? AND s.revoked_at IS NULL AND r.origin IN ('TAG_ADMIN','CUSTOM_TAG_TOKEN') ORDER BY s.created_at DESC LIMIT 1")) { source.setString(1, player.toString()); source.setString(2, entitlementId(id)); try (ResultSet row = source.executeQuery()) { if (row.next()) receipt = row.getString(1); } }
            if (receipt == null) return false;
            try (PreparedStatement statement = connection.prepareStatement("UPDATE entitlement_sources SET revoked_at=? WHERE receipt_id=? AND entitlement_id=?")) { statement.setLong(1, System.currentTimeMillis()); statement.setString(2, receipt); statement.setString(3, entitlementId(id)); statement.executeUpdate(); }
            try (PreparedStatement owned = connection.prepareStatement("UPDATE player_entitlements SET active=CASE WHEN EXISTS (SELECT 1 FROM entitlement_sources s WHERE s.player_uuid=player_entitlements.player_uuid AND s.entitlement_id=player_entitlements.entitlement_id AND s.revoked_at IS NULL) THEN 1 ELSE 0 END WHERE player_uuid=? AND entitlement_id=?")) { owned.setString(1, player.toString()); owned.setString(2, entitlementId(id)); owned.executeUpdate(); }
            return true;
        }).thenCompose(success -> success ? plugin.getEntitlementService().load(player).thenApply(ignored -> true) : CompletableFuture.completedFuture(false));
    }

    public CompletableFuture<String> issueToken(UUID player, String id) {
        if (player == null || find(id) == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Unknown tag"));
        String token = randomToken();
        return plugin.getDatabase().transaction(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("INSERT INTO custom_tag_tokens(token,tag_id,player_uuid,created_at) VALUES(?,?,?,?)")) { statement.setString(1, token); statement.setString(2, id.toLowerCase(Locale.ROOT)); statement.setString(3, player.toString()); statement.setLong(4, System.currentTimeMillis()); statement.executeUpdate(); }
            return token;
        });
    }

    public CompletableFuture<Boolean> redeemToken(UUID player, String token) {
        if (player == null || token == null || !token.matches("[A-Z2-9]{8,24}")) return CompletableFuture.completedFuture(false);
        return plugin.getDatabase().<String>transaction(connection -> {
            String id;
            try (PreparedStatement lookup = connection.prepareStatement("SELECT tag_id FROM custom_tag_tokens WHERE token=? AND player_uuid=? AND redeemed_at IS NULL")) { lookup.setString(1, token); lookup.setString(2, player.toString()); try (ResultSet row = lookup.executeQuery()) { if (!row.next()) return null; id = row.getString(1); } }
            String receipt = "tag-token:" + token;
            long now = System.currentTimeMillis();
            try (PreparedStatement mark = connection.prepareStatement("UPDATE custom_tag_tokens SET redeemed_at=? WHERE token=? AND redeemed_at IS NULL")) { mark.setLong(1, now); mark.setString(2, token); if (mark.executeUpdate() != 1) return null; }
            try (PreparedStatement reward = connection.prepareStatement("INSERT INTO reward_receipts(receipt_id,origin,player_uuid,bundle_version,status,created_at) VALUES(?,?,?,?,?,?)")) { reward.setString(1, receipt); reward.setString(2, "CUSTOM_TAG_TOKEN"); reward.setString(3, player.toString()); reward.setString(4, "tag-v1"); reward.setString(5, "APPLIED"); reward.setLong(6, now); reward.executeUpdate(); }
            try (PreparedStatement source = connection.prepareStatement("INSERT INTO entitlement_sources(receipt_id,player_uuid,entitlement_id,kind,value,created_at) VALUES(?,?,?,?,?,?)")) { source.setString(1, receipt); source.setString(2, player.toString()); source.setString(3, entitlementId(id)); source.setString(4, "CHAT_TAG"); source.setString(5, id); source.setLong(6, now); source.executeUpdate(); }
            try (PreparedStatement owned = connection.prepareStatement("INSERT INTO player_entitlements(player_uuid,entitlement_id,kind,value,active,created_at) VALUES(?,?,?,?,1,?) ON CONFLICT(player_uuid,entitlement_id) DO UPDATE SET active=1")) { owned.setString(1, player.toString()); owned.setString(2, entitlementId(id)); owned.setString(3, "CHAT_TAG"); owned.setString(4, id); owned.setLong(5, now); owned.executeUpdate(); }
            return id;
        }).thenApply(id -> { if (id != null) plugin.getEntitlementService().cache(player, entitlementId(id)); return id != null; });
    }

    public String entitlementId(String id) { return "cosmetic.tag." + id.toLowerCase(Locale.ROOT); }
    public static boolean safeId(String value) { return value != null && value.matches("[a-z0-9_-]{1,32}"); }
    public static boolean safeMarkup(String value) { return value != null && value.length() <= 160 && !value.matches("(?is).*<(click|hover|run_command|suggest_command|insert|selector|nbt|font)[:>].*"); }
    public record Definition(String id, String displayName, String markup, boolean custom) { }
    private String randomToken() { char[] alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); StringBuilder value = new StringBuilder(12); for (int i = 0; i < 12; i++) value.append(alphabet[random.nextInt(alphabet.length)]); return value.toString(); }
}
