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
        database.transaction("save the study session", c -> { write(c, session); return null; });
    }
    @Override public java.util.Optional<StudySession> find(UUID id) {
        return database.transaction("read study session", c -> readAll(c).stream().filter(s -> s.id().equals(id)).findFirst());
    }
    @Override public boolean delete(UUID id) {
        return database.transaction("delete study session", c -> SqliteStatements.update(c, "DELETE FROM study_session WHERE id=?", id) == 1);
    }
    static void write(java.sql.Connection connection, StudySession session) throws SQLException {
        String sql = """
                INSERT INTO study_session(id,profile_id,started_at,ended_at,active_seconds,zone_id,status) VALUES (?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET ended_at=excluded.ended_at,active_seconds=excluded.active_seconds,status=excluded.status
                WHERE study_session.profile_id=excluded.profile_id AND study_session.started_at=excluded.started_at
                AND study_session.zone_id=excluded.zone_id AND study_session.active_seconds<=excluded.active_seconds
                AND (study_session.ended_at IS NULL OR
                    (study_session.ended_at=excluded.ended_at AND study_session.status=excluded.status
                     AND study_session.active_seconds=excluded.active_seconds))
                """;
        try (var save = connection.prepareStatement(sql)) {
            save.setString(1, session.id().toString()); save.setString(2, session.profileId().toString());
            save.setString(3, session.startedAt().toString());
            save.setString(4, session.endedAt() == null ? null : session.endedAt().toString());
            save.setLong(5, session.activeFocusSeconds()); save.setString(6, session.studyZone().getId());
            save.setString(7, session.status().name());
            if (save.executeUpdate() != 1) throw new StudyDataException("Session changes conflict with saved facts. Completed sessions cannot be rewritten.");
        }
    }

    @Override public List<StudySession> findByPeriod(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) throw new IllegalArgumentException("Choose a valid start-inclusive, end-exclusive period.");
        return database.transaction("read study sessions", connection -> readAll(connection).stream()
                .filter(session -> !session.startedAt().isBefore(from) && session.startedAt().isBefore(to))
                .sorted(java.util.Comparator.comparing(StudySession::startedAt).thenComparing(StudySession::id)).toList());
    }
    static List<StudySession> readAll(java.sql.Connection connection) throws SQLException {
        List<StudySession> sessions = new ArrayList<>();
        // Java Instant comparisons preserve nanoseconds; SQLite julianday rounds to milliseconds.
        // M3 will introduce indexed/paged history alongside the full session lifecycle schema.
        String sql = "SELECT * FROM study_session";
        try (var query = connection.prepareStatement(sql)) {
            try (var row = query.executeQuery()) {
                while (row.next()) {
                    String end = row.getString("ended_at");
                    sessions.add(new StudySession(UUID.fromString(row.getString("id")), UUID.fromString(row.getString("profile_id")),
                            Instant.parse(row.getString("started_at")), end == null ? null : Instant.parse(end), row.getLong("active_seconds"),
                            ZoneId.of(row.getString("zone_id")), SessionStatus.valueOf(row.getString("status"))));
                }
            }
            return sessions.stream().sorted(java.util.Comparator.comparing(StudySession::startedAt).thenComparing(StudySession::id)).toList();
        }
    }
}
