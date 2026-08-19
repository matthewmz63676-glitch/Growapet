package me.growapet.trade;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("database")
@Tag("regression")
class TradeEscrowRecoveryTest {
    @TempDir Path temporary;

    @Test
    void abandonedEscrowIsReturnedExactlyOnce() throws Exception {
        Class.forName("org.sqlite.JDBC");
        UUID trade = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID pet = UUID.randomUUID();
        UUID egg = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + temporary.resolve("trade.db"))) {
            createSchema(connection);
            connection.createStatement().executeUpdate("INSERT INTO players(uuid,coins,gems,trades) VALUES('" + owner + "',90,45,0),('" + other + "',0,0,0)");
            connection.createStatement().executeUpdate("INSERT INTO pets(uuid,owner,equipped,trade_lock_id) VALUES('" + pet + "','" + owner + "',0,'" + trade + "')");
            connection.createStatement().executeUpdate("INSERT INTO trade_sessions(trade_id,player_one,player_two,state,created_at,updated_at) VALUES('" + trade + "','" + owner + "','" + other + "','OPEN',1,1)");
            connection.createStatement().executeUpdate("INSERT INTO trade_escrow VALUES('" + trade + "','" + owner + "','COINS','coins',10,NULL,'ACTIVE')");
            connection.createStatement().executeUpdate("INSERT INTO trade_escrow VALUES('" + trade + "','" + owner + "','GEMS','gems',5,NULL,'ACTIVE')");
            connection.createStatement().executeUpdate("INSERT INTO trade_escrow VALUES('" + trade + "','" + owner + "','PET','" + pet + "',1,NULL,'ACTIVE')");
            connection.createStatement().executeUpdate("INSERT INTO trade_escrow VALUES('" + trade + "','" + owner + "','EGG','" + egg + "',1,'encoded-item','PENDING')");

            TradeManager.recoverAbandoned(connection);
            TradeManager.recoverAbandoned(connection);

            try (var row = connection.createStatement().executeQuery("SELECT coins,gems FROM players WHERE uuid='" + owner + "'")) {
                assertTrue(row.next());
                assertEquals(100, row.getLong(1));
                assertEquals(50, row.getLong(2));
            }
            try (var row = connection.createStatement().executeQuery("SELECT owner,trade_lock_id FROM pets WHERE uuid='" + pet + "'")) {
                assertTrue(row.next());
                assertEquals(owner.toString(), row.getString(1));
                assertEquals(null, row.getString(2));
            }
            assertEquals(0, scalar(connection, "SELECT COUNT(*) FROM trade_escrow"));
            assertEquals(1, scalar(connection, "SELECT COUNT(*) FROM item_deliveries"));
            assertEquals("RECOVERED", text(connection, "SELECT state FROM trade_sessions WHERE trade_id='" + trade + "'"));
        }
    }

    @Test
    void requestCooldownIsStrictAndPersisted() throws Exception {
        Class.forName("org.sqlite.JDBC");
        UUID player = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + temporary.resolve("cooldown.db"))) {
            connection.createStatement().executeUpdate("CREATE TABLE trade_request_cooldowns(player_uuid TEXT PRIMARY KEY,last_request_at INTEGER NOT NULL)");
            assertTrue(TradeManager.recordRequestCooldown(connection, player, 100_000L, 10_000L));
            assertFalse(TradeManager.recordRequestCooldown(connection, player, 109_999L, 10_000L));
            assertTrue(TradeManager.recordRequestCooldown(connection, player, 110_000L, 10_000L));
        }
    }

    @Test
    void confirmedSettlementTransfersEveryAssetExactlyOnce() throws Exception {
        Class.forName("org.sqlite.JDBC");UUID trade=UUID.randomUUID(),one=UUID.randomUUID(),two=UUID.randomUUID(),pet=UUID.randomUUID(),egg=UUID.randomUUID();
        try(Connection connection=DriverManager.getConnection("jdbc:sqlite:"+temporary.resolve("settle.db"))){createSchema(connection);connection.createStatement().executeUpdate("CREATE TABLE trades(trade_id TEXT PRIMARY KEY,player_one TEXT,player_two TEXT,state TEXT,payload TEXT,created_at INTEGER,completed_at INTEGER)");connection.createStatement().executeUpdate("INSERT INTO players(uuid,coins,gems,trades)VALUES('"+one+"',90,45,0),('"+two+"',20,10,0)");connection.createStatement().executeUpdate("INSERT INTO pets(uuid,owner,equipped,trade_lock_id)VALUES('"+pet+"','"+one+"',0,'"+trade+"')");connection.createStatement().executeUpdate("INSERT INTO trade_sessions(trade_id,player_one,player_two,state,first_confirmed,second_confirmed,created_at,updated_at)VALUES('"+trade+"','"+one+"','"+two+"','COMMITTING',1,1,1,1)");connection.createStatement().executeUpdate("INSERT INTO trade_escrow VALUES('"+trade+"','"+one+"','COINS','coins',10,NULL,'ACTIVE'),('"+trade+"','"+two+"','GEMS','gems',5,NULL,'ACTIVE'),('"+trade+"','"+one+"','PET','"+pet+"',1,NULL,'ACTIVE'),('"+trade+"','"+two+"','EGG','"+egg+"',1,'encoded','ACTIVE')");
            TradeManager.settleEscrow(connection,trade,one,two);
            try(var row=connection.createStatement().executeQuery("SELECT coins,gems,trades FROM players WHERE uuid='"+one+"'")){assertTrue(row.next());assertEquals(90,row.getLong(1));assertEquals(50,row.getLong(2));assertEquals(1,row.getLong(3));}
            try(var row=connection.createStatement().executeQuery("SELECT coins,gems,trades FROM players WHERE uuid='"+two+"'")){assertTrue(row.next());assertEquals(30,row.getLong(1));assertEquals(10,row.getLong(2));assertEquals(1,row.getLong(3));}
            assertEquals(two.toString(),text(connection,"SELECT owner FROM pets WHERE uuid='"+pet+"'"));assertEquals(1,scalar(connection,"SELECT COUNT(*) FROM item_deliveries WHERE player_uuid='"+one+"'"));assertEquals(0,scalar(connection,"SELECT COUNT(*) FROM trade_escrow"));assertEquals("COMPLETED",text(connection,"SELECT state FROM trade_sessions WHERE trade_id='"+trade+"'"));assertEquals(1,scalar(connection,"SELECT COUNT(*) FROM trades"));
            assertThrows(Exception.class,()->TradeManager.settleEscrow(connection,trade,one,two));assertEquals(1,scalar(connection,"SELECT COUNT(*) FROM trades"));
        }
    }

    private static void createSchema(Connection connection) throws Exception {
        connection.createStatement().executeUpdate("CREATE TABLE players(uuid TEXT PRIMARY KEY,coins INTEGER NOT NULL,gems INTEGER NOT NULL,trades INTEGER NOT NULL)");
        connection.createStatement().executeUpdate("CREATE TABLE pets(uuid TEXT PRIMARY KEY,owner TEXT NOT NULL,equipped INTEGER NOT NULL,entity_uuid TEXT,trade_lock_id TEXT)");
        connection.createStatement().executeUpdate("CREATE TABLE trade_sessions(trade_id TEXT PRIMARY KEY,player_one TEXT,player_two TEXT,state TEXT,first_confirmed INTEGER DEFAULT 0,second_confirmed INTEGER DEFAULT 0,created_at INTEGER,updated_at INTEGER)");
        connection.createStatement().executeUpdate("CREATE TABLE trade_escrow(trade_id TEXT,owner_uuid TEXT,asset_type TEXT,asset_id TEXT,amount INTEGER,payload TEXT,state TEXT,PRIMARY KEY(trade_id,owner_uuid,asset_type,asset_id))");
        connection.createStatement().executeUpdate("CREATE TABLE item_deliveries(id TEXT PRIMARY KEY,player_uuid TEXT,item_type TEXT,item_data TEXT,created_at INTEGER)");
    }

    private static long scalar(Connection connection, String sql) throws Exception {
        try (var row = connection.createStatement().executeQuery(sql)) { row.next(); return row.getLong(1); }
    }

    private static String text(Connection connection, String sql) throws Exception {
        try (var row = connection.createStatement().executeQuery(sql)) { row.next(); return row.getString(1); }
    }
}
