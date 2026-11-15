package com.studymode.persistence.sqlite;

import com.studymode.application.port.UserProfileStore;
import com.studymode.application.error.StudyDataException;
import com.studymode.domain.profile.UserProfile;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

public final class SqliteUserProfileStore implements UserProfileStore {
    private final SqliteDatabase database;
    public SqliteUserProfileStore(SqliteDatabase database) { this.database = database; }

    @Override public Optional<UserProfile> findLocalProfile() {
        try (var connection = database.open(); var query = connection.prepareStatement("SELECT * FROM user_profile WHERE slot = 1");
             var row = query.executeQuery()) {
            if (!row.next()) return Optional.empty();
            return Optional.of(new UserProfile(UUID.fromString(row.getString("id")), row.getString("display_name"),
                    Instant.parse(row.getString("created_at")), ZoneId.of(row.getString("zone_id"))));
        } catch (SQLException | IllegalArgumentException | java.time.DateTimeException e) {
            throw database.failure("read your profile", e);
        }
    }

    @Override public void save(UserProfile profile) {
        String sql = """
                INSERT INTO user_profile(slot,id,display_name,created_at,zone_id) VALUES (1,?,?,?,?)
                ON CONFLICT(slot) DO UPDATE SET display_name=excluded.display_name, zone_id=excluded.zone_id
                WHERE user_profile.id=excluded.id AND user_profile.created_at=excluded.created_at
                """;
        try (var connection = database.open(); var save = connection.prepareStatement(sql)) {
            save.setString(1, profile.id().toString()); save.setString(2, profile.displayName());
            save.setString(3, profile.createdAt().toString()); save.setString(4, profile.studyZone().getId());
            if (save.executeUpdate() != 1) throw new StudyDataException("A different local profile already exists. Reload it before editing.");
        } catch (SQLException e) { throw database.failure("save your profile", e); }
    }
}
