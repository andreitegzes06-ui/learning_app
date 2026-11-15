package com.studymode.persistence.sqlite;

import com.studymode.application.error.StudyDataException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;

public final class SchemaMigrator {
    private final SqliteDatabase database;
    public SchemaMigrator(SqliteDatabase database) { this.database = database; }

    public void migrate() {
        try (var connection = database.open(); var statement = connection.createStatement()) {
            int version;
            try (var result = statement.executeQuery("PRAGMA user_version")) { version = result.getInt(1); }
            if (version > 1) throw new StudyDataException("This database was created by a newer Study Mode version. Open it with that version.");
            if (version == 1) {
                try (var result = statement.executeQuery("SELECT version FROM schema_version ORDER BY version DESC LIMIT 1")) {
                    if (!result.next() || result.getInt(1) != 1) throw new StudyDataException("Database version records are inconsistent. Data has not been reset.");
                }
                return;
            }
            String sql;
            try (var input = SchemaMigrator.class.getResourceAsStream("/db/migration/V001__foundation.sql")) {
                if (input == null) throw new IOException("Foundation migration is missing.");
                sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            connection.setAutoCommit(false);
            try {
                for (String command : sql.split(";")) if (!command.isBlank()) statement.execute(command);
                try (var insert = connection.prepareStatement("INSERT INTO schema_version VALUES (1, ?)")) {
                    insert.setString(1, Instant.now().toString()); insert.executeUpdate();
                }
                statement.execute("PRAGMA user_version = 1");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback(); throw e;
            }
        } catch (SQLException | IOException e) { throw database.failure("initialize local storage", e); }
    }
}
