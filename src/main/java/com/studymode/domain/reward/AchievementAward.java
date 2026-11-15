package com.studymode.domain.reward;

import com.studymode.domain.Validation;
import java.time.Instant;
import java.util.UUID;

public record AchievementAward(UUID profileId, String achievementCode, Instant awardedAt) {
    public AchievementAward {
        Validation.required(profileId, "Profile ID");
        achievementCode = Validation.text(achievementCode, "Achievement code", 60);
        if (!achievementCode.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Invalid achievement code.");
        Validation.required(awardedAt, "Award time");
    }
}
