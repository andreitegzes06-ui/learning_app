package com.studymode.platform;

import com.studymode.application.error.FocusModeException;
import com.studymode.domain.workspace.BlockedApplication;
import com.studymode.platform.simulation.SimulatedDistractionBlocker;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SimulatedDistractionBlockerTest {
    @Test void simulationHasAnObservableLifecycleWithDefensiveStateAndNoOsOperations() {
        var blocker = new SimulatedDistractionBlocker(Clock.fixed(Instant.parse("2026-11-15T12:00:00Z"), ZoneOffset.UTC));
        var app = new BlockedApplication("Example", Path.of(System.getProperty("java.io.tmpdir")).resolve("Example.exe").toAbsolutePath());
        var selected = new HashSet<>(Set.of(app)); blocker.start(selected); selected.clear();
        assertTrue(blocker.isRunning()); assertEquals(Set.of(app), blocker.selectedTargets());
        assertTrue(blocker.events().stream().anyMatch(event -> event.contains("SIMULATED would block: Example")));
        assertThrows(UnsupportedOperationException.class, () -> blocker.events().clear());
        assertThrows(UnsupportedOperationException.class, () -> blocker.selectedTargets().clear());
        assertThrows(FocusModeException.class, () -> blocker.start(Set.of()));
        blocker.stop(); blocker.stop(); assertFalse(blocker.isRunning()); assertTrue(blocker.selectedTargets().isEmpty());
        blocker.start(Set.of()); assertTrue(blocker.isRunning()); blocker.stop();
    }
}
