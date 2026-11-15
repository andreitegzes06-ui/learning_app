package com.studymode.ui.controller;

import com.studymode.application.service.*;
import com.studymode.application.error.StudyDataException;
import com.studymode.domain.study.StudySession;
import com.studymode.ui.view.StudyTimeText;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import static com.studymode.ui.view.ViewComponents.*;

public final class StudyHistoryController {
    private final StudyStatisticsService statistics;
    private final ProfileService profiles;
    private final ExecutorService io;
    private final TableView<StudySession> table = new TableView<>();
    private final Label feedback = text("", "feedback");
    private final VBox view;
    private final Button reload = new Button("Refresh history");
    public StudyHistoryController(StudyStatisticsService statistics, ProfileService profiles, ExecutorService io) {
        this.statistics = statistics; this.profiles = profiles; this.io = io;
        table.setId("session-history"); table.setPrefHeight(420);
        table.setPlaceholder(text("No completed or interrupted sessions yet. Start a session in Focus Session.", "muted"));
        TableColumn<StudySession, Instant> date = new TableColumn<>("Date / time");
        date.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue().endedAt()));
        date.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Instant value, boolean empty) {
                super.updateItem(value,empty); var session = getTableRow() == null ? null : getTableRow().getItem();
                setText(empty || value == null || session == null ? null : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(session.studyZone()).format(value));
            }
        });
        TableColumn<StudySession, String> classroom = new TableColumn<>("Classroom");
        classroom.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().context().classroomName().isEmpty() ? "General study" : value.getValue().context().classroomName()));
        TableColumn<StudySession, Long> duration = new TableColumn<>("Actual focus");
        duration.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue().activeFocusSeconds()));
        duration.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(Long value, boolean empty) { super.updateItem(value,empty); setText(empty || value == null ? null : StudyTimeText.duration(value)); }
        });
        TableColumn<StudySession, String> outcome = new TableColumn<>("Outcome");
        outcome.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().status().name()));
        TableColumn<StudySession, String> mode = new TableColumn<>("Mode");
        mode.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().context().demo() ? "DEMO" : value.getValue().context().plannedFocusSeconds() == 0 ? "Legacy" : "Standard"));
        TableColumn<StudySession, String> goal = new TableColumn<>("Goal");
        goal.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().context().goalName()));
        table.getColumns().add(date); table.getColumns().add(classroom); table.getColumns().add(duration);
        table.getColumns().add(outcome); table.getColumns().add(mode); table.getColumns().add(goal);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        reload.setOnAction(event -> refresh());
        view = new VBox(18, card(text("Your recorded sessions", "section-title"),text("Only actual focused seconds are recorded. Dates use the session’s captured study zone.","muted"), table, reload, feedback));
    }
    public VBox view() { return view; }
    public void refresh() {
        reload.setDisable(true);
        io.execute(() -> {
            try {
                var profile = profiles.load(); List<StudySession> sessions = profile.isPresent() ? statistics.history(profile.get().id()) : List.of();
                Platform.runLater(() -> { table.getItems().setAll(sessions); reload.setDisable(false); feedback.setText(sessions.size() + " saved session(s)."); });
            } catch (StudyDataException e) { Platform.runLater(() -> { reload.setDisable(false); feedback.setText(e.getMessage()); }); }
        });
    }
}
