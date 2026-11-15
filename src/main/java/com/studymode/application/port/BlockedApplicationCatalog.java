package com.studymode.application.port;
import com.studymode.domain.workspace.BlockedApplication;
import java.nio.file.Path;
import java.util.*;
public interface BlockedApplicationCatalog {
    List<BlockedApplication> findAll();
    Optional<BlockedApplication> find(Path path);
    void save(BlockedApplication application);
    boolean delete(Path path);
}
