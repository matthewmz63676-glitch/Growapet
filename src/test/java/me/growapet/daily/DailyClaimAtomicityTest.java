package me.growapet.daily;

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
class DailyClaimAtomicityTest {
    @TempDir Path temporary;

    @Test void duplicateClaimCannotIssueDuplicateCredit() throws Exception {
        UUID player = UUID.randomUUID();
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + temporary.resolve("daily.db"))) {
            connection.createStatement().executeUpdate("CREATE TABLE players(uuid TEXT PRIMARY KEY,credits INTEGER NOT NULL DEFAULT 0)");
            connection.createStatement().executeUpdate("CREATE TABLE daily_claims(player_uuid TEXT PRIMARY KEY,last_claim_at INTEGER NOT NULL,claim_count INTEGER NOT NULL DEFAULT 0)");
            try (var insert = connection.prepareStatement("INSERT INTO players(uuid) VALUES(?)")) { insert.setString(1, player.toString()); insert.executeUpdate(); }
            long now = System.currentTimeMillis();
            assertTrue(DailyManager.claimOnce(connection, player, now));
            assertFalse(DailyManager.claimOnce(connection, player, now + 1));
            try (var row = connection.createStatement().executeQuery("SELECT credits FROM players")) { assertTrue(row.next()); assertEquals(1, row.getLong(1)); }
            assertTrue(DailyManager.claimOnce(connection, player, now + DailyManager.COOLDOWN_MILLIS));
            try (var row = connection.createStatement().executeQuery("SELECT credits FROM players")) { assertTrue(row.next()); assertEquals(2, row.getLong(1)); }
        }
    }
}
