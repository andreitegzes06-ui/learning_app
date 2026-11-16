package com.studymode.application.service;

import com.studymode.application.port.*;
import com.studymode.application.error.FocusModeException;
import com.studymode.domain.workspace.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** User selection preferences; no process operation is triggered by adding/importing a record. */
public final class BlockedApplicationService {
    private final BlockedApplicationCatalog catalog;
    private final DistractionBlocker blocker;
    public BlockedApplicationService(BlockedApplicationCatalog catalog,DistractionBlocker blocker) { this.catalog=catalog; this.blocker=blocker; }
    public List<BlockedApplication> applications() { return catalog.findAll(); }
    public BlockedApplication add(String name,Path path) {
        try {
            var value=new BlockedApplication(name,path.toRealPath());
            if(!Files.isRegularFile(value.executablePath())) throw new FocusModeException("Select an existing executable file.");
            blocker.validateTarget(value);
            if(catalog.find(value.executablePath()).isPresent()) throw new FocusModeException("This executable is already in your blocklist.");
            for(var previous:catalog.findAll()) if(Files.exists(previous.executablePath()) && Files.isSameFile(previous.executablePath(),value.executablePath()))
                throw new FocusModeException("This executable is already selected through another path.");
            catalog.save(value); return value;
        } catch(IOException | SecurityException e) { throw new FocusModeException("Cannot read the selected executable: "+e.getMessage(),e); }
    }
    public void setEnabled(Path path,boolean enabled) {
        var value=catalog.find(path).orElseThrow(() -> new FocusModeException("This blocklist selection no longer exists. Reload."));
        catalog.save(value.withEnabled(enabled));
    }
    public void remove(Path path) {
        if(catalog.find(path).isEmpty()) throw new FocusModeException("This blocklist selection no longer exists. Reload.");
        catalog.delete(path);
    }
}
