package me.growapet.database;

import me.growapet.GrowAPet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Owns the sole SQLite connection and serializes every database operation.
 * No caller may retain or use the connection outside a submitted task.
 */
public final class Database {
    public enum State { NEW, STARTING, READY, FAILED, CLOSING, CLOSED }

    private static final DateTimeFormatter BACKUP_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final GrowAPet plugin;
    private final ExecutorService executor;
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    private volatile Connection connection;
    private volatile Path databaseFile;
    private volatile Throwable startupFailure;

    public Database(GrowAPet plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "GrowAPet-SQLite");
            thread.setDaemon(false);
            return thread;
        });
    }

    public CompletableFuture<Void> initialize() {
        if (!state.compareAndSet(State.NEW, State.STARTING)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Database already initialized"));
        }
        return this.<Void>submitInternal(connectionIgnored -> {
            Path dataFolder = plugin.getDataFolder().toPath();
            Files.createDirectories(dataFolder);
            databaseFile = dataFolder.resolve("database.db");
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
            configureConnection(connection);
            migrate(databaseFile, connection);
            state.set(State.READY);
            return null;
        }).whenComplete((ignored, error) -> {
            if (error != null) {
                startupFailure = unwrap(error);
                state.set(State.FAILED);
                plugin.getLogger().severe("Database initialization failed: " + startupFailure.getMessage());
            }
        });
    }

    private static void configureConnection(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private void migrate(Path databaseFile, Connection connection) throws SQLException, IOException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_migrations (version INTEGER PRIMARY KEY, applied_at INTEGER NOT NULL)");
        }
        int current = currentVersion(connection);
        List<Migration> migrations = Migrations.all();
        int installedVersion = current;
        if (migrations.stream().noneMatch(migration -> migration.version() > installedVersion)) {
            return;
        }
        if (Files.exists(databaseFile) && Files.size(databaseFile) > 0L) {
            Path backupDir = databaseFile.getParent().resolve("backups");
            Files.createDirectories(backupDir);
            Path backup = backupDir.resolve("database-pre-v" + (current + 1) + "-" + BACKUP_TIME.format(Instant.now()) + "-" + System.currentTimeMillis() + ".db");
            try (Statement checkpoint = connection.createStatement()) {
                checkpoint.execute("PRAGMA wal_checkpoint(FULL)");
            }
            long expectedBytes = Files.size(databaseFile);
            Files.copy(databaseFile, backup, StandardCopyOption.COPY_ATTRIBUTES);
            if (!Files.isRegularFile(backup) || Files.size(backup) != expectedBytes) {
                throw new IOException("Pre-migration backup verification failed for " + backup);
            }
            plugin.getLogger().info("Created pre-migration database backup: " + backup.getFileName());
        }
        for (Migration migration : migrations) {
            if (migration.version() <= current) continue;
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                migration.apply(connection);
                try (PreparedStatement applied = connection.prepareStatement(
                        "INSERT INTO schema_migrations(version, applied_at) VALUES (?, ?)")) {
                    applied.setInt(1, migration.version());
                    applied.setLong(2, System.currentTimeMillis());
                    applied.executeUpdate();
                }
                connection.commit();
                current = migration.version();
            } catch (Exception exception) {
                connection.rollback();
                throw exception instanceof SQLException sql ? sql : new SQLException(exception);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        }
    }

    private static int currentVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_migrations")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    public <T> CompletableFuture<T> async(DatabaseTask<T> task) {
        if (state.get() != State.READY) {
            String reason = startupFailure == null ? state.get().name() : startupFailure.getMessage();
            return CompletableFuture.failedFuture(new IllegalStateException("Database is not ready: " + reason));
        }
        return submitInternal(task);
    }

    private <T> CompletableFuture<T> submitInternal(DatabaseTask<T> task) {
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return task.run(connection);
                } catch (Exception exception) {
                    throw new CompletionException(exception);
                }
            }, executor);
        } catch (RejectedExecutionException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    public <T> CompletableFuture<T> transaction(DatabaseTask<T> task) {
        return async(connection -> {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = task.run(connection);
                connection.commit();
                return result;
            } catch (Exception exception) {
                connection.rollback();
                throw exception instanceof SQLException sql ? sql : new SQLException(exception);
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        });
    }

    /** Queues close after all previously submitted work; never blocks the server thread. */
    public CompletableFuture<Void> closeAsync() {
        State previous = state.getAndSet(State.CLOSING);
        if (previous == State.CLOSED || previous == State.CLOSING) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> result = submitInternal(ignored -> {
            if (connection != null && !connection.isClosed()) {
                try (Statement checkpoint = connection.createStatement()) {
                    checkpoint.execute("PRAGMA wal_checkpoint(TRUNCATE)");
                }
                connection.close();
            }
            state.set(State.CLOSED);
            return null;
        });
        result.whenComplete((ignored, error) -> executor.shutdown());
        return result;
    }

    public boolean isReady() {
        return state.get() == State.READY;
    }

    /** Creates and verifies a point-in-time SQLite backup on the database worker. */
    public CompletableFuture<Path> createVerifiedBackup(String label) {
        if (!isReady()) return CompletableFuture.failedFuture(new IllegalStateException("Database is not ready"));
        String safeLabel = label == null ? "manual" : label.replaceAll("[^A-Za-z0-9._-]", "_");
        return async(connection -> {
            if (databaseFile == null) throw new IllegalStateException("Database path is not initialized");
            Path backupDir = databaseFile.getParent().resolve("backups");
            Files.createDirectories(backupDir);
            try (Statement checkpoint = connection.createStatement()) { checkpoint.execute("PRAGMA wal_checkpoint(FULL)"); }
            Path backup = backupDir.resolve("database-" + safeLabel + "-" + BACKUP_TIME.format(Instant.now()) + "-" + System.currentTimeMillis() + ".db");
            long expected = Files.size(databaseFile);
            Files.copy(databaseFile, backup, StandardCopyOption.COPY_ATTRIBUTES);
            if (!Files.isRegularFile(backup) || Files.size(backup) != expected) throw new IOException("Backup verification failed: " + backup);
            plugin.getLogger().info("Created verified database backup: " + backup.getFileName());
            return backup;
        });
    }

    public State getState() {
        return state.get();
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @FunctionalInterface
    public interface DatabaseTask<T> {
        T run(Connection connection) throws Exception;
    }
}
