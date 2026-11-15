package com.studymode.application.data;

import com.studymode.domain.study.*;

/** Immutable UI state. Nanoseconds preserve sub-second progress across pauses within this process. */
public record FocusSessionSnapshot(FocusState state, StudySession session, long focusedNanos,
                                   long remainingNanos, boolean savePending, String notice) {
    public boolean active() { return state == FocusState.RUNNING || state == FocusState.PAUSED; }
    public double progress() {
        if (session == null || session.context().plannedFocusSeconds() == 0) return 0;
        return Math.min(1, (double) focusedNanos / (session.context().plannedFocusSeconds() * 1_000_000_000L));
    }
}
