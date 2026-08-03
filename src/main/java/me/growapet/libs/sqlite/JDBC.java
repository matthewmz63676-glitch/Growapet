/*
 * Decompiled with CFR 0.152.
 */
package me.growapet.libs.sqlite;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import me.growapet.libs.sqlite.SQLiteConfig;
import me.growapet.libs.sqlite.SQLiteConnection;
import me.growapet.libs.sqlite.SQLiteJDBCLoader;
import me.growapet.libs.sqlite.jdbc4.JDBC4Connection;
import me.growapet.libs.sqlite.util.Logger;
import me.growapet.libs.sqlite.util.LoggerFactory;

public class JDBC
implements Driver {
    private static final Logger logger = LoggerFactory.getLogger(JDBC.class);
    public static final String PREFIX = "jdbc:sqlite:";

    @Override
    public int getMajorVersion() {
        return SQLiteJDBCLoader.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return SQLiteJDBCLoader.getMinorVersion();
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return null;
    }

    @Override
    public boolean acceptsURL(String url) {
        return JDBC.isValidURL(url);
    }

    public static boolean isValidURL(String url) {
        return url != null && url.toLowerCase().startsWith(PREFIX);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return SQLiteConfig.getDriverPropertyInfo();
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        return JDBC.createConnection(url, info);
    }

    static String extractAddress(String url) {
        return url.substring(PREFIX.length());
    }

    public static SQLiteConnection createConnection(String url, Properties prop) throws SQLException {
        if (!JDBC.isValidURL(url)) {
            return null;
        }
        url = url.trim();
        return new JDBC4Connection(url, JDBC.extractAddress(url), prop);
    }

    static {
        try {
            DriverManager.registerDriver(new JDBC());
        }
        catch (SQLException e) {
            logger.error("Could not register driver", e);
        }
    }
}

