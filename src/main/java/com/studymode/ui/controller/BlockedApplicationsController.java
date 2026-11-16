package com.studymode.ui.controller;

import com.studymode.application.service.*;
import com.studymode.application.port.DistractionBlocker;
import com.studymode.domain.workspace.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import static com.studymode.ui.view.ViewComponents.*;
import static com.studymode.ui.view.StudyEditorActions.*;

public final class BlockedApplicationsController {
    private final BlockedApplicationService service;
    private final StudyOrganizationService organization;
    private final DistractionBlocker blocker;
    private final ExecutorService io;
    private final ListView<BlockedApplication> applications=new ListView<>();
    private final ListView<String> events=new ListView<>();
    private final ComboBox<FocusWorkspace> workspace=new ComboBox<>();
    private final TextField name=new TextField(), path=new TextField();
    private final CheckBox enabled=new CheckBox("Enabled for future/authorized focus sessions");
    private final Label feedback=text("","feedback");
    private final VBox view=new VBox(20);
    private boolean displaying;
    private record BlocklistOverview(List<BlockedApplication> applications,List<FocusWorkspace> workspaces,List<String> events) { }
    public BlockedApplicationsController(BlockedApplicationService service,StudyOrganizationService organization,
            DistractionBlocker blocker,ExecutorService io) {
        this.service=service; this.organization=organization; this.blocker=blocker; this.io=io;
        applications.setId("blocked-application-list"); name.setId("blocked-application-name"); path.setId("blocked-application-path");
        enabled.setId("blocked-application-enabled"); workspace.setId("blocked-application-workspace"); events.setId("blocking-events");
        names(applications,a -> (a.enabled()?"ENABLED · ":"DISABLED · ")+a.displayName()+"\n"+a.executablePath());
        names(workspace,FocusWorkspace::name); events.setPrefHeight(220); events.setPlaceholder(text("No monitoring events. Start focus to monitor authorized selections.","muted"));
        path.setEditable(false); name.setPromptText("Application display name");
        Button browse=new Button("Browse .exe"), add=new Button("Add application"), remove=new Button("Remove application"), reload=new Button("Reload selections / events"), assign=new Button("Assign selected app to workspace");
        browse.setOnAction(e -> {
            FileChooser chooser=new FileChooser(); chooser.setTitle("Select an application to block");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Windows executable","*.exe"));
            var file=chooser.showOpenDialog(view.getScene().getWindow());
            if(file!=null) { path.setText(file.getAbsolutePath()); name.setText(file.getName()); }
        });
        add.setId("add-blocked-application"); add.getStyleClass().add("primary-button");
        add.setOnAction(e -> {
            try {
                var draft=new BlockedApplication(name.getText(),Path.of(path.getText()));
                run(io,view,feedback,() -> { service.add(draft.displayName(),draft.executablePath()); return overview(); },
                        found -> { display(found); feedback.setText("Application added. New selections take effect on the next session."); });
            } catch(IllegalArgumentException ex) { feedback.setText(ex.getMessage()); }
        });
        applications.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> {
            displaying=true; enabled.setSelected(b!=null&&b.enabled()); enabled.setDisable(b==null); displaying=false;
        });
        enabled.setOnAction(e -> {
            if(displaying) return; var selected=applications.getSelectionModel().getSelectedItem(); if(selected==null) return;
            boolean checked=enabled.isSelected();
            run(io,view,feedback,() -> { service.setEnabled(selected.executablePath(),checked); return overview(); },
                    found -> { display(found); feedback.setText("Preference saved. Disabling/removal applies to active monitoring on the next timer update."); });
        });
        remove.setOnAction(e -> {
            var selected=applications.getSelectionModel().getSelectedItem();
            if(selected==null) { feedback.setText("Select an application first."); return; }
            if(!confirm(view,"Remove this application? Remove its workspace assignments first. Disabling is available without deleting assignments.")) return;
            run(io,view,feedback,() -> { service.remove(selected.executablePath()); return overview(); },found -> { display(found); feedback.setText("Application removed."); });
        });
        assign.setId("assign-blocked-application");
        assign.setOnAction(e -> {
            var app=applications.getSelectionModel().getSelectedItem(); var selected=workspace.getValue();
            if(app==null||selected==null) { feedback.setText("Select an application and a workspace."); return; }
            UUID id=selected.id();
            run(io,view,feedback,() -> {
                var current=organization.requireWorkspace(id); Set<BlockedApplication> targets=new HashSet<>(current.blocklist());
                if(!targets.add(app)) throw new IllegalArgumentException("This application is already assigned to this workspace.");
                organization.saveWorkspace(new FocusWorkspace(current.id(),current.name(),current.classroomId(),current.preferences(),current.resourceIds(),targets,current.shortcuts()),false);
                return overview();
            },found -> { display(found); feedback.setText("Workspace assignment saved. Start that workspace to use its blocklist."); });
        });
        reload.setOnAction(e -> refresh());
        enabled.setDisable(true);
        view.getChildren().setAll(
                card(text("Selected desktop applications","section-title"),text(blocker.mode()+" · best-effort, not impossible to bypass. Windows close mode requests a graceful close and may trigger save dialogs; it never forces termination. Pause and Stop cancel monitoring.","muted"),applications,enabled,new FlowPane(10,10,remove,reload)),
                card(text("Add an executable","section-title"),text("Name","field-label"),name,text("Verified executable path","field-label"),path,new FlowPane(10,10,browse,add)),
                card(text("Workspace assignment","section-title"),workspace,assign,text("Edit the workspace to remove assignments. Plain Focus uses the global enabled blocklist; workspace Start uses only that workspace's enabled selections.","muted")),
                card(text("Recent monitoring events","section-title"),events),feedback);
    }
    public VBox view() { return view; }
    public void refresh() { run(io,view,feedback,this::overview,this::display); }
    private BlocklistOverview overview() { return new BlocklistOverview(service.applications(),organization.workspaces(),blocker.events()); }
    private void display(BlocklistOverview found) {
        applications.getItems().setAll(found.applications()); workspace.getItems().setAll(found.workspaces()); events.getItems().setAll(found.events());
        name.clear(); path.clear(); enabled.setSelected(false); enabled.setDisable(true);
    }
}
