package com.studymode.application.service;

import com.studymode.application.port.*;
import com.studymode.application.error.*;
import com.studymode.domain.classroom.*;
import com.studymode.domain.planning.*;
import com.studymode.domain.profile.UserProfile;
import com.studymode.domain.study.*;
import com.studymode.domain.workspace.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Organizes existing domain entities through the existing persistence ports. */
public final class StudyOrganizationService {
    private final ClassroomCatalog classrooms;
    private final FocusWorkspaceStore workspaces;
    private final StudyPlanStore plans;
    private final UserProfileStore profiles;
    private final StudySessionLedger ledger;
    private final BlockedApplicationCatalog applications;
    private final Clock clock;
    public StudyOrganizationService(ClassroomCatalog classrooms, FocusWorkspaceStore workspaces, StudyPlanStore plans,
            UserProfileStore profiles, StudySessionLedger ledger, BlockedApplicationCatalog applications, Clock clock) {
        this.classrooms=classrooms; this.workspaces=workspaces; this.plans=plans; this.profiles=profiles;
        this.ledger=ledger; this.applications=applications; this.clock=clock;
    }
    public List<Classroom> classrooms() { return classrooms.findAll(); }
    public List<FocusWorkspace> workspaces() { return workspaces.findAll(); }
    public List<StudyGoal> goals() { var p=profile(); return plans.goals().stream().filter(g -> g.profileId().equals(p.id())).toList(); }
    public List<StudyTask> tasks(UUID classroom) { var p=profile(); requireClassroom(classroom);
        return plans.tasks().stream().filter(t -> p.id().equals(t.profileId()) && classroom.equals(t.classroomId())).toList(); }
    public UserProfile profile() { return profiles.findLocalProfile().orElseThrow(() -> new FocusModeException("Create a profile in Settings first.")); }
    public Classroom requireClassroom(UUID id) { return classrooms.find(id).orElseThrow(() -> new IllegalArgumentException("The classroom no longer exists. Reload your choices.")); }
    public FocusWorkspace requireWorkspace(UUID id) { return workspaces.find(id).orElseThrow(() -> new IllegalArgumentException("The workspace no longer exists. Reload your choices.")); }
    public void saveClassroom(Classroom value, boolean creating) {
        if (!creating) requireClassroom(value.id());
        if (value.resources().stream().map(r -> r.uri().normalize()).distinct().count()!=value.resources().size())
            throw new IllegalArgumentException("A resource URL may only appear once per classroom.");
        if (classrooms.findAll().stream().anyMatch(c -> !c.id().equals(value.id()) && c.name().equalsIgnoreCase(value.name())))
            throw new IllegalArgumentException("A classroom with this name already exists.");
        classrooms.save(value);
    }
    public void deleteClassroom(UUID id) { requireClassroom(id); classrooms.delete(id); }
    public void saveWorkspace(FocusWorkspace value, boolean creating) {
        Set<BlockedApplication> retained=new HashSet<>();
        if (!creating) { var previous=requireWorkspace(value.id()); retained.addAll(previous.blocklist()); retained.addAll(previous.shortcuts()); }
        value.validateAgainst(requireClassroom(value.classroomId()));
        if (workspaces.findAll().stream().anyMatch(w -> !w.id().equals(value.id()) && w.name().equalsIgnoreCase(value.name())))
            throw new IllegalArgumentException("A workspace with this name already exists.");
        Set<BlockedApplication> selections=new HashSet<>(value.blocklist()); selections.addAll(value.shortcuts());
        for (var app:selections) if (!retained.contains(app) && !java.nio.file.Files.isRegularFile(app.executablePath()))
            throw new IllegalArgumentException("Select an existing Windows executable: "+app.executablePath());
        for (var app:value.blocklist()) if (applications.find(app.executablePath()).isEmpty()) applications.save(app);
        workspaces.save(value);
    }
    public void deleteWorkspace(UUID id) { requireWorkspace(id); workspaces.delete(id); }
    public com.studymode.application.data.FocusSessionSnapshot startWorkspace(UUID id, UUID goalId, FocusSessionService focus) {
        var workspace=requireWorkspace(id); workspace.validateAgainst(requireClassroom(workspace.classroomId()));
        return focus.start(workspace.preferences().focusMinutes()*60L,workspace.classroomId(),goalId);
    }
    public void saveGoal(StudyGoal value, boolean creating) {
        if (!value.profileId().equals(profile().id())) throw new IllegalArgumentException("The goal must belong to your profile.");
        if (!creating && plans.findGoal(value.id()).isEmpty()) throw new IllegalArgumentException("The goal no longer exists.");
        if (value.classroomId()!=null) requireClassroom(value.classroomId());
        if (plans.goals().stream().anyMatch(g -> !g.id().equals(value.id()) && g.profileId().equals(value.profileId()) && g.text().equalsIgnoreCase(value.text())))
            throw new IllegalArgumentException("A goal with this name already exists.");
        plans.save(value);
    }
    public void deleteGoal(UUID id) { requireGoal(id); plans.deleteGoal(id); }
    public StudyGoal requireGoal(UUID id) {
        var goal=plans.findGoal(id).orElseThrow(() -> new IllegalArgumentException("The goal no longer exists."));
        if (!goal.profileId().equals(profile().id())) throw new IllegalArgumentException("This goal belongs to another profile.");
        return goal;
    }
    public void addTask(UUID classroom, String text) {
        requireClassroom(classroom); var p=profile();
        StudyTask task=new StudyTask(UUID.randomUUID(),p.id(),text,classroom,null,null,null);
        if (tasks(classroom).stream().anyMatch(t -> !t.completed() && t.text().equalsIgnoreCase(task.text())))
            throw new IllegalArgumentException("An unfinished task with this text already exists in this classroom.");
        plans.save(task);
    }
    public void completeTask(UUID id) { var task=requireTask(id); if (!task.completed()) plans.save(task.complete(clock.instant())); }
    public void deleteTask(UUID id) { requireTask(id); plans.deleteTask(id); }
    private StudyTask requireTask(UUID id) {
        var task=plans.findTask(id).orElseThrow(() -> new IllegalArgumentException("The task no longer exists."));
        if (!task.profileId().equals(profile().id())) throw new IllegalArgumentException("This task belongs to another profile.");
        return task;
    }
    public List<StudySession> classroomHistory(UUID id) {
        requireClassroom(id); var p=profile();
        return ledger.findByPeriod(Instant.MIN,Instant.MAX).stream()
                .filter(s -> s.finished() && p.id().equals(s.profileId()) && id.equals(s.context().classroomId()))
                .sorted(Comparator.comparing(StudySession::endedAt).reversed()).toList();
    }
    public long classroomSeconds(UUID id) { return total(classroomHistory(id).stream().filter(s -> s.status()==SessionStatus.COMPLETED).toList()); }
    /** Linked completed sessions within the goal's current local day or ISO Monday-start week. */
    public long goalSeconds(UUID id) {
        StudyGoal goal=requireGoal(id); ZoneId zone=profile().studyZone(); LocalDate today=LocalDate.now(clock.withZone(zone));
        LocalDate first=goal.period()==GoalPeriod.DAILY ? today : today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Instant from=first.atStartOfDay(zone).toInstant();
        Instant to=(goal.period()==GoalPeriod.DAILY ? first.plusDays(1) : first.plusWeeks(1)).atStartOfDay(zone).toInstant();
        return total(ledger.findByPeriod(Instant.MIN,Instant.MAX).stream()
                .filter(s -> s.status()==SessionStatus.COMPLETED && s.profileId().equals(goal.profileId()) && id.equals(s.context().goalId())
                        && !s.endedAt().isBefore(from) && s.endedAt().isBefore(to)).toList());
    }
    private long total(List<StudySession> sessions) {
        try { return sessions.stream().mapToLong(StudySession::activeFocusSeconds).reduce(0,Math::addExact); }
        catch (ArithmeticException e) { throw new StudyDataException("Stored study totals exceed the supported range.",e); }
    }
}
