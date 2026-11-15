package com.studymode.ui;

import com.studymode.application.service.ProfileService;
import com.studymode.application.port.DistractionBlocker;
import com.studymode.application.port.ClassroomCatalog;
import com.studymode.application.port.StudyDataTransfer;
import com.studymode.ui.controller.ClassroomController;
import com.studymode.ui.controller.DataTransferController;
import com.studymode.domain.profile.UserProfile;
import com.studymode.persistence.config.StartupConfiguration;
import com.studymode.persistence.config.StartupConfigurationLoader;
import com.studymode.ui.controller.ProfileController;
import com.studymode.ui.view.MilestoneInfoView;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import static com.studymode.ui.view.ViewComponents.*;

public final class AppShell extends BorderPane {
    private final Map<NavigationPage, ToggleButton> navigation = new EnumMap<>(NavigationPage.class);
    private final ProfileController profileController;
    private final StartupConfiguration config;
    private final StartupConfigurationLoader configurationLoader;
    private final ExecutorService io;
    private final DistractionBlocker blocker;
    private int savedSessionCount;
    private final ClassroomController classroomController;
    private final DataTransferController dataTransferController;
    private final Label profileCaption = text("", "muted");
    private Optional<UserProfile> profile;
    private NavigationPage current;

    public AppShell(ProfileService service, ExecutorService io, Optional<UserProfile> profile,
                    StartupConfiguration config, StartupConfigurationLoader configurationLoader,
                    DistractionBlocker blocker, int savedSessionCount, ClassroomCatalog classroomCatalog, StudyDataTransfer dataTransfer) {
        this.profile = profile; this.config = config; this.configurationLoader = configurationLoader; this.io = io;
        this.blocker = blocker; this.savedSessionCount = savedSessionCount;
        classroomController = new ClassroomController(classroomCatalog, io);
        profileController = new ProfileController(service, io, profile, saved -> {
            this.profile = Optional.of(saved); updateProfileCaption();
        });
        dataTransferController = new DataTransferController(dataTransfer, io, imported -> {
            this.profile = imported.profiles().stream().findFirst(); this.savedSessionCount = imported.sessions().size();
            profileController.reload(this.profile); updateProfileCaption();
        }, this::setDisable);
        getStyleClass().add("app-shell");
        setLeft(sidebar()); updateProfileCaption();
        navigate(profile.isEmpty() ? NavigationPage.SETTINGS : NavigationPage.fromKey(config.startPage()));
    }

    private Node sidebar() {
        ToggleGroup group = new ToggleGroup();
        VBox items = new VBox(7);
        for (NavigationPage page : NavigationPage.values()) {
            ToggleButton button = new ToggleButton(page.title()); button.setId("nav-" + page.key());
            button.setToggleGroup(group); button.setMaxWidth(Double.MAX_VALUE);
            button.getStyleClass().add("nav-button"); button.setOnAction(event -> navigate(page));
            navigation.put(page, button); items.getChildren().add(button);
        }
        VBox brand = new VBox(5, text("STUDY MODE", "brand"), text("Space to do your best work", "sidebar-subtitle"));
        Region spacer = new Region(); VBox.setVgrow(spacer, Priority.ALWAYS);
        VBox bar = new VBox(24, brand, items, spacer, text("MILESTONE 2 · LOCAL DATA", "sidebar-subtitle"), profileCaption);
        bar.getStyleClass().add("sidebar"); bar.setPrefWidth(242); bar.setMinWidth(210);
        ScrollPane scroll = new ScrollPane(bar); scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("sidebar-scroll"); scroll.setPrefWidth(242);
        return scroll;
    }

    public void navigate(NavigationPage page) {
        current = page; navigation.get(page).setSelected(true);
        VBox header = new VBox(8, text("YOUR LOCAL STUDY SPACE", "eyebrow"), text(page.title(), "page-title"), text(page.subtitle(), "muted"));
        Node content = switch (page) {
            case DASHBOARD -> dashboard();
            case SETTINGS -> settings();
            case CLASSROOMS -> { classroomController.refresh(); yield classroomController.view(); }
            default -> new MilestoneInfoView(page);
        };
        VBox body = new VBox(28, header, content); body.setPadding(new Insets(36)); body.setMaxWidth(1100);
        ScrollPane scroll = new ScrollPane(body); scroll.setFitToWidth(true); scroll.setId("page-content");
        scroll.getStyleClass().add("content-scroll"); setCenter(scroll);
    }

    private Node dashboard() {
        String greeting = profile.map(p -> "Welcome back, " + p.displayName()).orElse("Welcome to Study Mode");
        Button edit = new Button(profile.isPresent() ? "Edit profile" : "Create profile");
        edit.getStyleClass().add("primary-button"); edit.setOnAction(event -> navigate(NavigationPage.SETTINGS));
        VBox welcome = card(text(greeting, "section-title"),
                text("Your foundation is ready: local profile storage, a connected app shell, and a consistent study space.", "body-text"), edit);
        FlowPane cards = new FlowPane(16, 16);
        VBox identity = card(text("LOCAL PROFILE", "eyebrow"), text(profile.map(UserProfile::displayName).orElse("Not created"), "card-value"),
                text(profile.map(p -> p.studyZone().getId()).orElse("Create your profile in Settings"), "muted"));
        VBox safety = card(text("DISTRACTION BLOCKING", "eyebrow"), text(blocker.isRunning() ? "Simulation active" : "Inactive", "card-value"), text("Simulation adapter only · no processes modified", "muted"));
        identity.setPrefWidth(330); safety.setPrefWidth(330); cards.getChildren().addAll(identity, safety);
        return new VBox(22, welcome, cards,
                card(text("Build your study routine", "section-title"), text("Saved session records: " + savedSessionCount + ". Session recording is planned for milestone 3. No sample sessions or rewards are generated by this foundation.", "muted")),
                text("Browse the sidebar to see each feature’s implementation status.", "muted"));
    }

    private Node settings() {
        Label savedFeedback = text("", "feedback");
        Button saveSize = new Button("Save current window size");
        saveSize.setId("save-window-size");
        saveSize.setOnAction(event -> {
            int width = Math.max(900, Math.min(3840, (int) Math.round(getScene().getWindow().getWidth())));
            int height = Math.max(640, Math.min(2160, (int) Math.round(getScene().getWindow().getHeight())));
            saveSize.setDisable(true); savedFeedback.setText("Saving window preferences…");
            io.execute(() -> {
                String message;
                try { configurationLoader.saveWindowPreferences(config.dataDirectory(), width, height); message = "Window size saved for the next launch."; }
                catch (IOException | IllegalArgumentException | SecurityException e) { message = "Could not save preferences: " + e.getMessage(); }
                String result = message;
                javafx.application.Platform.runLater(() -> { saveSize.setDisable(false); savedFeedback.setText(result); });
            });
        });
        return new VBox(22, profileController.view(),
                card(text("Application preferences", "section-title"), text("Dark theme · local-only profile · simulation adapter", "body-text"), saveSize, savedFeedback),
                card(text("Local data", "section-title"), text(config.dataDirectory().toString(), "path-text"),
                        text("SQLite stores your study data here. Named startup presets remain planned.", "muted")), dataTransferController.view());
    }

    private void updateProfileCaption() { profileCaption.setText(profile.map(p -> "Local profile\n" + p.displayName()).orElse("No profile yet")); }
    public NavigationPage currentPage() { return current; }
}
