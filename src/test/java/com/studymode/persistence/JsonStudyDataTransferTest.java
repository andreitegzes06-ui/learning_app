package com.studymode.persistence;

import com.studymode.application.data.*;
import com.studymode.application.error.*;
import com.studymode.domain.study.*;
import com.studymode.persistence.sqlite.*;
import com.studymode.persistence.transfer.JsonStudyDataTransfer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JsonStudyDataTransferTest {
    @TempDir Path root;
    SqliteDatabase db;
    SqliteStudyDataArchive archive;
    JsonStudyDataTransfer transfer;
    StudyDataSnapshot sample;
    Path export;
    @BeforeEach void setup() throws Exception {
        db = new SqliteDatabase(root.resolve("data/study.db")); sample = StudyDataFixture.populate(db);
        archive = new SqliteStudyDataArchive(db, StudyDataFixture.CLOCK);
        transfer = new JsonStudyDataTransfer(archive, root.resolve("data")); export = root.resolve("export.json");
        transfer.exportTo(export);
    }
    @Test void exportsEveryEntityImportsAcrossDatabasesAndCreatesReadableRecoveryBackup() throws Exception {
        StudyDataSnapshot inspected = transfer.inspectImport(export); assertEquals(sample, inspected);
        var destination = new SqliteDatabase(root.resolve("destination/study.db")); new SchemaMigrator(destination).migrate();
        var destinationArchive = new SqliteStudyDataArchive(destination, StudyDataFixture.CLOCK);
        var importer = new JsonStudyDataTransfer(destinationArchive, root.resolve("destination"));
        Path backup = importer.importSnapshot(inspected);
        assertTrue(Files.exists(backup)); assertEquals(0, importer.inspectImport(backup).recordCount());
        assertEquals(sample, destinationArchive.snapshot());
        importer.exportTo(root.resolve("roundtrip.json")); assertEquals(sample, importer.inspectImport(root.resolve("roundtrip.json")));
    }
    @Test void replacingRecordsKeepsBackupOfPreviousProfileAndData() throws Exception {
        var original = sample.profiles().getFirst();
        new SqliteUserProfileStore(db).save(original.withDetails("Changed locally", original.studyZone()));
        Path backup = transfer.importSnapshot(transfer.inspectImport(export));
        assertEquals("Changed locally", transfer.inspectImport(backup).profiles().getFirst().displayName());
        assertEquals(sample, archive.snapshot());
    }
    @Test void rejectsMalformedUnknownDuplicateAndWrongTypedFieldsBeforeMutation() throws Exception {
        String valid = Files.readString(export);
        List<String> invalid = List.of("{", "null", valid + " {}", valid.replace("\"formatVersion\" : 4", "\"formatVersion\" : 99"),
                valid.replaceFirst("\\{", "{\"unknownField\":true,"), valid.replaceFirst("\\{", "{\"formatVersion\":1,"),
                valid.replace("\"targetMinutes\" : 30", "\"targetMinutes\" : 0"),
                valid.replace("\"targetMinutes\" : 30", "\"targetMinutes\" : \"30\""),
                valid.replace("\"targetMinutes\" : 30", "\"targetMinutes\" : 1.5"),
                valid.replace("https://classroom.google.com/c/oop", "javascript:alert(1)"));
        for (int i = 0; i < invalid.size(); i++) {
            Path broken = root.resolve("bad-" + i + ".json"); Files.writeString(broken, invalid.get(i));
            assertThrows(DataImportException.class, () -> transfer.inspectImport(broken), "invalid case " + i);
            assertEquals(sample, archive.snapshot());
        }
    }
    @Test void rejectsMissingReferencesAndDuplicateIdentifiersBeforeReplacement() {
        var badTasks = List.of(new com.studymode.domain.planning.StudyTask(UUID.randomUUID(), UUID.randomUUID(), "Unknown profile", null, null, null, null));
        var bad = new StudyDataSnapshot(1, sample.exportedAt(), sample.profiles(), sample.sessions(), sample.classrooms(), sample.applications(), sample.workspaces(), sample.goals(), badTasks, sample.achievements(), sample.awards());
        assertThrows(DataImportException.class, () -> transfer.importSnapshot(bad));
        var duplicate = new StudyDataSnapshot(1, sample.exportedAt(), sample.profiles(), sample.sessions(), List.of(sample.classrooms().getFirst(), sample.classrooms().getFirst()), sample.applications(), sample.workspaces(), sample.goals(), sample.tasks(), sample.achievements(), sample.awards());
        assertThrows(DataImportException.class, () -> transfer.importSnapshot(duplicate)); assertEquals(sample, archive.snapshot());
    }
    @Test void rejectsDuplicateWorkspaceSelectionsInsteadOfSilentlyDeduplicating() throws Exception {
        var workspace = sample.workspaces().getFirst();
        var duplicate = new StudyDataSnapshot.WorkspaceSelection(workspace.id(), workspace.name(), workspace.classroomId(), workspace.preferences(),
                List.of(workspace.resourceIds().getFirst(), workspace.resourceIds().getFirst()), workspace.blockedPaths());
        var bad = new StudyDataSnapshot(1, sample.exportedAt(), sample.profiles(), sample.sessions(), sample.classrooms(), sample.applications(), List.of(duplicate), sample.goals(), sample.tasks(), sample.achievements(), sample.awards());
        assertThrows(DataImportException.class, () -> transfer.importSnapshot(bad)); assertEquals(sample, archive.snapshot());
    }
    @Test void failedSqlInsertionRollsBackAllDeletesAndLeavesRecoveryBackup() throws Exception {
        try (var c = db.open(); var s = c.createStatement()) {
            s.execute("CREATE TRIGGER injected_failure BEFORE INSERT ON study_task BEGIN SELECT RAISE(ABORT,'injected persistence failure'); END");
        }
        assertThrows(StudyDataException.class, () -> transfer.importSnapshot(sample));
        assertEquals(sample, archive.snapshot());
        try (var files = Files.list(root.resolve("data/backups"))) { assertEquals(1, files.count()); }
    }
    @Test void cannotReplaceWithoutSuccessfullyWritingTheRecoveryBackup() throws Exception {
        Files.writeString(root.resolve("data/backups"), "This file prevents creating a backup directory");
        assertThrows(IOException.class, () -> transfer.importSnapshot(sample)); assertEquals(sample, archive.snapshot());
    }
    @Test void doesNotOverwriteExistingExportsOrApplicationFiles() throws Exception {
        byte[] original = Files.readAllBytes(export);
        assertThrows(IOException.class, () -> transfer.exportTo(export)); assertArrayEquals(original, Files.readAllBytes(export));
        assertThrows(IOException.class, () -> transfer.exportTo(root.resolve("data/backup.json")));
        assertThrows(IOException.class, () -> transfer.exportTo(root.resolve("data/study.db")));
        assertThrows(IOException.class, () -> transfer.exportTo(root.resolve("missing/export.json")));
        assertThrows(IOException.class, () -> transfer.inspectImport(root.resolve("missing.json")));
        assertEquals(sample, archive.snapshot());
    }
    @Test void importsUnfinishedSessionsAsInterruptedWithoutActivatingAnyWorkflow() throws Exception {
        var existing = sample.sessions().getFirst();
        var unfinished = new StudySession(existing.id(), existing.profileId(), existing.startedAt(), null, 15, existing.studyZone(), SessionStatus.PAUSED);
        var incoming = new StudyDataSnapshot(1, sample.exportedAt(), sample.profiles(), List.of(unfinished), sample.classrooms(), sample.applications(), sample.workspaces(), sample.goals(), sample.tasks(), sample.achievements(), sample.awards());
        transfer.importSnapshot(incoming);
        var saved = archive.snapshot().sessions().getFirst(); assertEquals(SessionStatus.INTERRUPTED, saved.status()); assertEquals(15, saved.activeFocusSeconds()); assertNotNull(saved.endedAt());
        new SqliteStudySessionLedger(db).save(new StudySession(UUID.randomUUID(), existing.profileId(), existing.startedAt(), null, 0, existing.studyZone(), SessionStatus.ACTIVE));
        assertThrows(DataImportException.class, () -> transfer.importSnapshot(sample)); assertEquals(2, archive.snapshot().sessions().size());
    }
    @Test void enforcesImportSizeAndParserDepthLimitsWithoutChangingDatabase() throws Exception {
        Path oversized = root.resolve("oversized.json");
        try (var out = Files.newOutputStream(oversized)) { byte[] block = new byte[1024 * 1024]; for (int i=0; i<21; i++) out.write(block); }
        assertThrows(DataImportException.class, () -> transfer.inspectImport(oversized));
        Path nested = root.resolve("nested.json"); Files.writeString(nested, "[".repeat(100) + "0" + "]".repeat(100));
        assertThrows(DataImportException.class, () -> transfer.inspectImport(nested)); assertEquals(sample, archive.snapshot());
    }
}
