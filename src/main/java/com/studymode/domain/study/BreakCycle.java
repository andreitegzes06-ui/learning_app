package com.studymode.domain.study;

import com.studymode.domain.workspace.FocusPreferences;


/** In-process cycle state. Restart deliberately discards breaks and cycle counts. */
public final class BreakCycle {
    public enum Phase { NONE, READY, RUNNING, PAUSED, COMPLETE, SKIPPED }
    public record Snapshot(Phase phase, boolean longBreak, int completedFocusPeriods, long remainingNanos,
                           FocusPreferences preferences, boolean automatic) {
        public Snapshot(Phase phase, boolean longBreak, int completedFocusPeriods, long remainingNanos) {
            this(phase,longBreak,completedFocusPeriods,remainingNanos,FocusPreferences.defaults(),false);
        }
        public boolean pending() { return phase == Phase.READY || phase == Phase.RUNNING || phase == Phase.PAUSED; }
    }
    private FocusPreferences preferences = FocusPreferences.defaults();
    private boolean automatic;
    private Phase phase = Phase.NONE;
    private int completed;
    private boolean longBreak;
    private long remaining, anchor;
    private String scope = "";

    public void configure(FocusPreferences preferences, boolean automatic) {
        if (snapshot().pending()) throw new IllegalArgumentException("Finish or skip the current break before changing cycles.");
        java.util.Objects.requireNonNull(preferences);
        if (!this.preferences.equals(preferences)) completed = 0;
        this.preferences = preferences; this.automatic = automatic;
    }
    public void beginFocus(String scope) {
        if (!this.scope.equals(scope)) { completed = 0; this.scope = scope; }
        phase = Phase.NONE; remaining = 0;
    }
    public void focusCompleted(long now) {
        completed++; longBreak = completed % preferences.longBreakEvery() == 0;
        remaining = (longBreak ? preferences.longBreakMinutes() : preferences.shortBreakMinutes()) * 60_000_000_000L;
        phase = Phase.READY;
        if (automatic) start(now);
    }
    public void start(long now) {
        require(Phase.READY); phase = Phase.RUNNING; anchor = now;
    }
    /** Returns true exactly once per completed break; suspend gaps pause without consuming time. */
    public boolean tick(long now) {
        if (phase != Phase.RUNNING) return false;
        long elapsed = now - anchor; anchor = now;
        if (elapsed < 0 || elapsed > 15_000_000_000L) { phase = Phase.PAUSED; return false; }
        remaining = Math.max(0, remaining - elapsed);
        if (remaining == 0) { phase = Phase.COMPLETE; return true; }
        return false;
    }
    public boolean pause(long now) { require(Phase.RUNNING); boolean completed=tick(now); if (phase == Phase.RUNNING) phase = Phase.PAUSED; return completed; }
    public void resume(long now) { require(Phase.PAUSED); phase = Phase.RUNNING; anchor = now; }
    public void skip() { if (!snapshot().pending()) throw new IllegalArgumentException("There is no break to skip."); phase = Phase.SKIPPED; remaining = 0; }
    public void cancel() { phase = Phase.NONE; remaining = 0; completed = 0; }
    public Snapshot snapshot() { return new Snapshot(phase, longBreak, completed, remaining, preferences, automatic); }
    private void require(Phase expected) { if (phase != expected) throw new IllegalArgumentException("Break is " + phase + "; expected " + expected + "."); }
}
