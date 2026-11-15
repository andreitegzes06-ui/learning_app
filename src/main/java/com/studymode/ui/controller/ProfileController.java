package com.studymode.ui.controller;

import com.studymode.application.error.StudyDataException;
import com.studymode.application.service.ProfileService;
import com.studymode.domain.profile.UserProfile;
import com.studymode.domain.Validation;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import static com.studymode.ui.view.ViewComponents.*;

public final class ProfileController {
    private final ProfileService service;
    private final ExecutorService io;
    private final Consumer<UserProfile> onSaved;
    private final TextField name = new TextField();
    private final ComboBox<String> zone = new ComboBox<>();
    private final Label feedback = text("", "feedback");
    private final Button save = new Button("Save profile");
    private final VBox view;

    public ProfileController(ProfileService service, ExecutorService io, Optional<UserProfile> existing, Consumer<UserProfile> onSaved) {
        this.service = service; this.io = io; this.onSaved = onSaved;
        name.setId("profile-name"); name.setPromptText("Your display name"); name.setMaxWidth(440);
        name.setAccessibleText("Display name");
        zone.getItems().setAll(ZoneId.getAvailableZoneIds().stream().sorted().toList());
        zone.setValue(existing.map(p -> p.studyZone().getId()).orElse(ZoneId.systemDefault().getId()));
        zone.setMaxWidth(440); zone.setId("profile-zone"); zone.setAccessibleText("Study time zone");
        name.setText(existing.map(UserProfile::displayName).orElse(""));
        save.getStyleClass().add("primary-button"); save.setId("save-profile"); save.setOnAction(event -> save());
        name.setOnAction(event -> save());
        Label nameLabel = text("Display name", "field-label"); nameLabel.setLabelFor(name);
        Label zoneLabel = text("Study time zone", "field-label"); zoneLabel.setLabelFor(zone);
        view = card(text(existing.isPresent() ? "Your local profile" : "Create your local profile", "section-title"),
                text("Saved on this computer. No account or sign-in required.", "muted"),
                nameLabel, name, zoneLabel, zone,
                text("Your study zone will define calendar days for future statistics and streaks.", "muted"), save, feedback);
    }

    public VBox view() { return view; }

    private void save() {
        if (save.isDisabled()) return;
        final String validatedName;
        final ZoneId validatedZone;
        try {
            validatedName = Validation.text(name.getText(), "Display name", 60);
            validatedZone = ZoneId.of(Validation.required(zone.getValue(), "Time zone"));
        } catch (IllegalArgumentException | java.time.DateTimeException e) {
            feedback.setText(e.getMessage()); feedback.getStyleClass().remove("success"); name.requestFocus(); return;
        }
        save.setDisable(true); name.setDisable(true); zone.setDisable(true);
        feedback.setText("Saving profile…");
        io.execute(() -> {
            try {
                UserProfile saved = service.saveDetails(validatedName, validatedZone);
                Platform.runLater(() -> {
                    name.setText(saved.displayName()); feedback.setText("Profile saved on this computer.");
                    feedback.getStyleClass().add("success"); setBusy(false); onSaved.accept(saved);
                });
            } catch (StudyDataException | IllegalArgumentException e) {
                Platform.runLater(() -> { feedback.setText(e.getMessage()); feedback.getStyleClass().remove("success"); setBusy(false); });
            }
        });
    }
    private void setBusy(boolean busy) { save.setDisable(busy); name.setDisable(busy); zone.setDisable(busy); }
}
