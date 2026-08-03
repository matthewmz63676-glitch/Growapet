/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package me.growapet.libs.sqlite.util;

import java.text.MessageFormat;
import java.util.logging.Level;
import me.growapet.libs.sqlite.util.Logger;

public class LoggerFactory {
    static final boolean USE_SLF4J;

    public static Logger getLogger(Class<?> hostClass) {
        if (USE_SLF4J) {
            return new SLF4JLogger(hostClass);
        }
        return new JDKLogger(hostClass);
    }

    static {
        boolean useSLF4J;
        try {
            Class.forName("org.slf4j.Logger");
            useSLF4J = true;
        }
        catch (Exception e) {
            useSLF4J = false;
        }
        USE_SLF4J = useSLF4J;
    }

    private static class SLF4JLogger
    implements Logger {
        final org.slf4j.Logger logger;

        SLF4JLogger(Class<?> hostClass) {
            this.logger = org.slf4j.LoggerFactory.getLogger(hostClass);
        }

        @Override
        public boolean isTraceEnabled() {
            return this.logger.isTraceEnabled();
        }

        @Override
        public void trace(String format, Object o1, Object o2) {
            this.logger.trace(format, o1, o2);
        }

        @Override
        public void info(String format, Object o1, Object o2) {
            this.logger.info(format, o1, o2);
        }

        @Override
        public void warn(String msg) {
            this.logger.warn(msg);
        }

        @Override
        public void error(String message, Throwable t) {
            this.logger.error(message, t);
        }

        @Override
        public void error(String format, Object o1, Throwable t) {
            this.logger.error(format, o1, (Object)t);
        }

        @Override
        public void error(String format, Object o1, Object o2, Throwable t) {
            this.logger.error(format, new Object[]{o1, o2, t});
        }
    }

    private static class JDKLogger
    implements Logger {
        final java.util.logging.Logger logger;

        public JDKLogger(Class<?> hostClass) {
            this.logger = java.util.logging.Logger.getLogger(hostClass.getCanonicalName());
        }

        @Override
        public boolean isTraceEnabled() {
            return this.logger.isLoggable(Level.FINEST);
        }

        @Override
        public void trace(String format, Object o1, Object o2) {
            if (this.logger.isLoggable(Level.FINEST)) {
                this.logger.log(Level.FINEST, MessageFormat.format(format, o1, o2));
            }
        }

        @Override
        public void info(String format, Object o1, Object o2) {
            if (this.logger.isLoggable(Level.INFO)) {
                this.logger.log(Level.INFO, MessageFormat.format(format, o1, o2));
            }
        }

        @Override
        public void warn(String msg) {
            this.logger.log(Level.WARNING, msg);
        }

        @Override
        public void error(String message, Throwable t) {
            this.logger.log(Level.SEVERE, message, t);
        }

        @Override
        public void error(String format, Object o1, Throwable t) {
            if (this.logger.isLoggable(Level.SEVERE)) {
                this.logger.log(Level.SEVERE, MessageFormat.format(format, o1), t);
            }
        }

        @Override
        public void error(String format, Object o1, Object o2, Throwable t) {
            if (this.logger.isLoggable(Level.SEVERE)) {
                this.logger.log(Level.SEVERE, MessageFormat.format(format, o1, o2), t);
            }
        }
    }
}

