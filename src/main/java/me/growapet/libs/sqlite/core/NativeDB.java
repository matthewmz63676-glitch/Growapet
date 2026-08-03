/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite.core;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import me.growapet.libs.sqlite.BusyHandler;
import me.growapet.libs.sqlite.Collation;
import me.growapet.libs.sqlite.Function;
import me.growapet.libs.sqlite.ProgressHandler;
import me.growapet.libs.sqlite.SQLiteConfig;
import me.growapet.libs.sqlite.SQLiteJDBCLoader;
import me.growapet.libs.sqlite.core.DB;
import me.growapet.libs.sqlite.core.SafeStmtPtr;
import me.growapet.libs.sqlite.util.Logger;
import me.growapet.libs.sqlite.util.LoggerFactory;

public final class NativeDB
extends DB {
    private static final Logger logger = LoggerFactory.getLogger(NativeDB.class);
    private static final int DEFAULT_BACKUP_BUSY_SLEEP_TIME_MILLIS = 100;
    private static final int DEFAULT_BACKUP_NUM_BUSY_BEFORE_FAIL = 3;
    private static final int DEFAULT_PAGES_PER_BACKUP_STEP = 100;
    private long pointer = 0L;
    private static boolean isLoaded;
    private static boolean loadSucceeded;
    private long busyHandler = 0L;
    private long commitListener = 0L;
    private long updateListener = 0L;
    private long progressHandler;

    public NativeDB(String url, String fileName, SQLiteConfig config) throws SQLException {
        super(url, fileName, config);
    }

    public static boolean load() throws Exception {
        if (isLoaded) {
            return loadSucceeded;
        }
        try {
            loadSucceeded = SQLiteJDBCLoader.initialize();
        }
        finally {
            isLoaded = true;
        }
        return loadSucceeded;
    }

    @Override
    protected synchronized void _open(String file, int openFlags) throws SQLException {
        this._open_utf8(NativeDB.stringToUtf8ByteArray(file), openFlags);
    }

    synchronized native void _open_utf8(byte[] var1, int var2) throws SQLException;

    @Override
    protected synchronized native void _close() throws SQLException;

    @Override
    public synchronized int _exec(String sql) throws SQLException {
        if (logger.isTraceEnabled()) {
            logger.trace("DriverManager [{}] [SQLite EXEC] {}", Thread.currentThread().getName(), sql);
        }
        return this._exec_utf8(NativeDB.stringToUtf8ByteArray(sql));
    }

    synchronized native int _exec_utf8(byte[] var1) throws SQLException;

    @Override
    public synchronized native int shared_cache(boolean var1);

    @Override
    public synchronized native int enable_load_extension(boolean var1);

    @Override
    public native void interrupt();

    @Override
    public synchronized native void busy_timeout(int var1);

    @Override
    public synchronized native void busy_handler(BusyHandler var1);

    @Override
    protected synchronized SafeStmtPtr prepare(String sql) throws SQLException {
        if (logger.isTraceEnabled()) {
            logger.trace("DriverManager [{}] [SQLite EXEC] {}", Thread.currentThread().getName(), sql);
        }
        return new SafeStmtPtr(this, this.prepare_utf8(NativeDB.stringToUtf8ByteArray(sql)));
    }

    synchronized native long prepare_utf8(byte[] var1) throws SQLException;

    @Override
    synchronized String errmsg() {
        return NativeDB.utf8ByteBufferToString(this.errmsg_utf8());
    }

    synchronized native ByteBuffer errmsg_utf8();

    @Override
    public synchronized String libversion() {
        return NativeDB.utf8ByteBufferToString(this.libversion_utf8());
    }

    native ByteBuffer libversion_utf8();

    @Override
    public synchronized native long changes();

    @Override
    public synchronized native long total_changes();

    @Override
    protected synchronized native int finalize(long var1);

    @Override
    public synchronized native int step(long var1);

    @Override
    public synchronized native int reset(long var1);

    @Override
    public synchronized native int clear_bindings(long var1);

    @Override
    synchronized native int bind_parameter_count(long var1);

    @Override
    public synchronized native int column_count(long var1);

    @Override
    public synchronized native int column_type(long var1, int var3);

    @Override
    public synchronized String column_decltype(long stmt, int col) {
        return NativeDB.utf8ByteBufferToString(this.column_decltype_utf8(stmt, col));
    }

    synchronized native ByteBuffer column_decltype_utf8(long var1, int var3);

    @Override
    public synchronized String column_table_name(long stmt, int col) {
        return NativeDB.utf8ByteBufferToString(this.column_table_name_utf8(stmt, col));
    }

    synchronized native ByteBuffer column_table_name_utf8(long var1, int var3);

    @Override
    public synchronized String column_name(long stmt, int col) {
        return NativeDB.utf8ByteBufferToString(this.column_name_utf8(stmt, col));
    }

    synchronized native ByteBuffer column_name_utf8(long var1, int var3);

    @Override
    public synchronized String column_text(long stmt, int col) {
        return NativeDB.utf8ByteBufferToString(this.column_text_utf8(stmt, col));
    }

    synchronized native ByteBuffer column_text_utf8(long var1, int var3);

    @Override
    public synchronized native byte[] column_blob(long var1, int var3);

    @Override
    public synchronized native double column_double(long var1, int var3);

    @Override
    public synchronized native long column_long(long var1, int var3);

    @Override
    public synchronized native int column_int(long var1, int var3);

    @Override
    synchronized native int bind_null(long var1, int var3);

    @Override
    synchronized native int bind_int(long var1, int var3, int var4);

    @Override
    synchronized native int bind_long(long var1, int var3, long var4);

    @Override
    synchronized native int bind_double(long var1, int var3, double var4);

    @Override
    synchronized int bind_text(long stmt, int pos, String v) {
        return this.bind_text_utf8(stmt, pos, NativeDB.stringToUtf8ByteArray(v));
    }

    synchronized native int bind_text_utf8(long var1, int var3, byte[] var4);

    @Override
    synchronized native int bind_blob(long var1, int var3, byte[] var4);

    @Override
    public synchronized native void result_null(long var1);

    @Override
    public synchronized void result_text(long context, String val) {
        this.result_text_utf8(context, NativeDB.stringToUtf8ByteArray(val));
    }

    synchronized native void result_text_utf8(long var1, byte[] var3);

    @Override
    public synchronized native void result_blob(long var1, byte[] var3);

    @Override
    public synchronized native void result_double(long var1, double var3);

    @Override
    public synchronized native void result_long(long var1, long var3);

    @Override
    public synchronized native void result_int(long var1, int var3);

    @Override
    public synchronized void result_error(long context, String err) {
        this.result_error_utf8(context, NativeDB.stringToUtf8ByteArray(err));
    }

    synchronized native void result_error_utf8(long var1, byte[] var3);

    @Override
    public synchronized String value_text(Function f, int arg) {
        return NativeDB.utf8ByteBufferToString(this.value_text_utf8(f, arg));
    }

    synchronized native ByteBuffer value_text_utf8(Function var1, int var2);

    @Override
    public synchronized native byte[] value_blob(Function var1, int var2);

    @Override
    public synchronized native double value_double(Function var1, int var2);

    @Override
    public synchronized native long value_long(Function var1, int var2);

    @Override
    public synchronized native int value_int(Function var1, int var2);

    @Override
    public synchronized native int value_type(Function var1, int var2);

    @Override
    public synchronized int create_function(String name, Function func, int nArgs, int flags) throws SQLException {
        return this.create_function_utf8(this.nameToUtf8ByteArray("function", name), func, nArgs, flags);
    }

    synchronized native int create_function_utf8(byte[] var1, Function var2, int var3, int var4);

    @Override
    public synchronized int destroy_function(String name) throws SQLException {
        return this.destroy_function_utf8(this.nameToUtf8ByteArray("function", name));
    }

    synchronized native int destroy_function_utf8(byte[] var1);

    @Override
    public synchronized int create_collation(String name, Collation coll) throws SQLException {
        return this.create_collation_utf8(this.nameToUtf8ByteArray("collation", name), coll);
    }

    synchronized native int create_collation_utf8(byte[] var1, Collation var2);

    @Override
    public synchronized int destroy_collation(String name) throws SQLException {
        return this.destroy_collation_utf8(this.nameToUtf8ByteArray("collation", name));
    }

    synchronized native int destroy_collation_utf8(byte[] var1);

    @Override
    public synchronized native int limit(int var1, int var2) throws SQLException;

    private byte[] nameToUtf8ByteArray(String nameType, String name) throws SQLException {
        byte[] nameUtf8 = NativeDB.stringToUtf8ByteArray(name);
        if (name == null || "".equals(name) || nameUtf8.length > 255) {
            throw new SQLException("invalid " + nameType + " name: '" + name + "'");
        }
        return nameUtf8;
    }

    @Override
    public int backup(String dbName, String destFileName, DB.ProgressObserver observer) throws SQLException {
        return this.backup(NativeDB.stringToUtf8ByteArray(dbName), NativeDB.stringToUtf8ByteArray(destFileName), observer, 100, 3, 100);
    }

    @Override
    public int backup(String dbName, String destFileName, DB.ProgressObserver observer, int sleepTimeMillis, int nTimeouts, int pagesPerStep) throws SQLException {
        return this.backup(NativeDB.stringToUtf8ByteArray(dbName), NativeDB.stringToUtf8ByteArray(destFileName), observer, sleepTimeMillis, nTimeouts, pagesPerStep);
    }

    synchronized native int backup(byte[] var1, byte[] var2, DB.ProgressObserver var3, int var4, int var5, int var6) throws SQLException;

    @Override
    public synchronized int restore(String dbName, String sourceFileName, DB.ProgressObserver observer) throws SQLException {
        return this.restore(dbName, sourceFileName, observer, 100, 3, 100);
    }

    @Override
    public synchronized int restore(String dbName, String sourceFileName, DB.ProgressObserver observer, int sleepTimeMillis, int nTimeouts, int pagesPerStep) throws SQLException {
        return this.restore(NativeDB.stringToUtf8ByteArray(dbName), NativeDB.stringToUtf8ByteArray(sourceFileName), observer, sleepTimeMillis, nTimeouts, pagesPerStep);
    }

    synchronized native int restore(byte[] var1, byte[] var2, DB.ProgressObserver var3, int var4, int var5, int var6) throws SQLException;

    @Override
    synchronized native boolean[][] column_metadata(long var1);

    @Override
    synchronized native void set_commit_listener(boolean var1);

    @Override
    synchronized native void set_update_listener(boolean var1);

    static void throwex(String msg) throws SQLException {
        throw new SQLException(msg);
    }

    static byte[] stringToUtf8ByteArray(String str) {
        if (str == null) {
            return null;
        }
        return str.getBytes(StandardCharsets.UTF_8);
    }

    static String utf8ByteBufferToString(ByteBuffer buffer) {
        if (buffer == null) {
            return null;
        }
        byte[] buff = new byte[buffer.remaining()];
        buffer.get(buff);
        return new String(buff, StandardCharsets.UTF_8);
    }

    @Override
    public synchronized native void register_progress_handler(int var1, ProgressHandler var2) throws SQLException;

    @Override
    public synchronized native void clear_progress_handler() throws SQLException;

    long getBusyHandler() {
        return this.busyHandler;
    }

    long getCommitListener() {
        return this.commitListener;
    }

    long getUpdateListener() {
        return this.updateListener;
    }

    long getProgressHandler() {
        return this.progressHandler;
    }

    @Override
    public synchronized native byte[] serialize(String var1) throws SQLException;

    @Override
    public synchronized native void deserialize(String var1, byte[] var2) throws SQLException;

    static {
        if ("The Android Project".equals(System.getProperty("java.vm.vendor"))) {
            System.loadLibrary("sqlitejdbc");
            isLoaded = true;
            loadSucceeded = true;
        } else {
            isLoaded = false;
            loadSucceeded = false;
        }
    }
}

