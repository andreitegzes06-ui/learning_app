package com.studymode.application.port;
import com.studymode.application.data.StudyDataSnapshot;
import java.io.IOException;
import java.nio.file.Path;
public interface StudyDataTransfer {
    void exportTo(Path file) throws IOException;
    StudyDataSnapshot inspectImport(Path file) throws IOException;
    Path importSnapshot(StudyDataSnapshot validatedData) throws IOException;
}
