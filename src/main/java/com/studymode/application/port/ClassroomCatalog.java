package com.studymode.application.port;
import com.studymode.domain.classroom.Classroom;
import java.util.*;
public interface ClassroomCatalog {
    List<Classroom> findAll();
    Optional<Classroom> find(UUID id);
    void save(Classroom classroom);
    boolean delete(UUID id);
}
