package com.studymode.persistence;

import com.studymode.application.data.StudyDataSnapshot;
import com.studymode.domain.profile.UserProfile;
import com.studymode.domain.study.*;
import com.studymode.domain.classroom.*;
import com.studymode.domain.workspace.*;
import com.studymode.domain.planning.*;
import com.studymode.domain.reward.*;
import com.studymode.persistence.sqlite.*;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

final class StudyDataFixture {
    static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static StudyDataSnapshot populate(SqliteDatabase db) {
        new SchemaMigrator(db).migrate();
        var profile = new UserProfile(UUID.randomUUID(), "Student's profile", NOW.minusSeconds(3600), ZoneId.of("Europe/London"));
        new SqliteUserProfileStore(db).save(profile);
        var resource = StudyResource.fromUrl(UUID.randomUUID(), "Lecture", "https://classroom.google.com/c/oop");
        var classroom = new Classroom(UUID.randomUUID(), "OOP '); DROP TABLE classroom; --", "Java study", List.of(resource));
        new SqliteClassroomCatalog(db).save(classroom);
        var app = new BlockedApplication("Example", Path.of(System.getProperty("java.io.tmpdir")).resolve("example.exe").toAbsolutePath());
        new SqliteBlockedApplicationCatalog(db).save(app);
        new SqliteFocusWorkspaceStore(db).save(new FocusWorkspace(UUID.randomUUID(), "Revision", classroom.id(), FocusPreferences.defaults(), Set.of(resource.id()), Set.of(app)));
        var plan = new SqliteStudyPlanStore(db);
        var goal = new StudyGoal(UUID.randomUUID(), profile.id(), "Study Java", 30, GoalPeriod.DAILY); plan.save(goal);
        plan.save(new StudyTask(UUID.randomUUID(), profile.id(), "Read lecture", classroom.id(), goal.id(), LocalDate.of(2026,11,16), null));
        var awards = new SqliteAchievementStore(db);
        awards.save(new Achievement("existing_award", "Saved award", "Imported or manually recorded facts only"));
        awards.save(new AchievementAward(profile.id(), "existing_award", NOW));
        new SqliteStudySessionLedger(db).save(new StudySession(UUID.randomUUID(), profile.id(), NOW.minusSeconds(120), NOW, 60, profile.studyZone(), SessionStatus.COMPLETED));
        return new SqliteStudyDataArchive(db, CLOCK).snapshot();
    }
}
