package com.studymode.persistence;

import com.studymode.application.service.*;
import com.studymode.application.port.StudyNotifications;
import com.studymode.application.error.*;
import com.studymode.domain.study.*;
import com.studymode.domain.workspace.*;
import com.studymode.persistence.sqlite.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BreakCycleWorkflowTest {
    @TempDir Path root;
    SqliteDatabase db;
    SqliteStudySessionLedger ledger;
    FocusSessionService focus;
    final FocusSessionWorkflowTest.TestTime time=new FocusSessionWorkflowTest.TestTime();
    final List<StudyNotifications.Event> events=new ArrayList<>();
    final List<Boolean> sounds=new ArrayList<>();
    UUID classroom;
    @BeforeEach void setup() {
        db=new SqliteDatabase(root.resolve("cycles.db")); var data=StudyDataFixture.populate(db);
        classroom=data.classrooms().getFirst().id(); ledger=new SqliteStudySessionLedger(db); ledger.delete(data.sessions().getFirst().id());
        focus=create(); focus.setNotifications((event,detail,sound) -> { events.add(event); sounds.add(sound); });
        focus.configureCycles(new FocusPreferences(25,1,2,2),true);
    }
    FocusSessionService create() { return new FocusSessionService(ledger,new SqliteUserProfileStore(db),new SqliteClassroomCatalog(db),new SqliteStudyPlanStore(db),time,time,true); }
    void seconds(int count) { for(int i=0;i<count;i++) { time.advance(Duration.ofSeconds(1)); focus.tick(); } }
    void completedFocus() { focus.start(10,null,null); seconds(10); }
    @Test void automaticShortBreakEndsWithoutAddingStudyTimeOrStartingFocus() {
        completedFocus(); assertEquals(BreakCycle.Phase.RUNNING,focus.snapshot().cycle().phase());
        assertFalse(focus.snapshot().cycle().longBreak()); assertEquals(60_000_000_000L,focus.snapshot().cycle().remainingNanos());
        seconds(60); assertEquals(BreakCycle.Phase.COMPLETE,focus.snapshot().cycle().phase());
        assertEquals(10,ledger.findByPeriod(Instant.MIN,Instant.MAX).getFirst().activeFocusSeconds());
        assertEquals(1,ledger.findByPeriod(Instant.MIN,Instant.MAX).size()); assertTrue(ledger.findUnfinished().isEmpty());
        assertEquals(List.of(StudyNotifications.Event.FOCUS_COMPLETED,StudyNotifications.Event.BREAK_COMPLETED),events);
        seconds(20); assertEquals(2,events.size());
    }
    @Test void longBreakFollowsConfiguredCompletedFocusCountAndSkipAllowsNextFocus() {
        completedFocus(); focus.skipBreak(); completedFocus();
        assertTrue(focus.snapshot().cycle().longBreak()); assertEquals(2,focus.snapshot().cycle().completedFocusPeriods());
        assertEquals(120_000_000_000L,focus.snapshot().cycle().remainingNanos()); focus.skipBreak(); completedFocus();
        assertFalse(focus.snapshot().cycle().longBreak());
        assertEquals(30,ledger.findByPeriod(Instant.MIN,Instant.MAX).stream().mapToLong(StudySession::activeFocusSeconds).sum());
    }
    @Test void manualBreakWaitsForExplicitStartAndCannotBeBypassedByStartingFocus() {
        focus.configureCycles(new FocusPreferences(25,1,2,2),false); completedFocus();
        assertEquals(BreakCycle.Phase.READY,focus.snapshot().cycle().phase()); seconds(100);
        assertEquals(60_000_000_000L,focus.snapshot().cycle().remainingNanos());
        assertThrows(FocusModeException.class,() -> focus.start(10,null,null));
        focus.startBreak(); seconds(1); assertEquals(59_000_000_000L,focus.snapshot().cycle().remainingNanos());
    }
    @Test void pausedFocusAndPausedBreakBothExcludeElapsedTime() {
        focus.start(10,null,null); seconds(2); focus.pause(); seconds(100); focus.resume(); seconds(8);
        seconds(3); focus.pause(); long remaining=focus.snapshot().cycle().remainingNanos(); seconds(100);
        assertEquals(remaining,focus.snapshot().cycle().remainingNanos()); focus.resume(); seconds(57);
        assertEquals(BreakCycle.Phase.COMPLETE,focus.snapshot().cycle().phase()); assertEquals(10,focus.snapshot().session().activeFocusSeconds());
    }
    @Test void breakSuspendGapPausesAndWallClockChangeDoesNotAffectCountdown() {
        completedFocus(); time.wall=time.wall.minus(Duration.ofDays(2)); seconds(1);
        assertEquals(59_000_000_000L,focus.snapshot().cycle().remainingNanos());
        time.advance(Duration.ofHours(1)); focus.tick(); assertEquals(BreakCycle.Phase.PAUSED,focus.snapshot().cycle().phase());
        focus.resume(); seconds(59); assertEquals(BreakCycle.Phase.COMPLETE,focus.snapshot().cycle().phase());
    }
    @Test void interruptionNeverStartsBreakOrAwardsTimeAfterStop() {
        focus.start(10,null,null); seconds(3); focus.interrupt(); seconds(100);
        assertEquals(3,focus.snapshot().session().activeFocusSeconds()); assertEquals(SessionStatus.INTERRUPTED,focus.snapshot().session().status());
        assertEquals(BreakCycle.Phase.NONE,focus.snapshot().cycle().phase()); assertTrue(events.isEmpty());
    }
    @Test void restartDuringBreakDiscardsBreakAndRetainsOnlyCompletedFocus() {
        completedFocus(); seconds(3); time.advance(Duration.ofDays(2)); var restarted=create();
        assertEquals(0,restarted.recoverUnfinished()); assertEquals(BreakCycle.Phase.NONE,restarted.snapshot().cycle().phase());
        assertEquals(10,ledger.findByPeriod(Instant.MIN,Instant.MAX).getFirst().activeFocusSeconds());
        restarted.start(10,null,null); assertEquals(0,restarted.snapshot().cycle().completedFocusPeriods());
    }
    @Test void restartDuringFocusUsesSavedCheckpointAndNeverNotifiesCompletion() {
        focus.start(10,null,null); seconds(7); time.advance(Duration.ofDays(2)); var restarted=create();
        restarted.setNotifications((event,detail,sound) -> fail("Recovery is not a completion"));
        assertEquals(1,restarted.recoverUnfinished()); var saved=ledger.findByPeriod(Instant.MIN,Instant.MAX).getFirst();
        assertEquals(5,saved.activeFocusSeconds()); assertEquals(SessionStatus.INTERRUPTED,saved.status());
    }
    @Test void disabledNotificationsAndSoundAreHonoured() {
        focus.notificationPreferences(false,true); completedFocus(); seconds(60); assertTrue(events.isEmpty());
        focus.notificationPreferences(true,false); completedFocus(); assertEquals(List.of(false),sounds);
        focus.skipBreak(); focus.notificationPreferences(true,true); completedFocus(); assertEquals(List.of(false,true),sounds);
    }
    @Test void notificationFailureCannotUndoCompletionOrBlockBreak() {
        focus.setNotifications((event,detail,sound) -> { throw new IllegalStateException("Unavailable"); });
        completedFocus(); assertEquals(BreakCycle.Phase.RUNNING,focus.snapshot().cycle().phase());
        assertEquals(10,ledger.findByPeriod(Instant.MIN,Instant.MAX).getFirst().activeFocusSeconds());
    }
    @Test void failedCompletionSaveDoesNotStartBreakOrNotifyUntilRetry() throws Exception {
        focus.start(10,null,null); seconds(5);
        try(var c=db.open(); var s=c.createStatement()) { s.execute("CREATE TRIGGER cycle_failure BEFORE UPDATE ON study_session BEGIN SELECT RAISE(ABORT,'test'); END"); }
        time.advance(Duration.ofSeconds(5)); assertThrows(StudyDataException.class,focus::tick);
        assertEquals(BreakCycle.Phase.NONE,focus.snapshot().cycle().phase()); assertTrue(events.isEmpty());
        try(var c=db.open(); var s=c.createStatement()) { s.execute("DROP TRIGGER cycle_failure"); }
        focus.retrySave(); focus.tick(); assertEquals(List.of(StudyNotifications.Event.FOCUS_COMPLETED),events);
        assertEquals(BreakCycle.Phase.RUNNING,focus.snapshot().cycle().phase());
    }
    @Test void workspaceTimingRoundTripsAndSwitchingWorkspaceResetsCount() {
        var store=new SqliteFocusWorkspaceStore(db);
        var programming=new FocusWorkspace(UUID.randomUUID(),"Programming",classroom,new FocusPreferences(50,10,30,3),Set.of(),Set.of());
        var reading=new FocusWorkspace(UUID.randomUUID(),"Reading",classroom,new FocusPreferences(25,5,15,4),Set.of(),Set.of());
        store.save(programming); store.save(reading); var reopened=new SqliteFocusWorkspaceStore(new SqliteDatabase(root.resolve("cycles.db")));
        assertEquals(programming.preferences(),reopened.find(programming.id()).orElseThrow().preferences());
        assertEquals(reading.preferences(),reopened.find(reading.id()).orElseThrow().preferences());
        focus.startWorkspace(reopened.find(programming.id()).orElseThrow(),null,false); seconds(2); focus.finish();
        assertEquals(600_000_000_000L,focus.snapshot().cycle().remainingNanos()); focus.skipBreak();
        focus.startWorkspace(reading,null,false); assertEquals(1500,focus.snapshot().session().context().plannedFocusSeconds());
        seconds(3); focus.finish(); assertEquals(300_000_000_000L,focus.snapshot().cycle().remainingNanos());
        assertEquals(1,focus.snapshot().cycle().completedFocusPeriods());
    }
    @Test void stoppingBreakOrClosingNeverChangesTheCompletedFocusFact() {
        completedFocus(); var saved=focus.snapshot().session(); focus.interrupt();
        assertEquals(BreakCycle.Phase.NONE,focus.snapshot().cycle().phase()); assertEquals(saved,ledger.find(saved.id()).orElseThrow());
        completedFocus(); focus.shutdown(); assertFalse(focus.snapshot().occupied());
    }
    @Test void timingAndTransitionValidation() {
        assertThrows(IllegalArgumentException.class,() -> new FocusPreferences(0,5,15,4));
        assertThrows(IllegalArgumentException.class,() -> new FocusPreferences(25,0,15,4));
        assertThrows(IllegalArgumentException.class,() -> new FocusPreferences(25,5,61,4));
        assertThrows(IllegalArgumentException.class,() -> new FocusPreferences(25,5,15,13));
        assertThrows(IllegalArgumentException.class,focus::startBreak); assertThrows(IllegalArgumentException.class,focus::skipBreak);
        focus.start(10,null,null); assertThrows(FocusModeException.class,() -> focus.configureCycles(FocusPreferences.defaults(),true));
    }
    @Test void importsRequireFocusAndBreakToBeStoppedBeforeReplacement() {
        focus.requireImportAllowed(); focus.start(10,null,null);
        assertThrows(DataImportException.class,focus::requireImportAllowed); seconds(10);
        assertThrows(DataImportException.class,focus::requireImportAllowed); focus.pause();
        assertThrows(DataImportException.class,focus::requireImportAllowed); focus.skipBreak(); focus.requireImportAllowed();
        focus.clearAfterImport(); assertEquals(0,focus.snapshot().cycle().completedFocusPeriods());
    }
    @Test void completionAtPauseNotifiesBreakOnlyOnce() {
        completedFocus(); seconds(59); time.advance(Duration.ofSeconds(1)); focus.pause();
        assertEquals(BreakCycle.Phase.COMPLETE,focus.snapshot().cycle().phase()); focus.tick();
        assertEquals(List.of(StudyNotifications.Event.FOCUS_COMPLETED,StudyNotifications.Event.BREAK_COMPLETED),events);
    }
    @Test void focusCompletionAndEveryBreakControlKeepApplicationBlockingInactive() {
        var blocker=new com.studymode.platform.simulation.SimulatedDistractionBlocker(time);
        var catalog=new SqliteBlockedApplicationCatalog(db);
        focus=new FocusSessionService(ledger,new SqliteUserProfileStore(db),new SqliteClassroomCatalog(db),new SqliteStudyPlanStore(db),time,time,true,
                new FocusBlockingService(blocker,catalog));
        focus.configureCycles(new FocusPreferences(25,1,2,2),true);
        focus.start(10,null,null); assertTrue(blocker.isRunning()); seconds(10); assertFalse(blocker.isRunning());
        focus.pause(); assertFalse(blocker.isRunning()); focus.resume(); assertFalse(blocker.isRunning());
        focus.skipBreak(); assertFalse(blocker.isRunning()); focus.start(10,null,null); assertTrue(blocker.isRunning());
        focus.shutdown(); assertFalse(blocker.isRunning()); blocker.close();
    }
}
