package com.studymode.ui.view;

import com.studymode.ui.NavigationPage;
import javafx.scene.layout.VBox;
import static com.studymode.ui.view.ViewComponents.*;

/** Honest feature status screen: navigation works, future actions are not presented as controls. */
public final class MilestoneInfoView extends VBox {
    public MilestoneInfoView(NavigationPage page) {
        super(22);
        String details = switch (page) {
            case FOCUS -> "Focus and break cycles, pause, resume, and session history will be implemented in milestone 3. No timer is running.";
            case CLASSROOMS -> "Subjects and their educational links are stored locally. Interactive link editing and launching remain planned; Google sign-in is not part of Study Mode.";
            case WORKSPACES -> "Workspace records, preferences, resource selections and application lists now persist in SQLite and JSON backups. The interactive workspace editor and focus integration remain planned; no environment starts automatically.";
            case BLOCKED -> "Real Windows enforcement arrives in milestone 5. Nothing is being blocked. The foundation includes a tested simulation adapter for development; it never touches operating-system processes.";
            case GOALS -> "Daily and weekly study targets and study tasks arrive in milestone 4. Progress will be calculated from recorded study time.";
            case STATISTICS -> "Statistics arrive in milestone 4, after session recording in milestone 3. Existing session facts are preserved in SQLite and backups; charts and automatic totals are not yet available.";
            case ACHIEVEMENTS -> "XP, levels, achievements, and study streak calculations arrive in milestone 6. Existing achievement definitions and award facts can already be stored and backed up. No rewards engine runs in this milestone.";
            default -> throw new IllegalArgumentException("This page has its own implemented view.");
        };
        getChildren().addAll(
                text(page == NavigationPage.WORKSPACES ? "STORED · WORKSPACE EDITOR PLANNED" : "PLANNED · MILESTONE " + page.milestone(), "status-badge"),
                card(text(page.title() + " is not available yet", "section-title"), text(details, "body-text")),
                text("Project foundation", "eyebrow"),
                text("This destination is part of the application’s working navigation. The workflow described above remains planned; persistence support does not activate it.", "muted"));
    }
}
