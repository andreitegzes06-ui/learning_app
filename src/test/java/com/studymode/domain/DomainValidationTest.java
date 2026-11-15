package com.studymode.domain;

import com.studymode.domain.classroom.*;
import com.studymode.domain.workspace.*;
import com.studymode.domain.profile.UserProfile;
import com.studymode.domain.study.*;
import com.studymode.domain.planning.*;
import com.studymode.domain.reward.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DomainValidationTest {
    private final UUID id = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-11-15T12:00:00Z");
    private final ZoneId zone = ZoneId.of("Europe/London");

    @ParameterizedTest @ValueSource(strings = {"", " ", "hello\nworld", "tab\tname"})
    void rejectsInvalidProfileNames(String name) {
        assertThrows(IllegalArgumentException.class, () -> new UserProfile(id, name, now, zone));
    }
    @Test void trimsNamesAndPreservesProfileIdentityOnEdit() {
        var original = new UserProfile(id, "  Andrei  ", now, zone);
        var edited = original.withDetails("Alex", ZoneId.of("UTC"));
        assertEquals("Andrei", original.displayName()); assertEquals(id, edited.id()); assertEquals(now, edited.createdAt());
        assertEquals("Alex", edited.displayName()); assertEquals(ZoneId.of("UTC"), edited.studyZone());
        assertThrows(IllegalArgumentException.class, () -> original.withDetails("a".repeat(61), zone));
        assertThrows(IllegalArgumentException.class, () -> new UserProfile(null, "A", now, zone));
    }
    @Test void validatesDurationBounds() {
        assertEquals(25, FocusPreferences.defaults().focusMinutes());
        assertDoesNotThrow(() -> new FocusPreferences(240, 1, 60, 12));
        assertThrows(IllegalArgumentException.class, () -> new FocusPreferences(0, 5, 15, 4));
        assertThrows(IllegalArgumentException.class, () -> new FocusPreferences(241, 5, 15, 4));
        assertThrows(IllegalArgumentException.class, () -> new FocusPreferences(25, 0, 15, 4));
        assertThrows(IllegalArgumentException.class, () -> new FocusPreferences(25, 5, 61, 4));
        assertThrows(IllegalArgumentException.class, () -> new FocusPreferences(25, 5, 15, 0));
    }
    @ParameterizedTest @ValueSource(strings = {"javascript:alert(1)", "file:///C:/private", "https://user:password@example.com", "https://", "not a url", "https://example.com:99999"})
    void rejectsUnsafeOrMalformedResources(String url) {
        assertThrows(IllegalArgumentException.class, () -> StudyResource.fromUrl(id, "Lecture", url));
    }
    @Test void classroomResourcesAreDefensiveAndUnique() {
        var resource = StudyResource.fromUrl(id, "Course", "https://classroom.google.com/c/course");
        var resources = new ArrayList<>(List.of(resource));
        var classroom = new Classroom(UUID.randomUUID(), " OOP ", "Java", resources);
        resources.clear(); assertEquals(1, classroom.resources().size());
        assertThrows(UnsupportedOperationException.class, () -> classroom.resources().clear());
        assertThrows(IllegalArgumentException.class, () -> new Classroom(id, "OOP", "", List.of(resource, resource)));
        assertThrows(IllegalArgumentException.class, () -> new Classroom(id, "OOP", "a".repeat(2001), List.of()));
    }
    @Test void workspaceUsesPathDeduplicationAndEnforcesClassroomMembership() {
        Path selected = Path.of(System.getProperty("java.io.tmpdir")).resolve("Example.exe").toAbsolutePath();
        var app = new BlockedApplication("Example", selected);
        var renamed = new BlockedApplication("Another label", selected.resolveSibling("EXAMPLE.EXE"));
        var apps = new HashSet<>(List.of(app, renamed)); assertEquals(1, apps.size());
        var classroom = new Classroom(id, "Java", "", List.of());
        var workspace = new FocusWorkspace(UUID.randomUUID(), "Revision", id, FocusPreferences.defaults(), Set.of(), apps);
        apps.clear(); assertEquals(1, workspace.blocklist().size());
        assertThrows(UnsupportedOperationException.class, () -> workspace.blocklist().clear());
        workspace.validateAgainst(classroom);
        var invalid = new FocusWorkspace(UUID.randomUUID(), "Revision", id, FocusPreferences.defaults(), Set.of(UUID.randomUUID()), Set.of());
        assertThrows(IllegalArgumentException.class, () -> invalid.validateAgainst(classroom));
        assertThrows(IllegalArgumentException.class, () -> workspace.validateAgainst(new Classroom(UUID.randomUUID(), "Other", "", List.of())));
        assertThrows(IllegalArgumentException.class, () -> new BlockedApplication("A", Path.of("relative.exe")));
        assertThrows(IllegalArgumentException.class, () -> new BlockedApplication("A", selected.resolveSibling("script.cmd")));
    }
    @Test void sessionsEnforceLifecycleFactsWithoutWallClockDurationAssumptions() {
        assertDoesNotThrow(() -> new StudySession(id, id, now, now.plusSeconds(60), 60, zone, SessionStatus.COMPLETED));
        assertThrows(IllegalArgumentException.class, () -> new StudySession(id, id, now, null, 0, zone, SessionStatus.COMPLETED));
        assertThrows(IllegalArgumentException.class, () -> new StudySession(id, id, now, now, 0, zone, SessionStatus.ACTIVE));
        assertThrows(IllegalArgumentException.class, () -> new StudySession(id, id, now, now.minusSeconds(1), 0, zone, SessionStatus.INTERRUPTED));
        assertThrows(IllegalArgumentException.class, () -> new StudySession(id, id, now, null, -1, zone, SessionStatus.ACTIVE));
    }
    @Test void goalsTasksAndAchievementsHaveMeaningfulValidation() {
        assertThrows(IllegalArgumentException.class, () -> new StudyGoal(id, id, "Read", 0, GoalPeriod.DAILY));
        assertThrows(IllegalArgumentException.class, () -> new StudyGoal(id, id, "Read", 10081, GoalPeriod.WEEKLY));
        assertThrows(IllegalArgumentException.class, () -> new StudyGoal(id, id, "Read", 30, null));
        assertEquals(10080, new StudyGoal(id, id, "Read", 10080, GoalPeriod.WEEKLY).targetMinutes());
        var task = new StudyTask(id, id, " Review ", null, null, LocalDate.of(2020, 1, 1), null);
        assertFalse(task.completed()); assertTrue(task.complete(now).completed()); assertEquals("Review", task.text());
        assertThrows(IllegalArgumentException.class, () -> new StudyTask(id, id, " ", null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new Achievement("Invalid Code", "First", "A first session"));
        assertEquals("first_focus", new Achievement("first_focus", "First focus", "Complete a session").code());
        assertThrows(IllegalArgumentException.class, () -> new AchievementAward(id, "first_focus", null));
        assertDoesNotThrow(() -> new AchievementAward(id, "first_focus", now));
    }
}
