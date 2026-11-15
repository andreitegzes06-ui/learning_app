package com.studymode.platform.desktop;

import com.studymode.application.error.FocusModeException;
import com.studymode.domain.workspace.BlockedApplication;
import java.io.IOException;
import java.nio.file.Files;

/** Explicit user-invoked shortcut launching only; no shell, arguments, elevation or termination. */
public final class StudyApplicationLauncher {
    public void launch(BlockedApplication application) {
        if (!Files.isRegularFile(application.executablePath()))
            throw new FocusModeException("The selected application no longer exists. Edit the workspace shortcut.");
        try { new ProcessBuilder(application.executablePath().toString()).start(); }
        catch (IOException | SecurityException e) { throw new FocusModeException("Could not open the selected study application: "+e.getMessage(),e); }
    }
}
