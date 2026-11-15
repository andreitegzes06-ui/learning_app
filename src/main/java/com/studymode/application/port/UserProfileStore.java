package com.studymode.application.port;

import com.studymode.domain.profile.UserProfile;
import java.util.Optional;

public interface UserProfileStore {
    Optional<UserProfile> findLocalProfile();
    void save(UserProfile profile);
}
