package com.studymode.application.data;

import com.studymode.domain.profile.UserProfile;
import com.studymode.domain.classroom.Classroom;
import com.studymode.domain.workspace.*;
import com.studymode.domain.study.StudySession;
import com.studymode.domain.planning.*;
import com.studymode.domain.reward.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** Versioned portable facts; no machine settings, live blocker state or executable code. */
public record StudyDataSnapshot(int formatVersion, Instant exportedAt, List<UserProfile> profiles,
        List<StudySession> sessions, List<Classroom> classrooms, List<ApplicationSelection> applications,
        List<WorkspaceSelection> workspaces, List<StudyGoal> goals, List<StudyTask> tasks,
        List<Achievement> achievements, List<AchievementAward> awards) {
    public StudyDataSnapshot {
        Objects.requireNonNull(exportedAt, "Export time");
        profiles = List.copyOf(profiles); sessions = List.copyOf(sessions); classrooms = List.copyOf(classrooms);
        applications = List.copyOf(applications); workspaces = List.copyOf(workspaces); goals = List.copyOf(goals);
        tasks = List.copyOf(tasks); achievements = List.copyOf(achievements); awards = List.copyOf(awards);
    }
    public record ApplicationSelection(String displayName, String executablePath) {
        public BlockedApplication toModel() { return new BlockedApplication(displayName, Path.of(executablePath)); }
        public static ApplicationSelection from(BlockedApplication app) { return new ApplicationSelection(app.displayName(), app.executablePath().toString()); }
    }
    public record WorkspaceSelection(UUID id, String name, UUID classroomId, FocusPreferences preferences,
                                     List<UUID> resourceIds, List<String> blockedPaths) {
        public WorkspaceSelection { resourceIds = List.copyOf(resourceIds); blockedPaths = List.copyOf(blockedPaths); }
        public static WorkspaceSelection from(FocusWorkspace w) {
            return new WorkspaceSelection(w.id(), w.name(), w.classroomId(), w.preferences(), w.resourceIds().stream().sorted().toList(),
                    w.blocklist().stream().map(a -> a.executablePath().toString()).sorted().toList());
        }
        public FocusWorkspace toModel(Map<String, BlockedApplication> applicationsByPath) {
            Set<BlockedApplication> blocklist = new HashSet<>();
            for (String path : blockedPaths) blocklist.add(Objects.requireNonNull(applicationsByPath.get(pathKey(path)), "Unknown workspace application"));
            return new FocusWorkspace(id, name, classroomId, preferences, Set.copyOf(resourceIds), blocklist);
        }
    }
    public static String pathKey(String path) { return Path.of(path).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT); }
    public int recordCount() {
        return profiles.size() + sessions.size() + classrooms.size() + classrooms.stream().mapToInt(c -> c.resources().size()).sum()
                + applications.size() + workspaces.size() + goals.size() + tasks.size() + achievements.size() + awards.size();
    }
}
