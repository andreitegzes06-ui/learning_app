package com.studymode.application.data;

import com.studymode.domain.classroom.Classroom;
import com.studymode.domain.planning.StudyGoal;
import com.studymode.domain.profile.UserProfile;
import java.util.*;

public record FocusSessionOptions(Optional<UserProfile> profile, List<Classroom> classrooms, List<StudyGoal> goals) {
    public FocusSessionOptions { classrooms = List.copyOf(classrooms); goals = List.copyOf(goals); }
}
