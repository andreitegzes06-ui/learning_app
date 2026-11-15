package com.studymode.application.service;

import com.studymode.application.port.UserProfileStore;
import com.studymode.domain.profile.UserProfile;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

public final class ProfileService {
    private final UserProfileStore store;
    private final Clock clock;
    public ProfileService(UserProfileStore store, Clock clock) { this.store = store; this.clock = clock; }
    public Optional<UserProfile> load() { return store.findLocalProfile(); }
    public UserProfile saveDetails(String name, ZoneId zone) {
        Optional<UserProfile> existing = store.findLocalProfile();
        UserProfile profile = existing.map(p -> p.withDetails(name, zone))
                .orElseGet(() -> new UserProfile(UUID.randomUUID(), name, clock.instant(), zone));
        store.save(profile);
        return profile;
    }
}
