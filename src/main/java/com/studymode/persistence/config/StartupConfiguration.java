package com.studymode.persistence.config;

import java.nio.file.Path;

public record StartupConfiguration(Path dataDirectory, String startPage, int windowWidth, int windowHeight, boolean demoMode) {
    public StartupConfiguration(Path dataDirectory, String startPage, int windowWidth, int windowHeight) {
        this(dataDirectory, startPage, windowWidth, windowHeight, false);
    }
    public StartupConfiguration {
        com.studymode.domain.Validation.required(dataDirectory, "Data directory");
        com.studymode.domain.Validation.required(startPage, "Start page");
        if (!dataDirectory.isAbsolute()) throw new IllegalArgumentException("Data directory must be absolute.");
        com.studymode.domain.Validation.range(windowWidth, 900, 3840, "Window width");
        com.studymode.domain.Validation.range(windowHeight, 640, 2160, "Window height");
    }
}
