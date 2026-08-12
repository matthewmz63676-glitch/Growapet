package me.growapet.database;

import me.growapet.models.PlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerDAO {
    private static final Set<String> LEADERBOARD_COLUMNS = Set.of(
            "coins", "gems", "credits", "level", "mob_kills", "boss_kills", "boss_damage",
            "damage_multiplier", "coin_multiplier", "gem_multiplier", "exp_multiplier", "pet_power",
            "playtime_seconds", "eggs_hatched", "pets_collected", "trades", "highest_pet_level");
    private final Database database;

    public PlayerDAO(Database database) { this.database = database; }

    public CompletableFuture<PlayerData> load(UUID uuid, String name, long startingCoins, long startingGems, long startingCredits) {
        return database.async(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM players WHERE uuid=?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) return map(result, uuid);
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO players(uuid,name,coins,gems,credits) VALUES(?,?,?,?,?)")) {
                insert.setString(1, uuid.toString());
                insert.setString(2, name);
                insert.setLong(3, Math.max(0, startingCoins));
                insert.setLong(4, Math.max(0, startingGems));
                insert.setLong(5, Math.max(0, startingCredits));
                insert.executeUpdate();
            }
            PlayerData fresh = new PlayerData(uuid, name);
            fresh.setCoins(startingCoins);
            fresh.setGems(startingGems);
            fresh.setCredits(startingCredits);
            fresh.setDirty(false);
            return fresh;
        });
    }

    private PlayerData map(ResultSet result, UUID uuid) throws SQLException {
        PlayerData data = new PlayerData(uuid, result.getString("name"));
        data.setCoins(result.getLong("coins"));
        data.setGems(result.getLong("gems"));
        data.setCredits(result.getLong("credits"));
        data.setLevel(result.getInt("level"));
        data.setExp(result.getLong("exp"));
        data.normalizeExperience();
        data.setExpMultiplier(result.getDouble("exp_multiplier"));
        data.setCoinMultiplier(result.getDouble("coin_multiplier"));
        data.setGemMultiplier(result.getDouble("gem_multiplier"));
        data.setDamageMultiplier(result.getDouble("damage_multiplier"));
        data.setCriticalChance(result.getDouble("critical_chance"));
        data.setCriticalDamage(result.getDouble("critical_damage"));
        data.setPetPower(optionalDouble(result, "pet_power"));
        data.setMobKills(result.getLong("mob_kills"));
        data.setBossKills(result.getLong("boss_kills"));
        data.setBossDamage(optionalDouble(result, "boss_damage"));
        data.setEggsHatched(result.getLong("eggs_hatched"));
        data.setPetsCollected(result.getLong("pets_collected"));
        data.setTrades(result.getLong("trades"));
        data.setQuestsCompleted(result.getLong("quests_completed"));
        data.setPlaytimeSeconds(result.getLong("playtime_seconds"));
        data.setHighestPetLevel(optionalInt(result, "highest_pet_level"));
        data.setActivePetUuid(result.getString("active_pet_uuid"));
        String zones = result.getString("unlocked_zones");
        if (zones != null && !zones.isBlank()) data.getUnlockedZones().addAll(Arrays.asList(zones.split(",")));
        String shop = result.getString("shop_levels");
        if (shop != null && !shop.isBlank()) {
            for (String entry : shop.split(",")) {
                String[] parts = entry.split(":", 2);
                if (parts.length != 2) continue;
                try { data.getShopLevels().put(parts[0], Math.max(0, Integer.parseInt(parts[1]))); }
                catch (NumberFormatException ignored) { }
            }
        }
        data.setDirty(true);
        return data;
    }

    public CompletableFuture<Void> save(PlayerData data) {
        PlayerSnapshot snapshot = PlayerSnapshot.capture(data);
        return database.transaction(connection -> {
            saveSnapshot(connection, snapshot);
            syncNormalized(connection, snapshot);
            return null;
        });
    }

    /** Saves non-economic state without overwriting balances/trade counters owned by an in-flight transaction. */
    public CompletableFuture<Void> saveNonEconomic(PlayerData data) {
        PlayerSnapshot value = PlayerSnapshot.capture(data);
        return database.transaction(connection -> {
            String sql = "UPDATE players SET name=?,level=?,exp=?,exp_multiplier=?,coin_multiplier=?,gem_multiplier=?,damage_multiplier=?,critical_chance=?,critical_damage=?,pet_power=?,mob_kills=?,boss_kills=?,boss_damage=?,eggs_hatched=?,pets_collected=?,quests_completed=?,playtime_seconds=?,highest_pet_level=?,active_pet_uuid=?,unlocked_zones=?,shop_levels=? WHERE uuid=?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int i=1;statement.setString(i++,value.name());statement.setInt(i++,value.level());statement.setLong(i++,value.exp());statement.setDouble(i++,value.expMultiplier());statement.setDouble(i++,value.coinMultiplier());statement.setDouble(i++,value.gemMultiplier());statement.setDouble(i++,value.damageMultiplier());statement.setDouble(i++,value.criticalChance());statement.setDouble(i++,value.criticalDamage());statement.setDouble(i++,value.petPower());statement.setLong(i++,value.mobKills());statement.setLong(i++,value.bossKills());statement.setDouble(i++,value.bossDamage());statement.setLong(i++,value.eggsHatched());statement.setLong(i++,value.petsCollected());statement.setLong(i++,value.questsCompleted());statement.setLong(i++,value.playtimeSeconds());statement.setInt(i++,value.highestPetLevel());statement.setString(i++,value.activePetUuid());statement.setString(i++,String.join(",",value.zones()));statement.setString(i++,encodeShop(value.shopLevels()));statement.setString(i,value.uuid().toString());if(statement.executeUpdate()!=1)throw new SQLException("Player row disappeared: "+value.uuid());
            }
            syncNormalized(connection,value);return null;
        });
    }

    public CompletableFuture<Void> saveWithReceipt(PlayerData data,String receiptId,String kind,long coinsDelta,long gemsDelta,long creditsDelta){PlayerSnapshot snapshot=PlayerSnapshot.capture(data);return database.transaction(connection->{saveSnapshot(connection,snapshot);syncNormalized(connection,snapshot);try(PreparedStatement receipt=connection.prepareStatement("INSERT INTO economy_transactions(id,player_uuid,kind,coins_delta,gems_delta,credits_delta,created_at)VALUES(?,?,?,?,?,?,?)")){receipt.setString(1,receiptId);receipt.setString(2,snapshot.uuid().toString());receipt.setString(3,kind);receipt.setLong(4,coinsDelta);receipt.setLong(5,gemsDelta);receipt.setLong(6,creditsDelta);receipt.setLong(7,System.currentTimeMillis());receipt.executeUpdate();}return null;});}

    private static void saveSnapshot(Connection connection, PlayerSnapshot value) throws SQLException {
        String sql = "UPDATE players SET name=?,coins=?,gems=?,credits=?,level=?,exp=?,exp_multiplier=?,coin_multiplier=?,gem_multiplier=?,damage_multiplier=?,critical_chance=?,critical_damage=?,pet_power=?,mob_kills=?,boss_kills=?,boss_damage=?,eggs_hatched=?,pets_collected=?,trades=?,quests_completed=?,playtime_seconds=?,highest_pet_level=?,active_pet_uuid=?,unlocked_zones=?,shop_levels=? WHERE uuid=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, value.name());
            statement.setLong(index++, value.coins()); statement.setLong(index++, value.gems()); statement.setLong(index++, value.credits());
            statement.setInt(index++, value.level()); statement.setLong(index++, value.exp());
            statement.setDouble(index++, value.expMultiplier()); statement.setDouble(index++, value.coinMultiplier());
            statement.setDouble(index++, value.gemMultiplier()); statement.setDouble(index++, value.damageMultiplier());
            statement.setDouble(index++, value.criticalChance()); statement.setDouble(index++, value.criticalDamage()); statement.setDouble(index++, value.petPower());
            statement.setLong(index++, value.mobKills()); statement.setLong(index++, value.bossKills()); statement.setDouble(index++, value.bossDamage());
            statement.setLong(index++, value.eggsHatched()); statement.setLong(index++, value.petsCollected()); statement.setLong(index++, value.trades());
            statement.setLong(index++, value.questsCompleted()); statement.setLong(index++, value.playtimeSeconds()); statement.setInt(index++, value.highestPetLevel());
            statement.setString(index++, value.activePetUuid()); statement.setString(index++, String.join(",", value.zones()));
            statement.setString(index++, encodeShop(value.shopLevels())); statement.setString(index, value.uuid().toString());
            if (statement.executeUpdate() != 1) throw new SQLException("Player row disappeared: " + value.uuid());
        }
    }

    private static void syncNormalized(Connection connection, PlayerSnapshot value) throws SQLException {
        try (PreparedStatement deleteZones = connection.prepareStatement("DELETE FROM player_zones WHERE player_uuid=?");
             PreparedStatement insertZone = connection.prepareStatement("INSERT INTO player_zones(player_uuid,zone_id,unlocked_at) VALUES(?,?,?)");
             PreparedStatement deleteShop = connection.prepareStatement("DELETE FROM player_shop_upgrades WHERE player_uuid=?");
             PreparedStatement insertShop = connection.prepareStatement("INSERT INTO player_shop_upgrades(player_uuid,upgrade_id,level) VALUES(?,?,?)")) {
            deleteZones.setString(1, value.uuid().toString()); deleteZones.executeUpdate();
            for (String zone : value.zones()) {
                insertZone.setString(1, value.uuid().toString()); insertZone.setString(2, zone); insertZone.setLong(3, System.currentTimeMillis()); insertZone.addBatch();
            }
            insertZone.executeBatch();
            deleteShop.setString(1, value.uuid().toString()); deleteShop.executeUpdate();
            for (Map.Entry<String,Integer> entry : value.shopLevels().entrySet()) {
                insertShop.setString(1, value.uuid().toString()); insertShop.setString(2, entry.getKey()); insertShop.setInt(3, entry.getValue()); insertShop.addBatch();
            }
            insertShop.executeBatch();
        }
    }

    public CompletableFuture<List<LeaderboardEntry>> topPlayers(String column, int limit) {
        if (!LEADERBOARD_COLUMNS.contains(column) || limit < 1) return CompletableFuture.completedFuture(List.of());
        return database.async(connection -> {
            List<LeaderboardEntry> entries = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT uuid,name," + column + " AS value FROM players ORDER BY value DESC LIMIT ?")) {
                statement.setInt(1, Math.min(limit, 100));
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) entries.add(new LeaderboardEntry(UUID.fromString(result.getString("uuid")), result.getString("name"), result.getDouble("value")));
                }
            }
            return entries;
        });
    }

    private static String encodeShop(Map<String,Integer> values) {
        StringBuilder result = new StringBuilder();
        values.forEach((key, value) -> { if (!result.isEmpty()) result.append(','); result.append(key).append(':').append(value); });
        return result.toString();
    }

    private static double optionalDouble(ResultSet result, String column) { try { return result.getDouble(column); } catch (SQLException ignored) { return 0; } }
    private static int optionalInt(ResultSet result, String column) { try { return result.getInt(column); } catch (SQLException ignored) { return 0; } }

    private record PlayerSnapshot(UUID uuid, String name, long coins, long gems, long credits, int level, long exp,
            double expMultiplier, double coinMultiplier, double gemMultiplier, double damageMultiplier,
            double criticalChance, double criticalDamage, double petPower, long mobKills, long bossKills,
            double bossDamage, long eggsHatched, long petsCollected, long trades, long questsCompleted,
            long playtimeSeconds, int highestPetLevel, String activePetUuid, Set<String> zones,
            Map<String,Integer> shopLevels, long revision) {
        static PlayerSnapshot capture(PlayerData data) {
            return new PlayerSnapshot(data.getUuid(), data.getName(), data.getCoins(), data.getGems(), data.getCredits(),
                    data.getLevel(), data.getExp(), data.getExpMultiplier(), data.getCoinMultiplier(), data.getGemMultiplier(),
                    data.getDamageMultiplier(), data.getCriticalChance(), data.getCriticalDamage(), data.getPetPower(),
                    data.getMobKills(), data.getBossKills(), data.getBossDamage(), data.getEggsHatched(), data.getPetsCollected(),
                    data.getTrades(), data.getQuestsCompleted(), data.getPlaytimeSeconds(), data.getHighestPetLevel(),
                    data.getActivePetUuid(), new LinkedHashSet<>(data.getUnlockedZones()), new LinkedHashMap<>(data.getShopLevels()), data.getRevision());
        }
    }

    public record LeaderboardEntry(UUID uuid, String name, double value) { }
}
