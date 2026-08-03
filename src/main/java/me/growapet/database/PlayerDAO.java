/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import me.growapet.database.Database;
import me.growapet.models.PlayerData;

public class PlayerDAO {
    private static final Set<String> LEADERBOARD_COLUMNS = Set.of("coins", "gems", "credits", "level", "mob_kills", "boss_kills", "damage_multiplier", "coin_multiplier", "gem_multiplier", "exp_multiplier");
    private final Database database;

    public PlayerDAO(Database database) {
        this.database = database;
    }

    public CompletableFuture<List<LeaderboardEntry>> topPlayers(String column, int limit) {
        if (!LEADERBOARD_COLUMNS.contains(column)) {
            return CompletableFuture.completedFuture(List.of());
        }
        return this.database.async(connection -> {
            ArrayList<LeaderboardEntry> results = new ArrayList<LeaderboardEntry>();
            try (PreparedStatement ps = connection.prepareStatement("SELECT uuid, name, " + column + " AS value FROM players ORDER BY value DESC LIMIT ?");){
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery();){
                    while (rs.next()) {
                        results.add(new LeaderboardEntry(UUID.fromString(rs.getString("uuid")), rs.getString("name"), rs.getDouble("value")));
                    }
                }
            }
            return results;
        });
    }

    public CompletableFuture<PlayerData> load(UUID uuid, String name) {
        return this.database.async(connection -> {
            block22: {
                try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM players WHERE uuid = ?");){
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery();){
                        if (!rs.next()) break block22;
                        String[] data = new PlayerData(uuid, rs.getString("name"));
                        data.setCoins(rs.getLong("coins"));
                        data.setGems(rs.getLong("gems"));
                        data.setCredits(rs.getLong("credits"));
                        data.setLevel(rs.getInt("level"));
                        data.setExp(rs.getLong("exp"));
                        data.setExpMultiplier(rs.getDouble("exp_multiplier"));
                        data.setCoinMultiplier(rs.getDouble("coin_multiplier"));
                        data.setGemMultiplier(rs.getDouble("gem_multiplier"));
                        data.setDamageMultiplier(rs.getDouble("damage_multiplier"));
                        data.setCriticalChance(rs.getDouble("critical_chance"));
                        data.setCriticalDamage(rs.getDouble("critical_damage"));
                        data.setMobKills(rs.getLong("mob_kills"));
                        data.setBossKills(rs.getLong("boss_kills"));
                        data.setEggsHatched(rs.getLong("eggs_hatched"));
                        data.setPetsCollected(rs.getLong("pets_collected"));
                        data.setTrades(rs.getLong("trades"));
                        data.setQuestsCompleted(rs.getLong("quests_completed"));
                        data.setPlaytimeSeconds(rs.getLong("playtime_seconds"));
                        data.setActivePetUuid(rs.getString("active_pet_uuid"));
                        String zonesRaw = null;
                        try {
                            zonesRaw = rs.getString("unlocked_zones");
                        }
                        catch (SQLException sQLException) {
                            // empty catch block
                        }
                        if (zonesRaw != null && !zonesRaw.isBlank()) {
                            data.getUnlockedZones().addAll(Arrays.asList(zonesRaw.split(",")));
                        }
                        String shopRaw = null;
                        try {
                            shopRaw = rs.getString("shop_levels");
                        }
                        catch (SQLException sQLException) {
                            // empty catch block
                        }
                        if (shopRaw != null && !shopRaw.isBlank()) {
                            for (String entry : shopRaw.split(",")) {
                                String[] parts = entry.split(":");
                                if (parts.length != 2) continue;
                                try {
                                    data.setShopLevel(parts[0], Integer.parseInt(parts[1]));
                                }
                                catch (NumberFormatException numberFormatException) {
                                    // empty catch block
                                }
                            }
                        }
                        String[] stringArray = data;
                        return stringArray;
                    }
                }
            }
            PlayerData fresh = new PlayerData(uuid, name);
            this.insert(connection, fresh);
            return fresh;
        });
    }

    private void insert(Connection connection, PlayerData data) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("INSERT OR IGNORE INTO players (uuid, name) VALUES (?, ?)");){
            ps.setString(1, data.getUuid().toString());
            ps.setString(2, data.getName());
            ps.executeUpdate();
        }
    }

    public CompletableFuture<Void> save(PlayerData data) {
        return this.database.async(connection -> {
            try (PreparedStatement ps = connection.prepareStatement("UPDATE players SET name=?, coins=?, gems=?, credits=?, level=?, exp=?,\nexp_multiplier=?, coin_multiplier=?, gem_multiplier=?, damage_multiplier=?,\ncritical_chance=?, critical_damage=?, mob_kills=?, boss_kills=?, eggs_hatched=?,\npets_collected=?, trades=?, quests_completed=?, playtime_seconds=?, active_pet_uuid=?,\nunlocked_zones=?, shop_levels=?\nWHERE uuid=?\n");){
                ps.setString(1, data.getName());
                ps.setLong(2, data.getCoins());
                ps.setLong(3, data.getGems());
                ps.setLong(4, data.getCredits());
                ps.setInt(5, data.getLevel());
                ps.setLong(6, data.getExp());
                ps.setDouble(7, data.getExpMultiplier());
                ps.setDouble(8, data.getCoinMultiplier());
                ps.setDouble(9, data.getGemMultiplier());
                ps.setDouble(10, data.getDamageMultiplier());
                ps.setDouble(11, data.getCriticalChance());
                ps.setDouble(12, data.getCriticalDamage());
                ps.setLong(13, data.getMobKills());
                ps.setLong(14, data.getBossKills());
                ps.setLong(15, data.getEggsHatched());
                ps.setLong(16, data.getPetsCollected());
                ps.setLong(17, data.getTrades());
                ps.setLong(18, data.getQuestsCompleted());
                ps.setLong(19, data.getPlaytimeSeconds());
                ps.setString(20, data.getActivePetUuid());
                ps.setString(21, String.join((CharSequence)",", data.getUnlockedZones()));
                StringBuilder shopLevels = new StringBuilder();
                for (Map.Entry<String, Integer> entry : data.getShopLevels().entrySet()) {
                    if (shopLevels.length() > 0) {
                        shopLevels.append(",");
                    }
                    shopLevels.append(entry.getKey()).append(":").append(entry.getValue());
                }
                ps.setString(22, shopLevels.toString());
                ps.setString(23, data.getUuid().toString());
                ps.executeUpdate();
                data.setDirty(false);
            }
            return null;
        });
    }

    public record LeaderboardEntry(UUID uuid, String name, double value) {
    }
}

