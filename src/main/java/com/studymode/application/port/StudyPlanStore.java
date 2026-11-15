package com.studymode.application.port;
import com.studymode.domain.planning.*;
import java.util.*;
public interface StudyPlanStore {
    List<StudyGoal> goals();
    List<StudyTask> tasks();
    Optional<StudyGoal> findGoal(UUID id);
    Optional<StudyTask> findTask(UUID id);
    void save(StudyGoal goal);
    void save(StudyTask task);
    boolean deleteGoal(UUID id);
    boolean deleteTask(UUID id);
}
