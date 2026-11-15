package com.studymode.persistence;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.studymode.application.error.*;
import com.studymode.persistence.sqlite.*;
import com.studymode.persistence.transfer.JsonStudyDataTransfer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

class FocusCompatibilityTest {
    @TempDir Path root;
    @Test void legacyFormatOneImportsWithUnknownPlanAndKeepsStrictTypeValidation() throws Exception {
        var db=new SqliteDatabase(root.resolve("data/study.db")); var original=StudyDataFixture.populate(db);
        var archive=new SqliteStudyDataArchive(db,StudyDataFixture.CLOCK); var transfer=new JsonStudyDataTransfer(archive,root.resolve("data"));
        Path file=root.resolve("legacy.json"); transfer.exportTo(file); var json=new ObjectMapper(); ObjectNode document=(ObjectNode)json.readTree(file.toFile());
        document.put("formatVersion",1);
        for (JsonNode session:document.get("sessions")) { ((ObjectNode)session).remove("context"); ((ObjectNode)session).remove("checkpointAt"); }
        json.writeValue(file.toFile(),document);
        var imported=transfer.inspectImport(file); assertEquals(2,imported.formatVersion()); assertEquals(0,imported.sessions().getFirst().context().plannedFocusSeconds());
        transfer.importSnapshot(imported); assertEquals(original,archive.snapshot());
        ((ObjectNode)document.at("/profiles/0")).put("displayName",123); json.writeValue(file.toFile(),document);
        assertThrows(DataImportException.class,() -> transfer.inspectImport(file)); assertEquals(original,archive.snapshot());
    }
    @Test void formatTwoPreservesDemoAndReferencesWithActualSecondsOnly() throws Exception {
        var db=new SqliteDatabase(root.resolve("data/study.db")); var original=StudyDataFixture.populate(db);
        var archive=new SqliteStudyDataArchive(db,StudyDataFixture.CLOCK); var transfer=new JsonStudyDataTransfer(archive,root.resolve("data"));
        Path file=root.resolve("demo.json"); transfer.exportTo(file); var json=new ObjectMapper(); ObjectNode document=(ObjectNode)json.readTree(file.toFile());
        ObjectNode session=(ObjectNode)document.at("/sessions/0"); session.put("activeFocusSeconds",10);
        ObjectNode context=(ObjectNode)session.get("context"); context.put("plannedFocusSeconds",10).put("demo",true);
        context.put("classroomId",original.classrooms().getFirst().id().toString()).put("classroomName","Captured classroom");
        context.put("goalId",original.goals().getFirst().id().toString()).put("goalName","Captured goal");
        json.writeValue(file.toFile(),document); transfer.importSnapshot(transfer.inspectImport(file));
        var saved=archive.snapshot().sessions().getFirst(); assertTrue(saved.context().demo()); assertEquals(10,saved.activeFocusSeconds());
        transfer.exportTo(root.resolve("again.json")); assertEquals(archive.snapshot(),transfer.inspectImport(root.resolve("again.json")));
        for (String invalid:java.util.List.of("true","false")) { context.put("demo",invalid); json.writeValue(file.toFile(),document); assertThrows(DataImportException.class,() -> transfer.inspectImport(file)); }
    }
    @Test void schemaTwoUpgradeKeepsActualFocusAndRecoveryFacts() throws Exception {
        var db=new SqliteDatabase(root.resolve("old/study.db"));
        try (var c=db.open(); var s=c.createStatement()) {
            for (String migration:java.util.List.of("V001__foundation.sql","V002__study_data.sql")) {
                try (var input=getClass().getResourceAsStream("/db/migration/"+migration)) {
                    for (String sql:new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split(";")) if(!sql.isBlank()) s.execute(sql);
                }
            }
            s.execute("INSERT INTO schema_version VALUES (1,'2026-11-15T00:00:00Z'),(2,'2026-11-15T00:00:00Z')"); s.execute("PRAGMA user_version=2");
        }
        var id=java.util.UUID.randomUUID(); var profile=new com.studymode.domain.profile.UserProfile(id,"Old profile",StudyDataFixture.NOW,ZoneOffset.UTC);
        new SqliteUserProfileStore(db).save(profile);
        try(var c=db.open();var p=c.prepareStatement("INSERT INTO study_session VALUES (?,?,?,?,?,?,?)")) {
            p.setString(1,java.util.UUID.randomUUID().toString());p.setString(2,id.toString());p.setString(3,StudyDataFixture.NOW.toString());
            p.setString(4,StudyDataFixture.NOW.plusSeconds(90).toString());p.setLong(5,60);p.setString(6,"Z");p.setString(7,"COMPLETED");p.executeUpdate();
        }
        new SchemaMigrator(db).migrate();var saved=new SqliteStudySessionLedger(db).findByPeriod(Instant.MIN,Instant.MAX).getFirst();
        assertEquals(60,saved.activeFocusSeconds());assertEquals(saved.endedAt(),saved.checkpointAt());assertEquals(0,saved.context().plannedFocusSeconds());
        assertEquals(profile,new SqliteUserProfileStore(db).findLocalProfile().orElseThrow());
    }
}
