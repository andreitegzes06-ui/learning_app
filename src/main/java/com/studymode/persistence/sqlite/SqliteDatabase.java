package com.studymode.persistence.sqlite;

import com.studymode.application.error.StudyDataException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Connections are short-lived and must be used on the application's I/O executor. */
public final class SqliteDatabase {
    private final Path file;

    public SqliteDatabase(Path file) { this.file = file.toAbsolutePath().normalize(); }

    public Connection open() throws SQLException {
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
        return new StudyDataException("Could not " + operation + ". Check the data folder permissions and whether the database is locked or damaged. Your data has not been reset.", cause);
    }
}
