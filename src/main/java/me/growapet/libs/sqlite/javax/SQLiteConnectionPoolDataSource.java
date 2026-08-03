/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite.javax;

import java.sql.SQLException;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.PooledConnection;
import me.growapet.libs.sqlite.SQLiteConfig;
import me.growapet.libs.sqlite.SQLiteDataSource;
import me.growapet.libs.sqlite.javax.SQLitePooledConnection;

public class SQLiteConnectionPoolDataSource
extends SQLiteDataSource
implements ConnectionPoolDataSource {
    public SQLiteConnectionPoolDataSource() {
    }

    public SQLiteConnectionPoolDataSource(SQLiteConfig config) {
        super(config);
    }

    @Override
    public PooledConnection getPooledConnection() throws SQLException {
        return this.getPooledConnection(null, null);
    }

    @Override
    public PooledConnection getPooledConnection(String user, String password) throws SQLException {
        return new SQLitePooledConnection(this.getConnection(user, password));
    }
}

