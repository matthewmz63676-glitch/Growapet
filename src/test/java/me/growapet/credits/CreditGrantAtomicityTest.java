package me.growapet.credits;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("database")
@Tag("regression")
class CreditGrantAtomicityTest {
    @TempDir Path temporary;

    @Test void receiptCanOnlyGrantOnce() throws Exception {
        UUID player = UUID.randomUUID();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + temporary.resolve("credits.db"))) {
            connection.createStatement().executeUpdate("CREATE TABLE players(uuid TEXT PRIMARY KEY,credits INTEGER NOT NULL DEFAULT 0)");
            connection.createStatement().executeUpdate("CREATE TABLE credit_grants(receipt_id TEXT PRIMARY KEY,player_uuid TEXT NOT NULL,credits INTEGER NOT NULL,source TEXT NOT NULL,granted_at INTEGER NOT NULL)");
            try (var insert = connection.prepareStatement("INSERT INTO players(uuid) VALUES(?)")) { insert.setString(1, player.toString()); insert.executeUpdate(); }
            assertTrue(CreditGrantService.grantOnce(connection, "web-42", player, 100, "WEBSTORE", 1));
            assertFalse(CreditGrantService.grantOnce(connection, "web-42", player, 100, "WEBSTORE", 2));
            try (var row = connection.createStatement().executeQuery("SELECT credits FROM players")) { assertTrue(row.next()); assertEquals(100, row.getLong(1)); }
        }
    }
}
