package com.studymode.ui.controller;

import com.studymode.application.data.*;
import com.studymode.application.error.*;
import com.studymode.application.service.FocusSessionService;
import com.studymode.domain.study.FocusState;
import com.studymode.ui.view.StudyTimeText;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import static com.studymode.ui.view.ViewComponents.*;

/** FX controls only; timer transitions and all persistence run on the shared I/O executor. */
public final class FocusSessionController implements AutoCloseable {
    private final FocusSessionService service;
    private final ExecutorService io;
    private final boolean demo;
    private final Runnable onSaved;
    private final TextField duration = new TextField();
    private final ComboBox<Selection> classroom = new ComboBox<>();
    private final ComboBox<Selection> goal = new ComboBox<>();
    private final Label countdown = text("", "countdown");
    private final Label state = text("IDLE", "status-badge");
    private final Label actual = text("Focused: 0h 00m 00s", "body-text");
    private final Label feedback = text("", "feedback");
    private final ProgressBar progress = new ProgressBar(0);
    private final Button start = new Button("Start");
    private final Button pause = new Button("Pause");
    private final Button resume = new Button("Resume");
    private final Button finish = new Button("Finish");
    private final Button stop = new Button("Stop / Interrupt");
    private final Button retry = new Button("Retry saving");
    private final Button preset25 = new Button("25 minutes");
    private final Button preset50 = new Button("50 minutes");
    private final Button reload = new Button("Reload choices");
    private final VBox view;
    private final ScheduledExecutorService pulses = Executors.newSingleThreadScheduledExecutor(r -> { var t = new Thread(r,"studymode-ui-pulse"); t.setDaemon(true); return t; });
    private final AtomicBoolean pulseQueued = new AtomicBoolean();
    private volatile boolean closed;
    private boolean busy;
    private boolean hasProfile;
    private FocusSessionSnapshot latest = new FocusSessionSnapshot(FocusState.IDLE,null,0,0,false,"");
    private String lastError = "";
    private UUID lastNotified;
    private record Selection(UUID id, String label) { @Override public String toString() { return label; } }

    public FocusSessionController(FocusSessionService service, ExecutorService io, boolean demo, Runnable onSaved, Runnable openSettings) {
        this.service = service; this.io = io; this.demo = demo; this.onSaved = onSaved;
        duration.setId("focus-duration"); duration.setText(demo ? "10" : "25"); duration.setMaxWidth(180);
        duration.setAccessibleText(demo ? "Focus seconds in demo mode" : "Focus minutes");
        classroom.setId("focus-classroom"); goal.setId("focus-goal"); classroom.setMaxWidth(440); goal.setMaxWidth(440);
        classroom.setAccessibleText("Optional classroom"); goal.setAccessibleText("Optional goal");
        classroom.getItems().add(new Selection(null,"No classroom")); goal.getItems().add(new Selection(null,"No goal"));
        classroom.getSelectionModel().selectFirst(); goal.getSelectionModel().selectFirst();
        preset25.setOnAction(event -> duration.setText(demo ? "1500" : "25")); preset50.setOnAction(event -> duration.setText(demo ? "3000" : "50"));
        countdown.setId("focus-countdown"); state.setId("focus-state"); actual.setId("focus-actual"); progress.setId("focus-progress"); progress.setMaxWidth(Double.MAX_VALUE);
        start.setId("focus-start"); pause.setId("focus-pause"); resume.setId("focus-resume");
        finish.setId("focus-finish"); stop.setId("focus-stop"); retry.setId("focus-retry");
        start.getStyleClass().add("primary-button"); finish.getStyleClass().add("primary-button");
        start.setOnAction(event -> {
            try {
                long entered = Long.parseLong(duration.getText().strip());
                if (entered < 1 || entered > (demo ? 14400 : 240)) throw new IllegalArgumentException("Duration is outside the allowed range.");
                long seconds = demo ? entered : entered * 60;
                UUID classroomId = classroom.getValue() == null ? null : classroom.getValue().id();
                UUID goalId = goal.getValue() == null ? null : goal.getValue().id();
                action(() -> service.start(seconds,classroomId,goalId));
            } catch (NumberFormatException e) { lastError = "Enter a whole number of " + (demo ? "seconds." : "minutes."); render(latest); }
            catch (IllegalArgumentException e) { lastError = e.getMessage(); render(latest); }
        });
        pause.setOnAction(event -> action(service::pause)); resume.setOnAction(event -> action(service::resume));
        finish.setOnAction(event -> action(service::finish)); stop.setOnAction(event -> action(service::interrupt)); retry.setOnAction(event -> action(service::retrySave));
        reload.setOnAction(event -> refresh());
        Button settings = new Button("Open profile settings"); settings.setOnAction(event -> openSettings.run());
        VBox timerCard = card(state,countdown,progress,actual,new FlowPane(10,10,start,pause,resume,finish,stop,retry),feedback,
                text("Finish marks a completed session early and saves only actual focus. Stop records an interruption. A long sleep/update gap pauses without crediting that gap.","muted"));
        VBox settingsCard = card(text("Set your intention", "section-title"), text(demo ? "Focus duration in seconds (1–14400)" : "Focus duration in minutes (1–240)","field-label"),
                duration,new FlowPane(10,10,preset25,preset50),text("Classroom (optional)","field-label"),classroom,text("Goal (optional)","field-label"),goal,new FlowPane(10,10,reload,settings));
        timerCard.setPrefWidth(480); settingsCard.setPrefWidth(330);
        view = new VBox(22,text(demo ? "DEMO / TEST MODE · real seconds are recorded" : "STANDARD FOCUS · actual focused time only", "status-badge"),
                new FlowPane(18,18,timerCard,settingsCard));
        render(latest);
        duration.textProperty().addListener((observable, before, value) -> {
            if (latest.state() == FocusState.IDLE) countdown.setText(previewCountdown());
        });
        pulses.scheduleAtFixedRate(this::queuePulse,250,250,TimeUnit.MILLISECONDS);
    }
    public VBox view() { return view; }
    public void refresh() {
        io.execute(() -> {
            try {
                var options = service.options(); var snapshot = service.snapshot();
                Platform.runLater(() -> {
                    if (closed) return; hasProfile = options.profile().isPresent();
                    UUID selectedClassroom = classroom.getValue() == null ? null : classroom.getValue().id();
                    UUID selectedGoal = goal.getValue() == null ? null : goal.getValue().id();
                    classroom.getItems().setAll(new Selection(null,"No classroom"));
                    options.classrooms().forEach(c -> classroom.getItems().add(new Selection(c.id(),c.name())));
                    goal.getItems().setAll(new Selection(null,"No goal")); options.goals().forEach(g -> goal.getItems().add(new Selection(g.id(),g.text())));
                    classroom.getSelectionModel().select(classroom.getItems().stream().filter(s -> java.util.Objects.equals(s.id(),selectedClassroom)).findFirst().orElse(classroom.getItems().getFirst()));
                    goal.getSelectionModel().select(goal.getItems().stream().filter(s -> java.util.Objects.equals(s.id(),selectedGoal)).findFirst().orElse(goal.getItems().getFirst()));
                    render(snapshot);
                });
            } catch (StudyDataException e) { report(e); }
        });
    }
    public void resetAfterImport() {
        io.execute(() -> { try { service.clearAfterImport(); refresh(); } catch (FocusModeException e) { report(e); } });
    }
    private void action(Supplier<FocusSessionSnapshot> command) {
        if (busy || closed) return; busy = true; render(latest);
        io.execute(() -> {
            try { var snapshot = command.get(); Platform.runLater(() -> { if (closed) return; busy = false; lastError = ""; render(snapshot); }); }
            catch (FocusModeException | StudyDataException e) { report(e); }
        });
    }
    private void queuePulse() {
        if (closed || !pulseQueued.compareAndSet(false,true)) return;
        try {
            io.execute(() -> {
                try { var snapshot = service.tick(); Platform.runLater(() -> { if (!closed) render(snapshot); }); }
                catch (FocusModeException | StudyDataException e) { report(e); }
                finally { pulseQueued.set(false); }
            });
        } catch (RejectedExecutionException e) { pulseQueued.set(false); if (!closed) throw e; }
    }
    private void report(RuntimeException error) {
        var snapshot = service.snapshot();
        Platform.runLater(() -> { if (closed) return; busy = false; lastError = error.getMessage(); render(snapshot); });
    }
    private void render(FocusSessionSnapshot snapshot) {
        latest = snapshot; state.setText(snapshot.state().name() + (snapshot.savePending() ? " · NOT SAVED" : ""));
        countdown.setText(snapshot.session() == null ? previewCountdown() : StudyTimeText.countdown(snapshot.remainingNanos()));
        progress.setProgress(snapshot.progress()); actual.setText("Focused: " + StudyTimeText.duration(snapshot.focusedNanos() / 1_000_000_000L));
        feedback.setText(!lastError.isEmpty() ? lastError : !hasProfile ? "Create a local profile in Settings before starting." : snapshot.notice());
        boolean active = snapshot.active(); boolean configuring = !busy && !active && !snapshot.savePending();
        start.setDisable(!configuring || !hasProfile); pause.setDisable(busy || snapshot.state() != FocusState.RUNNING);
        resume.setDisable(busy || snapshot.state() != FocusState.PAUSED || snapshot.savePending());
        finish.setDisable(busy || !active || snapshot.savePending()); stop.setDisable(busy || !active);
        retry.setVisible(snapshot.savePending()); retry.setManaged(snapshot.savePending()); retry.setDisable(busy || !snapshot.savePending());
        duration.setDisable(!configuring); preset25.setDisable(!configuring); preset50.setDisable(!configuring);
        classroom.setDisable(!configuring); goal.setDisable(!configuring); reload.setDisable(busy || active);
        if (snapshot.session() != null && snapshot.session().finished() && !snapshot.savePending() && !snapshot.session().id().equals(lastNotified)) {
            lastNotified = snapshot.session().id(); onSaved.run();
        }
    }
    private String previewCountdown() {
        try {
            long entered = Long.parseLong(duration.getText().strip());
            return entered >= 1 && entered <= (demo ? 14400 : 240)
                    ? StudyTimeText.countdown(entered * (demo ? 1 : 60) * 1_000_000_000L) : "--:--";
        } catch (NumberFormatException e) { return "--:--"; }
    }
    /** The window remains open if its final durable save fails. */
    public void requestClose(Runnable exit, java.util.function.Consumer<Boolean> setBusy) {
        if (busy) return;
        if (latest.active()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,"Interrupt the current focus session, save its actual focus time, and close?",ButtonType.CANCEL,ButtonType.OK);
            confirm.initOwner(view.getScene().getWindow()); confirm.setHeaderText("Close during a focus session");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        }
        busy = true; setBusy.accept(true);
        io.execute(() -> {
            try { var snapshot = service.shutdown(); Platform.runLater(() -> { busy = false; render(snapshot); exit.run(); }); }
            catch (StudyDataException | FocusModeException e) {
                Platform.runLater(() -> {
                    setBusy.accept(false);
                    Alert error = new Alert(Alert.AlertType.ERROR,"The session could not be saved, so Study Mode remains open. Open Focus Session and retry saving.\n" + e.getMessage(),ButtonType.OK);
                    error.initOwner(view.getScene().getWindow()); error.setHeaderText("Session save failed"); error.show();
                });
                report(e);
            }
        });
    }
    @Override public void close() { closed = true; pulses.shutdownNow(); }
}
