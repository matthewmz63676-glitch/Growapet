/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.growapet.GrowAPet;

public class Database {
    private final GrowAPet plugin;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> new Thread(r, "GrowAPet-Database"));
    private Connection connection;

    public Database(GrowAPet plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            File dataFolder = this.plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }
            File dbFile = new File(dataFolder, "database.db");
            Class.forName("me.growapet.libs.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            this.createTables();
        }
        catch (Exception e) {
            this.plugin.getLogger().severe("Failed to connect to database: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement st = this.connection.createStatement();){
            st.executeUpdate("    CREATE TABLE IF NOT EXISTS players (\n        uuid TEXT PRIMARY KEY,\n        name TEXT,\n        coins INTEGER DEFAULT 0,\n        gems INTEGER DEFAULT 0,\n        credits INTEGER DEFAULT 0,\n        level INTEGER DEFAULT 1,\n        exp INTEGER DEFAULT 0,\n        exp_multiplier REAL DEFAULT 1.0,\n        coin_multiplier REAL DEFAULT 1.0,\n        gem_multiplier REAL DEFAULT 1.0,\n        damage_multiplier REAL DEFAULT 1.0,\n        critical_chance REAL DEFAULT 0.0,\n        critical_damage REAL DEFAULT 1.5,\n        mob_kills INTEGER DEFAULT 0,\n        boss_kills INTEGER DEFAULT 0,\n        eggs_hatched INTEGER DEFAULT 0,\n        pets_collected INTEGER DEFAULT 0,\n        trades INTEGER DEFAULT 0,\n        quests_completed INTEGER DEFAULT 0,\n        playtime_seconds INTEGER DEFAULT 0,\n        active_pet_uuid TEXT\n    )\n");
            st.executeUpdate("    CREATE TABLE IF NOT EXISTS pets (\n        uuid TEXT PRIMARY KEY,\n        owner TEXT NOT NULL,\n        entity_type TEXT,\n        display_name TEXT,\n        rarity TEXT,\n        size TEXT,\n        level INTEGER DEFAULT 1,\n        exp INTEGER DEFAULT 0,\n        damage_multiplier REAL DEFAULT 1.0,\n        coin_multiplier REAL DEFAULT 1.0,\n        gem_multiplier REAL DEFAULT 1.0,\n        skin TEXT,\n        equipped INTEGER DEFAULT 0\n    )\n");
            st.executeUpdate("    CREATE TABLE IF NOT EXISTS plots (\n        owner TEXT PRIMARY KEY,\n        id INTEGER,\n        world TEXT,\n        x REAL,\n        y REAL,\n        z REAL,\n        size INTEGER DEFAULT 32,\n        pet_limit INTEGER DEFAULT 5,\n        egg_limit INTEGER DEFAULT 3\n    )\n");
            try {
                st.executeUpdate("ALTER TABLE pets ADD COLUMN size_int INTEGER DEFAULT 1");
            }
            catch (SQLException sQLException) {
                // empty catch block
            }
            try {
                st.executeUpdate("ALTER TABLE players ADD COLUMN unlocked_zones TEXT DEFAULT ''");
            }
            catch (SQLException sQLException) {
                // empty catch block
            }
            try {
                st.executeUpdate("ALTER TABLE players ADD COLUMN shop_levels TEXT DEFAULT ''");
            }
            catch (SQLException sQLException) {
                // empty catch block
            }
        }
    }

    public Connection getConnection() {
        return this.connection;
    }

    public <T> CompletableFuture<T> async(DatabaseTask<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.run(this.connection);
            }
            catch (SQLException e) {
                this.plugin.getLogger().severe("Database error: " + e.getMessage());
                return null;
            }
        }, this.executor);
    }

    public void close() {
        this.executor.shutdown();
        try {
            if (this.connection != null) {
                this.connection.close();
            }
        }
        catch (SQLException sQLException) {
            // empty catch block
        }
    }

    @FunctionalInterface
    public static interface DatabaseTask<T> {
        public T run(Connection var1) throws SQLException;
    }
}

