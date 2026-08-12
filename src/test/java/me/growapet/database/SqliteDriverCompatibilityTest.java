package me.growapet.database;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteDriverCompatibilityTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void opensAndPreservesRepresentativeLegacyPlayerData() throws Exception {
        Class.forName("org.sqlite.JDBC");
        Path databaseFile = temporaryDirectory.resolve("database.db");
        String jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();

        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE players (uuid TEXT PRIMARY KEY, name TEXT, coins INTEGER DEFAULT 0, gems INTEGER DEFAULT 0, credits INTEGER DEFAULT 0, level INTEGER DEFAULT 1, exp INTEGER DEFAULT 0)");
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO players (uuid, name, coins, gems, credits, level, exp) VALUES (?, ?, ?, ?, ?, ?, ?)") ) {
                insert.setString(1, "d1fd7667-24f7-47aa-ae41-dbe6b0f3d7b8");
                insert.setString(2, "LegacyPlayer");
                insert.setLong(3, 1250L);
                insert.setLong(4, 25L);
                insert.setLong(5, 3L);
                insert.setInt(6, 8);
                insert.setLong(7, 42L);
                insert.executeUpdate();
            }
        }

        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             PreparedStatement select = connection.prepareStatement("SELECT name, coins, gems, credits, level, exp FROM players WHERE uuid = ?")) {
            select.setString(1, "d1fd7667-24f7-47aa-ae41-dbe6b0f3d7b8");
            try (ResultSet result = select.executeQuery()) {
                result.next();
                assertEquals("LegacyPlayer", result.getString("name"));
                assertEquals(1250L, result.getLong("coins"));
                assertEquals(25L, result.getLong("gems"));
                assertEquals(3L, result.getLong("credits"));
                assertEquals(8, result.getInt("level"));
                assertEquals(42L, result.getLong("exp"));
            }
        }
    }
}
