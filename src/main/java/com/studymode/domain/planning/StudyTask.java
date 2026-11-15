package com.studymode.domain.planning;

import com.studymode.domain.Validation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record StudyTask(UUID id, UUID profileId, String text, UUID classroomId, UUID goalId,
                        LocalDate dueDate, Instant completedAt) {
    public StudyTask {
        Validation.required(id, "Task ID"); Validation.required(profileId, "Profile ID");
        text = Validation.text(text, "Task", 200);
    }
    public boolean completed() { return completedAt != null; }
    public StudyTask complete(Instant at) {
        return new StudyTask(id, profileId, text, classroomId, goalId, dueDate, Validation.required(at, "Completion time"));
    }
}
