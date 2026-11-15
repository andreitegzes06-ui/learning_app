package com.studymode.platform.simulation;

import com.studymode.application.error.FocusModeException;
import com.studymode.application.port.DistractionBlocker;
import com.studymode.domain.workspace.BlockedApplication;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Explicit simulation. Never enumerates, launches, or terminates OS processes. */
public final class SimulatedDistractionBlocker implements DistractionBlocker {
    private final Clock clock;
    private final List<String> events = new ArrayList<>();
    private Set<BlockedApplication> targets = Set.of();
    private boolean running;

    public SimulatedDistractionBlocker(Clock clock) { this.clock = java.util.Objects.requireNonNull(clock); }
    @Override public synchronized void start(Set<BlockedApplication> selected) {
        if (running) throw new FocusModeException("Simulation is already active. Stop it before starting again.");
        targets = Set.copyOf(selected);
        running = true;
        record("SIMULATED start: " + targets.size() + " selected application(s).");
        targets.forEach(target -> record("SIMULATED would block: " + target.displayName()));
    }
    @Override public synchronized void stop() {
        if (running) record("SIMULATED stopped; no OS processes were modified.");
        running = false;
        targets = Set.of();
    }
    @Override public synchronized boolean isRunning() { return running; }
    @Override public synchronized Set<BlockedApplication> selectedTargets() { return targets; }
    @Override public synchronized List<String> events() { return List.copyOf(events); }
    private void record(String message) {
        if (events.size() == 200) events.removeFirst();
        events.add(clock.instant() + " " + message);
    }
}
