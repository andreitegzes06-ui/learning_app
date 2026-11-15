package com.studymode.ui.controller;

import com.studymode.application.service.*;
import com.studymode.application.data.DashboardStatistics;
import com.studymode.application.error.StudyDataException;
import com.studymode.ui.view.StudyTimeText;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import static com.studymode.ui.view.ViewComponents.*;

public final class DashboardController {
    private final StudyStatisticsService statistics;
    private final ProfileService profiles;
    private final ExecutorService io;
    private final Label today = text("Loading…", "card-value");
    private final Label total = text("Loading…", "card-value");
    private final Label count = text("Loading…", "card-value");
    private final Label recent = text("Loading…", "body-text");
    private final Label feedback = text("", "feedback");
    private final VBox view;
    public DashboardController(StudyStatisticsService statistics, ProfileService profiles, ExecutorService io) {
        this.statistics = statistics; this.profiles = profiles; this.io = io;
        today.setId("today-focus"); total.setId("total-focus"); count.setId("completed-sessions"); recent.setId("recent-session");
        var todayCard = card(text("TODAY’S COMPLETED FOCUS", "eyebrow"), today, text("Completed today in your profile’s study zone", "muted"));
        var totalCard = card(text("TOTAL COMPLETED FOCUS", "eyebrow"), total, text("Actual seconds; interrupted sessions excluded", "muted"));
        var countCard = card(text("COMPLETED SESSIONS", "eyebrow"), count);
        for (var card : java.util.List.of(todayCard, totalCard, countCard)) card.setPrefWidth(330);
        view = new VBox(18, new FlowPane(16,16,todayCard,totalCard,countCard), card(text("Most recent saved session", "section-title"),recent), feedback);
    }
    public VBox view() { return view; }
    public void refresh() {
        io.execute(() -> {
            try {
                var profile = profiles.load();
                var summary = profile.map(p -> statistics.summary(p.id(), p.studyZone())).orElseGet(() -> new DashboardStatistics(0,0,0,Optional.empty()));
                Platform.runLater(() -> {
                    today.setText(StudyTimeText.duration(summary.todayCompletedSeconds())); total.setText(StudyTimeText.duration(summary.totalCompletedSeconds()));
                    count.setText(Long.toString(summary.completedSessionCount()));
                    recent.setText(summary.mostRecentSession().map(s -> s.endedAt().atZone(s.studyZone()).toLocalDate() + " · " + s.status()
                            + " · " + StudyTimeText.duration(s.activeFocusSeconds()) + " · " + (s.context().demo() ? "DEMO · " : "")
                            + (s.context().classroomName().isEmpty() ? "General study" : s.context().classroomName())).orElse("No saved sessions yet."));
                    feedback.setText("");
                });
            } catch (StudyDataException e) { Platform.runLater(() -> feedback.setText(e.getMessage())); }
        });
    }
}
