package com.studymode.persistence;

import com.studymode.application.service.*;
import com.studymode.application.error.*;
import com.studymode.domain.study.*;
import com.studymode.persistence.sqlite.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.function.LongSupplier;
import static org.junit.jupiter.api.Assertions.*;

class FocusSessionWorkflowTest {
    @TempDir Path root;
    SqliteDatabase db;
    SqliteStudySessionLedger ledger;
    FocusSessionService service;
    final TestTime time = new TestTime();
    UUID profileId, classroomId, goalId;
    @BeforeEach void setup() {
        db = new SqliteDatabase(root.resolve("focus.db")); var sample = StudyDataFixture.populate(db);
        profileId = sample.profiles().getFirst().id(); classroomId = sample.classrooms().getFirst().id(); goalId = sample.goals().getFirst().id();
        ledger = new SqliteStudySessionLedger(db); ledger.delete(sample.sessions().getFirst().id()); service = service(true);
    }
    private FocusSessionService service(boolean demo) {
        return new FocusSessionService(ledger,new SqliteUserProfileStore(db),new SqliteClassroomCatalog(db),new SqliteStudyPlanStore(db),time,time,demo);
    }
    @Test void startPauseResumeFinishExcludesPausedTimeAndPreservesReferences() {
        assertEquals(FocusState.IDLE,service.snapshot().state());
        service.start(10,classroomId,goalId); time.advance(Duration.ofSeconds(2)); service.pause();
        assertEquals(FocusState.PAUSED,service.snapshot().state());
        time.advance(Duration.ofHours(1)); service.tick(); service.resume(); time.advance(Duration.ofSeconds(3));
        var finished = service.finish(); assertEquals(FocusState.COMPLETED,finished.state());
        var saved = new SqliteStudySessionLedger(new SqliteDatabase(root.resolve("focus.db"))).find(finished.session().id()).orElseThrow();
        assertEquals(5,saved.activeFocusSeconds()); assertEquals(classroomId,saved.context().classroomId()); assertEquals(goalId,saved.context().goalId());
        assertTrue(saved.context().demo()); assertEquals(10,saved.context().plannedFocusSeconds()); assertTrue(saved.finished());
        assertEquals(1,new StudyStatisticsService(ledger,time).summary(profileId,ZoneOffset.UTC).completedSessionCount());
    }
    @Test void fractionalIntervalsAreAccumulatedWithoutRoundingEachPauseOrCountingTwice() {
        service.start(10,null,null); time.advance(Duration.ofMillis(600)); service.pause();
        time.advance(Duration.ofMinutes(30)); service.resume(); time.advance(Duration.ofMillis(400)); service.finish();
        assertEquals(1,service.snapshot().session().activeFocusSeconds());
        assertThrows(FocusModeException.class,service::finish); assertThrows(FocusModeException.class,service::interrupt);
        assertEquals(1,ledger.findByPeriod(Instant.MIN,Instant.MAX).size());
    }
    @Test void countdownCompletesAutomaticallyAndCapsDelayedPulseAtTheActualTarget() {
        service.start(10,null,null);
        for (int i=0;i<4;i++) { time.advance(Duration.ofSeconds(3)); service.tick(); }
        assertEquals(FocusState.COMPLETED,service.snapshot().state()); assertEquals(10,service.snapshot().session().activeFocusSeconds());
        assertEquals(0,service.snapshot().remainingNanos()); assertEquals(1,service.snapshot().progress());
        time.advance(Duration.ofMinutes(10)); service.tick(); assertEquals(10,service.snapshot().session().activeFocusSeconds());
    }
    @Test void interruptRetainsActualTimeButDoesNotEnterCompletedStatistics() {
        service.start(1500,null,null); time.advance(Duration.ofSeconds(4)); var stopped = service.interrupt();
        assertEquals(FocusState.INTERRUPTED,stopped.state()); assertEquals(4,stopped.session().activeFocusSeconds());
        var summary = new StudyStatisticsService(ledger,time).summary(profileId,ZoneOffset.UTC);
        assertEquals(0,summary.completedSessionCount()); assertEquals(0,summary.totalCompletedSeconds()); assertTrue(summary.mostRecentSession().isPresent());
    }
    @Test void rejectsInvalidTransitionsDurationsMissingReferencesAndDuplicateActiveSessions() {
        assertThrows(FocusModeException.class,service::pause); assertThrows(FocusModeException.class,service::resume);
        assertThrows(FocusModeException.class,service::finish); assertThrows(FocusModeException.class,service::interrupt);
        assertThrows(FocusModeException.class,() -> service.start(0,null,null)); assertThrows(FocusModeException.class,() -> service.start(-1,null,null));
        assertThrows(FocusModeException.class,() -> service.start(14401,null,null));
        assertThrows(FocusModeException.class,() -> service.start(10,UUID.randomUUID(),null)); assertThrows(FocusModeException.class,() -> service.start(10,null,UUID.randomUUID()));
        service.start(10,null,null);
        assertThrows(FocusModeException.class,() -> service.start(10,null,null)); assertThrows(FocusModeException.class,() -> service(false).start(1500,null,null));
        service.pause(); assertThrows(FocusModeException.class,service::pause); assertThrows(FocusModeException.class,() -> service.start(10,null,null));
        service.resume(); assertThrows(FocusModeException.class,service::resume);
        service.interrupt(); service.start(10,null,null); assertEquals(1,ledger.findUnfinished().size());
    }
    @Test void standardModeAllowsWholeMinutePresetsButShortSessionsRequireDemo() {
        var standard = service(false);
        assertThrows(FocusModeException.class,() -> standard.start(10,null,null)); assertThrows(FocusModeException.class,() -> standard.start(61,null,null));
        standard.start(1500,null,null); assertFalse(standard.snapshot().session().context().demo()); standard.interrupt();
        standard.start(3000,null,null); assertEquals(3000,standard.snapshot().session().context().plannedFocusSeconds());
    }
    @Test void missingProfileCannotStart() {
        var empty = new SqliteDatabase(root.resolve("empty.db")); new SchemaMigrator(empty).migrate();
        var noProfile = new FocusSessionService(new SqliteStudySessionLedger(empty),new SqliteUserProfileStore(empty),new SqliteClassroomCatalog(empty),new SqliteStudyPlanStore(empty),time,time,true);
        assertThrows(FocusModeException.class,() -> noProfile.start(10,null,null));
    }
    @Test void wallClockChangesDoNotChangeMonotonicFocusAndEndCannotPrecedeStart() {
        service.start(10,null,null); time.advance(Duration.ofSeconds(2)); time.wall = time.wall.minus(Duration.ofDays(2));
        service.pause(); time.advance(Duration.ofSeconds(100)); service.resume(); time.advance(Duration.ofSeconds(1)); service.finish();
        assertEquals(3,service.snapshot().session().activeFocusSeconds()); assertFalse(service.snapshot().session().endedAt().isBefore(service.snapshot().session().startedAt()));
    }
    @Test void longHeartbeatGapPausesWithoutInventingStudyTime() {
        service.start(1500,null,null); time.advance(Duration.ofSeconds(2)); service.tick();
        time.advance(Duration.ofHours(2)); service.tick(); assertEquals(FocusState.PAUSED,service.snapshot().state());
        assertEquals(2,service.snapshot().session().activeFocusSeconds()); assertTrue(service.snapshot().notice().contains("gap"));
        service.resume(); time.advance(Duration.ofSeconds(1)); service.finish(); assertEquals(3,service.snapshot().session().activeFocusSeconds());
    }
    @Test void restartRecoveryUsesOnlyCommittedCheckpointsAndNeverAutoResumes() {
        service.start(1500,classroomId,goalId); time.advance(Duration.ofSeconds(5)); service.tick();
        time.advance(Duration.ofSeconds(2)); service.tick(); // Not yet checkpointed.
        time.advance(Duration.ofDays(2)); var restarted = service(true);
        assertEquals(1,restarted.recoverUnfinished()); assertEquals(FocusState.IDLE,restarted.snapshot().state());
        var recovered = ledger.findByPeriod(Instant.MIN,Instant.MAX).getFirst();
        assertEquals(SessionStatus.INTERRUPTED,recovered.status()); assertEquals(5,recovered.activeFocusSeconds()); assertEquals(StudyDataFixture.NOW.plusSeconds(5),recovered.endedAt());
        assertEquals(classroomId,recovered.context().classroomId()); assertEquals(0,restarted.recoverUnfinished()); restarted.start(10,null,null);
    }
    @Test void shutdownInterruptsRunningAndPausedSessionsOnce() {
        service.start(10,null,null); time.advance(Duration.ofSeconds(2)); service.shutdown(); service.shutdown();
        assertEquals(SessionStatus.INTERRUPTED,ledger.findByPeriod(Instant.MIN,Instant.MAX).getFirst().status());
        service.start(10,null,null); time.advance(Duration.ofSeconds(3)); service.pause(); time.advance(Duration.ofHours(1)); service.shutdown();
        assertEquals(3,service.snapshot().session().activeFocusSeconds()); assertEquals(2,ledger.findByPeriod(Instant.MIN,Instant.MAX).size());
    }
    @Test void checkpointFailurePausesAndRetryPreservesActualTime() throws Exception {
        service.start(10,null,null);
        try (var c=db.open(); var s=c.createStatement()) { s.execute("CREATE TRIGGER fail_save BEFORE UPDATE ON study_session BEGIN SELECT RAISE(ABORT,'test save failure'); END"); }
        time.advance(Duration.ofSeconds(5)); assertThrows(StudyDataException.class,service::tick);
        assertEquals(FocusState.PAUSED,service.snapshot().state()); assertTrue(service.snapshot().savePending());
        assertThrows(FocusModeException.class,service::resume); assertThrows(FocusModeException.class,() -> service.start(10,null,null));
        time.advance(Duration.ofMinutes(1));
        try (var c=db.open(); var s=c.createStatement()) { s.execute("DROP TRIGGER fail_save"); }
        service.retrySave(); service.resume(); time.advance(Duration.ofSeconds(2)); service.finish(); assertEquals(7,service.snapshot().session().activeFocusSeconds());
    }
    @Test void failedFinishFreezesTimeAndRemainsRetryableBeforeAnotherStart() throws Exception {
        service.start(10,null,null);
        try (var c=db.open(); var s=c.createStatement()) { s.execute("CREATE TRIGGER fail_save BEFORE UPDATE ON study_session BEGIN SELECT RAISE(ABORT,'test'); END"); }
        time.advance(Duration.ofSeconds(2)); assertThrows(StudyDataException.class,service::finish);
        assertEquals(FocusState.COMPLETED,service.snapshot().state()); assertTrue(service.snapshot().savePending());
        time.advance(Duration.ofMinutes(1)); service.tick(); assertEquals(2,service.snapshot().session().activeFocusSeconds());
        assertThrows(FocusModeException.class,() -> service.start(10,null,null));
        try (var c=db.open(); var s=c.createStatement()) { s.execute("DROP TRIGGER fail_save"); }
        service.shutdown(); assertFalse(service.snapshot().savePending()); assertEquals(2,ledger.findByPeriod(Instant.MIN,Instant.MAX).getFirst().activeFocusSeconds());
    }
    @Test void statisticsUseCommittedCompletedTimeCorrectZoneAndMostRecentOutcome() {
        Instant start = StudyDataFixture.NOW.minus(Duration.ofDays(1));
        ledger.save(new StudySession(UUID.randomUUID(),profileId,start,Instant.parse("2026-11-14T23:59:00Z"),120,ZoneOffset.UTC,SessionStatus.COMPLETED));
        ledger.save(new StudySession(UUID.randomUUID(),profileId,start,Instant.parse("2026-11-15T00:10:00Z"),300,ZoneOffset.UTC,SessionStatus.COMPLETED));
        var interrupted = new StudySession(UUID.randomUUID(),profileId,start,StudyDataFixture.NOW,50,ZoneOffset.UTC,SessionStatus.INTERRUPTED); ledger.save(interrupted);
        var statistics = new StudyStatisticsService(ledger,time); var utc = statistics.summary(profileId,ZoneOffset.UTC);
        assertEquals(300,utc.todayCompletedSeconds()); assertEquals(420,utc.totalCompletedSeconds()); assertEquals(2,utc.completedSessionCount()); assertEquals(interrupted,utc.mostRecentSession().orElseThrow());
        assertEquals(0,statistics.summary(profileId,ZoneId.of("Pacific/Honolulu")).todayCompletedSeconds());
        assertEquals(3,statistics.history(profileId).size()); assertEquals(interrupted,statistics.history(profileId).getFirst());
    }
    @Test void sessionSnapshotsSurviveRenamingAndProtectRequiredReferencesFromDeletion() {
        service.start(10,classroomId,goalId); var name=service.snapshot().session().context().classroomName();
        var classes = new SqliteClassroomCatalog(db); var before=classes.find(classroomId).orElseThrow();
        classes.save(new com.studymode.domain.classroom.Classroom(classroomId,"New name",before.description(),before.resources()));
        time.advance(Duration.ofSeconds(1)); service.finish(); assertEquals(name,ledger.findByPeriod(Instant.MIN,Instant.MAX).getFirst().context().classroomName());
        assertThrows(StudyDataException.class,() -> new SqliteStudyPlanStore(db).deleteGoal(goalId));
    }
    @Test @org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="studymode.real.demo",matches="true")
    void realTenSecondDemoRecordsTenSecondsWithTheProductionClock() throws Exception {
        var live = new FocusSessionService(ledger,new SqliteUserProfileStore(db),new SqliteClassroomCatalog(db),new SqliteStudyPlanStore(db),Clock.systemUTC(),System::nanoTime,true);
        live.start(10,classroomId,goalId);
        var completed = new java.util.concurrent.CompletableFuture<com.studymode.application.data.FocusSessionSnapshot>();
        var scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        try {
            scheduler.scheduleAtFixedRate(() -> {
                try { var snapshot=live.tick(); if(snapshot.state()==FocusState.COMPLETED) completed.complete(snapshot); }
                catch(RuntimeException e) { completed.completeExceptionally(e); }
            },0,100,java.util.concurrent.TimeUnit.MILLISECONDS);
            var actual = completed.get(20,java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(actual.session().context().demo()); assertEquals(10,actual.session().activeFocusSeconds());
            assertEquals(10,new SqliteStudySessionLedger(new SqliteDatabase(root.resolve("focus.db"))).find(actual.session().id()).orElseThrow().activeFocusSeconds());
        } finally { scheduler.shutdownNow(); }
    }
    static final class TestTime extends Clock implements LongSupplier {
        Instant wall=StudyDataFixture.NOW; long nanos;
        void advance(Duration duration) { wall=wall.plus(duration); nanos+=duration.toNanos(); }
        @Override public Instant instant() { return wall; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(wall,zone); }
        @Override public long getAsLong() { return nanos; }
    }
}
