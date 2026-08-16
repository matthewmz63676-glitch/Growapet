package me.growapet.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationCompatibilityTest {
    @TempDir Path temporary;

    @Test void migrationsPreserveLegacyBalancesAndCreateDurabilityTables() throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (var connection=DriverManager.getConnection("jdbc:sqlite:"+temporary.resolve("legacy.db"))) {
            connection.createStatement().executeUpdate("CREATE TABLE players(uuid TEXT PRIMARY KEY,name TEXT,coins INTEGER DEFAULT 0,gems INTEGER DEFAULT 0,credits INTEGER DEFAULT 0,level INTEGER DEFAULT 1,exp INTEGER DEFAULT 0,exp_multiplier REAL DEFAULT 1,coin_multiplier REAL DEFAULT 1,gem_multiplier REAL DEFAULT 1,damage_multiplier REAL DEFAULT 1,critical_chance REAL DEFAULT 0,critical_damage REAL DEFAULT 1.5,mob_kills INTEGER DEFAULT 0,boss_kills INTEGER DEFAULT 0,eggs_hatched INTEGER DEFAULT 0,pets_collected INTEGER DEFAULT 0,trades INTEGER DEFAULT 0,quests_completed INTEGER DEFAULT 0,playtime_seconds INTEGER DEFAULT 0,active_pet_uuid TEXT)");
            connection.createStatement().executeUpdate("INSERT INTO players(uuid,name,coins,gems,credits)VALUES('00000000-0000-0000-0000-000000000001','Legacy',123,45,6)");
            for (Migration migration:Migrations.all()) migration.apply(connection);
            try(var row=connection.createStatement().executeQuery("SELECT coins,gems,credits FROM players WHERE name='Legacy'")){assertTrue(row.next());assertEquals(123,row.getLong(1));assertEquals(45,row.getLong(2));assertEquals(6,row.getLong(3));}
            try(var tables=connection.createStatement().executeQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('incubating_eggs','quest_progress','trades','item_deliveries','trade_sessions','trade_escrow','trade_request_cooldowns','daily_claims','credit_grants','plot_boosts','boss_state','mob_respawns','tutorial_progress','mob_spawn_points')")){assertTrue(tables.next());assertEquals(14,tables.getInt(1));}
            try(var columns=connection.createStatement().executeQuery("SELECT COUNT(*) FROM pragma_table_info('mob_respawns') WHERE name='spawn_point_id'")){assertTrue(columns.next());assertEquals(1,columns.getInt(1));}
            try(var columns=connection.createStatement().executeQuery("SELECT COUNT(*) FROM pragma_table_info('pets') WHERE name='trade_lock_id'")){assertTrue(columns.next());assertEquals(1,columns.getInt(1));}
            try(var columns=connection.createStatement().executeQuery("SELECT COUNT(*) FROM pragma_table_info('boss_state') WHERE name IN ('active','run_id','health','damage_data','updated_at')")){assertTrue(columns.next());assertEquals(5,columns.getInt(1));}
        }
    }
}
