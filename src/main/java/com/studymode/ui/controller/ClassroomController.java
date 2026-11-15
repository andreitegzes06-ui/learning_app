package com.studymode.ui.controller;

import com.studymode.application.service.StudyOrganizationService;
import com.studymode.domain.classroom.*;
import com.studymode.domain.planning.StudyTask;
import com.studymode.domain.study.*;
import com.studymode.ui.view.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import static com.studymode.ui.view.ViewComponents.*;
import static com.studymode.ui.view.StudyEditorActions.*;

/** Persistent subject editor, resource list, task checklist and subject-specific history. */
public final class ClassroomController {
    private final StudyOrganizationService service;
    private final ExecutorService io;
    private final ListView<Classroom> classrooms=new ListView<>();
    private final TextField name=new TextField(), resourceTitle=new TextField(), resourceUrl=new TextField(), taskText=new TextField();
    private final TextArea description=new TextArea(), notes=new TextArea();
    private final ColorPicker color=new ColorPicker(Color.web("#7569EF"));
    private final ListView<StudyResource> resources=new ListView<>();
    private final ListView<StudyTask> tasks=new ListView<>();
    private final ListView<StudySession> history=new ListView<>();
    private final Label feedback=text("","feedback"), totals=text("","body-text");
    private final VBox view=new VBox(20);
    private final VBox classroomDetails;
    private Classroom selected;
    private boolean replacing;
    public ClassroomController(StudyOrganizationService service, ExecutorService io, Consumer<String> openUrl) {
        this.service=service; this.io=io;
        classrooms.setId("classroom-list"); name.setId("classroom-name"); description.setId("classroom-description");
        notes.setId("classroom-notes"); color.setId("classroom-color"); resources.setId("classroom-resources");
        history.setId("classroom-history"); totals.setId("classroom-study-time");
        resourceTitle.setId("resource-title"); resourceUrl.setId("resource-url"); taskText.setId("task-text"); tasks.setId("classroom-tasks");
        names(classrooms,Classroom::name); names(resources,r -> r.title()+" — "+r.uri());
        names(tasks,t -> (t.completed() ? "✓ " : "○ ")+t.text());
        names(history,s -> s.endedAt()+" · "+StudyTimeText.duration(s.activeFocusSeconds())+" · "+s.status());
        classrooms.setCellFactory(list -> new ListCell<>() { @Override protected void updateItem(Classroom v,boolean empty) {
            super.updateItem(v,empty); setText(empty||v==null ? null : "● "+v.name());
            setTextFill(empty||v==null ? Color.WHITE : Color.web(v.color()));
        }});
        description.setPrefRowCount(2); notes.setPrefRowCount(4); description.setWrapText(true); notes.setWrapText(true);
        resourceTitle.setPromptText("Resource title"); resourceUrl.setPromptText("https://…"); taskText.setPromptText("What needs doing?");
        Button save=new Button("Save classroom"); save.setId("save-classroom"); save.getStyleClass().add("primary-button");
        Button fresh=new Button("New classroom"), reload=new Button("Reload classrooms"), delete=new Button("Delete classroom");
        fresh.setOnAction(e -> { classrooms.getSelectionModel().clearSelection(); edit(null); });
        reload.setOnAction(e -> refresh());
        save.setOnAction(e -> {
            try {
                String hex=String.format(Locale.ROOT,"#%02X%02X%02X",(int)Math.round(color.getValue().getRed()*255),(int)Math.round(color.getValue().getGreen()*255),(int)Math.round(color.getValue().getBlue()*255));
                boolean creating=selected==null;
                Classroom value=new Classroom(creating?UUID.randomUUID():selected.id(),name.getText(),description.getText(),List.copyOf(resources.getItems()),hex,notes.getText());
                run(io,view,feedback,() -> { service.saveClassroom(value,creating); return service.classrooms(); }, found -> {
                    replacing=true; classrooms.getItems().setAll(found); classrooms.getSelectionModel().select(value); replacing=false;
                    edit(value); feedback.setText("Classroom saved.");
                });
            } catch (IllegalArgumentException ex) { feedback.setText(ex.getMessage()); }
        });
        delete.setOnAction(e -> {
            if (selected==null) { feedback.setText("Select a saved classroom first."); return; }
            if (!confirm(view,"Delete this classroom and its resources? Tasks lose their classroom link. Goals, workspaces or sessions referencing it prevent deletion.")) return;
            UUID id=selected.id();
            run(io,view,feedback,() -> { service.deleteClassroom(id); return service.classrooms(); }, found -> {
                replacing=true; classrooms.getItems().setAll(found); replacing=false; edit(null); feedback.setText("Classroom deleted.");
            });
        });
        Button addResource=new Button("Add / update link"); addResource.setId("save-resource");
        addResource.setOnAction(e -> {
            try {
                var old=resources.getSelectionModel().getSelectedItem();
                var value=StudyResource.fromUrl(old==null?UUID.randomUUID():old.id(),resourceTitle.getText(),resourceUrl.getText());
                if (resources.getItems().stream().anyMatch(r -> !r.id().equals(value.id()) && r.uri().normalize().equals(value.uri().normalize())))
                    throw new IllegalArgumentException("This URL is already in the classroom.");
                if (old!=null) resources.getItems().set(resources.getItems().indexOf(old),value); else resources.getItems().add(value);
                resources.getSelectionModel().clearSelection(); resourceTitle.clear(); resourceUrl.clear();
                feedback.setText("Link prepared. Save classroom to persist changes.");
            } catch (IllegalArgumentException ex) { feedback.setText(ex.getMessage()); }
        });
        resources.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> { resourceTitle.setText(b==null?"":b.title()); resourceUrl.setText(b==null?"":b.uri().toString()); });
        Button newLink=new Button("New link"), removeResource=new Button("Remove link"), openResource=new Button("Open link");
        newLink.setOnAction(e -> { resources.getSelectionModel().clearSelection(); resourceTitle.clear(); resourceUrl.clear(); });
        removeResource.setOnAction(e -> { resources.getItems().remove(resources.getSelectionModel().getSelectedItem()); feedback.setText("Save classroom to persist link removal. Selected workspace links cannot be removed."); });
        openResource.setOnAction(e -> { var r=resources.getSelectionModel().getSelectedItem(); if(r!=null) openUrl.accept(r.uri().toString()); else feedback.setText("Select a link first."); });
        Button addTask=new Button("Add task"), complete=new Button("Complete task"), removeTask=new Button("Remove task");
        addTask.setId("add-task"); complete.setId("complete-task"); removeTask.setId("remove-task");
        addTask.setOnAction(e -> {
            if(selected==null) { feedback.setText("Save a classroom before adding tasks."); return; }
            UUID id=selected.id(); String input=taskText.getText();
            run(io,view,feedback,() -> { service.addTask(id,input); return service.tasks(id); }, found -> { tasks.getItems().setAll(found); taskText.clear(); feedback.setText("Task saved."); });
        });
        complete.setOnAction(e -> changeTask(false));
        removeTask.setOnAction(e -> changeTask(true));
        classroomDetails=new VBox(14,card(text("Tasks", "section-title"),tasks,taskText,new FlowPane(10,10,addTask,complete,removeTask)),
                card(text("Classroom study history","section-title"),totals,history));
        view.getChildren().setAll(card(text("Your classrooms","section-title"),classrooms,new FlowPane(10,10,fresh,reload)),
                card(text("Classroom details","section-title"),text("Name","field-label"),name,text("Description","field-label"),description,
                        text("Classroom color","field-label"),color,text("Notes","field-label"),notes,
                        text("Educational links · save with classroom","field-label"),resources,resourceTitle,resourceUrl,
                        new FlowPane(10,10,newLink,addResource,removeResource,openResource),new FlowPane(10,10,save,delete),feedback),classroomDetails);
        classrooms.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> { if(!replacing) edit(b); });
        edit(null);
    }
    public VBox view() { return view; }
    public void refresh() {
        run(io,view,feedback,service::classrooms,found -> { replacing=true; classrooms.getItems().setAll(found); replacing=false; edit(null); feedback.setText(found.size()+" classroom(s) loaded."); });
    }
    private void edit(Classroom value) {
        selected=value; name.setText(value==null?"":value.name()); description.setText(value==null?"":value.description());
        notes.setText(value==null?"":value.notes()); color.setValue(Color.web(value==null?"#7569EF":value.color()));
        resources.getItems().setAll(value==null?List.of():value.resources());
        tasks.getItems().clear(); history.getItems().clear(); totals.setText("");
        classroomDetails.setVisible(value!=null); classroomDetails.setManaged(value!=null);
        if(value!=null) {
            UUID id=value.id();
            // Keep loading independent of form edits; discard stale selection results.
            io.execute(() -> {
                try { var found=service.tasks(id); var sessions=service.classroomHistory(id); long seconds=service.classroomSeconds(id);
                    javafx.application.Platform.runLater(() -> { if(selected!=null && selected.id().equals(id)) { tasks.getItems().setAll(found); history.getItems().setAll(sessions); totals.setText("Completed study time: "+StudyTimeText.duration(seconds)); } });
                } catch (IllegalArgumentException | com.studymode.application.error.StudyDataException | com.studymode.application.error.FocusModeException ex) {
                    javafx.application.Platform.runLater(() -> { if(selected!=null && selected.id().equals(id)) feedback.setText(ex.getMessage()); });
                }
            });
        }
    }
    private void changeTask(boolean remove) {
        var task=tasks.getSelectionModel().getSelectedItem();
        if(task==null || selected==null) { feedback.setText("Select a task first."); return; }
        UUID classroom=selected.id();
        run(io,view,feedback,() -> { if(remove) service.deleteTask(task.id()); else service.completeTask(task.id()); return service.tasks(classroom); },
                found -> { tasks.getItems().setAll(found); feedback.setText(remove?"Task removed.":"Task completed."); });
    }
}
