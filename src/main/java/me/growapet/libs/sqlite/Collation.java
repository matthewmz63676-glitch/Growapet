/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite;

import java.sql.Connection;
import java.sql.SQLException;
import me.growapet.libs.sqlite.SQLiteConnection;
import me.growapet.libs.sqlite.core.DB;

public abstract class Collation {
    private SQLiteConnection conn;
    private DB db;

    public static final void create(Connection conn, String name, Collation f) throws SQLException {
        if (conn == null || !(conn instanceof SQLiteConnection)) {
            throw new SQLException("connection must be to an SQLite db");
        }
        if (conn.isClosed()) {
            throw new SQLException("connection closed");
        }
        f.conn = (SQLiteConnection)conn;
        f.db = f.conn.getDatabase();
        if (f.db.create_collation(name, f) != 0) {
            throw new SQLException("error creating collation");
        }
    }

    public static final void destroy(Connection conn, String name) throws SQLException {
        if (conn == null || !(conn instanceof SQLiteConnection)) {
            throw new SQLException("connection must be to an SQLite db");
        }
        ((SQLiteConnection)conn).getDatabase().destroy_collation(name);
    }

    protected abstract int xCompare(String var1, String var2);
}

