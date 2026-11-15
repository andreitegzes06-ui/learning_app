package com.studymode.domain.classroom;

import com.studymode.domain.Validation;
import java.util.List;
import java.util.UUID;

public record Classroom(UUID id, String name, String description, List<StudyResource> resources, String color, String notes) {
    public Classroom(UUID id, String name, String description, List<StudyResource> resources) {
        this(id, name, description, resources, "#7569EF", "");
    }
    public Classroom {
        Validation.required(id, "Classroom ID");
        name = Validation.text(name, "Classroom name", 100);
        description = Validation.description(description);
        color = Validation.text(color, "Classroom color", 7).toUpperCase(java.util.Locale.ROOT);
        if (!color.matches("#[0-9A-F]{6}")) throw new IllegalArgumentException("Choose a valid classroom color (#RRGGBB).");
        notes = Validation.description(notes);
        resources = List.copyOf(Validation.required(resources, "Resources"));
        if (resources.stream().map(StudyResource::id).distinct().count() != resources.size()) {
            throw new IllegalArgumentException("Resource IDs must be unique in a classroom.");
        }
    }
}
