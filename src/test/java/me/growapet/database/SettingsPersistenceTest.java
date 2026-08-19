package me.growapet.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("database")
@Tag("regression")
final class SettingsPersistenceTest {
    @TempDir Path temporary;
    @Test void booleanPreferencesUpsertAndSurviveReopen() throws Exception {
        Path file=temporary.resolve("database.db");Class.forName("org.sqlite.JDBC");
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+file)){for(Migration migration:Migrations.all())migration.apply(connection);try(var statement=connection.prepareStatement("INSERT INTO settings(player_uuid,setting_key,setting_value)VALUES(?,?,?) ON CONFLICT(player_uuid,setting_key)DO UPDATE SET setting_value=excluded.setting_value")){statement.setString(1,"player");statement.setString(2,"actionbar");statement.setString(3,"false");statement.executeUpdate();statement.setString(2,"trade_requests");statement.setString(3,"false");statement.executeUpdate();}}
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+file);var rows=connection.createStatement().executeQuery("SELECT setting_key,setting_value FROM settings WHERE player_uuid='player' ORDER BY setting_key")){rows.next();assertEquals("actionbar",rows.getString(1));assertEquals("false",rows.getString(2));rows.next();assertEquals("trade_requests",rows.getString(1));assertEquals("false",rows.getString(2));}
    }
}
