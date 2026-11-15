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
    @Test void allNinePagesNavigateAndProfileFormValidatesSavesAndEdits() throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Platform.startup(ready::countDown);
        assertTrue(ready.await(15, TimeUnit.SECONDS));
        var io = Executors.newSingleThreadExecutor();
        var database = new SqliteDatabase(root.resolve("ui.db")); new SchemaMigrator(database).migrate();
        var service = new ProfileService(new SqliteUserProfileStore(database), Clock.systemUTC());
        Stage[] stage = new Stage[1]; AppShell[] shell = new AppShell[1];
        try {
            fx(() -> {
                shell[0] = new AppShell(service, io, Optional.empty(), new StartupConfiguration(root, "dashboard", 1200, 800), new StartupConfigurationLoader(),
                        new com.studymode.platform.simulation.SimulatedDistractionBlocker(Clock.systemUTC()), 0);
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
        } finally {
            fx(() -> { if (stage[0] != null) stage[0].close(); });
            io.shutdown(); assertTrue(io.awaitTermination(10, TimeUnit.SECONDS)); Platform.exit();
        }
    }
    private static void fx(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null); Platform.runLater(task); task.get(15, TimeUnit.SECONDS);
    }
}
