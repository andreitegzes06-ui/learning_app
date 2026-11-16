package com.studymode.application.service;

import com.studymode.application.data.*;
import com.studymode.application.error.*;
import com.studymode.application.port.*;
import com.studymode.domain.study.*;
import java.time.*;
import java.util.*;
import java.util.function.LongSupplier;

/** Serialized use cases, run off the FX thread. No JavaFX, wall-clock elapsed math, or OS enforcement. */
public final class FocusSessionService {
    private static final long SECOND = 1_000_000_000L;
    private static final long CHECKPOINT_INTERVAL = 5 * SECOND;
    private static final long MAX_HEARTBEAT_GAP = 15 * SECOND;
    private final StudySessionLedger ledger;
    private final UserProfileStore profiles;
    private final ClassroomCatalog classrooms;
    private final StudyPlanStore plans;
    private final Clock clock;
    private final LongSupplier nanoTime;
    private final boolean demoMode;
    private FocusBlockingService blocking;
    private final BreakCycle cycle = new BreakCycle();
    private StudyNotifications notifications = (event, detail, sound) -> { };
    private boolean notificationsEnabled = true, completionSound, automaticBreaks;
    private UUID notifiedCompletion;
    private StudySession current;
    private long focusedNanos;
    private long anchor;
    private long lastSave;
    private boolean savePending;
    private String notice = "";

    public FocusSessionService(StudySessionLedger ledger, UserProfileStore profiles, ClassroomCatalog classrooms,
                               StudyPlanStore plans, Clock clock, LongSupplier nanoTime, boolean demoMode) {
        this.ledger = ledger; this.profiles = profiles; this.classrooms = classrooms; this.plans = plans;
        this.clock = clock; this.nanoTime = nanoTime; this.demoMode = demoMode;
    }
    public FocusSessionService(StudySessionLedger ledger, UserProfileStore profiles, ClassroomCatalog classrooms,
            StudyPlanStore plans, Clock clock, java.util.function.LongSupplier nanoTime, boolean demoMode, FocusBlockingService blocking) {
        this(ledger,profiles,classrooms,plans,clock,nanoTime,demoMode); this.blocking=blocking;
    }
    public synchronized void configureCycles(com.studymode.domain.workspace.FocusPreferences preferences, boolean automatic) {
        if (snapshot().occupied() || savePending) throw new FocusModeException("Configure cycles before starting focus.");
        cycle.configure(preferences,automatic); automaticBreaks=automatic;
    }
    public synchronized void automaticBreaks(boolean automatic) { automaticBreaks=automatic; }
    public synchronized void notificationPreferences(boolean enabled, boolean sound) { notificationsEnabled=enabled; completionSound=sound; }
    public synchronized void setNotifications(StudyNotifications notifications) { this.notifications=Objects.requireNonNull(notifications); }
    private void notifyCompletion(StudyNotifications.Event event, String detail) {
        if (notificationsEnabled) {
            try { notifications.publish(event,detail,completionSound); }
            catch (RuntimeException e) { notice += " Notification delivery unavailable."; }
        }
    }
    public synchronized FocusSessionSnapshot startBreak() { cycle.start(nanoTime.getAsLong()); notice="Break started. Break time is excluded from study history."; return snapshot(); }
    public synchronized FocusSessionSnapshot skipBreak() { cycle.skip(); notice="Break skipped. Start the next focus period when ready."; return snapshot(); }
    public synchronized String blockingStatus() { return blocking==null ? "SIMULATED · inactive" : blocking.status(); }
    public void cancelBlockingImmediately() { if(blocking!=null) blocking.stop(); }
    public synchronized FocusSessionOptions options() {
        var profile = profiles.findLocalProfile();
        return new FocusSessionOptions(profile, classrooms.findAll(), plans.goals().stream()
                .filter(g -> profile.isPresent() && g.profileId().equals(profile.get().id())).toList());
    }
    public synchronized FocusSessionSnapshot start(long seconds, UUID classroomId, UUID goalId) {
        return start(seconds,classroomId,goalId,false);
    }
    public synchronized FocusSessionSnapshot start(long seconds, UUID classroomId, UUID goalId,boolean authorized) {
        return startSelected(seconds,classroomId,goalId,authorized,blocking==null?Set.of():blocking.globalTargets(),"global blocklist");
    }
    public synchronized FocusSessionSnapshot startWorkspace(com.studymode.domain.workspace.FocusWorkspace workspace,UUID goalId,boolean authorized) {
        workspace.validateAgainst(classrooms.find(workspace.classroomId()).orElseThrow(() -> new FocusModeException("The workspace classroom no longer exists.")));
        configureCycles(workspace.preferences(), automaticBreaks);
        return startSelected(workspace.preferences().focusMinutes()*60L,workspace.classroomId(),goalId,authorized,workspace.blocklist(),"workspace: "+workspace.id());
    }
    private FocusSessionSnapshot startSelected(long seconds,UUID classroomId,UUID goalId,boolean authorized,
            Set<com.studymode.domain.workspace.BlockedApplication> targets,String scope) {
        if (snapshot().occupied() || savePending) throw new FocusModeException("Finish or interrupt the current session and save it before starting another.");
        if (seconds <= 0 || seconds > 14_400 || (!demoMode && (seconds < 60 || seconds % 60 != 0))) {
            throw new FocusModeException(demoMode ? "Demo focus duration must be 1–14400 whole seconds." : "Focus duration must be 1–240 whole minutes.");
        }
        var profile = profiles.findLocalProfile().orElseThrow(() -> new FocusModeException("Create a local profile in Settings before starting a session."));
        var classroom = classroomId == null ? null : classrooms.find(classroomId).orElseThrow(() -> new FocusModeException("The selected classroom no longer exists. Reload the choices."));
        var goal = goalId == null ? null : plans.findGoal(goalId).orElseThrow(() -> new FocusModeException("The selected goal no longer exists. Reload the choices."));
        if (goal != null && !goal.profileId().equals(profile.id())) throw new FocusModeException("The selected goal belongs to another profile.");
        if (goal != null && goal.classroomId()!=null && !goal.classroomId().equals(classroomId))
            throw new FocusModeException("Select the classroom assigned to this goal before starting.");
        if (!ledger.findUnfinished().isEmpty()) throw new FocusModeException("An unfinished session already exists. Restart Study Mode to recover it safely.");
        if(blocking!=null) blocking.prepare(targets,authorized,scope);
        Instant now = clock.instant();
        SessionContext context = new SessionContext(classroomId, classroom == null ? "" : classroom.name(),
                goalId, goal == null ? "" : goal.text(), (int) seconds, demoMode);
        StudySession started = new StudySession(UUID.randomUUID(), profile.id(), now, null, 0, profile.studyZone(), SessionStatus.ACTIVE, context, now);
        ledger.save(started); // The timer starts only after durable creation succeeds.
        cycle.beginFocus(scope);
        current = started; focusedNanos = 0; anchor = nanoTime.getAsLong(); lastSave = anchor;
        savePending = false; notice = "Focus session started.";
        beginBlocking(); anchor=nanoTime.getAsLong();
        return snapshot();
    }
    public synchronized FocusSessionSnapshot pause() {
        if (cycle.snapshot().phase()==BreakCycle.Phase.RUNNING) {
            if(cycle.pause(nanoTime.getAsLong())) notifyCompletion(StudyNotifications.Event.BREAK_COMPLETED,"Break completed. Start focus when ready.");
            return snapshot();
        }
        require(FocusState.RUNNING, "Pause"); stopBlocking(); advance();
        if (state() == FocusState.RUNNING) { update(SessionStatus.PAUSED); notice = "Paused. Paused time is excluded."; persist(); }
        return snapshot();
    }
    public synchronized FocusSessionSnapshot resume() {
        if (cycle.snapshot().phase()==BreakCycle.Phase.PAUSED) { cycle.resume(nanoTime.getAsLong()); return snapshot(); }
        require(FocusState.PAUSED, "Resume");
        if (savePending) throw new FocusModeException("Save the paused checkpoint before resuming.");
        update(SessionStatus.ACTIVE); notice = "Focus session resumed."; persist();
        beginBlocking();
        anchor = nanoTime.getAsLong(); // Resume-save latency and the entire pause are excluded.
        return snapshot();
    }
    public synchronized FocusSessionSnapshot finish() { return end(SessionStatus.COMPLETED); }
    public synchronized FocusSessionSnapshot interrupt() { return end(SessionStatus.INTERRUPTED); }
    private FocusSessionSnapshot end(SessionStatus outcome) {
        if (cycle.snapshot().pending()) {
            if(outcome==SessionStatus.COMPLETED) throw new FocusModeException("Finish applies only to focus. Use Skip break.");
            cycle.cancel(); stopBlocking(); notice="Cycle stopped. No break time was recorded."; return snapshot();
        }
        if (!snapshot().active()) throw new FocusModeException("Finish/Stop requires a running or paused session.");
        stopBlocking(); advance();
        if(outcome==SessionStatus.INTERRUPTED && cycle.snapshot().pending()) cycle.cancel();
        if (snapshot().active()) {
            update(outcome);
            notice = outcome == SessionStatus.COMPLETED ? "Completed. Only actual focused time was recorded." : "Interrupted. Actual focused time was retained.";
            persist();
        }
        return snapshot();
    }
    public synchronized FocusSessionSnapshot tick() {
        if (savePending) return snapshot();
        if(cycle.tick(nanoTime.getAsLong())) {
            notice="Break completed. Start focus when ready.";
            notifyCompletion(StudyNotifications.Event.BREAK_COMPLETED,notice);
        }
        advance();
        if(state()==FocusState.RUNNING && blocking!=null) {
            try { blocking.refresh(); }
            catch(FocusModeException e) { stopBlocking(); update(SessionStatus.PAUSED); notice=e.getMessage(); persist(); }
        }
        if (state() == FocusState.RUNNING && nanoTime.getAsLong() - lastSave >= CHECKPOINT_INTERVAL) {
            update(SessionStatus.ACTIVE); persist();
        }
        return snapshot();
    }
    private void advance() {
        if (state() != FocusState.RUNNING) return;
        long now = nanoTime.getAsLong(); long elapsed = now - anchor; anchor = now;
        if (elapsed < 0 || elapsed > MAX_HEARTBEAT_GAP) {
            update(SessionStatus.PAUSED);
            notice = "Paused after a long timer gap (sleep or stalled updates). The gap was not credited; resume when ready.";
            persist(); return;
        }
        long target = current.context().plannedFocusSeconds() * SECOND;
        focusedNanos = Math.min(target, focusedNanos + elapsed);
        if (focusedNanos >= target) {
            update(SessionStatus.COMPLETED); notice = "Countdown completed. Actual focused time saved."; persist();
        }
    }
    private Instant safeNow() {
        Instant now = clock.instant();
        return now.isBefore(current.checkpointAt()) ? current.checkpointAt() : now;
    }
    private void update(SessionStatus status) {
        if(status!=SessionStatus.ACTIVE) stopBlocking();
        Instant now = safeNow(); boolean ended = status == SessionStatus.COMPLETED || status == SessionStatus.INTERRUPTED;
        current = new StudySession(current.id(), current.profileId(), current.startedAt(), ended ? now : null,
                focusedNanos / SECOND, current.studyZone(), status, current.context(), now);
    }
    private void persist() {
        savePending = true;
        try { ledger.save(current); savePending = false; lastSave = nanoTime.getAsLong();
            if(current.status()==SessionStatus.COMPLETED && !current.id().equals(notifiedCompletion)) {
                notifiedCompletion=current.id(); cycle.focusCompleted(lastSave);
                notifyCompletion(StudyNotifications.Event.FOCUS_COMPLETED,"Focus completed: " + current.activeFocusSeconds() + " actual seconds.");
            } else if(current.status()==SessionStatus.INTERRUPTED) cycle.cancel(); }
        catch (StudyDataException e) {
            stopBlocking();
            if (state() == FocusState.RUNNING) update(SessionStatus.PAUSED);
            notice = "Timer stopped advancing because a save failed. Retry saving before starting or resuming.";
            throw e;
        }
    }
    public synchronized FocusSessionSnapshot retrySave() {
        if (!savePending || current == null) throw new FocusModeException("There is no unsaved session to retry.");
        persist(); notice = "Session saved."; return snapshot();
    }
    /** Run at startup before enabling Start. Never estimates time while the process was absent. */
    public synchronized int recoverUnfinished() {
        if (current != null || savePending) throw new FocusModeException("Recovery is only available before a live session starts.");
        var unfinished = ledger.findUnfinished();
        for (StudySession session : unfinished) {
            ledger.save(new StudySession(session.id(), session.profileId(), session.startedAt(), session.checkpointAt(),
                    session.activeFocusSeconds(), session.studyZone(), SessionStatus.INTERRUPTED, session.context(), session.checkpointAt()));
        }
        if (!unfinished.isEmpty()) notice = "Recovered " + unfinished.size() + " unfinished session(s) as interrupted, using saved focus time only.";
        return unfinished.size();
    }
    public synchronized FocusSessionSnapshot shutdown() {
        stopBlocking();
        if (snapshot().occupied()) return interrupt();
        if (savePending) return retrySave();
        return snapshot();
    }
    public synchronized void clearAfterImport() {
        if (snapshot().occupied() || savePending) throw new FocusModeException("Cannot clear an active or unsaved session.");
        cycle.cancel();
        current = null; focusedNanos = 0; notice = "Study data reloaded.";
    }
    public synchronized void requireImportAllowed() {
        if(snapshot().occupied() || savePending) throw new DataImportException("Stop focus or skip the break and save before importing study data.");
    }
    public synchronized FocusSessionSnapshot snapshot() {
        long target = current == null ? 0 : current.context().plannedFocusSeconds() * SECOND;
        return new FocusSessionSnapshot(state(), current, focusedNanos, Math.max(0, target - focusedNanos), savePending, notice, cycle.snapshot());
    }
    private void stopBlocking() { if(blocking!=null) blocking.stop(); }
    private void beginBlocking() {
        if(blocking==null) return;
        try { blocking.begin(); }
        catch(FocusModeException e) { stopBlocking(); update(SessionStatus.PAUSED); notice=e.getMessage(); persist(); }
    }
    private FocusState state() {
        if (current == null) return FocusState.IDLE;
        return switch (current.status()) {
            case ACTIVE -> FocusState.RUNNING; case PAUSED -> FocusState.PAUSED;
            case COMPLETED -> FocusState.COMPLETED; case INTERRUPTED -> FocusState.INTERRUPTED;
        };
    }
    private void require(FocusState required, String action) {
        if (state() != required) throw new FocusModeException(action + " is not valid while " + state() + ".");
    }
}
