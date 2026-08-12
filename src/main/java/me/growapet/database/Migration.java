package me.growapet.database;

import java.sql.Connection;

public record Migration(int version, MigrationAction action) {
    public void apply(Connection connection) throws Exception {
        action.apply(connection);
    }

    @FunctionalInterface
    public interface MigrationAction {
        void apply(Connection connection) throws Exception;
    }
}
