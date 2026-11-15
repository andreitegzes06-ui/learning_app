package com.studymode.persistence.sqlite;

import com.studymode.application.error.StudyDataException;
import com.studymode.application.port.StudySessionLedger;
import com.studymode.domain.study.SessionStatus;
import com.studymode.domain.study.StudySession;
import com.studymode.domain.study.SessionContext;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Durable focus ledger; immutable settings/final facts and unique unfinished session constraints are preserved. */
public final class SqliteStudySessionLedger implements StudySessionLedger {
    private final SqliteDatabase database;
    public SqliteStudySessionLedger(SqliteDatabase database) { this.database = database; }

    @Override public void save(StudySession session) {
        database.transaction("save the study session", c -> { write(c, session); return null; });
    }
    @Override public java.util.Optional<StudySession> find(UUID id) {
        return database.transaction("read study session", c -> SqliteStatements.query(c,
                "SELECT * FROM study_session WHERE id=?", SqliteStudySessionLedger::readRow, id).stream().findFirst());
    }
    @Override public List<StudySession> findUnfinished() {
        return database.transaction("read unfinished sessions", c -> SqliteStatements.query(c,
                "SELECT * FROM study_session WHERE ended_at IS NULL", SqliteStudySessionLedger::readRow));
    }
    @Override public boolean delete(UUID id) {
        return database.transaction("delete study session", c -> SqliteStatements.update(c, "DELETE FROM study_session WHERE id=?", id) == 1);
    }
    static void write(java.sql.Connection connection, StudySession session) throws SQLException {
        var previous = SqliteStatements.query(connection, "SELECT * FROM study_session WHERE id=?", SqliteStudySessionLedger::readRow, session.id());
        if (!previous.isEmpty()) {
            var saved = previous.getFirst();
            if (!saved.context().equals(session.context()) || session.checkpointAt().isBefore(saved.checkpointAt())
                    || (saved.finished() && !saved.equals(session))) {
                throw new StudyDataException("Saved session settings and finalized facts cannot be rewritten; checkpoints cannot move backwards.");
            }
        }
        String sql = """
                INSERT INTO study_session(id,profile_id,started_at,ended_at,active_seconds,zone_id,status,
                    classroom_id,goal_id,classroom_name,goal_name,planned_seconds,is_demo,checkpoint_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET ended_at=excluded.ended_at,active_seconds=excluded.active_seconds,
                    status=excluded.status,checkpoint_at=excluded.checkpoint_at
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
            SessionContext context = session.context();
            save.setString(8, context.classroomId() == null ? null : context.classroomId().toString());
            save.setString(9, context.goalId() == null ? null : context.goalId().toString());
            save.setString(10, context.classroomName()); save.setString(11, context.goalName());
            save.setInt(12, context.plannedFocusSeconds()); save.setInt(13, context.demo() ? 1 : 0);
            save.setString(14, session.checkpointAt().toString());
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
        return SqliteStatements.query(connection, "SELECT * FROM study_session", SqliteStudySessionLedger::readRow).stream()
                .sorted(java.util.Comparator.comparing(StudySession::startedAt).thenComparing(StudySession::id)).toList();
    }
    private static StudySession readRow(java.sql.ResultSet row) throws SQLException {
        String end = row.getString("ended_at"); String classroom = row.getString("classroom_id"); String goal = row.getString("goal_id");
        return new StudySession(UUID.fromString(row.getString("id")), UUID.fromString(row.getString("profile_id")),
                Instant.parse(row.getString("started_at")), end == null ? null : Instant.parse(end), row.getLong("active_seconds"),
                ZoneId.of(row.getString("zone_id")), SessionStatus.valueOf(row.getString("status")),
                new SessionContext(classroom == null ? null : UUID.fromString(classroom), row.getString("classroom_name"),
                        goal == null ? null : UUID.fromString(goal), row.getString("goal_name"), row.getInt("planned_seconds"), row.getInt("is_demo") == 1),
                Instant.parse(row.getString("checkpoint_at")));
    }
}
