package com.studymode.domain.study;

import com.studymode.domain.Validation;
import java.util.UUID;

/** Immutable settings/labels captured at Start; zero planned seconds identifies a legacy record. */
public record SessionContext(UUID classroomId, String classroomName, UUID goalId, String goalName,
                             int plannedFocusSeconds, boolean demo) {
    public SessionContext {
        Validation.range(plannedFocusSeconds, 0, 14_400, "Planned focus seconds");
        classroomName = Validation.required(classroomName, "Classroom snapshot").strip();
        goalName = Validation.required(goalName, "Goal snapshot").strip();
        if (!classroomName.isEmpty()) classroomName = Validation.text(classroomName, "Classroom snapshot", 100);
        if (!goalName.isEmpty()) goalName = Validation.text(goalName, "Goal snapshot", 200);
        if ((classroomId != null && classroomName.isEmpty()) || (goalId != null && goalName.isEmpty())) {
            throw new IllegalArgumentException("Selected classrooms and goals require saved labels.");
        }
        if (demo && plannedFocusSeconds == 0) throw new IllegalArgumentException("Demo sessions require a positive planned duration.");
        if (!demo && plannedFocusSeconds != 0 && (plannedFocusSeconds < 60 || plannedFocusSeconds % 60 != 0)) {
            throw new IllegalArgumentException("Standard sessions use whole minutes; use explicit demo mode for short sessions.");
        }
    }
    public static SessionContext legacy() { return new SessionContext(null, "", null, "", 0, false); }
}
