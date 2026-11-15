package com.studymode.application.port;
import com.studymode.domain.workspace.FocusWorkspace;
import java.util.*;
public interface FocusWorkspaceStore {
    List<FocusWorkspace> findAll();
    Optional<FocusWorkspace> find(UUID id);
    void save(FocusWorkspace workspace);
    boolean delete(UUID id);
}
