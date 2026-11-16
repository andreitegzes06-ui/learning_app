package com.studymode.application.data;

import com.studymode.application.error.DataImportException;
import com.studymode.domain.workspace.BlockedApplication;
import com.studymode.domain.classroom.Classroom;
import java.util.*;
import java.util.function.Function;

public final class SnapshotValidator {
    public void validate(StudyDataSnapshot data) {
        try {
            if (data.formatVersion() < 1 || data.formatVersion() > 4) reject("Unsupported JSON format version: " + data.formatVersion());
            if (data.recordCount() > 100_000) reject("Import exceeds the 100,000-record limit.");
            if (data.profiles().size() > 1) reject("Only one local profile is supported per data directory.");
            Set<UUID> profiles = unique(data.profiles(), p -> p.id(), "profile");
            unique(data.classrooms(), c -> c.name().toLowerCase(Locale.ROOT), "classroom name");
            unique(data.workspaces(), w -> w.name().strip().toLowerCase(Locale.ROOT), "workspace name");
            unique(data.goals(), g -> g.profileId()+":"+g.text().toLowerCase(Locale.ROOT), "goal name");
            unique(data.sessions(), s -> s.id(), "session"); unique(data.classrooms(), c -> c.id(), "classroom");
            unique(data.goals(), g -> g.id(), "goal"); unique(data.tasks(), t -> t.id(), "task");
            unique(data.workspaces(), w -> w.id(), "workspace"); unique(data.achievements(), a -> a.code(), "achievement");
            unique(data.awards(), a -> a.profileId() + ":" + a.achievementCode(), "award");
            unique(data.classrooms().stream().flatMap(c -> c.resources().stream()).toList(), r -> r.id(), "resource");
            Set<UUID> classrooms = unique(data.classrooms(), c -> c.id(), "classroom");
            Set<UUID> goals = unique(data.goals(), g -> g.id(), "goal");
            Set<String> achievements = unique(data.achievements(), a -> a.code(), "achievement");
            Set<UUID> unfinished = new HashSet<>();
            for (var session : data.sessions()) {
                reference(profiles, session.profileId(), "session profile");
                if (session.context().classroomId() != null) reference(classrooms, session.context().classroomId(), "session classroom");
                if (session.context().goalId() != null) reference(goals, session.context().goalId(), "session goal");
                if (session.context().goalId()!=null) {
                    var goal=data.goals().stream().filter(g -> g.id().equals(session.context().goalId())).findFirst().orElseThrow();
                    if (!goal.profileId().equals(session.profileId()) || (goal.classroomId()!=null && !goal.classroomId().equals(session.context().classroomId())))
                        reject("Session goal must belong to its profile and assigned classroom.");
                }
                if (!session.finished() && !unfinished.add(session.profileId())) reject("A profile has more than one unfinished session.");
            }
            data.goals().forEach(g -> { reference(profiles, g.profileId(), "goal profile"); if (g.classroomId()!=null) reference(classrooms,g.classroomId(),"goal classroom"); });
            data.tasks().forEach(t -> {
                reference(profiles, t.profileId(), "task profile");
                if (t.classroomId() != null) reference(classrooms, t.classroomId(), "task classroom");
                if (t.goalId() != null) reference(goals, t.goalId(), "task goal");
            });
            data.awards().forEach(a -> { reference(profiles, a.profileId(), "award profile"); reference(achievements, a.achievementCode(), "award definition"); });
            Map<String, BlockedApplication> apps = new HashMap<>();
            for (var selection : data.applications()) {
                BlockedApplication app = selection.toModel();
                if (apps.put(StudyDataSnapshot.pathKey(app.executablePath().toString()), app) != null) reject("Duplicate application path.");
            }
            Map<UUID, Classroom> classes = new HashMap<>(); data.classrooms().forEach(c -> classes.put(c.id(), c));
            for (var workspace : data.workspaces()) {
                unique(workspace.resourceIds(), Function.identity(), "workspace resource selection");
                unique(workspace.blockedPaths(), StudyDataSnapshot::pathKey, "workspace application selection");
                unique(workspace.shortcuts(), a -> StudyDataSnapshot.pathKey(a.executablePath()), "workspace shortcut");
                reference(classrooms, workspace.classroomId(), "workspace classroom");
                workspace.toModel(apps).validateAgainst(classes.get(workspace.classroomId()));
            }
        } catch (IllegalArgumentException | NullPointerException e) { throw new DataImportException("Invalid study data: " + e.getMessage(), e); }
    }
    private static <T, K> Set<K> unique(List<T> values, Function<T, K> key, String type) {
        Set<K> keys = new HashSet<>();
        for (T value : values) if (!keys.add(Objects.requireNonNull(key.apply(value)))) reject("Duplicate " + type + " identifier.");
        return keys;
    }
    private static <K> void reference(Set<K> known, K key, String field) { if (!known.contains(key)) reject("Unknown " + field + "."); }
    private static void reject(String message) { throw new DataImportException(message); }
}
