package com.studymode.ui.controller;

import com.studymode.application.service.*;
import com.studymode.domain.classroom.Classroom;
import com.studymode.domain.planning.*;
import com.studymode.ui.view.StudyTimeText;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import static com.studymode.ui.view.ViewComponents.*;
import static com.studymode.ui.view.StudyEditorActions.*;

public final class GoalController {
    private final StudyOrganizationService service;
    private final ExecutorService io;
    private final ListView<StudyGoal> goals=new ListView<>();
    private final ComboBox<Classroom> classroom=new ComboBox<>();
    private final ComboBox<GoalPeriod> period=new ComboBox<>();
    private final TextField name=new TextField(), target=new TextField("60");
    private final Label feedback=text("","feedback"), progressText=text("","body-text");
    private final ProgressBar progress=new ProgressBar(0);
    private final CheckBox authorizeClosing=new CheckBox("I authorize graceful closing of the global enabled blocklist. Only used in Windows close mode; review Blocked Applications first.");
    private final VBox view=new VBox(20);
    private StudyGoal selected;
    private UUID profileId;
    private Map<UUID,Long> seconds=Map.of();
    private boolean replacing;
    private record GoalOverview(List<StudyGoal> goals,List<Classroom> classrooms,Map<UUID,Long> seconds,UUID profileId) { }
    public GoalController(StudyOrganizationService service, FocusSessionService focus, ExecutorService io, Runnable showFocus) {
        this.service=service; this.io=io;
        authorizeClosing.setWrapText(true); authorizeClosing.setId("goal-authorize-closing");
        goals.setId("goal-list"); name.setId("goal-name"); target.setId("goal-target"); classroom.setId("goal-classroom"); period.setId("goal-period");
        progressText.setId("goal-progress-text"); progress.setId("goal-progress");
        names(goals,StudyGoal::text); names(classroom,Classroom::name); classroom.setPromptText("Any classroom");
        period.getItems().setAll(GoalPeriod.values()); period.setValue(GoalPeriod.DAILY);
        Button fresh=new Button("New goal"), reload=new Button("Reload goals"), save=new Button("Save goal"), delete=new Button("Delete goal");
        Button clear=new Button("Clear classroom"), start=new Button("Start linked focus");
        save.setId("save-goal"); start.setId("start-goal"); save.getStyleClass().add("primary-button");
        fresh.setOnAction(e -> { goals.getSelectionModel().clearSelection(); edit(null); });
        clear.setOnAction(e -> classroom.setValue(null)); reload.setOnAction(e -> refresh());
        save.setOnAction(e -> {
            try {
                if(profileId==null) throw new IllegalArgumentException("Create a profile in Settings first.");
                int minutes=Integer.parseInt(target.getText().strip()); boolean creating=selected==null;
                var value=new StudyGoal(creating?UUID.randomUUID():selected.id(),profileId,name.getText(),minutes,period.getValue(),classroom.getValue()==null?null:classroom.getValue().id());
                run(io,view,feedback,() -> { service.saveGoal(value,creating); return overview(); }, found -> { display(found); goals.getSelectionModel().select(value); edit(value); feedback.setText("Goal saved."); });
            } catch (NumberFormatException ex) { feedback.setText("Target must be a whole number of minutes (1–10080)."); }
            catch (IllegalArgumentException ex) { feedback.setText(ex.getMessage()); }
        });
        delete.setOnAction(e -> {
            if(selected==null) { feedback.setText("Select a saved goal first."); return; }
            if(!confirm(view,"Delete this goal? Task links are cleared. Recorded sessions referencing it prevent deletion.")) return;
            UUID id=selected.id();
            run(io,view,feedback,() -> { service.deleteGoal(id); return overview(); }, found -> { display(found); feedback.setText("Goal deleted."); });
        });
        start.setOnAction(e -> {
            if(selected==null) { feedback.setText("Select a saved goal first."); return; }
            UUID id=selected.id();
            boolean authorized=authorizeClosing.isSelected(); authorizeClosing.setSelected(false);
            run(io,view,feedback,() -> { var goal=service.requireGoal(id); return focus.start(Math.min(25,goal.targetMinutes())*60L,goal.classroomId(),id,authorized); }, saved -> showFocus.run());
        });
        view.getChildren().setAll(card(text("Study goals","section-title"),text("Progress counts linked, completed sessions in your current study-zone day or Monday-start week. Paused and interrupted time is excluded.","muted"),
                goals,new FlowPane(10,10,fresh,reload)),
                card(text("Goal details","section-title"),text("Name","field-label"),name,text("Target minutes (1–10080)","field-label"),target,
                        text("Repeats","field-label"),period,text("Classroom assignment (optional)","field-label"),classroom,clear,
                        new FlowPane(10,10,save,delete),authorizeClosing,start,progressText,progress,feedback));
        progress.setMaxWidth(Double.MAX_VALUE);
        goals.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> { if(!replacing) edit(b); });
        edit(null);
    }
    public VBox view() { return view; }
    public void refresh() { run(io,view,feedback,this::overview,this::display); }
    private GoalOverview overview() {
        var found=service.goals(); Map<UUID,Long> totals=new HashMap<>();
        found.forEach(g -> totals.put(g.id(),service.goalSeconds(g.id())));
        return new GoalOverview(found,service.classrooms(),Map.copyOf(totals),service.profile().id());
    }
    private void display(GoalOverview data) {
        replacing=true; goals.getItems().setAll(data.goals()); classroom.getItems().setAll(data.classrooms()); replacing=false;
        seconds=data.seconds(); profileId=data.profileId(); edit(null);
    }
    private void edit(StudyGoal value) {
        selected=value; name.setText(value==null?"":value.text()); target.setText(value==null?"60":Integer.toString(value.targetMinutes()));
        period.setValue(value==null?GoalPeriod.DAILY:value.period());
        classroom.setValue(value==null?null:classroom.getItems().stream().filter(c -> c.id().equals(value.classroomId())).findFirst().orElse(null));
        long actual=value==null?0:seconds.getOrDefault(value.id(),0L);
        progress.setProgress(value==null?0:Math.min(1,(double)actual/(value.targetMinutes()*60L)));
        progressText.setText(value==null?"Save and select a goal to see progress.":StudyTimeText.duration(actual)+" / "+value.targetMinutes()+" minutes · "+value.period());
    }
}
