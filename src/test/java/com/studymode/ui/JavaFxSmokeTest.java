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
        var blocker=new com.studymode.platform.simulation.SimulatedDistractionBlocker(Clock.systemUTC());
        var focus = new com.studymode.application.service.FocusSessionService(ledger,new SqliteUserProfileStore(database),new SqliteClassroomCatalog(database),new SqliteStudyPlanStore(database),Clock.systemUTC(),fakeNanos::get,true,new com.studymode.application.service.FocusBlockingService(blocker,new SqliteBlockedApplicationCatalog(database)));
        Stage[] stage = new Stage[1]; AppShell[] shell = new AppShell[1];
        try {
            fx(() -> {
                shell[0] = new AppShell(service, io, Optional.empty(), new StartupConfiguration(root, "dashboard", 1200, 800,true), new StartupConfigurationLoader(),
                        blocker,
                        new com.studymode.persistence.transfer.JsonStudyDataTransfer(new SqliteStudyDataArchive(database, Clock.systemUTC()), root),focus,new com.studymode.application.service.StudyStatisticsService(ledger,Clock.systemUTC()),new com.studymode.application.service.StudyOrganizationService(new SqliteClassroomCatalog(database),new SqliteFocusWorkspaceStore(database),new SqliteStudyPlanStore(database),new SqliteUserProfileStore(database),ledger,new SqliteBlockedApplicationCatalog(database),Clock.systemUTC()),url -> { },new com.studymode.application.service.BlockedApplicationService(new SqliteBlockedApplicationCatalog(database),blocker));
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
            fx(() -> {
                assertFalse(((Button)shell[0].lookup("#focus-start-break")).isDisabled());
                ((Button)shell[0].lookup("#focus-start-break")).fire();
            }); drain(io);
            assertEquals(com.studymode.domain.study.BreakCycle.Phase.RUNNING,focus.snapshot().cycle().phase());
            fx(() -> ((Button)shell[0].lookup("#focus-pause")).fire()); drain(io);
            assertEquals(com.studymode.domain.study.BreakCycle.Phase.PAUSED,focus.snapshot().cycle().phase());
            fx(() -> ((Button)shell[0].lookup("#focus-resume")).fire()); drain(io);
            fx(() -> ((Button)shell[0].lookup("#focus-skip-break")).fire()); drain(io);
            assertEquals(com.studymode.domain.study.BreakCycle.Phase.SKIPPED,focus.snapshot().cycle().phase());
            fx(() -> ((Button)shell[0].lookup("#focus-start")).fire()); io.submit(() -> { }).get(10,TimeUnit.SECONDS);
            fakeNanos.addAndGet(1_000_000_000L); fx(() -> ((Button)shell[0].lookup("#focus-stop")).fire());
            io.submit(() -> { }).get(10,TimeUnit.SECONDS); assertEquals(com.studymode.domain.study.FocusState.INTERRUPTED,focus.snapshot().state());
            assertEquals(2,ledger.findByPeriod(java.time.Instant.MIN,java.time.Instant.MAX).size());
            fx(() -> {
                ((CheckBox)shell[0].lookup("#focus-auto-break")).setSelected(true);
                ((TextField)shell[0].lookup("#focus-short-break")).setText("1");
                ((TextField)shell[0].lookup("#focus-long-break")).setText("2");
                ((TextField)shell[0].lookup("#focus-long-every")).setText("2");
                ((Button)shell[0].lookup("#focus-start")).fire();
            }); drain(io);
            fakeNanos.addAndGet(10_000_000_000L); io.submit(focus::tick).get(10,TimeUnit.SECONDS);
            fx(() -> { shell[0].navigate(NavigationPage.FOCUS); shell[0].applyCss(); shell[0].layout(); }); drain(io);
            fx(() -> {
                assertTrue(((Label)shell[0].lookup("#focus-state")).getText().contains("SHORT BREAK"));
                assertEquals("01:00",((Label)shell[0].lookup("#focus-countdown")).getText());
                assertTrue(javafx.stage.Window.getWindows().stream().anyMatch(w -> w.getScene()!=null && w.getScene().lookup("#study-notification")!=null));
            });
            for(int i=0;i<60;i++) { fakeNanos.addAndGet(1_000_000_000L); io.submit(focus::tick).get(10,TimeUnit.SECONDS); }
            assertEquals(com.studymode.domain.study.BreakCycle.Phase.COMPLETE,focus.snapshot().cycle().phase());
            assertEquals(10,focus.snapshot().session().activeFocusSeconds());
            fx(() -> { shell[0].navigate(NavigationPage.FOCUS); shell[0].applyCss(); shell[0].layout(); }); drain(io);
            fx(() -> {
                assertTrue(((Label)shell[0].lookup("#focus-state")).getText().contains("COMPLETE"));
                snapshot(stage[0],"m5-focus-900.png");
                stage[0].setWidth(1200); stage[0].setHeight(800); shell[0].applyCss(); shell[0].layout();
                snapshot(stage[0],"m5-focus-1200.png");
            });
            fx(() -> { shell[0].navigate(NavigationPage.CLASSROOMS); shell[0].applyCss(); shell[0].layout(); });
            io.submit(() -> { }).get(10, TimeUnit.SECONDS);
            fx(() -> {
                ((TextField) shell[0].lookup("#classroom-name")).setText("UI classroom");
                ((Button) shell[0].lookup("#save-classroom")).fire();
            });
            io.submit(() -> { }).get(10, TimeUnit.SECONDS);
            fx(() -> assertTrue(shell[0].lookupAll(".feedback").stream().map(node -> ((Label) node).getText()).anyMatch(text -> text.contains("Classroom saved"))));
            assertEquals("UI classroom", new SqliteClassroomCatalog(new SqliteDatabase(root.resolve("ui.db"))).findAll().getFirst().name());
            fx(() -> {
                ((TextArea)shell[0].lookup("#classroom-notes")).setText("Exam revision");
                ((TextField)shell[0].lookup("#resource-title")).setText("University course");
                ((TextField)shell[0].lookup("#resource-url")).setText("https://classroom.google.com/c/java");
                ((Button)shell[0].lookup("#save-resource")).fire();
                ((Button)shell[0].lookup("#save-classroom")).fire();
            });
            drain(io);
            assertEquals("Exam revision",new SqliteClassroomCatalog(database).findAll().getFirst().notes());
            assertEquals(1,new SqliteClassroomCatalog(database).findAll().getFirst().resources().size());
            fx(() -> { ((TextField)shell[0].lookup("#task-text")).setText("Revise interfaces"); ((Button)shell[0].lookup("#add-task")).fire(); });
            drain(io);
            fx(() -> { ((ListView<?>)shell[0].lookup("#classroom-tasks")).getSelectionModel().selectFirst(); ((Button)shell[0].lookup("#complete-task")).fire(); });
            drain(io); assertTrue(new SqliteStudyPlanStore(database).tasks().getFirst().completed());
            fx(() -> { shell[0].navigate(NavigationPage.GOALS); shell[0].applyCss(); shell[0].layout(); });
            drain(io);
            fx(() -> {
                ((TextField)shell[0].lookup("#goal-name")).setText("Prepare Java exam");
                ((TextField)shell[0].lookup("#goal-target")).setText("15");
                ((ComboBox<?>)shell[0].lookup("#goal-classroom")).getSelectionModel().selectFirst();
                ((Button)shell[0].lookup("#save-goal")).fire();
            });
            drain(io);
            assertEquals(1,new SqliteStudyPlanStore(database).goals().size());
            fx(() -> { shell[0].navigate(NavigationPage.WORKSPACES); shell[0].applyCss(); shell[0].layout(); });
            drain(io);
            fx(() -> {
                ((TextField)shell[0].lookup("#workspace-name")).setText("Exam workspace");
                ((TextField)shell[0].lookup("#workspace-focus")).setText("1");
                ((TextField)shell[0].lookup("#workspace-break")).setText("2");
                ((TextField)shell[0].lookup("#workspace-long-break")).setText("7");
                ((TextField)shell[0].lookup("#workspace-long-every")).setText("3");
                ((ComboBox<?>)shell[0].lookup("#workspace-classroom")).getSelectionModel().selectFirst();
                ((ListView<?>)shell[0].lookup("#workspace-resources")).getSelectionModel().selectFirst();
                ((Button)shell[0].lookup("#save-workspace")).fire();
            });
            drain(io);
            assertEquals(1,new SqliteFocusWorkspaceStore(database).findAll().size());
            assertEquals(new com.studymode.domain.workspace.FocusPreferences(1,2,7,3),new SqliteFocusWorkspaceStore(database).findAll().getFirst().preferences());
            fx(() -> { ((ComboBox<?>)shell[0].lookup("#workspace-goal")).getSelectionModel().selectFirst(); ((Button)shell[0].lookup("#start-workspace")).fire(); });
            drain(io);
            assertEquals(NavigationPage.FOCUS,shell[0].currentPage());
            assertEquals(60,focus.snapshot().session().context().plannedFocusSeconds());
            fx(() -> {
                shell[0].applyCss(); shell[0].layout(); assertEquals("60",((TextField)shell[0].lookup("#focus-duration")).getText());
                assertEquals("2",((TextField)shell[0].lookup("#focus-short-break")).getText());
                assertEquals("7",((TextField)shell[0].lookup("#focus-long-break")).getText());
                assertEquals("3",((TextField)shell[0].lookup("#focus-long-every")).getText());
            });
            fakeNanos.addAndGet(4_000_000_000L); fx(() -> ((Button)shell[0].lookup("#focus-finish")).fire());
            drain(io); assertEquals(4,focus.snapshot().session().activeFocusSeconds());
            fx(() -> { shell[0].navigate(NavigationPage.GOALS); shell[0].applyCss(); shell[0].layout(); }); drain(io);
            fx(() -> { ((ListView<?>)shell[0].lookup("#goal-list")).getSelectionModel().selectFirst(); assertTrue(((Label)shell[0].lookup("#goal-progress-text")).getText().contains("04s")); });
            fx(() -> { shell[0].navigate(NavigationPage.CLASSROOMS); shell[0].applyCss(); shell[0].layout(); }); drain(io);
            fx(() -> ((ListView<?>)shell[0].lookup("#classroom-list")).getSelectionModel().selectFirst()); drain(io);
            fx(() -> assertEquals(1,((ListView<?>)shell[0].lookup("#classroom-history")).getItems().size()));
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
    private static void snapshot(Stage stage,String name) {
        var image=stage.getScene().snapshot(null); int width=(int)image.getWidth(), height=(int)image.getHeight();
        var output=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) output.setRGB(x,y,image.getPixelReader().getArgb(x,y));
        try { javax.imageio.ImageIO.write(output,"png",Path.of("target",name).toFile()); }
        catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    private static void drain(ExecutorService io) throws Exception {
        io.submit(() -> { }).get(10,TimeUnit.SECONDS); fx(() -> { });
        io.submit(() -> { }).get(10,TimeUnit.SECONDS); fx(() -> { });
    }
    private static void fx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null); Platform.runLater(task); task.get(15, TimeUnit.SECONDS);
    }
}
