package com.studymode.domain.profile;

import com.studymode.domain.Validation;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

public record UserProfile(UUID id, String displayName, Instant createdAt, ZoneId studyZone) {
    public UserProfile {
        Validation.required(id, "Profile ID");
        displayName = Validation.text(displayName, "Display name", 60);
        Validation.required(createdAt, "Creation time");
        Validation.required(studyZone, "Study time zone");
    }

    public UserProfile withDetails(String name, ZoneId zone) {
        return new UserProfile(id, name, createdAt, zone);
    }
}
