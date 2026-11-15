package com.studymode.ui.view;

import com.studymode.application.error.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import java.util.concurrent.ExecutorService;
import java.util.function.*;

/** Shared FX/background handoff for the subject, environment and planning editors. */
public final class StudyEditorActions {
    private StudyEditorActions() { }
    public static <T> void run(ExecutorService io, Node view, Label feedback, Supplier<T> work, Consumer<T> done) {
        if (view.isDisable()) return;
        view.setDisable(true); feedback.setText("Working…");
        io.execute(() -> {
            try { T result=work.get(); Platform.runLater(() -> { view.setDisable(false); feedback.setText(""); done.accept(result); }); }
            catch (IllegalArgumentException | StudyDataException | FocusModeException | SecurityException e) {
                Platform.runLater(() -> { view.setDisable(false); feedback.setText(e.getMessage()); });
            }
        });
    }
    public static boolean confirm(Node owner, String message) {
        Alert alert=new Alert(Alert.AlertType.CONFIRMATION,message,ButtonType.CANCEL,ButtonType.OK);
        alert.initOwner(owner.getScene().getWindow()); alert.setHeaderText("Confirm deletion");
        return alert.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;
    }
    public static <T> void names(ListView<T> list, Function<T,String> label) {
        list.setCellFactory(v -> new ListCell<>() { @Override protected void updateItem(T value,boolean empty) {
            super.updateItem(value,empty); setText(empty||value==null ? null : label.apply(value));
        }});
        list.setPrefHeight(150); list.setPlaceholder(new Label("No records yet."));
    }
    public static <T> void names(ComboBox<T> box, Function<T,String> label) {
        box.setCellFactory(v -> new ListCell<>() { @Override protected void updateItem(T value,boolean empty) {
            super.updateItem(value,empty); setText(empty||value==null ? null : label.apply(value));
        }});
        box.setButtonCell(new ListCell<>() { @Override protected void updateItem(T value,boolean empty) {
            super.updateItem(value,empty); setText(empty||value==null ? null : label.apply(value));
        }});
        box.setMaxWidth(Double.MAX_VALUE);
    }
}
