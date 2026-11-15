package com.studymode.application.data;

import com.studymode.domain.study.StudySession;
import java.util.Optional;

public record DashboardStatistics(long todayCompletedSeconds, long totalCompletedSeconds,
                                   long completedSessionCount, Optional<StudySession> mostRecentSession) { }
