/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite.core;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Pattern;
import me.growapet.libs.sqlite.SQLiteConnection;
import me.growapet.libs.sqlite.SQLiteConnectionConfig;
import me.growapet.libs.sqlite.core.Codes;
import me.growapet.libs.sqlite.core.CoreResultSet;
import me.growapet.libs.sqlite.core.DB;
import me.growapet.libs.sqlite.core.SafeStmtPtr;
import me.growapet.libs.sqlite.jdbc3.JDBC3Connection;
import me.growapet.libs.sqlite.jdbc4.JDBC4ResultSet;

public abstract class CoreStatement
implements Codes {
    public final SQLiteConnection conn;
    protected final CoreResultSet rs;
    public SafeStmtPtr pointer;
    protected String sql = null;
    protected int batchPos;
    protected Object[] batch = null;
    protected boolean resultsWaiting = false;
    private Statement generatedKeysStat = null;
    private ResultSet generatedKeysRs = null;
    private static final Pattern INSERT_PATTERN = Pattern.compile("^\\s*(?:with\\s+.+\\(.+?\\))*\\s*(?:insert|replace)\\s*", 34);

    protected CoreStatement(SQLiteConnection c) {
        this.conn = c;
        this.rs = new JDBC4ResultSet(this);
    }

    public DB getDatabase() {
        return this.conn.getDatabase();
    }

    public SQLiteConnectionConfig getConnectionConfig() {
        return this.conn.getConnectionConfig();
    }

    protected final void checkOpen() throws SQLException {
        if (this.pointer.isClosed()) {
            throw new SQLException("statement is not executing");
        }
    }

    boolean isOpen() throws SQLException {
        return !this.pointer.isClosed();
    }

    protected boolean exec() throws SQLException {
        if (this.sql == null) {
            throw new SQLException("SQLiteJDBC internal error: sql==null");
        }
        if (this.rs.isOpen()) {
            throw new SQLException("SQLite JDBC internal error: rs.isOpen() on exec.");
        }
        if (this.conn instanceof JDBC3Connection) {
            ((JDBC3Connection)this.conn).tryEnforceTransactionMode();
        }
        boolean success = false;
        boolean rc = false;
        try {
            rc = this.conn.getDatabase().execute(this, null);
            success = true;
        }
        finally {
            this.notifyFirstStatementExecuted();
            this.resultsWaiting = rc;
            if (!success) {
                this.pointer.close();
            }
        }
        return this.pointer.safeRunInt(DB::column_count) != 0;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    protected boolean exec(String sql) throws SQLException {
        if (sql == null) {
            throw new SQLException("SQLiteJDBC internal error: sql==null");
        }
        if (this.rs.isOpen()) {
            throw new SQLException("SQLite JDBC internal error: rs.isOpen() on exec.");
        }
        if (this.conn instanceof JDBC3Connection) {
            ((JDBC3Connection)this.conn).tryEnforceTransactionMode();
        }
        boolean rc = false;
        boolean success = false;
        try {
            rc = this.conn.getDatabase().execute(sql, this.conn.getAutoCommit());
            success = true;
        }
        finally {
            this.notifyFirstStatementExecuted();
            this.resultsWaiting = rc;
            if (!success && this.pointer != null) {
                this.pointer.close();
            }
        }
        return this.pointer.safeRunInt(DB::column_count) != 0;
    }

    protected void internalClose() throws SQLException {
        if (this.pointer != null && !this.pointer.isClosed()) {
            if (this.conn.isClosed()) {
                throw DB.newSQLException(1, "Connection is closed");
            }
            this.rs.close();
            this.batch = null;
            this.batchPos = 0;
            int resp = this.pointer.close();
            if (resp != 0 && resp != 21) {
                this.conn.getDatabase().throwex(resp);
            }
        }
    }

    protected void notifyFirstStatementExecuted() {
        this.conn.setFirstStatementExecuted(true);
    }

    public abstract ResultSet executeQuery(String var1, boolean var2) throws SQLException;

    protected void checkIndex(int index) throws SQLException {
        if (this.batch == null) {
            throw new SQLException("No parameter has been set yet");
        }
        if (index < 1 || index > this.batch.length) {
            throw new SQLException("Parameter index is invalid");
        }
    }

    protected void clearGeneratedKeys() throws SQLException {
        if (this.generatedKeysRs != null && !this.generatedKeysRs.isClosed()) {
            this.generatedKeysRs.close();
        }
        this.generatedKeysRs = null;
        if (this.generatedKeysStat != null && !this.generatedKeysStat.isClosed()) {
            this.generatedKeysStat.close();
        }
        this.generatedKeysStat = null;
    }

    public void updateGeneratedKeys() throws SQLException {
        if (this.conn.getConnectionConfig().isGetGeneratedKeys()) {
            this.clearGeneratedKeys();
            if (this.sql != null && INSERT_PATTERN.matcher(this.sql).find()) {
                this.generatedKeysStat = this.conn.createStatement();
                this.generatedKeysRs = this.generatedKeysStat.executeQuery("SELECT last_insert_rowid();");
            }
        }
    }

    public ResultSet getGeneratedKeys() throws SQLException {
        if (this.generatedKeysRs == null) {
            this.generatedKeysStat = this.conn.createStatement();
            this.generatedKeysRs = this.generatedKeysStat.executeQuery("SELECT 1 WHERE 1 = 2;");
        }
        return this.generatedKeysRs;
    }
}

