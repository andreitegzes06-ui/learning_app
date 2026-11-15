package com.studymode.domain.planning;

import com.studymode.domain.Validation;
import java.util.UUID;

public record StudyGoal(UUID id, UUID profileId, String text, int targetMinutes, GoalPeriod period) {
    public StudyGoal {
        Validation.required(id, "Goal ID"); Validation.required(profileId, "Profile ID");
        text = Validation.text(text, "Goal", 200);
        Validation.range(targetMinutes, 1, 10080, "Target minutes");
        Validation.required(period, "Goal period");
    }
}
