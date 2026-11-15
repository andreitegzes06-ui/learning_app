package com.studymode.bootstrap;

import com.studymode.application.service.ProfileService;
import com.studymode.application.port.DistractionBlocker;
import com.studymode.application.port.StudySessionLedger;
import com.studymode.platform.simulation.SimulatedDistractionBlocker;
import com.studymode.application.error.StudyDataException;
import com.studymode.persistence.config.*;
import com.studymode.persistence.sqlite.*;
import com.studymode.ui.AppShell;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static com.studymode.ui.view.ViewComponents.*;

public final class StudyModeApplication extends Application {
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "studymode-io"); thread.setDaemon(false); return thread;
    });
    private final StartupConfigurationLoader configurationLoader = new StartupConfigurationLoader();
    private DataDirectoryLock directoryLock;
    private final DistractionBlocker blocker = new SimulatedDistractionBlocker(Clock.systemUTC());
    private volatile boolean stopping;

    @Override public void start(Stage stage) {
        stage.setTitle("Study Mode"); stage.setMinWidth(900); stage.setMinHeight(640);
        showScene(stage, card(text("Study Mode", "page-title"), text("Opening your local study space…", "muted")), 1200, 800);
        stage.show(); initialize(stage);
    }

    private void initialize(Stage stage) {
        io.execute(() -> {
            DataDirectoryLock acquired = null;
            try {
                StartupConfiguration config = configurationLoader.load(getParameters().getRaw(), System.getenv());
                acquired = DataDirectoryLock.acquire(config.dataDirectory());
                SqliteDatabase database = new SqliteDatabase(config.dataDirectory().resolve("studymode.db"));
                new SchemaMigrator(database).migrate();
                ProfileService profiles = new ProfileService(new SqliteUserProfileStore(database), Clock.systemUTC());
                var profile = profiles.load();
                StudySessionLedger ledger = new SqliteStudySessionLedger(database);
                var sessions = ledger.findByPeriod(java.time.Instant.MIN, java.time.Instant.MAX);
                // Loading records and blocker status never starts a session or modifies processes.
                directoryLock = acquired; acquired = null;
                if (!stopping) Platform.runLater(() -> {
                    if (!stopping) showScene(stage, new AppShell(profiles, io, profile, config, configurationLoader, blocker, sessions.size(),
                            new SqliteClassroomCatalog(database), new com.studymode.persistence.transfer.JsonStudyDataTransfer(
                                    new SqliteStudyDataArchive(database, Clock.systemUTC()), config.dataDirectory())), config.windowWidth(), config.windowHeight());
                });
            } catch (IOException | StudyDataException | IllegalArgumentException | SecurityException e) {
                if (!stopping) Platform.runLater(() -> showFailure(stage, e));
            } finally {
                if (acquired != null) closeLock(acquired);
            }
        });
    }

    private void showFailure(Stage stage, Exception error) {
        Button retry = new Button("Retry opening data"); retry.getStyleClass().add("primary-button");
        retry.setOnAction(event -> { retry.setDisable(true); initialize(stage); });
        Button exit = new Button("Close Study Mode"); exit.setOnAction(event -> Platform.exit());
        VBox failure = card(text("We couldn’t open your study space", "page-title"),
                text(error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(), "feedback"),
                text("Your database has not been reset. Check the startup options, folder permissions, and any other running instance, then retry.", "muted"), retry, exit);
        showScene(stage, failure, 1000, 700);
    }

    private void showScene(Stage stage, javafx.scene.Parent root, int width, int height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(java.util.Objects.requireNonNull(getClass().getResource("/styles/dark.css")).toExternalForm());
        stage.setScene(scene);
    }

    @Override public void stop() {
        stopping = true;
        blocker.stop();
        // This queued cleanup follows any profile save already submitted; it does not block the FX thread.
        io.execute(() -> { if (directoryLock != null) closeLock(directoryLock); });
        io.shutdown();
    }
    private void closeLock(DataDirectoryLock lock) {
        try { lock.close(); } catch (IOException e) { System.err.println("Study Mode could not close its data lock: " + e.getMessage()); }
    }
}
