package me.growapet.tutorial;

import me.growapet.database.Migration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("database")
final class TutorialProgressAtomicityTest {
    @TempDir Path temporary;

    @Test void theSameGameplaySignalCannotAdvanceAStageTwice() throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + temporary.resolve("tutorial.db"))) {
            for (Migration migration : migrations()) migration.apply(connection);
            connection.createStatement().executeUpdate("INSERT INTO tutorial_progress(player_uuid,stage,updated_at) VALUES('player','MOB',0)");
            assertEquals(1, advance(connection, "MOB", "SHOP"));
            assertEquals(0, advance(connection, "MOB", "SHOP"));
            try (var row = connection.createStatement().executeQuery("SELECT stage FROM tutorial_progress WHERE player_uuid='player'")) {
                row.next(); assertEquals("SHOP", row.getString(1));
            }
        }
    }

    private static int advance(java.sql.Connection connection, String expected, String next) throws Exception {
        try (var statement = connection.prepareStatement("UPDATE tutorial_progress SET stage=? WHERE player_uuid='player' AND stage=?")) {
            statement.setString(1, next); statement.setString(2, expected); return statement.executeUpdate();
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Migration> migrations() throws Exception {
        Class<?> type = Class.forName("me.growapet.database.Migrations");
        var method = type.getDeclaredMethod("all"); method.setAccessible(true);
        return (java.util.List<Migration>) method.invoke(null);
    }
}
