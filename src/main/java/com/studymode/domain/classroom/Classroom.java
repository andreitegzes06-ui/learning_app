package com.studymode.domain.classroom;

import com.studymode.domain.Validation;
import java.util.List;
import java.util.UUID;

public record Classroom(UUID id, String name, String description, List<StudyResource> resources) {
    public Classroom {
        Validation.required(id, "Classroom ID");
        name = Validation.text(name, "Classroom name", 100);
        description = Validation.description(description);
        resources = List.copyOf(Validation.required(resources, "Resources"));
        if (resources.stream().map(StudyResource::id).distinct().count() != resources.size()) {
            throw new IllegalArgumentException("Resource IDs must be unique in a classroom.");
        }
    }
}
