package com.studymode.ui.controller;

import com.studymode.application.service.*;
import com.studymode.domain.classroom.*;
import com.studymode.domain.workspace.*;
import com.studymode.domain.planning.StudyGoal;
import com.studymode.platform.desktop.StudyApplicationLauncher;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import static com.studymode.ui.view.ViewComponents.*;
import static com.studymode.ui.view.StudyEditorActions.*;

public final class WorkspaceController {
    private final StudyOrganizationService service;
    private final ExecutorService io;
    private final ListView<FocusWorkspace> workspaces=new ListView<>();
    private final ComboBox<Classroom> classroom=new ComboBox<>();
    private final ComboBox<StudyGoal> goal=new ComboBox<>();
    private final TextField name=new TextField(), focus=new TextField("25"), rest=new TextField("5");
    private final ListView<StudyResource> resources=new ListView<>();
    private final ListView<BlockedApplication> blocklist=new ListView<>(), shortcuts=new ListView<>();
    private final Label feedback=text("","feedback");
    private final VBox view=new VBox(20);
    private FocusWorkspace selected;
    private boolean replacing;
    private record WorkspaceOverview(List<FocusWorkspace> workspaces,List<Classroom> classrooms,List<StudyGoal> goals) { }
    public WorkspaceController(StudyOrganizationService service, FocusSessionService sessions, ExecutorService io,
            Runnable showFocus, Consumer<String> openUrl) {
        this.service=service; this.io=io;
        workspaces.setId("workspace-list"); name.setId("workspace-name"); classroom.setId("workspace-classroom");
        focus.setId("workspace-focus"); rest.setId("workspace-break"); resources.setId("workspace-resources"); goal.setId("workspace-goal");
        blocklist.setId("workspace-blocklist"); shortcuts.setId("workspace-shortcuts");
        names(workspaces,FocusWorkspace::name); names(classroom,Classroom::name); names(goal,StudyGoal::text);
        names(resources,r -> r.title()+" — "+r.uri()); names(blocklist,BlockedApplication::toString); names(shortcuts,BlockedApplication::toString);
        resources.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        classroom.setPromptText("Select classroom"); goal.setPromptText("No linked goal");
        classroom.valueProperty().addListener((o,a,b) -> {
            resources.getItems().setAll(b==null?List.of():b.resources());
        });
        Button fresh=new Button("New workspace"), reload=new Button("Reload workspaces"), save=new Button("Save workspace"), delete=new Button("Delete workspace"), start=new Button("Start saved workspace");
        save.setId("save-workspace"); start.setId("start-workspace"); save.getStyleClass().add("primary-button"); start.getStyleClass().add("primary-button");
        fresh.setOnAction(e -> { workspaces.getSelectionModel().clearSelection(); edit(null); }); reload.setOnAction(e -> refresh());
        save.setOnAction(e -> {
            try {
                if(classroom.getValue()==null) throw new IllegalArgumentException("Select a classroom.");
                int minutes=Integer.parseInt(focus.getText().strip()), breakMinutes=Integer.parseInt(rest.getText().strip());
                var previous=selected==null?FocusPreferences.defaults():selected.preferences();
                var value=new FocusWorkspace(selected==null?UUID.randomUUID():selected.id(),name.getText(),classroom.getValue().id(),
                        new FocusPreferences(minutes,breakMinutes,previous.longBreakMinutes(),previous.longBreakEvery()),
                        resources.getSelectionModel().getSelectedItems().stream().map(StudyResource::id).collect(java.util.stream.Collectors.toSet()),
                        Set.copyOf(blocklist.getItems()),Set.copyOf(shortcuts.getItems()));
                boolean creating=selected==null;
                run(io,view,feedback,() -> { service.saveWorkspace(value,creating); return overview(); }, found -> {
                    display(found); workspaces.getSelectionModel().select(value); edit(value); feedback.setText("Workspace saved.");
                });
            } catch (NumberFormatException ex) { feedback.setText("Focus and break durations must be whole minutes."); }
            catch (IllegalArgumentException ex) { feedback.setText(ex.getMessage()); }
        });
        delete.setOnAction(e -> {
            if(selected==null) { feedback.setText("Select a saved workspace first."); return; }
            if(!confirm(view,"Delete this workspace configuration? Recorded study sessions are retained.")) return;
            UUID id=selected.id();
            run(io,view,feedback,() -> { service.deleteWorkspace(id); return overview(); }, found -> { display(found); feedback.setText("Workspace deleted."); });
        });
        start.setOnAction(e -> {
            if(selected==null) { feedback.setText("Save and select a workspace first. Start uses its saved settings."); return; }
            UUID id=selected.id(), goalId=goal.getValue()==null?null:goal.getValue().id();
            run(io,view,feedback,() -> service.startWorkspace(id,goalId,sessions),result -> showFocus.run());
        });
        Button clearGoal=new Button("Clear linked goal"); clearGoal.setOnAction(e -> goal.setValue(null));
        Button addBlock=new Button("Select blocked .exe"), removeBlock=new Button("Remove selection"), addShortcut=new Button("Select study .exe"), removeShortcut=new Button("Remove shortcut"), launch=new Button("Open selected study app");
        addBlock.setOnAction(e -> chooseApplication(blocklist)); addShortcut.setOnAction(e -> chooseApplication(shortcuts));
        removeBlock.setOnAction(e -> blocklist.getItems().remove(blocklist.getSelectionModel().getSelectedItem()));
        removeShortcut.setOnAction(e -> shortcuts.getItems().remove(shortcuts.getSelectionModel().getSelectedItem()));
        launch.setOnAction(e -> {
            var app=shortcuts.getSelectionModel().getSelectedItem();
            if(app==null) { feedback.setText("Select a study application shortcut first."); return; }
            run(io,view,feedback,() -> { new StudyApplicationLauncher().launch(app); return true; },done -> feedback.setText("Study application opened."));
        });
        Button open=new Button("Open selected link"); open.setOnAction(e -> {
            var resource=resources.getSelectionModel().getSelectedItem();
            if(resource==null) feedback.setText("Select a classroom resource first."); else openUrl.accept(resource.uri().toString());
        });
        view.getChildren().setAll(card(text("Focus workspaces","section-title"),workspaces,new FlowPane(10,10,fresh,reload)),
                card(text("Environment settings","section-title"),text("Name","field-label"),name,text("Classroom","field-label"),classroom,
                        text("Focus minutes (1–240)","field-label"),focus,text("Default break minutes (1–60)","field-label"),rest,
                        text("Break preference is saved; this milestone runs single focus sessions without automatic break cycles.","muted"),
                        text("Optional classroom resources · Ctrl-click to select several","field-label"),resources,open,
                        text("Saved blocklist · Windows enforcement is not active","field-label"),blocklist,new FlowPane(10,10,addBlock,removeBlock),
                        text("Optional study application shortcuts · opened only when you request it","field-label"),shortcuts,new FlowPane(10,10,addShortcut,removeShortcut,launch),
                        new FlowPane(10,10,save,delete)),
                card(text("Start the saved environment","section-title"),text("The saved classroom and focus duration configure the real timer. Optional goal is chosen for this session.","muted"),
                        goal,clearGoal,start,feedback));
        workspaces.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> { if(!replacing) edit(b); });
        edit(null);
    }
    public VBox view() { return view; }
    public void refresh() { run(io,view,feedback,this::overview,this::display); }
    private WorkspaceOverview overview() { return new WorkspaceOverview(service.workspaces(),service.classrooms(),service.goals()); }
    private void display(WorkspaceOverview found) {
        replacing=true; workspaces.getItems().setAll(found.workspaces()); classroom.getItems().setAll(found.classrooms()); goal.getItems().setAll(found.goals()); replacing=false; edit(null);
    }
    private void edit(FocusWorkspace value) {
        selected=value; name.setText(value==null?"":value.name()); focus.setText(value==null?"25":Integer.toString(value.preferences().focusMinutes()));
        rest.setText(value==null?"5":Integer.toString(value.preferences().shortBreakMinutes()));
        classroom.setValue(value==null?null:classroom.getItems().stream().filter(c -> c.id().equals(value.classroomId())).findFirst().orElse(null));
        resources.getSelectionModel().clearSelection();
        if(value!=null) for(int i=0;i<resources.getItems().size();i++) if(value.resourceIds().contains(resources.getItems().get(i).id())) resources.getSelectionModel().select(i);
        blocklist.getItems().setAll(value==null?Set.of():value.blocklist()); shortcuts.getItems().setAll(value==null?Set.of():value.shortcuts()); goal.setValue(null);
    }
    private void chooseApplication(ListView<BlockedApplication> list) {
        FileChooser picker=new FileChooser(); picker.setTitle("Select a Windows application"); picker.getExtensionFilters().add(new FileChooser.ExtensionFilter("Windows executable","*.exe"));
        var file=picker.showOpenDialog(view.getScene().getWindow()); if(file==null) return;
        try {
            var app=new BlockedApplication(file.getName(),file.toPath().toAbsolutePath());
            if(list.getItems().contains(app)) { feedback.setText("This executable is already selected."); return; }
            run(io,view,feedback,() -> { if(!Files.isRegularFile(app.executablePath())) throw new IllegalArgumentException("Select an existing executable file."); return app; },
                    selectedApp -> { list.getItems().add(selectedApp); feedback.setText("Selection prepared. Save workspace to persist it."); });
        } catch (IllegalArgumentException ex) { feedback.setText(ex.getMessage()); }
    }
}
