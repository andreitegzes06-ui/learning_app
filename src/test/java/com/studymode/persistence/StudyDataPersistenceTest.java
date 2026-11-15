package com.studymode.persistence;

import com.studymode.application.error.*;
import com.studymode.domain.classroom.*;
import com.studymode.domain.workspace.*;
import com.studymode.domain.planning.*;
import com.studymode.domain.reward.*;
import com.studymode.persistence.sqlite.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StudyDataPersistenceTest {
    @TempDir Path root;
    private SqliteDatabase database() { return new SqliteDatabase(root.resolve("nested/data/study.db")); }

    @Test void automaticallyCreatesWritableDirectoryAndPersistsEveryEntityAcrossReopen() {
        SqliteDatabase db = database(); var expected = StudyDataFixture.populate(db);
        assertTrue(Files.exists(root.resolve("nested/data/study.db")));
        new SchemaMigrator(database()).migrate();
        assertEquals(expected, new SqliteStudyDataArchive(database(), StudyDataFixture.CLOCK).snapshot());
        assertEquals(1, new SqliteClassroomCatalog(db).findAll().size());
        assertTrue(new SqliteClassroomCatalog(db).find(expected.classrooms().getFirst().id()).isPresent());
    }
    @Test void classroomEditsRetainResourcesAndReferencedResourceRemovalRollsBackEverything() {
        var db = database(); var sample = StudyDataFixture.populate(db); var catalog = new SqliteClassroomCatalog(db);
        Classroom original = sample.classrooms().getFirst();
        var edited = new Classroom(original.id(), "Renamed", "New description", List.of(new StudyResource(original.resources().getFirst().id(), "Updated title", java.net.URI.create("https://example.com/new"))));
        catalog.save(edited); assertEquals(edited, catalog.find(original.id()).orElseThrow());
        assertThrows(StudyDataException.class, () -> catalog.save(new Classroom(original.id(), "Should roll back", "", List.of())));
        assertEquals(edited, catalog.find(original.id()).orElseThrow());
        assertThrows(StudyDataException.class, () -> catalog.save(new Classroom(UUID.randomUUID(), "Other", "", edited.resources())));
        assertEquals(1, catalog.findAll().size());
    }
    @Test void workspaceEditsAndFailedRelationshipChangesAreAtomic() {
        var db = database(); var sample = StudyDataFixture.populate(db); var store = new SqliteFocusWorkspaceStore(db);
        var original = store.findAll().getFirst();
        var edited = new FocusWorkspace(original.id(), "Long revision", original.classroomId(), new FocusPreferences(50,10,20,3), original.resourceIds(), original.blocklist());
        store.save(edited); assertEquals(edited, store.find(original.id()).orElseThrow());
        assertThrows(StudyDataException.class, () -> store.save(new FocusWorkspace(original.id(), "Bad", original.classroomId(), original.preferences(), Set.of(UUID.randomUUID()), Set.of())));
        assertEquals(edited, store.find(original.id()).orElseThrow());
        var other = new Classroom(UUID.randomUUID(), "Other", "", List.of()); new SqliteClassroomCatalog(db).save(other);
        assertThrows(StudyDataException.class, () -> store.save(new FocusWorkspace(original.id(), "Wrong class", other.id(), original.preferences(), original.resourceIds(), Set.of())));
        assertEquals(edited, store.find(original.id()).orElseThrow());
    }
    @Test void deletesProtectWorkspacesAndAwardsButDetachOptionalTaskLinks() {
        var db = database(); var sample = StudyDataFixture.populate(db);
        var classes = new SqliteClassroomCatalog(db); var apps = new SqliteBlockedApplicationCatalog(db); var plans = new SqliteStudyPlanStore(db);
        var awards = new SqliteAchievementStore(db); var workspaces = new SqliteFocusWorkspaceStore(db);
        UUID classroomId = sample.classrooms().getFirst().id(); var app = apps.findAll().getFirst();
        assertThrows(StudyDataException.class, () -> classes.delete(classroomId));
        assertThrows(StudyDataException.class, () -> apps.delete(app.executablePath()));
        assertThrows(StudyDataException.class, () -> awards.deleteDefinition("existing_award"));
        assertThrows(StudyDataException.class, () -> new SqliteUserProfileStore(db).delete(sample.profiles().getFirst().id()));
        assertTrue(plans.deleteGoal(sample.goals().getFirst().id())); assertNull(plans.tasks().getFirst().goalId());
        assertTrue(workspaces.delete(workspaces.findAll().getFirst().id())); assertTrue(classes.delete(classroomId));
        assertNull(plans.tasks().getFirst().classroomId()); assertTrue(apps.delete(app.executablePath()));
        assertTrue(awards.deleteAward(sample.profiles().getFirst().id(), "existing_award")); assertTrue(awards.deleteDefinition("existing_award"));
        assertTrue(plans.deleteTask(plans.tasks().getFirst().id()));
        var ledger = new SqliteStudySessionLedger(db); assertTrue(ledger.find(sample.sessions().getFirst().id()).isPresent()); assertTrue(ledger.delete(sample.sessions().getFirst().id()));
        assertTrue(new SqliteUserProfileStore(db).delete(sample.profiles().getFirst().id()));
        assertTrue(new SqliteUserProfileStore(db).findLocalProfile().isEmpty()); assertFalse(classes.delete(classroomId));
    }
    @Test void goalsTasksApplicationMetadataAndAwardFactsCanBeEditedWithoutAnEngine() {
        var db = database(); var sample = StudyDataFixture.populate(db); var plans = new SqliteStudyPlanStore(db);
        var goal = sample.goals().getFirst(); var changedGoal = new StudyGoal(goal.id(), goal.profileId(), "Updated", 90, GoalPeriod.WEEKLY);
        plans.save(changedGoal); assertEquals(changedGoal, plans.findGoal(goal.id()).orElseThrow());
        var task = sample.tasks().getFirst().complete(StudyDataFixture.NOW); plans.save(task); assertEquals(task, plans.findTask(task.id()).orElseThrow());
        var app = sample.applications().getFirst().toModel(); var apps = new SqliteBlockedApplicationCatalog(db);
        apps.save(new BlockedApplication("Renamed", app.executablePath())); assertEquals("Renamed", apps.find(app.executablePath()).orElseThrow().displayName());
        var awards = new SqliteAchievementStore(db); awards.save(new Achievement("existing_award", "Updated title", "Updated description"));
        awards.save(new AchievementAward(goal.profileId(), "existing_award", StudyDataFixture.NOW.plusSeconds(1)));
        assertEquals(1, awards.awards().size()); assertEquals("Updated title", awards.definitions().getFirst().title());
    }
    @Test void sqliteConstraintsAndPreparedStatementsRejectInvalidDataAndPreserveTables() throws Exception {
        var db = database(); StudyDataFixture.populate(db);
        try (var c = db.open(); var p = c.prepareStatement("INSERT INTO classroom(id,name,description) VALUES (?, ?, ?)")) {
            p.setString(1, UUID.randomUUID().toString()); p.setString(2, ""); p.setString(3, "");
            assertThrows(java.sql.SQLException.class, p::executeUpdate);
        }
        var plans = new SqliteStudyPlanStore(db);
        var error = assertThrows(StudyDataException.class, () -> plans.save(new StudyGoal(UUID.randomUUID(), UUID.randomUUID(), "Bad FK", 10, GoalPeriod.DAILY)));
        assertTrue(error.getMessage().contains("relationship")); assertNotNull(error.getCause());
        assertEquals(1, new SqliteClassroomCatalog(db).findAll().size());
        assertEquals(1, plans.goals().size());
    }
    @Test void migratesRealV1DatabasePreservingProfileAndSessionFacts() throws Exception {
        var db = database();
        try (var c = db.open(); var s = c.createStatement(); var input = getClass().getResourceAsStream("/db/migration/V001__foundation.sql")) {
            for (String sql : new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split(";")) if (!sql.isBlank()) s.execute(sql);
            s.execute("INSERT INTO schema_version VALUES (1,'2026-11-15T00:00:00Z')"); s.execute("PRAGMA user_version=1");
        }
        var profile = new com.studymode.domain.profile.UserProfile(UUID.randomUUID(), "Existing", StudyDataFixture.NOW, java.time.ZoneOffset.UTC);
        new SqliteUserProfileStore(db).save(profile);
        var session = new com.studymode.domain.study.StudySession(UUID.randomUUID(), profile.id(), StudyDataFixture.NOW.minusSeconds(60), StudyDataFixture.NOW, 60, profile.studyZone(), com.studymode.domain.study.SessionStatus.COMPLETED);
        try (var c = db.open(); var insert = c.prepareStatement("INSERT INTO study_session VALUES (?,?,?,?,?,?,?)")) {
            insert.setString(1,session.id().toString()); insert.setString(2,profile.id().toString());
            insert.setString(3,session.startedAt().toString()); insert.setString(4,session.endedAt().toString());
            insert.setLong(5,60); insert.setString(6,profile.studyZone().getId()); insert.setString(7,"COMPLETED"); insert.executeUpdate();
        }
        new SchemaMigrator(db).migrate(); new SchemaMigrator(db).migrate();
        assertEquals(profile, new SqliteUserProfileStore(db).findLocalProfile().orElseThrow());
        assertEquals(session, new SqliteStudySessionLedger(db).find(session.id()).orElseThrow());
        assertTrue(new SqliteClassroomCatalog(db).findAll().isEmpty());
        try (var c = db.open(); var s = c.createStatement(); var rows = s.executeQuery("SELECT count(*) FROM schema_version")) { rows.next(); assertEquals(4, rows.getInt(1)); }
    }
    @Test void failedV2MigrationRollsBackAndLeavesV1Untouched() throws Exception {
        var db = database();
        try (var c = db.open(); var s = c.createStatement(); var input = getClass().getResourceAsStream("/db/migration/V001__foundation.sql")) {
            for (String sql : new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split(";")) if (!sql.isBlank()) s.execute(sql);
            s.execute("INSERT INTO schema_version VALUES (1,'2026-11-15T00:00:00Z')"); s.execute("PRAGMA user_version=1"); s.execute("CREATE TABLE classroom(sentinel TEXT)");
        }
        assertThrows(StudyDataException.class, () -> new SchemaMigrator(db).migrate());
        try (var c = db.open(); var s = c.createStatement(); var row = s.executeQuery("PRAGMA user_version")) { row.next(); assertEquals(1, row.getInt(1)); }
    }
}
