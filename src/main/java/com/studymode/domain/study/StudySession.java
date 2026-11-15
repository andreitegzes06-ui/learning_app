package com.studymode.domain.study;

import com.studymode.domain.Validation;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/** Durable focus facts; elapsed seconds are measured monotonically, independently of wall-clock labels. */
public record StudySession(UUID id, UUID profileId, Instant startedAt, Instant endedAt,
                           long activeFocusSeconds, ZoneId studyZone, SessionStatus status,
                           SessionContext context, Instant checkpointAt) {
    /** Source compatibility for existing ledger clients and records without focus settings. */
    public StudySession(UUID id, UUID profileId, Instant startedAt, Instant endedAt,
                        long activeFocusSeconds, ZoneId studyZone, SessionStatus status) {
        this(id, profileId, startedAt, endedAt, activeFocusSeconds, studyZone, status,
                SessionContext.legacy(), endedAt == null ? startedAt : endedAt);
    }
    public StudySession {
        Validation.required(id, "Session ID");
        Validation.required(profileId, "Profile ID");
        Validation.required(startedAt, "Start time");
        Validation.required(studyZone, "Study time zone");
        Validation.required(status, "Session status");
        Validation.required(context, "Session context");
        Validation.required(checkpointAt, "Checkpoint time");
        if (context.plannedFocusSeconds() > 0 && activeFocusSeconds > context.plannedFocusSeconds()) {
            throw new IllegalArgumentException("Actual focused time cannot exceed the session's planned duration.");
        }
        boolean finished = status == SessionStatus.COMPLETED || status == SessionStatus.INTERRUPTED;
        if (finished != (endedAt != null)) throw new IllegalArgumentException("Only finished sessions must have an end time.");
        if (activeFocusSeconds < 0 || (endedAt != null && endedAt.isBefore(startedAt))) {
            throw new IllegalArgumentException("Session duration must be nonnegative and end cannot precede start.");
        }
        if (checkpointAt.isBefore(startedAt) || (endedAt != null && checkpointAt.isAfter(endedAt))) {
            throw new IllegalArgumentException("Checkpoint time must lie within the session's recorded interval.");
        }
    }

    public boolean finished() { return endedAt != null; }
}
