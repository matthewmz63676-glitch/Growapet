package me.growapet.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("database")
@Tag("regression")
final class QuestClaimAtomicityTest {
    @TempDir Path temporary;
    @Test void repeatedClaimUpdatesAwardExactlyOnce() throws Exception {
        Class.forName("org.sqlite.JDBC");
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+temporary.resolve("database.db"))){
            for(Migration migration:Migrations.all())migration.apply(connection);
            connection.createStatement().executeUpdate("INSERT INTO players(uuid,name,coins)VALUES('player','Player',0)");
            connection.createStatement().executeUpdate("INSERT INTO quest_progress(player_uuid,quest_key,period_key,progress,claimed)VALUES('player','daily:test','2026-08-03',10,0)");
            assertEquals(1,claim(connection));assertEquals(0,claim(connection));
            try(var row=connection.createStatement().executeQuery("SELECT coins FROM players WHERE uuid='player'")){row.next();assertEquals(100,row.getLong(1));}
        }
    }
    private static int claim(java.sql.Connection connection)throws Exception{connection.setAutoCommit(false);try(var update=connection.prepareStatement("UPDATE quest_progress SET claimed=1 WHERE player_uuid='player' AND quest_key='daily:test' AND period_key='2026-08-03' AND claimed=0 AND progress>=10")){int won=update.executeUpdate();if(won==1)connection.createStatement().executeUpdate("UPDATE players SET coins=coins+100 WHERE uuid='player'");connection.commit();return won;}finally{connection.setAutoCommit(true);}}
}
