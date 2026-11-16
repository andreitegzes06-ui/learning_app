package com.studymode.persistence.config;

import java.nio.file.Path;

public record StartupConfiguration(Path dataDirectory, String startPage, int windowWidth, int windowHeight, boolean demoMode,String blockerMode) {
    public StartupConfiguration(Path dataDirectory,String startPage,int windowWidth,int windowHeight,boolean demoMode) {
        this(dataDirectory,startPage,windowWidth,windowHeight,demoMode,"simulated");
    }
    public StartupConfiguration(Path dataDirectory, String startPage, int windowWidth, int windowHeight) {
        this(dataDirectory, startPage, windowWidth, windowHeight, false);
    }
    public StartupConfiguration {
        com.studymode.domain.Validation.required(dataDirectory, "Data directory");
        com.studymode.domain.Validation.required(startPage, "Start page");
        if(!java.util.Set.of("simulated","windows-dry-run","windows").contains(blockerMode)) throw new IllegalArgumentException("Use blocker=simulated, windows-dry-run or windows.");
        if (!dataDirectory.isAbsolute()) throw new IllegalArgumentException("Data directory must be absolute.");
        com.studymode.domain.Validation.range(windowWidth, 900, 3840, "Window width");
        com.studymode.domain.Validation.range(windowHeight, 640, 2160, "Window height");
    }
}
