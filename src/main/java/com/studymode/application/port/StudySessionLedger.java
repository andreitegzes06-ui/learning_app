package com.studymode.application.port;

import com.studymode.domain.study.StudySession;
import java.time.Instant;
import java.util.List;

/** Durable session facts. Period queries use a start-inclusive, end-exclusive interval. */
public interface StudySessionLedger {
    void save(StudySession session);
    List<StudySession> findByPeriod(Instant fromInclusive, Instant toExclusive);
}
