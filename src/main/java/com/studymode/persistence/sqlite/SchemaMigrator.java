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
        database.transaction("initialize local storage", connection -> {
          try (var statement = connection.createStatement()) {
            int version;
            try (var result = statement.executeQuery("PRAGMA user_version")) { result.next(); version = result.getInt(1); }
            if (version > 3) throw new StudyDataException("This database was created by a newer Study Mode version. Open it with that version.");
            if (version > 0) {
                var versions = SqliteStatements.query(connection, "SELECT version FROM schema_version ORDER BY version", row -> row.getInt(1));
                if (versions.size() != version) throw new StudyDataException("Database version records are inconsistent. Data has not been reset.");
                for (int i = 0; i < version; i++) {
                    if (versions.get(i) != i + 1) throw new StudyDataException("Database migration history is inconsistent.");
                }
            }
            String[] migrations = {"V001__foundation.sql", "V002__study_data.sql", "V003__focus_sessions.sql"};
            for (int index = version; index < migrations.length; index++) {
                String sql;
                try (var input = SchemaMigrator.class.getResourceAsStream("/db/migration/" + migrations[index])) {
                    if (input == null) throw new IOException("Migration is missing: " + migrations[index]);
                    sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) { throw new StudyDataException("Could not read the database migration. Data has not been reset.", e); }
                for (String command : sql.split(";")) if (!command.isBlank()) statement.execute(command);
                SqliteStatements.update(connection, "INSERT INTO schema_version VALUES (?, ?)", index + 1, Instant.now());
                statement.execute("PRAGMA user_version = " + (index + 1));
            }
            return null;
          }
        });
    }
}
