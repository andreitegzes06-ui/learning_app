package com.studymode.persistence;

import com.studymode.application.error.StudyDataException;
import com.studymode.application.service.ProfileService;
import com.studymode.domain.profile.UserProfile;
import com.studymode.domain.study.*;
import com.studymode.persistence.sqlite.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SqliteFoundationTest {
    @TempDir Path root;
    SqliteDatabase database;
    SqliteUserProfileStore profiles;
    final Instant start = Instant.parse("2026-11-15T12:00:00Z");
    final ZoneId zone = ZoneId.of("UTC");
    @BeforeEach void initialize() {
        database = new SqliteDatabase(root.resolve("study.db")); new SchemaMigrator(database).migrate();
        profiles = new SqliteUserProfileStore(database);
    }
    @Test void profileSurvivesCreateEditAndReopenWithIdentityIntact() {
        assertTrue(profiles.findLocalProfile().isEmpty());
        var service = new ProfileService(profiles, Clock.fixed(start, ZoneOffset.UTC));
        var original = service.saveDetails("Andrei", zone);
        var edited = service.saveDetails("Alex", ZoneId.of("Europe/London"));
        assertEquals(original.id(), edited.id()); assertEquals(start, edited.createdAt());
        var reopened = new SqliteUserProfileStore(new SqliteDatabase(root.resolve("study.db"))).findLocalProfile().orElseThrow();
        assertEquals(edited, reopened);
        assertThrows(IllegalArgumentException.class, () -> service.saveDetails(" ", zone));
        assertEquals(edited, profiles.findLocalProfile().orElseThrow());
    }
    @Test void preventsReplacingTheLocalProfileWithADifferentIdentity() {
        var original = new UserProfile(UUID.randomUUID(), "A", start, zone); profiles.save(original);
        assertThrows(StudyDataException.class, () -> profiles.save(new UserProfile(UUID.randomUUID(), "B", start, zone)));
        assertEquals(original, profiles.findLocalProfile().orElseThrow());
    }
    @Test void migrationIsRepeatableAndRejectsNewerSchemas() throws Exception {
        new SchemaMigrator(database).migrate();
        try (var connection = database.open(); var statement = connection.createStatement()) { statement.execute("PRAGMA user_version = 99"); }
        assertThrows(StudyDataException.class, () -> new SchemaMigrator(database).migrate());
    }
    @Test void corruptDatabaseIsNotSilentlyReset() throws Exception {
        Path broken = root.resolve("broken.db"); byte[] original = "not a SQLite database".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(broken, original);
        assertThrows(StudyDataException.class, () -> new SchemaMigrator(new SqliteDatabase(broken)).migrate());
        assertArrayEquals(original, Files.readAllBytes(broken));
        assertThrows(StudyDataException.class, () -> new SchemaMigrator(new SqliteDatabase(root)).migrate());
    }
    @Test void ledgerPersistsQueriesAndFinalizesIdempotently() {
        UUID profileId = UUID.randomUUID(); profiles.save(new UserProfile(profileId, "Student", start, zone));
        var ledger = new SqliteStudySessionLedger(database); UUID sessionId = UUID.randomUUID();
        ledger.save(new StudySession(sessionId, profileId, start, null, 0, zone, SessionStatus.ACTIVE));
        ledger.save(new StudySession(sessionId, profileId, start, null, 15, zone, SessionStatus.PAUSED));
        var finished = new StudySession(sessionId, profileId, start, start.plusSeconds(90), 60, zone, SessionStatus.COMPLETED);
        ledger.save(finished); ledger.save(finished);
        var found = ledger.findByPeriod(start, start.plusSeconds(1)); assertEquals(java.util.List.of(finished), found);
        assertThrows(UnsupportedOperationException.class, found::clear);
        assertTrue(ledger.findByPeriod(start.minusSeconds(10), start).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ledger.findByPeriod(start, start));
        assertThrows(StudyDataException.class, () -> ledger.save(new StudySession(sessionId, profileId, start, null, 0, zone, SessionStatus.ACTIVE)));
    }
    @Test void ledgerEnforcesProfileForeignKeyAndSingleUnfinishedSession() {
        UUID profileId = UUID.randomUUID(); var ledger = new SqliteStudySessionLedger(database);
        var active = new StudySession(UUID.randomUUID(), profileId, start, null, 0, zone, SessionStatus.ACTIVE);
        assertThrows(StudyDataException.class, () -> ledger.save(active));
        profiles.save(new UserProfile(profileId, "Student", start, zone)); ledger.save(active);
        assertThrows(StudyDataException.class, () -> ledger.save(new StudySession(UUID.randomUUID(), profileId, start.plusSeconds(1), null, 0, zone, SessionStatus.ACTIVE)));
    }
    @Test void periodQueriesRespectNanosecondBoundariesAndChronologicalOrdering() {
        UUID profileId = UUID.randomUUID(); profiles.save(new UserProfile(profileId, "Student", start, zone));
        var ledger = new SqliteStudySessionLedger(database);
        var early = new StudySession(UUID.randomUUID(), profileId, start.plusNanos(1), start.plusSeconds(1), 0, zone, SessionStatus.COMPLETED);
        var late = new StudySession(UUID.randomUUID(), profileId, start.plusNanos(2), start.plusSeconds(1), 0, zone, SessionStatus.COMPLETED);
        ledger.save(late); ledger.save(early);
        assertEquals(java.util.List.of(early), ledger.findByPeriod(start, start.plusNanos(2)));
        assertEquals(java.util.List.of(late), ledger.findByPeriod(start.plusNanos(2), start.plusSeconds(1)));
        assertEquals(java.util.List.of(early, late), ledger.findByPeriod(start, start.plusSeconds(1)));
    }
}
