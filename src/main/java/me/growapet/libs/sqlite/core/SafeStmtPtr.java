/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite.core;

import java.sql.SQLException;
import me.growapet.libs.sqlite.core.DB;

public class SafeStmtPtr {
    private final DB db;
    private final long ptr;
    private volatile boolean closed = false;
    private int closedRC;
    private SQLException closeException;

    public SafeStmtPtr(DB db, long ptr) {
        this.db = db;
        this.ptr = ptr;
    }

    public boolean isClosed() {
        return this.closed;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public int close() throws SQLException {
        DB dB = this.db;
        synchronized (dB) {
            return this.internalClose();
        }
    }

    private int internalClose() throws SQLException {
        try {
            if (this.closed) {
                if (this.closeException != null) {
                    throw this.closeException;
                }
                int n = this.closedRC;
                return n;
            }
            int n = this.closedRC = this.db.finalize(this, this.ptr);
            return n;
        }
        catch (SQLException ex) {
            this.closeException = ex;
            throw ex;
        }
        finally {
            this.closed = true;
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public <E extends Throwable> int safeRunInt(SafePtrIntFunction<E> run) throws SQLException, E {
        DB dB = this.db;
        synchronized (dB) {
            this.ensureOpen();
            return run.run(this.db, this.ptr);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public <E extends Throwable> long safeRunLong(SafePtrLongFunction<E> run) throws SQLException, E {
        DB dB = this.db;
        synchronized (dB) {
            this.ensureOpen();
            return run.run(this.db, this.ptr);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public <E extends Throwable> double safeRunDouble(SafePtrDoubleFunction<E> run) throws SQLException, E {
        DB dB = this.db;
        synchronized (dB) {
            this.ensureOpen();
            return run.run(this.db, this.ptr);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public <T, E extends Throwable> T safeRun(SafePtrFunction<T, E> run) throws SQLException, E {
        DB dB = this.db;
        synchronized (dB) {
            this.ensureOpen();
            return run.run(this.db, this.ptr);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public <E extends Throwable> void safeRunConsume(SafePtrConsumer<E> run) throws SQLException, E {
        DB dB = this.db;
        synchronized (dB) {
            this.ensureOpen();
            run.run(this.db, this.ptr);
        }
    }

    private void ensureOpen() throws SQLException {
        if (this.closed) {
            throw new SQLException("stmt pointer is closed");
        }
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        SafeStmtPtr that = (SafeStmtPtr)o;
        return this.ptr == that.ptr;
    }

    public int hashCode() {
        return Long.hashCode(this.ptr);
    }

    @FunctionalInterface
    public static interface SafePtrConsumer<E extends Throwable> {
        public void run(DB var1, long var2) throws E;
    }

    @FunctionalInterface
    public static interface SafePtrFunction<T, E extends Throwable> {
        public T run(DB var1, long var2) throws E;
    }

    @FunctionalInterface
    public static interface SafePtrDoubleFunction<E extends Throwable> {
        public double run(DB var1, long var2) throws E;
    }

    @FunctionalInterface
    public static interface SafePtrLongFunction<E extends Throwable> {
        public long run(DB var1, long var2) throws E;
    }

    @FunctionalInterface
    public static interface SafePtrIntFunction<E extends Throwable> {
        public int run(DB var1, long var2) throws E;
    }
}

