package com.studymode.persistence;

import com.studymode.persistence.config.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StartupConfigurationTest {
    @Test void demoModeIsExplicitAndBooleanValuesAreValidated() throws Exception {
        var parser = new StartupConfigurationLoader();
        assertTrue(parser.load(java.util.List.of("--data-dir=" + root,"--demo=true","--start-page=history"),java.util.Map.of()).demoMode());
        assertFalse(parser.load(java.util.List.of("--data-dir=" + root),java.util.Map.of()).demoMode());
        assertThrows(IllegalArgumentException.class,() -> parser.load(java.util.List.of("--data-dir=" + root,"--demo=yes"),java.util.Map.of()));
    }
    @TempDir Path root;
    final StartupConfigurationLoader loader = new StartupConfigurationLoader();
    @Test void defaultsAndPreferencesRoundTripWithoutSavingTransientOverrides() throws Exception {
        var arguments = List.of("--data-dir=" + root);
        var defaults = loader.load(arguments, Map.of()); assertEquals(1200, defaults.windowWidth());
        loader.saveWindowPreferences(root, 1100, 720);
        var saved = loader.load(arguments, Map.of()); assertEquals(1100, saved.windowWidth()); assertEquals(720, saved.windowHeight());
        var overridden = loader.load(List.of("--data-dir=" + root, "--window-width=1300"), Map.of());
        assertEquals(1300, overridden.windowWidth()); assertEquals(1100, loader.load(arguments, Map.of()).windowWidth());
    }
    @Test void explicitFileOverridesSavedSettingsAndCliOverridesFile() throws Exception {
        loader.saveWindowPreferences(root, 1100, 720); Path external = root.resolve("alternate.properties");
        Files.writeString(external, "window-width=1400\nstart-page=statistics\n");
        var config = loader.load(List.of("--data-dir=" + root, "--config=" + external, "--window-width=1500"), Map.of());
        assertEquals(1500, config.windowWidth()); assertEquals(720, config.windowHeight()); assertEquals("statistics", config.startPage());
    }
    @Test void rejectsUnknownDuplicateInvalidAndUnsafeStartupOptions() {
        for (String option : List.of("--unknown=x", "--blocker=unknown", "--window-width=abc", "--window-width=800", "--start-page=missing", "--data-dir=relative", "--config=relative", "--config=", "--window-height=9999999999999999999")) {
            assertThrows(IllegalArgumentException.class, () -> loader.load(List.of("--data-dir=" + root, option), Map.of()), option);
        }
        assertThrows(IllegalArgumentException.class, () -> loader.load(List.of("--data-dir=" + root, "--data-dir=" + root), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> loader.load(List.of(), Map.of()));
        assertThrows(IOException.class, () -> loader.load(List.of("--data-dir=" + root, "--config=" + root.resolve("missing.properties")), Map.of()));
    }
    @Test void invalidSavedFileIsNotOverwritten() throws Exception {
        Path settings = root.resolve("settings.properties"); Files.writeString(settings, "unknown-key=value\n");
        assertThrows(IllegalArgumentException.class, () -> loader.load(List.of("--data-dir=" + root), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> loader.saveWindowPreferences(root, 1200, 800));
        assertEquals("unknown-key=value\n", Files.readString(settings));
    }
    @Test void defaultRootUsesLocalAppDataAndDataLockIsExclusiveAndReleasable() throws Exception {
        assertEquals(root.resolve("StudyMode"), loader.load(List.of(), Map.of("LOCALAPPDATA", root.toString())).dataDirectory());
        try (var first = DataDirectoryLock.acquire(root)) { assertThrows(IOException.class, () -> DataDirectoryLock.acquire(root)); }
        try (var reopened = DataDirectoryLock.acquire(root)) { assertNotNull(reopened); }
    }
}
