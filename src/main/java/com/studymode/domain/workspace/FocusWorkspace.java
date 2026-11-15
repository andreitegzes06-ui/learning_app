package com.studymode.domain.workspace;

import com.studymode.domain.Validation;
import com.studymode.domain.classroom.Classroom;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record FocusWorkspace(UUID id, String name, UUID classroomId, FocusPreferences preferences,
                             Set<UUID> resourceIds, Set<BlockedApplication> blocklist, Set<BlockedApplication> shortcuts) {
    public FocusWorkspace(UUID id, String name, UUID classroomId, FocusPreferences preferences,
                          Set<UUID> resourceIds, Set<BlockedApplication> blocklist) {
        this(id, name, classroomId, preferences, resourceIds, blocklist, Set.of());
    }
    public FocusWorkspace {
        Validation.required(id, "Workspace ID");
        name = Validation.text(name, "Workspace name", 100);
        Validation.required(classroomId, "Classroom ID");
        Validation.required(preferences, "Focus preferences");
        resourceIds = Set.copyOf(Validation.required(resourceIds, "Resource IDs"));
        blocklist = Set.copyOf(Validation.required(blocklist, "Blocklist"));
        shortcuts = Set.copyOf(Validation.required(shortcuts, "Study application shortcuts"));
        if (shortcuts.stream().anyMatch(blocklist::contains))
            throw new IllegalArgumentException("A study shortcut cannot also be in the workspace blocklist.");
    }

    public void validateAgainst(Classroom classroom) {
        Validation.required(classroom, "Classroom");
        Set<UUID> available = classroom.resources().stream().map(r -> r.id()).collect(Collectors.toSet());
        if (!classroomId.equals(classroom.id()) || !available.containsAll(resourceIds)) {
            throw new IllegalArgumentException("Workspace resources must belong to its classroom.");
        }
    }
}
