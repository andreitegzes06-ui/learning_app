package com.studymode.ui;

import com.studymode.application.service.ProfileService;
import com.studymode.persistence.config.*;
import com.studymode.persistence.sqlite.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in test using an actual Windows JavaFX window; no headless toolkit substitution. */
@EnabledIfSystemProperty(named = "studymode.ui.tests", matches = "true")
class JavaFxSmokeTest {
    @TempDir Path root;
    @TempDir Path exports;
    @Test void allNinePagesNavigateAndProfileFormValidatesSavesAndEdits() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        var io = Executors.newSingleThreadExecutor();
        var database = new SqliteDatabase(root.resolve("ui.db")); new SchemaMigrator(database).migrate();
        var service = new ProfileService(new SqliteUserProfileStore(database), Clock.systemUTC());
        var fakeNanos = new java.util.concurrent.atomic.AtomicLong();
        var ledger = new SqliteStudySessionLedger(database);
        var focus = new com.studymode.application.service.FocusSessionService(ledger,new SqliteUserProfileStore(database),new SqliteClassroomCatalog(database),new SqliteStudyPlanStore(database),Clock.systemUTC(),fakeNanos::get,true);
        Stage[] stage = new Stage[1]; AppShell[] shell = new AppShell[1];
        try {
            fx(() -> {
                shell[0] = new AppShell(service, io, Optional.empty(), new StartupConfiguration(root, "dashboard", 1200, 800,true), new StartupConfigurationLoader(),
                        new com.studymode.platform.simulation.SimulatedDistractionBlocker(Clock.systemUTC()),
                        new SqliteClassroomCatalog(database), new com.studymode.persistence.transfer.JsonStudyDataTransfer(new SqliteStudyDataArchive(database, Clock.systemUTC()), root),focus,new com.studymode.application.service.StudyStatisticsService(ledger,Clock.systemUTC()));
                stage[0] = new Stage(); stage[0].setTitle("Study Mode — UI test");
                Scene scene = new Scene(shell[0], 1200, 800);
                scene.getStylesheets().add(getClass().getResource("/styles/dark.css").toExternalForm());
                stage[0].setScene(scene); stage[0].show();
                assertEquals(NavigationPage.SETTINGS, shell[0].currentPage());
                for (NavigationPage page : NavigationPage.values()) {
                    ((ToggleButton) shell[0].lookup("#nav-" + page.key())).fire();
                    shell[0].applyCss(); shell[0].layout();
                    assertEquals(page, shell[0].currentPage());
                    assertNotNull(shell[0].lookup("#page-content"));
                }
                ((Button) shell[0].lookup("#save-profile")).fire();
                assertTrue(shell[0].lookupAll(".feedback").stream().map(node -> ((Label) node).getText()).anyMatch(text -> text.contains("Display name")));
                ((TextField) shell[0].lookup("#profile-name")).setText("Foundation Student");
                ((Button) shell[0].lookup("#save-profile")).fire();
            });
            io.submit(() -> { }).get(10, TimeUnit.SECONDS);
            fx(() -> assertTrue(shell[0].lookupAll(".feedback").stream().map(node -> ((Label) node).getText()).anyMatch(text -> text.contains("Profile saved"))));
            var created = service.load().orElseThrow(); assertEquals("Foundation Student", created.displayName());
            fx(() -> {
                ((TextField) shell[0].lookup("#profile-name")).setText("Edited Student");
                ((Button) shell[0].lookup("#save-profile")).fire();
            });
            io.submit(() -> { }).get(10, TimeUnit.SECONDS);
            fx(() -> {
                shell[0].navigate(NavigationPage.DASHBOARD); shell[0].applyCss(); shell[0].layout();
                assertTrue(shell[0].lookupAll(".label").stream().map(node -> ((Label) node).getText()).anyMatch(text -> text.contains("Welcome back, Edited Student")));
                stage[0].setWidth(900); stage[0].setHeight(640); shell[0].layout();
                shell[0].navigate(NavigationPage.SETTINGS);
                shell[0].applyCss(); shell[0].layout();
                ((Button) shell[0].lookup("#save-window-size")).fire();
            });
            io.submit(() -> { }).get(10, TimeUnit.SECONDS);
            var edited = new SqliteUserProfileStore(new SqliteDatabase(root.resolve("ui.db"))).findLocalProfile().orElseThrow();
            assertEquals(created.id(), edited.id()); assertEquals("Edited Student", edited.displayName());
            assertTrue(java.nio.file.Files.exists(root.resolve("settings.properties")));
            fx(() -> { shell[0].navigate(NavigationPage.FOCUS); shell[0].applyCss(); shell[0].layout(); });
            io.submit(() -> { }).get(10,TimeUnit.SECONDS);
            fx(() -> { ((TextField)shell[0].lookup("#focus-duration")).setText("50"); shell[0].navigate(NavigationPage.FOCUS); shell[0].applyCss(); shell[0].layout(); });
            io.submit(() -> { }).get(10,TimeUnit.SECONDS);
            fx(() -> {
                assertEquals("00:50",((Label)shell[0].lookup("#focus-countdown")).getText());
                ((TextField)shell[0].lookup("#focus-duration")).setText("0"); ((Button)shell[0].lookup("#focus-start")).fire();
                assertEquals(com.studymode.domain.study.FocusState.IDLE,focus.snapshot().state());
                ((TextField)shell[0].lookup("#focus-duration")).setText("10"); ((Button)shell[0].lookup("#focus-start")).fire();
            });
            io.submit(() -> { }).get(10,TimeUnit.SECONDS); fakeNanos.addAndGet(2_000_000_000L);
            fx(() -> ((Button)shell[0].lookup("#focus-pause")).fire());
            io.submit(() -> { }).get(10,TimeUnit.SECONDS); assertEquals(com.studymode.domain.study.FocusState.PAUSED,focus.snapshot().state());
            fakeNanos.addAndGet(60_000_000_000L); fx(() -> ((Button)shell[0].lookup("#focus-resume")).fire());
            io.submit(() -> { }).get(10,TimeUnit.SECONDS); fakeNanos.addAndGet(3_000_000_000L);
            fx(() -> ((Button)shell[0].lookup("#focus-finish")).fire());
            io.submit(() -> { }).get(10,TimeUnit.SECONDS); assertEquals(5,focus.snapshot().session().activeFocusSeconds());
            fx(() -> { shell[0].navigate(NavigationPage.HISTORY); shell[0].applyCss(); shell[0].layout(); });
            io.submit(() -> { }).get(10,TimeUnit.SECONDS);
            fx(() -> assertEquals(1,((TableView<?>)shell[0].lookup("#session-history")).getItems().size()));
            fx(() -> { shell[0].navigate(NavigationPage.DASHBOARD); shell[0].applyCss(); shell[0].layout(); });
            io.submit(() -> { }).get(10,TimeUnit.SECONDS);
            fx(() -> {
                assertEquals("1",((Label)shell[0].lookup("#completed-sessions")).getText());
                assertTrue(((Label)shell[0].lookup("#total-focus")).getText().contains("05s"));
            });
            fx(() -> { shell[0].navigate(NavigationPage.FOCUS); shell[0].applyCss(); shell[0].layout(); });
            io.submit(() -> { }).get(10,TimeUnit.SECONDS);
            fx(() -> ((Button)shell[0].lookup("#focus-start")).fire()); io.submit(() -> { }).get(10,TimeUnit.SECONDS);
            fakeNanos.addAndGet(1_000_000_000L); fx(() -> ((Button)shell[0].lookup("#focus-stop")).fire());
            io.submit(() -> { }).get(10,TimeUnit.SECONDS); assertEquals(com.studymode.domain.study.FocusState.INTERRUPTED,focus.snapshot().state());
            assertEquals(2,ledger.findByPeriod(java.time.Instant.MIN,java.time.Instant.MAX).size());
            fx(() -> { shell[0].navigate(NavigationPage.CLASSROOMS); shell[0].applyCss(); shell[0].layout(); });
            io.submit(() -> { }).get(10, TimeUnit.SECONDS);
            fx(() -> {
                ((TextField) shell[0].lookup("#classroom-name")).setText("UI classroom");
                ((Button) shell[0].lookup("#save-classroom")).fire();
            });
            io.submit(() -> { }).get(10, TimeUnit.SECONDS);
            fx(() -> assertTrue(shell[0].lookupAll(".feedback").stream().map(node -> ((Label) node).getText()).anyMatch(text -> text.contains("Classroom saved"))));
            assertEquals("UI classroom", new SqliteClassroomCatalog(new SqliteDatabase(root.resolve("ui.db"))).findAll().getFirst().name());
            try (var c = database.open(); var s = c.createStatement()) { s.execute("CREATE TRIGGER ui_failure BEFORE UPDATE ON classroom BEGIN SELECT RAISE(ABORT,'test failure'); END"); }
            fx(() -> {
                ((TextField) shell[0].lookup("#classroom-name")).setText("Must not save"); ((Button) shell[0].lookup("#save-classroom")).fire();
            });
            io.submit(() -> { }).get(10, TimeUnit.SECONDS);
            fx(() -> assertTrue(shell[0].lookupAll(".feedback").stream().map(node -> ((Label) node).getText()).anyMatch(text -> text.contains("Could not save classroom"))));
            assertEquals("UI classroom", new SqliteClassroomCatalog(database).findAll().getFirst().name());
            var dataController = new com.studymode.ui.controller.DataTransferController(
                    new com.studymode.persistence.transfer.JsonStudyDataTransfer(new SqliteStudyDataArchive(database, Clock.systemUTC()), root), io,
                    snapshot -> { }, value -> { });
            Path export = exports.resolve("ui-export.json");
            fx(() -> { stage[0].getScene().setRoot(dataController.view()); dataController.exportTo(export); });
            io.submit(() -> { }).get(10, TimeUnit.SECONDS);
            fx(() -> assertTrue(dataController.view().lookupAll(".feedback").stream().map(node -> ((Label) node).getText()).anyMatch(text -> text.contains("Study data exported"))));
            assertTrue(java.nio.file.Files.exists(export));
            fx(() -> dataController.exportTo(export));
            io.submit(() -> { }).get(10, TimeUnit.SECONDS);
            fx(() -> assertTrue(dataController.view().lookupAll(".feedback").stream().map(node -> ((Label) node).getText()).anyMatch(text -> text.contains("Export failed"))));
        } finally {
            fx(() -> { if (shell[0] != null) shell[0].close(); if (stage[0] != null) stage[0].close(); });
            io.shutdown(); assertTrue(io.awaitTermination(10, TimeUnit.SECONDS)); Platform.exit();
        }
    }
    private static void fx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null); Platform.runLater(task); task.get(15, TimeUnit.SECONDS);
    }
}
