package com.studymode.application.port;

import com.studymode.domain.workspace.BlockedApplication;
import java.util.List;
import java.util.Set;

public interface DistractionBlocker {
    void start(Set<BlockedApplication> targets);
    void stop();
    boolean isRunning();
    Set<BlockedApplication> selectedTargets();
    List<String> events();
}
