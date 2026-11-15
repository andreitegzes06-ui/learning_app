package com.studymode.persistence.sqlite;

import com.studymode.application.data.*;
import com.studymode.application.port.StudyDataArchive;
import com.studymode.application.error.DataImportException;
import com.studymode.domain.study.*;
import com.studymode.domain.workspace.BlockedApplication;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

/** A consistent multi-table snapshot/replacement boundary, not a generic CRUD repository. */
public final class SqliteStudyDataArchive implements StudyDataArchive {
    private final SqliteDatabase database;
    private final Clock clock;
    public SqliteStudyDataArchive(SqliteDatabase database, Clock clock) { this.database = database; this.clock = clock; }
    @Override public StudyDataSnapshot snapshot() { return database.transaction("read a consistent study backup", this::read); }
    private StudyDataSnapshot read(Connection c) throws SQLException {
        return new StudyDataSnapshot(2, clock.instant(), SqliteUserProfileStore.readAll(c), SqliteStudySessionLedger.readAll(c),
                SqliteClassroomCatalog.readAll(c), SqliteBlockedApplicationCatalog.readAll(c).stream().map(StudyDataSnapshot.ApplicationSelection::from).toList(),
                SqliteFocusWorkspaceStore.readAll(c).stream().map(StudyDataSnapshot.WorkspaceSelection::from).toList(),
                SqliteStudyPlanStore.readGoals(c), SqliteStudyPlanStore.readTasks(c), SqliteAchievementStore.readDefinitions(c), SqliteAchievementStore.readAwards(c));
    }
    @Override public void replace(StudyDataSnapshot data, Consumer<StudyDataSnapshot> backupWriter) {
        new SnapshotValidator().validate(data);
        database.transaction("replace study data (previous data is retained on failure)", c -> {
            StudyDataSnapshot previous = read(c);
            if (previous.sessions().stream().anyMatch(s -> !s.finished())) throw new DataImportException("Stop the active or paused session before importing data.");
            backupWriter.accept(previous);
            // Constant table names in dependency order; all user data is bound through prepared statements.
            for (String table : List.of("workspace_resource", "workspace_application", "focus_workspace", "study_session", "study_task", "study_goal", "achievement_award", "achievement", "study_resource", "classroom", "blocked_application", "user_profile")) {
                SqliteStatements.update(c, "DELETE FROM " + table);
            }
            for (var p : data.profiles()) SqliteUserProfileStore.write(c, p);
            for (var classroom : data.classrooms()) SqliteClassroomCatalog.write(c, classroom);
            Map<String, BlockedApplication> apps = new HashMap<>();
            for (var selection : data.applications()) {
                var app = selection.toModel(); SqliteBlockedApplicationCatalog.write(c, app); apps.put(StudyDataSnapshot.pathKey(selection.executablePath()), app);
            }
            for (var workspace : data.workspaces()) SqliteFocusWorkspaceStore.write(c, workspace.toModel(apps));
            for (var goal : data.goals()) SqliteStudyPlanStore.write(c, goal);
            for (var task : data.tasks()) SqliteStudyPlanStore.write(c, task);
            for (var achievement : data.achievements()) SqliteAchievementStore.write(c, achievement);
            for (var award : data.awards()) SqliteAchievementStore.write(c, award);
            for (var session : data.sessions()) {
                if (!session.finished()) session = new StudySession(session.id(), session.profileId(), session.startedAt(),
                        session.checkpointAt(), session.activeFocusSeconds(), session.studyZone(), SessionStatus.INTERRUPTED,
                        session.context(), session.checkpointAt());
                SqliteStudySessionLedger.write(c, session);
            }
            return null;
        });
    }
}
