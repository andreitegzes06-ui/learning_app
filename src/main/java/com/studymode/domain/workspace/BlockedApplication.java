package com.studymode.domain.workspace;

import com.studymode.domain.Validation;
import java.nio.file.Path;
import java.util.Locale;

/** Saved selection metadata only. OS identity and safety must be revalidated before enforcement. */
public final class BlockedApplication {
    private final String displayName;
    private final Path executablePath;
    private final String pathIdentity;
    private final boolean enabled;

    public BlockedApplication(String displayName, Path executablePath) {
        this(displayName,executablePath,true);
    }
    public BlockedApplication(String displayName, Path executablePath, boolean enabled) {
        this.displayName = Validation.text(displayName, "Application name", 100);
        this.executablePath = Validation.required(executablePath, "Executable path").normalize();
        if (!this.executablePath.isAbsolute() || this.executablePath.getFileName() == null
                || !this.executablePath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe")) {
            throw new IllegalArgumentException("Select an absolute path to a Windows .exe file.");
        }
        this.pathIdentity = this.executablePath.toString().toLowerCase(Locale.ROOT);
        this.enabled = enabled;
    }

    public String displayName() { return displayName; }
    public Path executablePath() { return executablePath; }
    public boolean enabled() { return enabled; }
    public BlockedApplication withEnabled(boolean value) { return new BlockedApplication(displayName,executablePath,value); }
    @Override public boolean equals(Object other) {
        return other instanceof BlockedApplication application && pathIdentity.equals(application.pathIdentity);
    }
    @Override public int hashCode() { return pathIdentity.hashCode(); }
    @Override public String toString() { return displayName + " (" + executablePath + ")"; }
}
