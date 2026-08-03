/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite.jdbc3;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.JDBCType;
import java.sql.ParameterMetaData;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import me.growapet.libs.sqlite.SQLiteConnection;
import me.growapet.libs.sqlite.core.CorePreparedStatement;
import me.growapet.libs.sqlite.core.DB;
import me.growapet.libs.sqlite.jdbc3.JDBC3Connection;

public abstract class JDBC3PreparedStatement
extends CorePreparedStatement {
    protected JDBC3PreparedStatement(SQLiteConnection conn, String sql) throws SQLException {
        super(conn, sql);
    }

    public void clearParameters() throws SQLException {
        this.checkOpen();
        this.pointer.safeRunConsume(DB::clear_bindings);
        if (this.batch != null) {
            for (int i = this.batchPos; i < this.batchPos + this.paramCount; ++i) {
                this.batch[i] = null;
            }
        }
    }

    public boolean execute() throws SQLException {
        this.checkOpen();
        this.rs.close();
        this.pointer.safeRunConsume(DB::reset);
        this.exhaustedResults = false;
        if (this.conn instanceof JDBC3Connection) {
            ((JDBC3Connection)this.conn).tryEnforceTransactionMode();
        }
        return this.withConnectionTimeout(() -> {
            boolean success = false;
            try {
                Object object = this.conn;
                synchronized (object) {
                    this.resultsWaiting = this.conn.getDatabase().execute(this, this.batch);
                    this.updateGeneratedKeys();
                    success = true;
                    this.updateCount = this.getDatabase().changes();
                }
                object = 0 != this.columnCount;
                return object;
            }
            finally {
                if (!success && !this.pointer.isClosed()) {
                    this.pointer.safeRunConsume(DB::reset);
                }
            }
        });
    }

    public ResultSet executeQuery() throws SQLException {
        this.checkOpen();
        if (this.columnCount == 0) {
            throw new SQLException("Query does not return results");
        }
        this.rs.close();
        this.pointer.safeRunConsume(DB::reset);
        this.exhaustedResults = false;
        if (this.conn instanceof JDBC3Connection) {
            ((JDBC3Connection)this.conn).tryEnforceTransactionMode();
        }
        return this.withConnectionTimeout(() -> {
            boolean success = false;
            try {
                this.resultsWaiting = this.conn.getDatabase().execute(this, this.batch);
                success = true;
            }
            finally {
                if (!success && !this.pointer.isClosed()) {
                    this.pointer.safeRunInt(DB::reset);
                }
            }
            return this.getResultSet();
        });
    }

    public int executeUpdate() throws SQLException {
        return (int)this.executeLargeUpdate();
    }

    public long executeLargeUpdate() throws SQLException {
        this.checkOpen();
        if (this.columnCount != 0) {
            throw new SQLException("Query returns results");
        }
        this.rs.close();
        this.pointer.safeRunConsume(DB::reset);
        this.exhaustedResults = false;
        if (this.conn instanceof JDBC3Connection) {
            ((JDBC3Connection)this.conn).tryEnforceTransactionMode();
        }
        return this.withConnectionTimeout(() -> {
            SQLiteConnection sQLiteConnection = this.conn;
            synchronized (sQLiteConnection) {
                long rc = this.conn.getDatabase().executeUpdate(this, this.batch);
                this.updateGeneratedKeys();
                return rc;
            }
        });
    }

    public void addBatch() throws SQLException {
        this.checkOpen();
        this.batchPos += this.paramCount;
        ++this.batchQueryCount;
        if (this.batch == null) {
            this.batch = new Object[this.paramCount];
        }
        if (this.batchPos + this.paramCount > this.batch.length) {
            Object[] nb = new Object[this.batch.length * 2];
            System.arraycopy(this.batch, 0, nb, 0, this.batch.length);
            this.batch = nb;
        }
        System.arraycopy(this.batch, this.batchPos - this.paramCount, this.batch, this.batchPos, this.paramCount);
    }

    public ParameterMetaData getParameterMetaData() {
        return (ParameterMetaData)((Object)this);
    }

    public int getParameterCount() throws SQLException {
        this.checkOpen();
        return this.paramCount;
    }

    public String getParameterClassName(int param) throws SQLException {
        this.checkOpen();
        return "java.lang.String";
    }

    public String getParameterTypeName(int pos) throws SQLException {
        this.checkIndex(pos);
        return JDBCType.valueOf(this.getParameterType(pos)).getName();
    }

    public int getParameterType(int pos) throws SQLException {
        this.checkIndex(pos);
        Object paramValue = this.batch[pos - 1];
        if (paramValue == null) {
            return 0;
        }
        if (paramValue instanceof Integer || paramValue instanceof Short || paramValue instanceof Boolean) {
            return 4;
        }
        if (paramValue instanceof Long) {
            return -5;
        }
        if (paramValue instanceof Double || paramValue instanceof Float) {
            return 7;
        }
        return 12;
    }

    public int getParameterMode(int pos) {
        return 1;
    }

    public int getPrecision(int pos) {
        return 0;
    }

    public int getScale(int pos) {
        return 0;
    }

    public int isNullable(int pos) {
        return 1;
    }

    public boolean isSigned(int pos) {
        return true;
    }

    public Statement getStatement() {
        return this;
    }

    public void setBigDecimal(int pos, BigDecimal value) throws SQLException {
        this.batch(pos, value == null ? null : value.toString());
    }

    private byte[] readBytes(InputStream istream, int length) throws SQLException {
        if (length < 0) {
            throw new SQLException("Error reading stream. Length should be non-negative");
        }
        byte[] bytes = new byte[length];
        try {
            int bytesRead;
            for (int totalBytesRead = 0; totalBytesRead < length; totalBytesRead += bytesRead) {
                bytesRead = istream.read(bytes, totalBytesRead, length - totalBytesRead);
                if (bytesRead != -1) continue;
                throw new IOException("End of stream has been reached");
            }
            return bytes;
        }
        catch (IOException cause) {
            SQLException exception = new SQLException("Error reading stream");
            exception.initCause(cause);
            throw exception;
        }
    }

    public void setBinaryStream(int pos, InputStream istream, int length) throws SQLException {
        if (istream == null && length == 0) {
            this.setBytes(pos, null);
        }
        this.setBytes(pos, this.readBytes(istream, length));
    }

    public void setAsciiStream(int pos, InputStream istream, int length) throws SQLException {
        this.setUnicodeStream(pos, istream, length);
    }

    public void setUnicodeStream(int pos, InputStream istream, int length) throws SQLException {
        if (istream == null && length == 0) {
            this.setString(pos, null);
        }
        try {
            this.setString(pos, new String(this.readBytes(istream, length), "UTF-8"));
        }
        catch (UnsupportedEncodingException e) {
            SQLException exception = new SQLException("UTF-8 is not supported");
            exception.initCause(e);
            throw exception;
        }
    }

    public void setBoolean(int pos, boolean value) throws SQLException {
        this.setInt(pos, value ? 1 : 0);
    }

    public void setByte(int pos, byte value) throws SQLException {
        this.setInt(pos, value);
    }

    public void setBytes(int pos, byte[] value) throws SQLException {
        this.batch(pos, value);
    }

    public void setDouble(int pos, double value) throws SQLException {
        this.batch(pos, new Double(value));
    }

    public void setFloat(int pos, float value) throws SQLException {
        this.batch(pos, new Float(value));
    }

    public void setInt(int pos, int value) throws SQLException {
        this.batch(pos, new Integer(value));
    }

    public void setLong(int pos, long value) throws SQLException {
        this.batch(pos, new Long(value));
    }

    public void setNull(int pos, int u1) throws SQLException {
        this.setNull(pos, u1, null);
    }

    public void setNull(int pos, int u1, String u2) throws SQLException {
        this.batch(pos, null);
    }

    public void setObject(int pos, Object value) throws SQLException {
        if (value == null) {
            this.batch(pos, null);
        } else if (value instanceof java.util.Date) {
            this.setDateByMilliseconds(pos, ((java.util.Date)value).getTime(), Calendar.getInstance());
        } else if (value instanceof Long) {
            this.batch(pos, value);
        } else if (value instanceof Integer) {
            this.batch(pos, value);
        } else if (value instanceof Short) {
            this.batch(pos, new Integer(((Short)value).intValue()));
        } else if (value instanceof Float) {
            this.batch(pos, value);
        } else if (value instanceof Double) {
            this.batch(pos, value);
        } else if (value instanceof Boolean) {
            this.setBoolean(pos, (Boolean)value);
        } else if (value instanceof byte[]) {
            this.batch(pos, value);
        } else if (value instanceof BigDecimal) {
            this.setBigDecimal(pos, (BigDecimal)value);
        } else {
            this.batch(pos, value.toString());
        }
    }

    public void setObject(int p, Object v, int t) throws SQLException {
        this.setObject(p, v);
    }

    public void setObject(int p, Object v, int t, int s) throws SQLException {
        this.setObject(p, v);
    }

    public void setShort(int pos, short value) throws SQLException {
        this.setInt(pos, value);
    }

    public void setString(int pos, String value) throws SQLException {
        this.batch(pos, value);
    }

    public void setCharacterStream(int pos, Reader reader, int length) throws SQLException {
        try {
            int cnt;
            StringBuffer sb = new StringBuffer();
            char[] cbuf = new char[8192];
            while ((cnt = reader.read(cbuf)) > 0) {
                sb.append(cbuf, 0, cnt);
            }
            this.setString(pos, sb.toString());
        }
        catch (IOException e) {
            throw new SQLException("Cannot read from character stream, exception message: " + e.getMessage());
        }
    }

    public void setDate(int pos, Date x) throws SQLException {
        this.setDate(pos, x, Calendar.getInstance());
    }

    public void setDate(int pos, Date x, Calendar cal) throws SQLException {
        if (x == null) {
            this.setObject(pos, null);
        } else {
            this.setDateByMilliseconds(pos, x.getTime(), cal);
        }
    }

    public void setTime(int pos, Time x) throws SQLException {
        this.setTime(pos, x, Calendar.getInstance());
    }

    public void setTime(int pos, Time x, Calendar cal) throws SQLException {
        if (x == null) {
            this.setObject(pos, null);
        } else {
            this.setDateByMilliseconds(pos, x.getTime(), cal);
        }
    }

    public void setTimestamp(int pos, Timestamp x) throws SQLException {
        this.setTimestamp(pos, x, Calendar.getInstance());
    }

    public void setTimestamp(int pos, Timestamp x, Calendar cal) throws SQLException {
        if (x == null) {
            this.setObject(pos, null);
        } else {
            this.setDateByMilliseconds(pos, x.getTime(), cal);
        }
    }

    public ResultSetMetaData getMetaData() throws SQLException {
        this.checkOpen();
        return (ResultSetMetaData)((Object)this.rs);
    }

    @Override
    protected SQLException unsupported() {
        return new SQLFeatureNotSupportedException("not implemented by SQLite JDBC driver");
    }

    protected SQLException invalid() {
        return new SQLException("method cannot be called on a PreparedStatement");
    }

    public void setArray(int i, Array x) throws SQLException {
        throw this.unsupported();
    }

    public void setBlob(int i, Blob x) throws SQLException {
        throw this.unsupported();
    }

    public void setClob(int i, Clob x) throws SQLException {
        throw this.unsupported();
    }

    public void setRef(int i, Ref x) throws SQLException {
        throw this.unsupported();
    }

    public void setURL(int pos, URL x) throws SQLException {
        throw this.unsupported();
    }

    @Override
    public boolean execute(String sql) throws SQLException {
        throw this.invalid();
    }

    @Override
    public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
        throw this.invalid();
    }

    @Override
    public boolean execute(String sql, int[] colinds) throws SQLException {
        throw this.invalid();
    }

    @Override
    public boolean execute(String sql, String[] colnames) throws SQLException {
        throw this.invalid();
    }

    @Override
    public int executeUpdate(String sql) throws SQLException {
        throw this.invalid();
    }

    @Override
    public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        throw this.invalid();
    }

    @Override
    public int executeUpdate(String sql, int[] colinds) throws SQLException {
        throw this.invalid();
    }

    @Override
    public int executeUpdate(String sql, String[] cols) throws SQLException {
        throw this.invalid();
    }

    @Override
    public long executeLargeUpdate(String sql) throws SQLException {
        throw this.invalid();
    }

    @Override
    public long executeLargeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
        throw this.invalid();
    }

    @Override
    public long executeLargeUpdate(String sql, int[] colinds) throws SQLException {
        throw this.invalid();
    }

    @Override
    public long executeLargeUpdate(String sql, String[] cols) throws SQLException {
        throw this.invalid();
    }

    @Override
    public ResultSet executeQuery(String sql) throws SQLException {
        throw this.invalid();
    }

    @Override
    public void addBatch(String sql) throws SQLException {
        throw this.invalid();
    }
}

