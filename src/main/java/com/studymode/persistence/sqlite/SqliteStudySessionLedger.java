package com.studymode.persistence.sqlite;

import com.studymode.application.error.StudyDataException;
import com.studymode.application.port.StudySessionLedger;
import com.studymode.domain.study.SessionStatus;
import com.studymode.domain.study.StudySession;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Durable foundation adapter. No timer or automatic session creation is implemented in M1. */
public final class SqliteStudySessionLedger implements StudySessionLedger {
    private final SqliteDatabase database;
    public SqliteStudySessionLedger(SqliteDatabase database) { this.database = database; }

    @Override public void save(StudySession session) {
        String sql = """
                INSERT INTO study_session(id,profile_id,started_at,ended_at,active_seconds,zone_id,status) VALUES (?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET ended_at=excluded.ended_at,active_seconds=excluded.active_seconds,status=excluded.status
                WHERE study_session.profile_id=excluded.profile_id AND study_session.started_at=excluded.started_at
                AND study_session.zone_id=excluded.zone_id AND study_session.active_seconds<=excluded.active_seconds
                AND (study_session.ended_at IS NULL OR
                    (study_session.ended_at=excluded.ended_at AND study_session.status=excluded.status
                     AND study_session.active_seconds=excluded.active_seconds))
                """;
        try (var connection = database.open(); var save = connection.prepareStatement(sql)) {
            save.setString(1, session.id().toString()); save.setString(2, session.profileId().toString());
            save.setString(3, session.startedAt().toString());
            save.setString(4, session.endedAt() == null ? null : session.endedAt().toString());
            save.setLong(5, session.activeFocusSeconds()); save.setString(6, session.studyZone().getId());
            save.setString(7, session.status().name());
            if (save.executeUpdate() != 1) throw new StudyDataException("Session changes conflict with saved facts. Completed sessions cannot be rewritten.");
        } catch (SQLException e) { throw database.failure("save the study session", e); }
    }

    @Override public List<StudySession> findByPeriod(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) throw new IllegalArgumentException("Choose a valid start-inclusive, end-exclusive period.");
        List<StudySession> sessions = new ArrayList<>();
        // Java Instant comparisons preserve nanoseconds; SQLite julianday rounds to milliseconds.
        // M3 will introduce indexed/paged history alongside the full session lifecycle schema.
        String sql = "SELECT * FROM study_session";
        try (var connection = database.open(); var query = connection.prepareStatement(sql)) {
            try (var row = query.executeQuery()) {
                while (row.next()) {
                    String end = row.getString("ended_at");
                    sessions.add(new StudySession(UUID.fromString(row.getString("id")), UUID.fromString(row.getString("profile_id")),
                            Instant.parse(row.getString("started_at")), end == null ? null : Instant.parse(end), row.getLong("active_seconds"),
                            ZoneId.of(row.getString("zone_id")), SessionStatus.valueOf(row.getString("status"))));
                }
            }
            return sessions.stream().filter(session -> !session.startedAt().isBefore(from) && session.startedAt().isBefore(to))
                    .sorted(java.util.Comparator.comparing(StudySession::startedAt).thenComparing(StudySession::id)).toList();
        } catch (SQLException | IllegalArgumentException | java.time.DateTimeException e) { throw database.failure("read study sessions", e); }
    }
}
