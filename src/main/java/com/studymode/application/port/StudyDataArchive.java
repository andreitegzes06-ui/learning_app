package com.studymode.application.port;
import com.studymode.application.data.StudyDataSnapshot;
import java.util.function.Consumer;
public interface StudyDataArchive {
    StudyDataSnapshot snapshot();
    /** Atomically replace records; backup must complete before any DELETE executes. */
    void replace(StudyDataSnapshot data, Consumer<StudyDataSnapshot> backupWriter);
}
