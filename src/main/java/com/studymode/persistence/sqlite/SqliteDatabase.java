package com.studymode.persistence.sqlite;

import com.studymode.application.error.StudyDataException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.file.Files;
import java.io.IOException;

/** Connections are short-lived and must be used on the application's I/O executor. */
public final class SqliteDatabase {
    private final Path file;

    public SqliteDatabase(Path file) { this.file = file.toAbsolutePath().normalize(); }

    public Connection open() throws SQLException {
        try { Files.createDirectories(file.getParent()); }
        catch (IOException | SecurityException e) { throw new SQLException("Cannot create the application data directory.", e); }
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 3000");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    public StudyDataException failure(String operation, Exception cause) {
        String hint = cause instanceof SQLException sql && (sql.getErrorCode() == 19 || String.valueOf(sql.getMessage()).contains("CONSTRAINT"))
                ? " A relationship or uniqueness constraint prevents this change. Remove dependent records or correct the reference first."
                : " Check the data folder permissions and whether the database is locked or damaged.";
        return new StudyDataException("Could not " + operation + "." + hint + " Your data has not been reset.", cause);
    }

    @FunctionalInterface public interface SqlOperation<T> { T run(Connection connection) throws SQLException; }

    public <T> T transaction(String operation, SqlOperation<T> work) {
        try (var connection = open()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection); connection.commit(); return result;
            } catch (SQLException | RuntimeException e) {
                try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException | IllegalArgumentException | java.time.DateTimeException e) { throw failure(operation, e); }
    }
}
