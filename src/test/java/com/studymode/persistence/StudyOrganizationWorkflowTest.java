package com.studymode.persistence;

import com.studymode.application.service.*;
import com.studymode.application.error.*;
import com.studymode.domain.classroom.*;
import com.studymode.domain.planning.*;
import com.studymode.domain.profile.*;
import com.studymode.domain.study.*;
import com.studymode.domain.workspace.*;
import com.studymode.persistence.sqlite.*;
import com.studymode.persistence.transfer.JsonStudyDataTransfer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class StudyOrganizationWorkflowTest {
    @TempDir Path root;
    private SqliteDatabase database;
    private StudyOrganizationService service;
    private SqliteStudySessionLedger ledger;
    private UserProfile profile;
    private Classroom classroom;
    private final Clock clock=Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"),ZoneOffset.UTC);
    @BeforeEach void setup() {
        database=new SqliteDatabase(root.resolve("data/study.db")); new SchemaMigrator(database).migrate();
        profile=new UserProfile(UUID.randomUUID(),"Student",clock.instant().minusSeconds(1000),ZoneOffset.UTC);
        new SqliteUserProfileStore(database).save(profile); ledger=new SqliteStudySessionLedger(database);
        service=service(database);
        classroom=new Classroom(UUID.randomUUID(),"Java","OOP",List.of(StudyResource.fromUrl(UUID.randomUUID(),"Course","https://classroom.google.com/c/java")),"#123ABC","Review inheritance");
        service.saveClassroom(classroom,true);
    }
    private StudyOrganizationService service(SqliteDatabase db) {
        return new StudyOrganizationService(new SqliteClassroomCatalog(db),new SqliteFocusWorkspaceStore(db),new SqliteStudyPlanStore(db),
                new SqliteUserProfileStore(db),new SqliteStudySessionLedger(db),new SqliteBlockedApplicationCatalog(db),clock);
    }
    private StudyGoal goal(GoalPeriod period) {
        var goal=new StudyGoal(UUID.randomUUID(),profile.id(),"Study Java",30,period,classroom.id()); service.saveGoal(goal,true); return goal;
    }
    private FocusWorkspace workspace() {
        return new FocusWorkspace(UUID.randomUUID(),"Java desk",classroom.id(),new FocusPreferences(50,10,15,4),Set.of(classroom.resources().getFirst().id()),Set.of());
    }
    private FocusSessionService focus(AtomicLong nanos) {
        return new FocusSessionService(ledger,new SqliteUserProfileStore(database),new SqliteClassroomCatalog(database),new SqliteStudyPlanStore(database),clock,nanos::get,false);
    }
    private void session(UUID classId,UUID goalId,long seconds,Instant end,SessionStatus status) {
        ledger.save(new StudySession(UUID.randomUUID(),profile.id(),end.minusSeconds(seconds),end,seconds,ZoneOffset.UTC,status,
                new SessionContext(classId,classId==null?"":"Java",goalId,goalId==null?"":"Study Java",0,false),end));
    }
    @Test void classroomNotesColorResourcesEditAndReload() {
        var edited=new Classroom(classroom.id(),"Advanced Java","New description",classroom.resources(),"#AA5500","Practice interfaces");
        service.saveClassroom(edited,false);
        var reopened=service(new SqliteDatabase(root.resolve("data/study.db")));
        assertEquals(edited,reopened.classrooms().getFirst());
        service.deleteClassroom(classroom.id()); assertTrue(service.classrooms().isEmpty());
    }
    @Test void tasksCompleteRemoveAndSurviveRestart() {
        service.addTask(classroom.id(),"Read chapter");
        assertThrows(IllegalArgumentException.class,() -> service.addTask(classroom.id()," read chapter "));
        var task=service.tasks(classroom.id()).getFirst(); service.completeTask(task.id()); service.completeTask(task.id());
        assertTrue(service(new SqliteDatabase(root.resolve("data/study.db"))).tasks(classroom.id()).getFirst().completed());
        service.deleteTask(task.id()); assertTrue(service.tasks(classroom.id()).isEmpty());
    }
    @Test void classroomDeletionDetachesTasksAndGoalReferencesPreventDeletion() {
        service.addTask(classroom.id(),"Keep task"); var g=goal(GoalPeriod.DAILY);
        assertThrows(StudyDataException.class,() -> service.deleteClassroom(classroom.id()));
        service.deleteGoal(g.id()); service.deleteClassroom(classroom.id());
        assertNull(new SqliteStudyPlanStore(database).tasks().getFirst().classroomId());
    }
    @Test void workspaceCrudPreservesResourcesBlocklistShortcutsAndPreferences() throws Exception {
        var blocked=new BlockedApplication("Distraction",Files.createFile(root.resolve("blocked.exe")));
        var shortcut=new BlockedApplication("Editor",Files.createFile(root.resolve("editor.exe")));
        var w=workspace(); w=new FocusWorkspace(w.id(),w.name(),w.classroomId(),w.preferences(),w.resourceIds(),Set.of(blocked),Set.of(shortcut));
        service.saveWorkspace(w,true);
        assertEquals(w,service(new SqliteDatabase(root.resolve("data/study.db"))).workspaces().getFirst());
        var edited=new FocusWorkspace(w.id(),"Exam desk",classroom.id(),new FocusPreferences(25,5,20,3),Set.of(),Set.of(),Set.of(shortcut));
        service.saveWorkspace(edited,false); assertEquals(edited,service.workspaces().getFirst());
        service.deleteWorkspace(w.id()); assertTrue(service.workspaces().isEmpty());
    }
    @Test void workspaceStartsExistingEngineWithSavedClassAndDuration() {
        var w=workspace(); service.saveWorkspace(w,true); var g=goal(GoalPeriod.DAILY);
        AtomicLong nanos=new AtomicLong(); var focus=focus(nanos);
        var started=service.startWorkspace(w.id(),g.id(),focus);
        assertEquals(FocusState.RUNNING,started.state()); assertEquals(3000,started.session().context().plannedFocusSeconds());
        assertEquals(classroom.id(),started.session().context().classroomId()); assertEquals(g.id(),started.session().context().goalId());
        assertThrows(FocusModeException.class,() -> service.startWorkspace(w.id(),null,focus));
        nanos.set(12_000_000_000L); focus.finish();
        assertEquals(12,new SqliteStudySessionLedger(new SqliteDatabase(root.resolve("data/study.db"))).find(started.session().id()).orElseThrow().activeFocusSeconds());
        assertEquals(12,service.goalSeconds(g.id())); assertEquals(12,service.classroomSeconds(classroom.id()));
    }
    @Test void goalCrudWithClassAssignmentAndReload() {
        var g=goal(GoalPeriod.DAILY); var edit=new StudyGoal(g.id(),profile.id(),"Weekly exam",120,GoalPeriod.WEEKLY,classroom.id());
        service.saveGoal(edit,false); assertEquals(edit,service(new SqliteDatabase(root.resolve("data/study.db"))).goals().getFirst());
        service.deleteGoal(g.id()); assertTrue(service.goals().isEmpty());
    }
    @Test void dailyProgressCountsOnlyLinkedCompletedActualSecondsOnCompletionDate() {
        var g=goal(GoalPeriod.DAILY);
        session(classroom.id(),g.id(),10,clock.instant(),SessionStatus.COMPLETED);
        session(classroom.id(),g.id(),15,clock.instant(),SessionStatus.INTERRUPTED);
        session(classroom.id(),null,25,clock.instant(),SessionStatus.COMPLETED);
        session(classroom.id(),g.id(),50,clock.instant().minusSeconds(86400),SessionStatus.COMPLETED);
        assertEquals(10,service.goalSeconds(g.id())); assertEquals(85,service.classroomSeconds(classroom.id()));
        assertEquals(4,service.classroomHistory(classroom.id()).size());
    }
    @Test void weeklyProgressUsesMondayBoundary() {
        var g=goal(GoalPeriod.WEEKLY);
        session(classroom.id(),g.id(),20,Instant.parse("2026-11-16T00:00:00Z"),SessionStatus.COMPLETED);
        session(classroom.id(),g.id(),30,Instant.parse("2026-11-15T23:59:59Z"),SessionStatus.COMPLETED);
        assertEquals(20,service.goalSeconds(g.id()));
    }
    @Test void referencedDeletionAndResourceRemovalAreAtomic() {
        var w=workspace(); service.saveWorkspace(w,true);
        assertThrows(StudyDataException.class,() -> service.deleteClassroom(classroom.id()));
        assertThrows(StudyDataException.class,() -> service.saveClassroom(new Classroom(classroom.id(),"Changed","",List.of()),false));
        assertEquals(classroom,service.classrooms().getFirst());
        service.deleteWorkspace(w.id()); var g=goal(GoalPeriod.DAILY); session(classroom.id(),g.id(),5,clock.instant(),SessionStatus.COMPLETED);
        assertThrows(StudyDataException.class,() -> service.deleteGoal(g.id()));
        assertThrows(StudyDataException.class,() -> service.deleteClassroom(classroom.id()));
        var other=new Classroom(UUID.randomUUID(),"Math","",List.of()); service.saveClassroom(other,true);
        assertThrows(StudyDataException.class,() -> service.saveGoal(new StudyGoal(g.id(),profile.id(),g.text(),40,g.period(),other.id()),false));
    }
    @Test void invalidAndDuplicateInputsRejectedBeforePersistence() {
        assertThrows(IllegalArgumentException.class,() -> new Classroom(UUID.randomUUID()," ","",List.of()));
        assertThrows(IllegalArgumentException.class,() -> new Classroom(UUID.randomUUID(),"Math","",List.of(),"red",""));
        assertThrows(IllegalArgumentException.class,() -> service.saveClassroom(new Classroom(UUID.randomUUID()," java ","",List.of()),true));
        assertThrows(IllegalArgumentException.class,() -> new StudyGoal(UUID.randomUUID(),profile.id(),"Study",0,GoalPeriod.DAILY));
        var g=goal(GoalPeriod.DAILY);
        assertThrows(IllegalArgumentException.class,() -> service.saveGoal(new StudyGoal(UUID.randomUUID(),profile.id(),"STUDY JAVA",60,GoalPeriod.WEEKLY),true));
        assertThrows(IllegalArgumentException.class,() -> StudyResource.fromUrl(UUID.randomUUID(),"Bad","javascript:alert(1)"));
        var duplicateLink=StudyResource.fromUrl(UUID.randomUUID(),"Same URL",classroom.resources().getFirst().uri().toString());
        assertThrows(IllegalArgumentException.class,() -> service.saveClassroom(new Classroom(classroom.id(),classroom.name(),"",List.of(classroom.resources().getFirst(),duplicateLink)),false));
        assertThrows(IllegalArgumentException.class,() -> new BlockedApplication("Bad",Path.of("relative.exe")));
        assertThrows(IllegalArgumentException.class,() -> new FocusPreferences(0,5,15,4));
        var w=workspace(); service.saveWorkspace(w,true);
        assertThrows(IllegalArgumentException.class,() -> service.saveWorkspace(new FocusWorkspace(UUID.randomUUID(),"JAVA DESK",classroom.id(),w.preferences(),Set.of(),Set.of()),true));
        assertEquals(1,service.workspaces().size()); assertEquals(1,service.goals().size());
    }
    @Test void deletedReferencesAndConflictingGoalClassroomCannotStart() {
        var g=goal(GoalPeriod.DAILY); var focus=focus(new AtomicLong());
        assertThrows(FocusModeException.class,() -> focus.start(60,null,g.id()));
        var w=workspace(); service.saveWorkspace(w,true); service.deleteWorkspace(w.id());
        assertThrows(IllegalArgumentException.class,() -> service.startWorkspace(w.id(),null,focus));
        service.deleteGoal(g.id()); service.deleteClassroom(classroom.id());
        assertThrows(IllegalArgumentException.class,() -> service.saveWorkspace(w,true));
        assertThrows(IllegalArgumentException.class,() -> service.addTask(classroom.id(),"No classroom"));
        assertTrue(ledger.findUnfinished().isEmpty());
    }
    @Test void missingExecutableAndConflictingSelectionsRejected() {
        var app=new BlockedApplication("Missing",root.resolve("missing.exe")); var w=workspace();
        assertThrows(IllegalArgumentException.class,() -> service.saveWorkspace(new FocusWorkspace(w.id(),w.name(),w.classroomId(),w.preferences(),Set.of(),Set.of(app)),true));
        assertThrows(IllegalArgumentException.class,() -> new FocusWorkspace(w.id(),w.name(),w.classroomId(),w.preferences(),Set.of(),Set.of(app),Set.of(app)));
        assertTrue(service.workspaces().isEmpty());
    }
    @Test void newFieldsRoundTripAndFormatTwoCompatibility() throws Exception {
        var g=goal(GoalPeriod.DAILY); var w=workspace();
        var shortcut=new BlockedApplication("Editor",Files.createFile(root.resolve("study.exe")));
        w=new FocusWorkspace(w.id(),w.name(),w.classroomId(),w.preferences(),w.resourceIds(),w.blocklist(),Set.of(shortcut)); service.saveWorkspace(w,true);
        var archive=new SqliteStudyDataArchive(database,clock); var transfer=new JsonStudyDataTransfer(archive,root.resolve("data"));
        Path file=root.resolve("transfer.json"); transfer.exportTo(file); var original=archive.snapshot();
        assertEquals(original,transfer.inspectImport(file)); transfer.importSnapshot(transfer.inspectImport(file)); assertEquals(original,archive.snapshot());
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper(); var doc=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(file.toFile());
        doc.put("formatVersion",2);
        for(var app:doc.get("applications")) ((com.fasterxml.jackson.databind.node.ObjectNode)app).remove("enabled");
        for(var workspace:doc.get("workspaces")) for(var app:workspace.get("shortcuts")) ((com.fasterxml.jackson.databind.node.ObjectNode)app).remove("enabled");
        for(var c:doc.get("classrooms")) { ((com.fasterxml.jackson.databind.node.ObjectNode)c).remove("color"); ((com.fasterxml.jackson.databind.node.ObjectNode)c).remove("notes"); }
        for(var goal:doc.get("goals")) ((com.fasterxml.jackson.databind.node.ObjectNode)goal).remove("classroomId");
        for(var workspace:doc.get("workspaces")) ((com.fasterxml.jackson.databind.node.ObjectNode)workspace).remove("shortcuts");
        mapper.writeValue(file.toFile(),doc); var imported=transfer.inspectImport(file);
        assertEquals(4,imported.formatVersion()); assertNull(imported.goals().getFirst().classroomId());
        assertEquals("#7569EF",imported.classrooms().getFirst().color()); assertEquals("",imported.classrooms().getFirst().notes());
        assertEquals(original,archive.snapshot());
    }
    @Test void schemaThreeUpgradePreservesExistingOrganizationAndSessionFacts() throws Exception {
        var old=new SqliteDatabase(root.resolve("old/study.db"));
        UUID profileId=UUID.randomUUID(), classId=UUID.randomUUID(), goalId=UUID.randomUUID(), workspaceId=UUID.randomUUID();
        try(var c=old.open(); var statement=c.createStatement()) {
            var names=List.of("V001__foundation.sql","V002__study_data.sql","V003__focus_sessions.sql");
            for(int i=0;i<names.size();i++) {
                try(var input=getClass().getResourceAsStream("/db/migration/"+names.get(i))) {
                    for(String command:new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).split(";")) if(!command.isBlank()) statement.execute(command);
                }
                try(var p=c.prepareStatement("INSERT INTO schema_version VALUES (?,?)")) { p.setInt(1,i+1); p.setString(2,clock.instant().toString()); p.executeUpdate(); }
            }
            statement.execute("PRAGMA user_version=3");
        }
        var oldProfile=new UserProfile(profileId,"Existing student",clock.instant(),ZoneOffset.UTC); new SqliteUserProfileStore(old).save(oldProfile);
        try(var c=old.open()) {
            try(var p=c.prepareStatement("INSERT INTO classroom VALUES (?,?,?)")) { p.setString(1,classId.toString());p.setString(2,"Existing class");p.setString(3,"Keep description");p.executeUpdate(); }
            try(var p=c.prepareStatement("INSERT INTO study_goal VALUES (?,?,?,?,?)")) { p.setString(1,goalId.toString());p.setString(2,profileId.toString());p.setString(3,"Existing goal");p.setInt(4,30);p.setString(5,"DAILY");p.executeUpdate(); }
            try(var p=c.prepareStatement("INSERT INTO focus_workspace VALUES (?,?,?,?,?,?,?)")) { p.setString(1,workspaceId.toString());p.setString(2,classId.toString());p.setString(3,"Existing desk");p.setInt(4,25);p.setInt(5,5);p.setInt(6,15);p.setInt(7,4);p.executeUpdate(); }
        }
        var fact=new StudySession(UUID.randomUUID(),profileId,clock.instant().minusSeconds(20),clock.instant(),10,ZoneOffset.UTC,SessionStatus.COMPLETED);
        new SqliteStudySessionLedger(old).save(fact); new SchemaMigrator(old).migrate(); new SchemaMigrator(old).migrate();
        assertEquals(fact,new SqliteStudySessionLedger(old).find(fact.id()).orElseThrow());
        assertEquals("Keep description",new SqliteClassroomCatalog(old).find(classId).orElseThrow().description());
        assertEquals("#7569EF",new SqliteClassroomCatalog(old).find(classId).orElseThrow().color());
        assertNull(new SqliteStudyPlanStore(old).findGoal(goalId).orElseThrow().classroomId());
        assertTrue(new SqliteFocusWorkspaceStore(old).find(workspaceId).orElseThrow().shortcuts().isEmpty());
    }
    @Test void unsupportedLegacyVersionCannotBeUpgradedAndFailedImportKeepsDatabase() throws Exception {
        var archive=new SqliteStudyDataArchive(database,clock); var transfer=new JsonStudyDataTransfer(archive,root.resolve("data"));
        Path file=root.resolve("invalid-version.json"); transfer.exportTo(file); var before=archive.snapshot();
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper(); var doc=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(file.toFile());
        doc.put("formatVersion",0);
        for(var c:doc.get("classrooms")) { ((com.fasterxml.jackson.databind.node.ObjectNode)c).remove("color"); ((com.fasterxml.jackson.databind.node.ObjectNode)c).remove("notes"); }
        for(var g:doc.get("goals")) ((com.fasterxml.jackson.databind.node.ObjectNode)g).remove("classroomId");
        for(var w:doc.get("workspaces")) ((com.fasterxml.jackson.databind.node.ObjectNode)w).remove("shortcuts");
        mapper.writeValue(file.toFile(),doc);
        assertThrows(DataImportException.class,() -> transfer.inspectImport(file)); assertEquals(before,archive.snapshot());
        assertFalse(Files.exists(root.resolve("data/backups")));
    }
    @Test void shortcutMissingOnThisMachineShowsUsefulFailureWithoutLaunchingAnything() {
        var app=new BlockedApplication("Missing editor",root.resolve("not-installed.exe"));
        var error=assertThrows(FocusModeException.class,() -> new com.studymode.platform.desktop.StudyApplicationLauncher().launch(app));
        assertTrue(error.getMessage().contains("no longer exists"));
    }    @Test void goalProgressUsesProfileZoneAcrossUtcMidnight() {
        new SqliteUserProfileStore(database).save(profile.withDetails(profile.displayName(),ZoneId.of("Asia/Tokyo")));
        var g=goal(GoalPeriod.DAILY);
        session(classroom.id(),g.id(),10,Instant.parse("2026-11-17T23:00:00Z"),SessionStatus.COMPLETED);
        session(classroom.id(),g.id(),20,Instant.parse("2026-11-17T14:59:59Z"),SessionStatus.COMPLETED);
        assertEquals(10,service.goalSeconds(g.id()));
    }}
