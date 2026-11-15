package com.studymode.domain.study;

import com.studymode.domain.Validation;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/** Foundation session facts. Workspace snapshots and per-day checkpoints arrive with M3. */
public record StudySession(UUID id, UUID profileId, Instant startedAt, Instant endedAt,
                           long activeFocusSeconds, ZoneId studyZone, SessionStatus status) {
    public StudySession {
        Validation.required(id, "Session ID");
        Validation.required(profileId, "Profile ID");
        Validation.required(startedAt, "Start time");
        Validation.required(studyZone, "Study time zone");
        Validation.required(status, "Session status");
        boolean finished = status == SessionStatus.COMPLETED || status == SessionStatus.INTERRUPTED;
        if (finished != (endedAt != null)) throw new IllegalArgumentException("Only finished sessions must have an end time.");
        if (activeFocusSeconds < 0 || (endedAt != null && endedAt.isBefore(startedAt))) {
            throw new IllegalArgumentException("Session duration must be nonnegative and end cannot precede start.");
        }
    }

    public boolean finished() { return endedAt != null; }
}
