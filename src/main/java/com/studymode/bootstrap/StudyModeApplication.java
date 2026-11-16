package com.studymode.bootstrap;

import com.studymode.application.service.ProfileService;
import com.studymode.application.service.FocusSessionService;
import com.studymode.application.service.StudyStatisticsService;
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
    private volatile DistractionBlocker blocker = new SimulatedDistractionBlocker(Clock.systemUTC());
    private volatile boolean stopping;
    private FocusSessionService focusSessions;
    private AppShell shell;

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
                blocker.close();
                blocker = switch(config.blockerMode()) {
                    case "windows" -> new com.studymode.platform.windows.WindowsDistractionBlocker(false,Clock.systemUTC());
                    case "windows-dry-run" -> new com.studymode.platform.windows.WindowsDistractionBlocker(true,Clock.systemUTC());
                    default -> new SimulatedDistractionBlocker(Clock.systemUTC());
                };
                focusSessions = new FocusSessionService(ledger,new SqliteUserProfileStore(database),new SqliteClassroomCatalog(database),
                        new SqliteStudyPlanStore(database),Clock.systemUTC(),System::nanoTime,config.demoMode(),
                        new com.studymode.application.service.FocusBlockingService(blocker,new SqliteBlockedApplicationCatalog(database)));
                focusSessions.recoverUnfinished();
                // Loading records and blocker status never starts a session or modifies processes.
                directoryLock = acquired; acquired = null;
                if (!stopping) Platform.runLater(() -> {
                    if (!stopping) {
                        stage.setTitle(config.demoMode() ? "Study Mode — Demo / Test" : "Study Mode");
                        shell = new AppShell(profiles, io, profile, config, configurationLoader, blocker,
                                new com.studymode.persistence.transfer.JsonStudyDataTransfer(
                                new SqliteStudyDataArchive(database, Clock.systemUTC()), config.dataDirectory()),focusSessions,new StudyStatisticsService(ledger,Clock.systemUTC()),
                                new com.studymode.application.service.StudyOrganizationService(new SqliteClassroomCatalog(database),new SqliteFocusWorkspaceStore(database),
                                        new SqliteStudyPlanStore(database),new SqliteUserProfileStore(database),ledger,new SqliteBlockedApplicationCatalog(database),Clock.systemUTC()),
                                getHostServices()::showDocument,new com.studymode.application.service.BlockedApplicationService(new SqliteBlockedApplicationCatalog(database),blocker));
                        showScene(stage,shell,config.windowWidth(),config.windowHeight());
                        stage.setOnCloseRequest(event -> { event.consume(); shell.requestClose(Platform::exit); });
                    }
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
        blocker.close();
        if (shell != null) shell.close();
        // This queued cleanup follows any profile save already submitted; it does not block the FX thread.
        io.execute(() -> {
            try { if (focusSessions != null) focusSessions.shutdown(); }
            catch (StudyDataException e) { System.err.println("Final session save failed; restart recovery will use the last committed checkpoint: " + e.getMessage()); }
            finally { if (directoryLock != null) closeLock(directoryLock); }
        });
        io.shutdown();
    }
    private void closeLock(DataDirectoryLock lock) {
        try { lock.close(); } catch (IOException e) { System.err.println("Study Mode could not close its data lock: " + e.getMessage()); }
    }
}
