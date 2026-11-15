package com.studymode.ui.controller;

import com.studymode.application.error.StudyDataException;
import com.studymode.application.port.ClassroomCatalog;
import com.studymode.domain.classroom.Classroom;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import static com.studymode.ui.view.ViewComponents.*;

/** Small persistent classroom editor; resources imported/saved through the catalog are preserved on edits. */
public final class ClassroomController {
    private final ClassroomCatalog catalog;
    private final ExecutorService io;
    private final ListView<Classroom> classrooms = new ListView<>();
    private final TextField name = new TextField();
    private final TextArea description = new TextArea();
    private final Label feedback = text("", "feedback");
    private final Label resources = text("", "muted");
    private final Button save = new Button("Save classroom");
    private final Button delete = new Button("Delete classroom");
    private final Button fresh = new Button("New classroom");
    private final Button reload = new Button("Reload classrooms");
    private final VBox view;
    private Classroom selected;
    private boolean busy;

    public ClassroomController(ClassroomCatalog catalog, ExecutorService io) {
        this.catalog = catalog; this.io = io;
        classrooms.setId("classroom-list"); classrooms.setPrefHeight(180); classrooms.setPlaceholder(text("No classrooms yet. Create your first subject below.", "muted"));
        classrooms.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(Classroom value, boolean empty) { super.updateItem(value, empty); setText(empty || value == null ? null : value.name()); }
        });
        classrooms.getSelectionModel().selectedItemProperty().addListener((observable, before, value) -> { if (!busy) edit(value); });
        name.setId("classroom-name"); name.setPromptText("Subject or course name"); name.setAccessibleText("Classroom name");
        description.setId("classroom-description"); description.setPromptText("Optional description"); description.setPrefRowCount(3); description.setWrapText(true);
        save.setId("save-classroom"); save.getStyleClass().add("primary-button"); save.setOnAction(event -> save());
        delete.setOnAction(event -> {
            if (selected == null || busy) return;
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Delete “" + selected.name() + "”? Its resources will be removed and tasks will lose their classroom link. Referencing workspaces must be removed first.", ButtonType.CANCEL, ButtonType.OK);
            alert.initOwner(view().getScene().getWindow()); alert.setHeaderText("Delete classroom");
            if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) removeSelected();
        });
        fresh.setOnAction(event -> { classrooms.getSelectionModel().clearSelection(); edit(null); });
        reload.setOnAction(event -> refresh());
        view = new VBox(20, card(text("Your classrooms", "section-title"), classrooms, new FlowPane(10, 10, fresh, reload)),
                card(text("Classroom details", "section-title"), text("Name", "field-label"), name, text("Description", "field-label"), description, resources,
                        new FlowPane(10, 10, save, delete), feedback));
        edit(null);
    }
    public VBox view() { return view; }
    public void refresh() {
        if (busy) return; setBusy(true); feedback.setText("Loading classrooms…");
        io.execute(() -> {
            try { var found = catalog.findAll(); Platform.runLater(() -> { setBusy(false); classrooms.getItems().setAll(found); edit(null); feedback.setText(found.size() + " classroom(s) loaded."); }); }
            catch (StudyDataException e) { fail(e); }
        });
    }
    private void edit(Classroom value) {
        selected = value; name.setText(value == null ? "" : value.name()); description.setText(value == null ? "" : value.description());
        resources.setText(value == null ? "Educational links can be stored through JSON import; link editing will arrive with the resource workflow."
                : value.resources().size() + " resource(s) retained. " + String.join(" · ", value.resources().stream().map(r -> r.title()).toList()));
        delete.setDisable(value == null || busy);
    }
    private void save() {
        if (busy) return;
        final Classroom value;
        try { value = new Classroom(selected == null ? UUID.randomUUID() : selected.id(), name.getText(), description.getText(), selected == null ? List.of() : selected.resources()); }
        catch (IllegalArgumentException e) { feedback.setText(e.getMessage()); return; }
        setBusy(true); feedback.setText("Saving classroom…");
        io.execute(() -> {
            try { catalog.save(value); var found = catalog.findAll(); Platform.runLater(() -> { setBusy(false); classrooms.getItems().setAll(found); classrooms.getSelectionModel().select(value); edit(value); feedback.setText("Classroom saved."); }); }
            catch (StudyDataException e) { fail(e); }
        });
    }
    private void removeSelected() {
        UUID id = selected.id(); setBusy(true);
        io.execute(() -> {
            try { catalog.delete(id); var found = catalog.findAll(); Platform.runLater(() -> { setBusy(false); classrooms.getItems().setAll(found); edit(null); feedback.setText("Classroom deleted."); }); }
            catch (StudyDataException e) { fail(e); }
        });
    }
    private void fail(StudyDataException error) { Platform.runLater(() -> { setBusy(false); feedback.setText(error.getMessage()); }); }
    private void setBusy(boolean value) { busy = value; save.setDisable(value); fresh.setDisable(value); reload.setDisable(value); classrooms.setDisable(value); name.setDisable(value); description.setDisable(value); delete.setDisable(value || selected == null); }
}
