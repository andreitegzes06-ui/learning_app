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
            case CLASSROOMS -> "Classrooms will organise subjects and their educational links, including Google Classroom URLs. Editing and resource launching arrive in milestone 2; Google sign-in is not part of Study Mode.";
            case WORKSPACES -> "A workspace will bring together one classroom, focus and break preferences, selected resources, and a deduplicated application blocklist. Saved workspace editing arrives in milestone 2.";
            case BLOCKED -> "Real Windows enforcement arrives in milestone 5. Nothing is being blocked. The foundation includes a tested simulation adapter for development; it never touches operating-system processes.";
            case GOALS -> "Daily and weekly study targets and study tasks arrive in milestone 4. Progress will be calculated from recorded study time.";
            case STATISTICS -> "Statistics arrive in milestone 4, after session recording in milestone 3. Totals and trends will use saved session facts; there is no study activity to chart yet.";
            case ACHIEVEMENTS -> "XP, levels, achievements, and study streaks arrive in milestone 6. Rewards will be based on actual recorded focus time. No rewards have been generated.";
            default -> throw new IllegalArgumentException("This page has its own implemented view.");
        };
        getChildren().addAll(
                text("PLANNED · MILESTONE " + page.milestone(), "status-badge"),
                card(text(page.title() + " is not available yet", "section-title"), text(details, "body-text")),
                text("Project foundation", "eyebrow"),
                text("This destination is part of the application’s working navigation. Its feature workflow will be added in the milestone shown above.", "muted"));
    }
}
