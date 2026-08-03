/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite;

import java.sql.Connection;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import me.growapet.libs.sqlite.JDBC;
import me.growapet.libs.sqlite.SQLiteConnection;
import me.growapet.libs.sqlite.SQLiteConnectionConfig;
import me.growapet.libs.sqlite.SQLiteLimits;
import me.growapet.libs.sqlite.SQLiteOpenMode;

public class SQLiteConfig {
    public static final String DEFAULT_DATE_STRING_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final int DEFAULT_MAX_LENGTH = 1000000000;
    private static final int DEFAULT_MAX_COLUMN = 2000;
    private static final int DEFAULT_MAX_SQL_LENGTH = 1000000;
    private static final int DEFAULT_MAX_FUNCTION_ARG = 100;
    private static final int DEFAULT_MAX_ATTACHED = 10;
    private static final int DEFAULT_MAX_PAGE_COUNT = 0x3FFFFFFF;
    private final Properties pragmaTable;
    private int openModeFlag = 0;
    private int busyTimeout;
    private boolean explicitReadOnly;
    private final SQLiteConnectionConfig defaultConnectionConfig;
    static final Set<String> pragmaSet = new TreeSet<String>();

    public SQLiteConfig() {
        this(new Properties());
    }

    public SQLiteConfig(Properties prop) {
        this.pragmaTable = prop;
        String openMode = this.pragmaTable.getProperty(Pragma.OPEN_MODE.pragmaName);
        if (openMode != null) {
            this.openModeFlag = Integer.parseInt(openMode);
        } else {
            this.setOpenMode(SQLiteOpenMode.READWRITE);
            this.setOpenMode(SQLiteOpenMode.CREATE);
        }
        this.setSharedCache(Boolean.parseBoolean(this.pragmaTable.getProperty(Pragma.SHARED_CACHE.pragmaName, "false")));
        this.setOpenMode(SQLiteOpenMode.OPEN_URI);
        this.setBusyTimeout(Integer.parseInt(this.pragmaTable.getProperty(Pragma.BUSY_TIMEOUT.pragmaName, "3000")));
        this.defaultConnectionConfig = SQLiteConnectionConfig.fromPragmaTable(this.pragmaTable);
        this.explicitReadOnly = Boolean.parseBoolean(this.pragmaTable.getProperty(Pragma.JDBC_EXPLICIT_READONLY.pragmaName, "false"));
    }

    public SQLiteConnectionConfig newConnectionConfig() {
        return this.defaultConnectionConfig.copyConfig();
    }

    public Connection createConnection(String url) throws SQLException {
        return JDBC.createConnection(url, this.toProperties());
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public void apply(Connection conn) throws SQLException {
        HashSet<String> pragmaParams = new HashSet<String>();
        for (Pragma each : Pragma.values()) {
            pragmaParams.add(each.pragmaName);
        }
        if (conn instanceof SQLiteConnection) {
            SQLiteConnection sqliteConn = (SQLiteConnection)conn;
            sqliteConn.setLimit(SQLiteLimits.SQLITE_LIMIT_ATTACHED, this.parseLimitPragma(Pragma.LIMIT_ATTACHED, 10));
            sqliteConn.setLimit(SQLiteLimits.SQLITE_LIMIT_COLUMN, this.parseLimitPragma(Pragma.LIMIT_COLUMN, 2000));
            sqliteConn.setLimit(SQLiteLimits.SQLITE_LIMIT_COMPOUND_SELECT, this.parseLimitPragma(Pragma.LIMIT_COMPOUND_SELECT, -1));
            sqliteConn.setLimit(SQLiteLimits.SQLITE_LIMIT_EXPR_DEPTH, this.parseLimitPragma(Pragma.LIMIT_EXPR_DEPTH, -1));
            sqliteConn.setLimit(SQLiteLimits.SQLITE_LIMIT_FUNCTION_ARG, this.parseLimitPragma(Pragma.LIMIT_FUNCTION_ARG, 100));
            sqliteConn.setLimit(SQLiteLimits.SQLITE_LIMIT_LENGTH, this.parseLimitPragma(Pragma.LIMIT_LENGTH, 1000000000));
            sqliteConn.setLimit(SQLiteLimits.SQLITE_LIMIT_LIKE_PATTERN_LENGTH, this.parseLimitPragma(Pragma.LIMIT_LIKE_PATTERN_LENGTH, -1));
            sqliteConn.setLimit(SQLiteLimits.SQLITE_LIMIT_SQL_LENGTH, this.parseLimitPragma(Pragma.LIMIT_SQL_LENGTH, 1000000));
            sqliteConn.setLimit(SQLiteLimits.SQLITE_LIMIT_TRIGGER_DEPTH, this.parseLimitPragma(Pragma.LIMIT_TRIGGER_DEPTH, -1));
            sqliteConn.setLimit(SQLiteLimits.SQLITE_LIMIT_VARIABLE_NUMBER, this.parseLimitPragma(Pragma.LIMIT_VARIABLE_NUMBER, -1));
            sqliteConn.setLimit(SQLiteLimits.SQLITE_LIMIT_VDBE_OP, this.parseLimitPragma(Pragma.LIMIT_VDBE_OP, -1));
            sqliteConn.setLimit(SQLiteLimits.SQLITE_LIMIT_WORKER_THREADS, this.parseLimitPragma(Pragma.LIMIT_WORKER_THREADS, -1));
            sqliteConn.setLimit(SQLiteLimits.SQLITE_LIMIT_PAGE_COUNT, this.parseLimitPragma(Pragma.LIMIT_PAGE_COUNT, 0x3FFFFFFF));
        }
        pragmaParams.remove(Pragma.OPEN_MODE.pragmaName);
        pragmaParams.remove(Pragma.SHARED_CACHE.pragmaName);
        pragmaParams.remove(Pragma.LOAD_EXTENSION.pragmaName);
        pragmaParams.remove(Pragma.DATE_PRECISION.pragmaName);
        pragmaParams.remove(Pragma.DATE_CLASS.pragmaName);
        pragmaParams.remove(Pragma.DATE_STRING_FORMAT.pragmaName);
        pragmaParams.remove(Pragma.PASSWORD.pragmaName);
        pragmaParams.remove(Pragma.HEXKEY_MODE.pragmaName);
        pragmaParams.remove(Pragma.LIMIT_ATTACHED.pragmaName);
        pragmaParams.remove(Pragma.LIMIT_COLUMN.pragmaName);
        pragmaParams.remove(Pragma.LIMIT_COMPOUND_SELECT.pragmaName);
        pragmaParams.remove(Pragma.LIMIT_EXPR_DEPTH.pragmaName);
        pragmaParams.remove(Pragma.LIMIT_FUNCTION_ARG.pragmaName);
        pragmaParams.remove(Pragma.LIMIT_LENGTH.pragmaName);
        pragmaParams.remove(Pragma.LIMIT_LIKE_PATTERN_LENGTH.pragmaName);
        pragmaParams.remove(Pragma.LIMIT_SQL_LENGTH.pragmaName);
        pragmaParams.remove(Pragma.LIMIT_TRIGGER_DEPTH.pragmaName);
        pragmaParams.remove(Pragma.LIMIT_VARIABLE_NUMBER.pragmaName);
        pragmaParams.remove(Pragma.LIMIT_VDBE_OP.pragmaName);
        pragmaParams.remove(Pragma.LIMIT_WORKER_THREADS.pragmaName);
        pragmaParams.remove(Pragma.LIMIT_PAGE_COUNT.pragmaName);
        pragmaParams.remove(Pragma.JDBC_EXPLICIT_READONLY.pragmaName);
        pragmaParams.remove(Pragma.JDBC_GET_GENERATED_KEYS.pragmaName);
        try (Statement stat = conn.createStatement();){
            String password;
            if (this.pragmaTable.containsKey(Pragma.PASSWORD.pragmaName) && (password = this.pragmaTable.getProperty(Pragma.PASSWORD.pragmaName)) != null && !password.isEmpty()) {
                String hexkeyMode = this.pragmaTable.getProperty(Pragma.HEXKEY_MODE.pragmaName);
                String passwordPragma = HexKeyMode.SSE.name().equalsIgnoreCase(hexkeyMode) ? "pragma hexkey = '%s'" : (HexKeyMode.SQLCIPHER.name().equalsIgnoreCase(hexkeyMode) ? "pragma key = \"x'%s'\"" : "pragma key = '%s'");
                stat.execute(String.format(passwordPragma, password.replace("'", "''")));
                stat.execute("select 1 from sqlite_schema");
            }
            for (Object each : this.pragmaTable.keySet()) {
                String value;
                String key = each.toString();
                if (!pragmaParams.contains(key) || (value = this.pragmaTable.getProperty(key)) == null) continue;
                stat.execute(String.format("pragma %s=%s", key, value));
            }
        }
    }

    private void set(Pragma pragma, boolean flag) {
        this.setPragma(pragma, Boolean.toString(flag));
    }

    private void set(Pragma pragma, int num) {
        this.setPragma(pragma, Integer.toString(num));
    }

    private boolean getBoolean(Pragma pragma, String defaultValue) {
        return Boolean.parseBoolean(this.pragmaTable.getProperty(pragma.pragmaName, defaultValue));
    }

    private int parseLimitPragma(Pragma pragma, int defaultValue) {
        if (!this.pragmaTable.containsKey(pragma.pragmaName)) {
            return defaultValue;
        }
        String valueString = this.pragmaTable.getProperty(pragma.pragmaName);
        try {
            return Integer.parseInt(valueString);
        }
        catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public boolean isEnabledSharedCache() {
        return this.getBoolean(Pragma.SHARED_CACHE, "false");
    }

    public boolean isEnabledLoadExtension() {
        return this.getBoolean(Pragma.LOAD_EXTENSION, "false");
    }

    public int getOpenModeFlags() {
        return this.openModeFlag;
    }

    public void setPragma(Pragma pragma, String value) {
        this.pragmaTable.put(pragma.pragmaName, value);
    }

    public Properties toProperties() {
        this.pragmaTable.setProperty(Pragma.OPEN_MODE.pragmaName, Integer.toString(this.openModeFlag));
        this.pragmaTable.setProperty(Pragma.TRANSACTION_MODE.pragmaName, this.defaultConnectionConfig.getTransactionMode().getValue());
        this.pragmaTable.setProperty(Pragma.DATE_CLASS.pragmaName, this.defaultConnectionConfig.getDateClass().getValue());
        this.pragmaTable.setProperty(Pragma.DATE_PRECISION.pragmaName, this.defaultConnectionConfig.getDatePrecision().getValue());
        this.pragmaTable.setProperty(Pragma.DATE_STRING_FORMAT.pragmaName, this.defaultConnectionConfig.getDateStringFormat());
        this.pragmaTable.setProperty(Pragma.JDBC_EXPLICIT_READONLY.pragmaName, this.explicitReadOnly ? "true" : "false");
        this.pragmaTable.setProperty(Pragma.JDBC_GET_GENERATED_KEYS.pragmaName, this.defaultConnectionConfig.isGetGeneratedKeys() ? "true" : "false");
        return this.pragmaTable;
    }

    static DriverPropertyInfo[] getDriverPropertyInfo() {
        Pragma[] pragma = Pragma.values();
        DriverPropertyInfo[] result = new DriverPropertyInfo[pragma.length];
        int index = 0;
        for (Pragma p : Pragma.values()) {
            DriverPropertyInfo di = new DriverPropertyInfo(p.pragmaName, null);
            di.choices = p.choices;
            di.description = p.description;
            di.required = false;
            result[index++] = di;
        }
        return result;
    }

    public boolean isExplicitReadOnly() {
        return this.explicitReadOnly;
    }

    public void setExplicitReadOnly(boolean readOnly) {
        this.explicitReadOnly = readOnly;
    }

    public void setOpenMode(SQLiteOpenMode mode) {
        this.openModeFlag |= mode.flag;
    }

    public void resetOpenMode(SQLiteOpenMode mode) {
        this.openModeFlag &= ~mode.flag;
    }

    public void setSharedCache(boolean enable) {
        this.set(Pragma.SHARED_CACHE, enable);
    }

    public void enableLoadExtension(boolean enable) {
        this.set(Pragma.LOAD_EXTENSION, enable);
    }

    public void setReadOnly(boolean readOnly) {
        if (readOnly) {
            this.setOpenMode(SQLiteOpenMode.READONLY);
            this.resetOpenMode(SQLiteOpenMode.CREATE);
            this.resetOpenMode(SQLiteOpenMode.READWRITE);
        } else {
            this.setOpenMode(SQLiteOpenMode.READWRITE);
            this.setOpenMode(SQLiteOpenMode.CREATE);
            this.resetOpenMode(SQLiteOpenMode.READONLY);
        }
    }

    public void setCacheSize(int numberOfPages) {
        this.set(Pragma.CACHE_SIZE, numberOfPages);
    }

    public void enableCaseSensitiveLike(boolean enable) {
        this.set(Pragma.CASE_SENSITIVE_LIKE, enable);
    }

    @Deprecated
    public void enableCountChanges(boolean enable) {
        this.set(Pragma.COUNT_CHANGES, enable);
    }

    public void setDefaultCacheSize(int numberOfPages) {
        this.set(Pragma.DEFAULT_CACHE_SIZE, numberOfPages);
    }

    public void deferForeignKeys(boolean enable) {
        this.set(Pragma.DEFER_FOREIGN_KEYS, enable);
    }

    @Deprecated
    public void enableEmptyResultCallBacks(boolean enable) {
        this.set(Pragma.EMPTY_RESULT_CALLBACKS, enable);
    }

    public void setEncoding(Encoding encoding) {
        this.setPragma(Pragma.ENCODING, encoding.typeName);
    }

    public void enforceForeignKeys(boolean enforce) {
        this.set(Pragma.FOREIGN_KEYS, enforce);
    }

    @Deprecated
    public void enableFullColumnNames(boolean enable) {
        this.set(Pragma.FULL_COLUMN_NAMES, enable);
    }

    public void enableFullSync(boolean enable) {
        this.set(Pragma.FULL_SYNC, enable);
    }

    public void incrementalVacuum(int numberOfPagesToBeRemoved) {
        this.set(Pragma.INCREMENTAL_VACUUM, numberOfPagesToBeRemoved);
    }

    public void setJournalMode(JournalMode mode) {
        this.setPragma(Pragma.JOURNAL_MODE, mode.name());
    }

    public void setJournalSizeLimit(int limit) {
        this.set(Pragma.JOURNAL_SIZE_LIMIT, limit);
    }

    public void useLegacyFileFormat(boolean use) {
        this.set(Pragma.LEGACY_FILE_FORMAT, use);
    }

    public void setLegacyAlterTable(boolean flag) {
        this.set(Pragma.LEGACY_ALTER_TABLE, flag);
    }

    public void setLockingMode(LockingMode mode) {
        this.setPragma(Pragma.LOCKING_MODE, mode.name());
    }

    public void setPageSize(int numBytes) {
        this.set(Pragma.PAGE_SIZE, numBytes);
    }

    public void setMaxPageCount(int numPages) {
        this.set(Pragma.MAX_PAGE_COUNT, numPages);
    }

    public void setReadUncommitted(boolean useReadUncommittedIsolationMode) {
        this.set(Pragma.READ_UNCOMMITTED, useReadUncommittedIsolationMode);
    }

    public void enableRecursiveTriggers(boolean enable) {
        this.set(Pragma.RECURSIVE_TRIGGERS, enable);
    }

    public void enableReverseUnorderedSelects(boolean enable) {
        this.set(Pragma.REVERSE_UNORDERED_SELECTS, enable);
    }

    public void enableShortColumnNames(boolean enable) {
        this.set(Pragma.SHORT_COLUMN_NAMES, enable);
    }

    public void setSynchronous(SynchronousMode mode) {
        this.setPragma(Pragma.SYNCHRONOUS, mode.name());
    }

    public void setHexKeyMode(HexKeyMode mode) {
        this.setPragma(Pragma.HEXKEY_MODE, mode.name());
    }

    public void setTempStore(TempStore storeType) {
        this.setPragma(Pragma.TEMP_STORE, storeType.name());
    }

    public void setTempStoreDirectory(String directoryName) {
        this.setPragma(Pragma.TEMP_STORE_DIRECTORY, String.format("'%s'", directoryName));
    }

    public void setUserVersion(int version) {
        this.set(Pragma.USER_VERSION, version);
    }

    public void setApplicationId(int id) {
        this.set(Pragma.APPLICATION_ID, id);
    }

    public void setTransactionMode(TransactionMode transactionMode) {
        this.defaultConnectionConfig.setTransactionMode(transactionMode);
    }

    public void setTransactionMode(String transactionMode) {
        this.setTransactionMode(TransactionMode.getMode(transactionMode));
    }

    public TransactionMode getTransactionMode() {
        return this.defaultConnectionConfig.getTransactionMode();
    }

    public void setDatePrecision(String datePrecision) {
        this.defaultConnectionConfig.setDatePrecision(DatePrecision.getPrecision(datePrecision));
    }

    public void setDateClass(String dateClass) {
        this.defaultConnectionConfig.setDateClass(DateClass.getDateClass(dateClass));
    }

    public void setDateStringFormat(String dateStringFormat) {
        this.defaultConnectionConfig.setDateStringFormat(dateStringFormat);
    }

    public void setBusyTimeout(int milliseconds) {
        this.setPragma(Pragma.BUSY_TIMEOUT, Integer.toString(milliseconds));
        this.busyTimeout = milliseconds;
    }

    public int getBusyTimeout() {
        return this.busyTimeout;
    }

    public boolean isGetGeneratedKeys() {
        return this.defaultConnectionConfig.isGetGeneratedKeys();
    }

    public void setGetGeneratedKeys(boolean generatedKeys) {
        this.defaultConnectionConfig.setGetGeneratedKeys(generatedKeys);
    }

    static {
        for (Pragma pragma : Pragma.values()) {
            pragmaSet.add(pragma.pragmaName);
        }
    }

    public static enum DateClass implements PragmaValue
    {
        INTEGER,
        TEXT,
        REAL;


        @Override
        public String getValue() {
            return this.name();
        }

        public static DateClass getDateClass(String dateClass) {
            return DateClass.valueOf(dateClass.toUpperCase());
        }
    }

    public static enum DatePrecision implements PragmaValue
    {
        SECONDS,
        MILLISECONDS;


        @Override
        public String getValue() {
            return this.name();
        }

        public static DatePrecision getPrecision(String precision) {
            return DatePrecision.valueOf(precision.toUpperCase());
        }
    }

    public static enum TransactionMode implements PragmaValue
    {
        DEFERRED,
        IMMEDIATE,
        EXCLUSIVE;


        @Override
        public String getValue() {
            return this.name();
        }

        public static TransactionMode getMode(String mode) {
            return TransactionMode.valueOf(mode.toUpperCase());
        }
    }

    public static enum HexKeyMode implements PragmaValue
    {
        NONE,
        SSE,
        SQLCIPHER;


        @Override
        public String getValue() {
            return this.name();
        }
    }

    public static enum TempStore implements PragmaValue
    {
        DEFAULT,
        FILE,
        MEMORY;


        @Override
        public String getValue() {
            return this.name();
        }
    }

    public static enum SynchronousMode implements PragmaValue
    {
        OFF,
        NORMAL,
        FULL;


        @Override
        public String getValue() {
            return this.name();
        }
    }

    public static enum LockingMode implements PragmaValue
    {
        NORMAL,
        EXCLUSIVE;


        @Override
        public String getValue() {
            return this.name();
        }
    }

    public static enum JournalMode implements PragmaValue
    {
        DELETE,
        TRUNCATE,
        PERSIST,
        MEMORY,
        WAL,
        OFF;


        @Override
        public String getValue() {
            return this.name();
        }
    }

    public static enum Encoding implements PragmaValue
    {
        UTF8("'UTF-8'"),
        UTF16("'UTF-16'"),
        UTF16_LITTLE_ENDIAN("'UTF-16le'"),
        UTF16_BIG_ENDIAN("'UTF-16be'"),
        UTF_8(UTF8),
        UTF_16(UTF16),
        UTF_16LE(UTF16_LITTLE_ENDIAN),
        UTF_16BE(UTF16_BIG_ENDIAN);

        public final String typeName;

        private Encoding(String typeName) {
            this.typeName = typeName;
        }

        private Encoding(Encoding encoding) {
            this.typeName = encoding.getValue();
        }

        @Override
        public String getValue() {
            return this.typeName;
        }

        public static Encoding getEncoding(String value) {
            return Encoding.valueOf(value.replaceAll("-", "_").toUpperCase());
        }
    }

    private static interface PragmaValue {
        public String getValue();
    }

    public static enum Pragma {
        OPEN_MODE("open_mode", "Database open-mode flag", null),
        SHARED_CACHE("shared_cache", "Enable SQLite Shared-Cache mode, native driver only", OnOff.access$000()),
        LOAD_EXTENSION("enable_load_extension", "Enable SQLite load_extension() function, native driver only", OnOff.access$000()),
        CACHE_SIZE("cache_size", "Maximum number of database disk pages that SQLite will hold in memory at once per open database file", null),
        MMAP_SIZE("mmap_size", "Maximum number of bytes that are set aside for memory-mapped I/O on a single database", null),
        CASE_SENSITIVE_LIKE("case_sensitive_like", "Installs a new application-defined LIKE function that is either case sensitive or insensitive depending on the value", OnOff.access$000()),
        COUNT_CHANGES("count_changes", "Deprecated", OnOff.access$000()),
        DEFAULT_CACHE_SIZE("default_cache_size", "Deprecated", null),
        DEFER_FOREIGN_KEYS("defer_foreign_keys", "When the defer_foreign_keys PRAGMA is on, enforcement of all foreign key constraints is delayed until the outermost transaction is committed. The defer_foreign_keys pragma defaults to OFF so that foreign key constraints are only deferred if they are created as \"DEFERRABLE INITIALLY DEFERRED\". The defer_foreign_keys pragma is automatically switched off at each COMMIT or ROLLBACK. Hence, the defer_foreign_keys pragma must be separately enabled for each transaction. This pragma is only meaningful if foreign key constraints are enabled, of course.", OnOff.access$000()),
        EMPTY_RESULT_CALLBACKS("empty_result_callback", "Deprecated", OnOff.access$000()),
        ENCODING("encoding", "Set the encoding that the main database will be created with if it is created by this session", Pragma.toStringArray(Encoding.values())),
        FOREIGN_KEYS("foreign_keys", "Set the enforcement of foreign key constraints", OnOff.access$000()),
        FULL_COLUMN_NAMES("full_column_names", "Deprecated", OnOff.access$000()),
        FULL_SYNC("fullsync", "Whether or not the F_FULLFSYNC syncing method is used on systems that support it. Only Mac OS X supports F_FULLFSYNC.", OnOff.access$000()),
        INCREMENTAL_VACUUM("incremental_vacuum", "Causes up to N pages to be removed from the freelist. The database file is truncated by the same amount. The incremental_vacuum pragma has no effect if the database is not in auto_vacuum=incremental mode or if there are no pages on the freelist. If there are fewer than N pages on the freelist, or if N is less than 1, or if the \"(N)\" argument is omitted, then the entire freelist is cleared.", null),
        JOURNAL_MODE("journal_mode", "Set the journal mode for databases associated with the current database connection", Pragma.toStringArray(JournalMode.values())),
        JOURNAL_SIZE_LIMIT("journal_size_limit", "Limit the size of rollback-journal and WAL files left in the file-system after transactions or checkpoints", null),
        LEGACY_ALTER_TABLE("legacy_alter_table", "Use legacy alter table behavior", OnOff.access$000()),
        LEGACY_FILE_FORMAT("legacy_file_format", "No-op", OnOff.access$000()),
        LOCKING_MODE("locking_mode", "Set the database connection locking-mode", Pragma.toStringArray(LockingMode.values())),
        PAGE_SIZE("page_size", "Set the page size of the database. The page size must be a power of two between 512 and 65536 inclusive.", null),
        MAX_PAGE_COUNT("max_page_count", "Set the maximum number of pages in the database file", null),
        READ_UNCOMMITTED("read_uncommitted", "Set READ UNCOMMITTED isolation", OnOff.access$000()),
        RECURSIVE_TRIGGERS("recursive_triggers", "Set the recursive trigger capability", OnOff.access$000()),
        REVERSE_UNORDERED_SELECTS("reverse_unordered_selects", "When enabled, this PRAGMA causes many SELECT statements without an ORDER BY clause to emit their results in the reverse order from what they normally would", OnOff.access$000()),
        SECURE_DELETE("secure_delete", "When secure_delete is on, SQLite overwrites deleted content with zeros", new String[]{"true", "false", "fast"}),
        SHORT_COLUMN_NAMES("short_column_names", "Deprecated", OnOff.access$000()),
        SYNCHRONOUS("synchronous", "Set the \"synchronous\" flag", Pragma.toStringArray(SynchronousMode.values())),
        TEMP_STORE("temp_store", "When temp_store is DEFAULT (0), the compile-time C preprocessor macro SQLITE_TEMP_STORE is used to determine where temporary tables and indices are stored. When temp_store is MEMORY (2) temporary tables and indices are kept as if they were in pure in-memory databases. When temp_store is FILE (1) temporary tables and indices are stored in a file. The temp_store_directory pragma can be used to specify the directory containing temporary files when FILE is specified. When the temp_store setting is changed, all existing temporary tables, indices, triggers, and views are immediately deleted.", Pragma.toStringArray(TempStore.values())),
        TEMP_STORE_DIRECTORY("temp_store_directory", "Deprecated", null),
        USER_VERSION("user_version", "Set the value of the user-version integer at offset 60 in the database header. The user-version is an integer that is available to applications to use however they want. SQLite makes no use of the user-version itself.", null),
        APPLICATION_ID("application_id", "Set the 32-bit signed big-endian \"Application ID\" integer located at offset 68 into the database header. Applications that use SQLite as their application file-format should set the Application ID integer to a unique integer so that utilities such as file(1) can determine the specific file type rather than just reporting \"SQLite3 Database\"", null),
        LIMIT_LENGTH("limit_length", "The maximum size of any string or BLOB or table row, in bytes.", null),
        LIMIT_SQL_LENGTH("limit_sql_length", "The maximum length of an SQL statement, in bytes.", null),
        LIMIT_COLUMN("limit_column", "The maximum number of columns in a table definition or in the result set of a SELECT or the maximum number of columns in an index or in an ORDER BY or GROUP BY clause.", null),
        LIMIT_EXPR_DEPTH("limit_expr_depth", "The maximum depth of the parse tree on any expression.", null),
        LIMIT_COMPOUND_SELECT("limit_compound_select", "The maximum number of terms in a compound SELECT statement.", null),
        LIMIT_VDBE_OP("limit_vdbe_op", "The maximum number of instructions in a virtual machine program used to implement an SQL statement. If sqlite3_prepare_v2() or the equivalent tries to allocate space for more than this many opcodes in a single prepared statement, an SQLITE_NOMEM error is returned.", null),
        LIMIT_FUNCTION_ARG("limit_function_arg", "The maximum number of arguments on a function.", null),
        LIMIT_ATTACHED("limit_attached", "The maximum number of attached databases.", null),
        LIMIT_LIKE_PATTERN_LENGTH("limit_like_pattern_length", "The maximum length of the pattern argument to the LIKE or GLOB operators.", null),
        LIMIT_VARIABLE_NUMBER("limit_variable_number", "The maximum index number of any parameter in an SQL statement.", null),
        LIMIT_TRIGGER_DEPTH("limit_trigger_depth", "The maximum depth of recursion for triggers.", null),
        LIMIT_WORKER_THREADS("limit_worker_threads", "The maximum number of auxiliary worker threads that a single prepared statement may start.", null),
        LIMIT_PAGE_COUNT("limit_page_count", "The maximum number of pages allowed in a single database file.", null),
        TRANSACTION_MODE("transaction_mode", "Set the transaction mode", Pragma.toStringArray(TransactionMode.values())),
        DATE_PRECISION("date_precision", "\"seconds\": Read and store integer dates as seconds from the Unix Epoch (SQLite standard).\n\"milliseconds\": (DEFAULT) Read and store integer dates as milliseconds from the Unix Epoch (Java standard).", Pragma.toStringArray(DatePrecision.values())),
        DATE_CLASS("date_class", "\"integer\": (Default) store dates as number of seconds or milliseconds from the Unix Epoch\n\"text\": store dates as a string of text\n\"real\": store dates as Julian Dates", Pragma.toStringArray(DateClass.values())),
        DATE_STRING_FORMAT("date_string_format", "Format to store and retrieve dates stored as text. Defaults to \"yyyy-MM-dd HH:mm:ss.SSS\"", null),
        BUSY_TIMEOUT("busy_timeout", "Sets a busy handler that sleeps for a specified amount of time when a table is locked", null),
        HEXKEY_MODE("hexkey_mode", "Mode of the secret key", Pragma.toStringArray(HexKeyMode.values())),
        PASSWORD("password", "Database password", null),
        JDBC_EXPLICIT_READONLY("jdbc.explicit_readonly", "Set explicit read only transactions", null),
        JDBC_GET_GENERATED_KEYS("jdbc.get_generated_keys", "Enable retrieval of generated keys", OnOff.access$000());

        public final String pragmaName;
        public final String[] choices;
        public final String description;

        private Pragma(String pragmaName) {
            this(pragmaName, null);
        }

        private Pragma(String pragmaName, String[] choices) {
            this(pragmaName, null, choices);
        }

        private Pragma(String pragmaName, String description, String[] choices) {
            this.pragmaName = pragmaName;
            this.description = description;
            this.choices = choices;
        }

        private static String[] toStringArray(PragmaValue[] list) {
            String[] result = new String[list.length];
            for (int i = 0; i < list.length; ++i) {
                result[i] = list[i].getValue();
            }
            return result;
        }

        public final String getPragmaName() {
            return this.pragmaName;
        }
    }

    static class OnOff {
        private static final String[] Values = new String[]{"true", "false"};

        OnOff() {
        }

        static /* synthetic */ String[] access$000() {
            return Values;
        }
    }
}

