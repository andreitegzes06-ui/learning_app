package com.studymode.application.service;

import com.studymode.application.data.DashboardStatistics;
import com.studymode.application.port.StudySessionLedger;
import com.studymode.domain.study.*;
import java.time.*;
import java.util.*;

public final class StudyStatisticsService {
    private final StudySessionLedger ledger;
    private final Clock clock;
    public StudyStatisticsService(StudySessionLedger ledger, Clock clock) { this.ledger = ledger; this.clock = clock; }
    public List<StudySession> history(UUID profileId) {
        return ledger.findByPeriod(Instant.MIN, Instant.MAX).stream().filter(s -> s.profileId().equals(profileId) && s.finished())
                .sorted(Comparator.comparing(StudySession::endedAt).thenComparing(StudySession::id).reversed()).toList();
    }
    /** Today means completed on today's date in the profile's reporting zone, not a duration estimate. */
    public DashboardStatistics summary(UUID profileId, ZoneId zone) {
        var history = history(profileId); LocalDate today = LocalDate.now(clock.withZone(zone));
        long todaySeconds = 0, totalSeconds = 0, count = 0;
        try {
            for (StudySession session : history) if (session.status() == SessionStatus.COMPLETED) {
                count++; totalSeconds = Math.addExact(totalSeconds, session.activeFocusSeconds());
                if (session.endedAt().atZone(zone).toLocalDate().equals(today)) todaySeconds = Math.addExact(todaySeconds, session.activeFocusSeconds());
            }
        } catch (ArithmeticException e) { throw new com.studymode.application.error.StudyDataException("Stored study totals exceed the supported range.", e); }
        return new DashboardStatistics(todaySeconds, totalSeconds, count, history.stream().findFirst());
    }
}
