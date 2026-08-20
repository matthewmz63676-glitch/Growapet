package me.growapet.discord;

import me.growapet.GrowAPet;
import me.growapet.rewards.RewardBundle;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Durable, single-use Minecraft UUID to Discord account linking boundary. */
public final class DiscordLinkService {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final GrowAPet plugin;
    private final SecureRandom random = new SecureRandom();

    public DiscordLinkService(GrowAPet plugin) { this.plugin = plugin; }

    public CompletableFuture<String> issue(UUID playerId) {
        if (playerId == null) return CompletableFuture.failedFuture(new IllegalArgumentException("Player is required"));
        String token = randomToken(10);
        long expires = System.currentTimeMillis() + Math.max(60, plugin.getConfigManager().discord().getLong("token-expiry-seconds", 300)) * 1000L;
        return plugin.getDatabase().transaction(connection -> {
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM discord_link_tokens WHERE player_uuid=? OR expires_at<?")) {
                delete.setString(1, playerId.toString()); delete.setLong(2, System.currentTimeMillis()); delete.executeUpdate();
            }
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO discord_link_tokens(token_hash,player_uuid,expires_at,created_at) VALUES(?,?,?,?)")) {
                insert.setString(1, hash(token)); insert.setString(2, playerId.toString()); insert.setLong(3, expires); insert.setLong(4, System.currentTimeMillis()); insert.executeUpdate();
            }
            return null;
        }).thenApply(ignored -> token);
    }

    public CompletableFuture<LinkResult> consume(String token, String discordId, String discordName) {
        if (token == null || !token.matches("[A-Za-z2-9]{8,16}") || discordId == null || !discordId.matches("[0-9]{5,32}"))
            return CompletableFuture.completedFuture(LinkResult.invalid());
        String tokenHash = hash(token.toUpperCase(Locale.ROOT));
        return plugin.getDatabase().transaction(connection -> {
            UUID player = null;
            try (PreparedStatement lookup = connection.prepareStatement("SELECT player_uuid,expires_at,consumed_at,attempts FROM discord_link_tokens WHERE token_hash=?")) {
                lookup.setString(1, tokenHash);
                try (ResultSet row = lookup.executeQuery()) {
                    if (!row.next()) return LinkResult.invalid();
                    if (row.getObject("consumed_at") != null || row.getLong("expires_at") < System.currentTimeMillis()) return LinkResult.expired();
                    if (row.getInt("attempts") >= plugin.getConfigManager().discord().getInt("link-attempt-limit", 5)) return LinkResult.rateLimited();
                    player = UUID.fromString(row.getString("player_uuid"));
                }
            } catch (IllegalArgumentException invalid) { return LinkResult.invalid(); }
            try (PreparedStatement existing = connection.prepareStatement("SELECT player_uuid FROM discord_links WHERE discord_id=? AND unlinked_at IS NULL")) {
                existing.setString(1, discordId);
                try (ResultSet row = existing.executeQuery()) { if (row.next() && !player.toString().equals(row.getString(1))) return LinkResult.alreadyLinked(); }
            }
            try (PreparedStatement mark = connection.prepareStatement("UPDATE discord_link_tokens SET consumed_at=?,attempts=attempts+1 WHERE token_hash=? AND consumed_at IS NULL")) {
                mark.setLong(1, System.currentTimeMillis()); mark.setString(2, tokenHash); if (mark.executeUpdate() != 1) return LinkResult.invalid();
            }
            try (PreparedStatement link = connection.prepareStatement("INSERT INTO discord_links(player_uuid,discord_id,discord_name,linked_at) VALUES(?,?,?,?) ON CONFLICT(player_uuid) DO UPDATE SET discord_id=excluded.discord_id,discord_name=excluded.discord_name,linked_at=excluded.linked_at,unlinked_at=NULL")) {
                link.setString(1, player.toString()); link.setString(2, discordId); link.setString(3, safeName(discordName)); link.setLong(4, System.currentTimeMillis()); link.executeUpdate();
            }
            return LinkResult.success(player, discordId, safeName(discordName));
        }).thenCompose(result -> {
            if (!result.success()) return CompletableFuture.completedFuture(result);
            plugin.getDiscordIntegration().cacheLink(result.playerId(), result.discordId(), result.discordName());
            plugin.getDiscordIntegration().applyLinkedRole(result.discordId());
            RewardBundle reward = new RewardBundle("discord-link-v1", 0, 0, 0,
                    java.util.List.of(new RewardBundle.Entitlement("discord.link.exp5", "EXP_MULTIPLIER", "0.05"), new RewardBundle.Entitlement("cosmetic.tag.discord", "CHAT_TAG", "discord")), java.util.List.of());
            return plugin.getRewardFulfilmentService().fulfil("discord-link:" + result.playerId(), "DISCORD_LINK", result.playerId(), reward).handle((applied, error) -> {
                if (error != null) plugin.getLogger().warning("Discord link reward queued for retry: " + error.getMessage());
                return result;
            });
        });
    }

    public CompletableFuture<Void> unlink(UUID playerId) {
        return plugin.getDatabase().transaction(connection -> {
            String discordId = null;
            try (PreparedStatement lookup = connection.prepareStatement("SELECT discord_id FROM discord_links WHERE player_uuid=? AND unlinked_at IS NULL")) { lookup.setString(1, playerId.toString()); try (ResultSet row = lookup.executeQuery()) { if (row.next()) discordId = row.getString(1); } }
            long unlinkedAt = System.currentTimeMillis();
            // Keep the original identity for audit, but free the table-wide legacy
            // UNIQUE(discord_id) value so the same Discord account can be linked
            // again later without creating a duplicate active identity.
            try (PreparedStatement statement = connection.prepareStatement("UPDATE discord_links SET unlinked_at=?,unlinked_discord_id=?,discord_id=? WHERE player_uuid=? AND unlinked_at IS NULL")) {
                statement.setLong(1, unlinkedAt); statement.setString(2, discordId); statement.setString(3, "unlinked:" + playerId + ":" + unlinkedAt); statement.setString(4, playerId.toString()); statement.executeUpdate();
            }
            if (discordId != null) plugin.getDiscordIntegration().removeLinkedRole(discordId);
            return null;
        }).thenRun(() -> plugin.getDiscordIntegration().removeLink(playerId));
    }

    public CompletableFuture<LinkStatus> status(UUID playerId) {
        return plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT discord_id,discord_name,linked_at FROM discord_links WHERE player_uuid=? AND unlinked_at IS NULL")) {
                statement.setString(1, playerId.toString());
                try (ResultSet row = statement.executeQuery()) { return row.next() ? new LinkStatus(true, row.getString(1), row.getString(2), row.getLong(3), plugin.getEntitlementService().has(playerId, "discord.link.exp5")) : LinkStatus.unlinked(plugin.getEntitlementService().has(playerId, "discord.link.exp5")); }
            }
        });
    }

    public CompletableFuture<UUID> playerForDiscord(String discordId) {
        return plugin.getDatabase().async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT player_uuid FROM discord_links WHERE discord_id=? AND unlinked_at IS NULL")) {
                statement.setString(1, discordId); try (ResultSet row = statement.executeQuery()) { return row.next() ? UUID.fromString(row.getString(1)) : null; }
            }
        });
    }

    private String randomToken(int length) { StringBuilder result = new StringBuilder(length); for (int i = 0; i < length; i++) result.append(ALPHABET[random.nextInt(ALPHABET.length)]); return result.toString(); }
    private static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception error) { throw new IllegalStateException(error); } }
    private static String safeName(String value) { return value == null ? "" : value.replaceAll("[^\\p{L}\\p{N}_.# -]", "").substring(0, Math.min(128, value.replaceAll("[^\\p{L}\\p{N}_.# -]", "").length())); }

    public record LinkStatus(boolean linked, String discordId, String discordName, long linkedAt, boolean lifetimeReward) {
        static LinkStatus unlinked(boolean reward) { return new LinkStatus(false, "", "", 0, reward); }
    }
    public record LinkResult(boolean success, UUID playerId, String discordId, String discordName, String message) {
        static LinkResult success(UUID player, String id, String name) { return new LinkResult(true, player, id, name, "Linked"); }
        static LinkResult invalid() { return new LinkResult(false, null, "", "", "Invalid or already-used link code."); }
        static LinkResult expired() { return new LinkResult(false, null, "", "", "That link code has expired."); }
        static LinkResult rateLimited() { return new LinkResult(false, null, "", "", "Too many link attempts."); }
        static LinkResult alreadyLinked() { return new LinkResult(false, null, "", "", "That Discord account is already linked."); }
    }
}
