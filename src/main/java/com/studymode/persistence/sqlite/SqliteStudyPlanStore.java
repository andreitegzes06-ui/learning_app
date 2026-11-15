package com.studymode.persistence.sqlite;

import com.studymode.application.port.StudyPlanStore;
import com.studymode.domain.planning.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static com.studymode.persistence.sqlite.SqliteStatements.*;

public final class SqliteStudyPlanStore implements StudyPlanStore {
    private final SqliteDatabase database;
    public SqliteStudyPlanStore(SqliteDatabase database) { this.database = database; }
    @Override public List<StudyGoal> goals() { return database.transaction("load goals", SqliteStudyPlanStore::readGoals); }
    @Override public List<StudyTask> tasks() { return database.transaction("load tasks", SqliteStudyPlanStore::readTasks); }
    @Override public Optional<StudyGoal> findGoal(UUID id) { return goals().stream().filter(g -> g.id().equals(id)).findFirst(); }
    @Override public Optional<StudyTask> findTask(UUID id) { return tasks().stream().filter(t -> t.id().equals(id)).findFirst(); }
    @Override public void save(StudyGoal goal) { database.transaction("save study goal", c -> { write(c, goal); return null; }); }
    @Override public void save(StudyTask task) { database.transaction("save study task", c -> { write(c, task); return null; }); }
    @Override public boolean deleteGoal(UUID id) { return database.transaction("delete goal", c -> update(c, "DELETE FROM study_goal WHERE id=?", id) == 1); }
    @Override public boolean deleteTask(UUID id) { return database.transaction("delete task", c -> update(c, "DELETE FROM study_task WHERE id=?", id) == 1); }
    static List<StudyGoal> readGoals(Connection c) throws SQLException {
        return query(c, "SELECT * FROM study_goal ORDER BY id", r -> new StudyGoal(UUID.fromString(r.getString("id")), UUID.fromString(r.getString("profile_id")), r.getString("text"), r.getInt("target_minutes"), GoalPeriod.valueOf(r.getString("period")), uuid(r.getString("classroom_id"))));
    }
    static List<StudyTask> readTasks(Connection c) throws SQLException {
        return query(c, "SELECT * FROM study_task ORDER BY id", r -> new StudyTask(UUID.fromString(r.getString("id")), UUID.fromString(r.getString("profile_id")), r.getString("text"), uuid(r.getString("classroom_id")), uuid(r.getString("goal_id")),
                r.getString("due_date") == null ? null : LocalDate.parse(r.getString("due_date")), r.getString("completed_at") == null ? null : Instant.parse(r.getString("completed_at"))));
    }
    private static UUID uuid(String value) { return value == null ? null : UUID.fromString(value); }
    static void write(Connection c, StudyGoal g) throws SQLException {
        if (!query(c, "SELECT id FROM study_goal WHERE profile_id=? AND lower(trim(text))=lower(trim(?)) AND id<>?", r -> r.getString(1), g.profileId(), g.text(), g.id()).isEmpty())
            throw new com.studymode.application.error.StudyDataException("A goal with this name already exists.");
        var linked = SqliteStudySessionLedger.readAll(c).stream().filter(s -> g.id().equals(s.context().goalId())).toList();
        if (linked.stream().anyMatch(s -> !s.profileId().equals(g.profileId()) || (g.classroomId()!=null && !g.classroomId().equals(s.context().classroomId()))))
            throw new com.studymode.application.error.StudyDataException("The goal assignment conflicts with its recorded sessions. Keep the original classroom or remove its assignment.");
        update(c, "INSERT INTO study_goal(id,profile_id,text,target_minutes,period,classroom_id) VALUES (?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET profile_id=excluded.profile_id,text=excluded.text,target_minutes=excluded.target_minutes,period=excluded.period,classroom_id=excluded.classroom_id", g.id(), g.profileId(), g.text(), g.targetMinutes(), g.period(), g.classroomId());
    }
    static void write(Connection c, StudyTask t) throws SQLException {
        update(c, """
                INSERT INTO study_task VALUES (?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET profile_id=excluded.profile_id,
                text=excluded.text,classroom_id=excluded.classroom_id,goal_id=excluded.goal_id,due_date=excluded.due_date,completed_at=excluded.completed_at
                """, t.id(), t.profileId(), t.text(), t.classroomId(), t.goalId(), t.dueDate(), t.completedAt());
    }
}
