package com.studymode.ui.controller;

import com.studymode.application.data.StudyDataSnapshot;
import com.studymode.application.error.*;
import com.studymode.application.port.StudyDataTransfer;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import static com.studymode.ui.view.ViewComponents.*;

public final class DataTransferController {
    private final StudyDataTransfer transfer;
    private final ExecutorService io;
    private final Consumer<StudyDataSnapshot> onImport;
    private final Consumer<Boolean> busy;
    private final Label feedback = text("", "feedback");
    private final VBox view;
    public DataTransferController(StudyDataTransfer transfer, ExecutorService io, Consumer<StudyDataSnapshot> onImport, Consumer<Boolean> busy) {
        this.transfer = transfer; this.io = io; this.onImport = onImport; this.busy = busy;
        Button export = new Button("Export study data…"); export.setId("export-study-data");
        Button importData = new Button("Import study data…"); importData.setId("import-study-data");
        export.setOnAction(event -> {
            FileChooser chooser = chooser("Export study data to a new JSON file"); chooser.setInitialFileName("study-mode-export.json");
            var file = chooser.showSaveDialog(view().getScene().getWindow()); if (file != null) exportTo(file.toPath());
        });
        importData.setOnAction(event -> {
            var file = chooser("Choose a study JSON file to validate").showOpenDialog(view().getScene().getWindow());
            if (file != null) inspect(file.toPath());
        });
        view = card(text("Study data backup", "section-title"),
                text("Export a local JSON backup. Imports replace all study records after validation and confirmation; a recovery backup is saved first. JSON contains your private study information and is not encrypted. Choose a new export filename outside the app data folder.", "muted"),
                new FlowPane(12, 12, export, importData), feedback);
    }
    public VBox view() { return view; }
    public void exportTo(Path path) {
        busy.accept(true); feedback.setText("Exporting study data…");
        io.execute(() -> {
            try { transfer.exportTo(path); complete("Study data exported to " + path); }
            catch (IOException | StudyDataException | DataImportException | SecurityException e) { complete("Export failed: " + e.getMessage()); }
        });
    }
    private void inspect(Path path) {
        busy.accept(true); feedback.setText("Validating import…");
        io.execute(() -> {
            try {
                StudyDataSnapshot preview = transfer.inspectImport(path);
                Platform.runLater(() -> {
                    busy.accept(false);
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Replace all local study records with " + preview.recordCount() + " imported records?\nProfiles: " + preview.profiles().size()
                                    + " · Classrooms: " + preview.classrooms().size() + " · Workspaces: " + preview.workspaces().size()
                                    + "\nSessions: " + preview.sessions().size() + " · Goals: " + preview.goals().size() + " · Tasks: " + preview.tasks().size()
                                    + "\nUnfinished imported sessions become interrupted. No application blocking or reward calculations will start. A recovery backup is required before replacement.", ButtonType.CANCEL, ButtonType.OK);
                    confirm.initOwner(view.getScene().getWindow()); confirm.setHeaderText("Validated study data — confirm replacement");
                    if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) replace(preview);
                    else feedback.setText("Import cancelled. No records were changed.");
                });
            } catch (IOException | DataImportException | StudyDataException | SecurityException e) { complete("Import validation failed: " + e.getMessage()); }
        });
    }
    private void replace(StudyDataSnapshot preview) {
        busy.accept(true); feedback.setText("Backing up and importing…");
        io.execute(() -> {
            try { Path backup = transfer.importSnapshot(preview); Platform.runLater(() -> { busy.accept(false); onImport.accept(preview); feedback.setText("Import completed. Previous data backup: " + backup); }); }
            catch (IOException | DataImportException | StudyDataException | SecurityException e) { complete("Import failed: " + e.getMessage()); }
        });
    }
    private void complete(String message) { Platform.runLater(() -> { busy.accept(false); feedback.setText(message); }); }
    private FileChooser chooser(String title) {
        FileChooser chooser = new FileChooser(); chooser.setTitle(title); chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Study data JSON", "*.json")); return chooser;
    }
}
