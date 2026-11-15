package com.studymode.application.port;
import com.studymode.domain.reward.*;
import java.util.*;
public interface AchievementStore {
    List<Achievement> definitions();
    List<AchievementAward> awards();
    void save(Achievement definition);
    void save(AchievementAward award);
    boolean deleteDefinition(String code);
    boolean deleteAward(UUID profileId, String code);
}
