package com.studymode.ui.view;

import com.studymode.ui.NavigationPage;
import javafx.scene.layout.VBox;
import static com.studymode.ui.view.ViewComponents.*;

/** Honest feature status screen: navigation works, future actions are not presented as controls. */
public final class MilestoneInfoView extends VBox {
    public MilestoneInfoView(NavigationPage page) {
        super(22);
        String details = switch (page) {
            case STATISTICS -> "Dashboard now shows recorded completed time and session count; History lists saved outcomes. Detailed charts, filters and goal progress remain planned for milestone 4.";
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
