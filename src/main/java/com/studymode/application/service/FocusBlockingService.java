package com.studymode.application.service;

import com.studymode.application.port.*;
import com.studymode.application.error.FocusModeException;
import com.studymode.domain.workspace.BlockedApplication;
import java.util.*;

/** Keeps one session's authorized membership, with live disabling/removal and no silent new targets. */
public final class FocusBlockingService implements AutoCloseable {
    private final DistractionBlocker blocker;
    private final BlockedApplicationCatalog catalog;
    private volatile Set<BlockedApplication> approved=Set.of(), active=Set.of();
    private volatile boolean expected;
    private volatile boolean suspended=true;
    private String scope="global";
    public FocusBlockingService(DistractionBlocker blocker,BlockedApplicationCatalog catalog) { this.blocker=blocker; this.catalog=catalog; }
    public void prepare(Set<BlockedApplication> requested,boolean authorized,String scope) {
        Set<BlockedApplication> found=new HashSet<>();
        for(var target:requested) catalog.find(target.executablePath()).filter(BlockedApplication::enabled).ifPresent(found::add);
        if(!found.isEmpty() && blocker.mode().equals("WINDOWS CLOSE") && !authorized)
            throw new FocusModeException("Authorize graceful closing of the selected applications before starting, or launch with blocker=windows-dry-run.");
        found.forEach(blocker::validateTarget); approved=Set.copyOf(found); active=Set.of(); expected=false; suspended=false; this.scope=scope;
    }
    public Set<BlockedApplication> globalTargets() { return Set.copyOf(catalog.findAll()); }
    public void begin() {
        suspended=false;
        active=enabled(); expected=!active.isEmpty();
        if(expected) { try { blocker.start(active); } catch(RuntimeException e) { expected=false; blocker.stop(); throw e; } }
        if(suspended) blocker.stop();
    }
    public void refresh() {
        if(suspended) return;
        if(expected && !blocker.isRunning()) { expected=false; throw new FocusModeException("Application monitoring stopped unexpectedly. Focus is paused. Inspect blocking status and Resume to retry."); }
        var selected=enabled();
        if(!selected.equals(active)) { blocker.stop(); active=selected; expected=!selected.isEmpty(); if(expected) blocker.start(selected); }
    }
    private Set<BlockedApplication> enabled() {
        Set<BlockedApplication> result=new HashSet<>();
        for(var target:approved) catalog.find(target.executablePath()).filter(BlockedApplication::enabled).ifPresent(result::add);
        return Set.copyOf(result);
    }
    public void stop() { suspended=true; expected=false; blocker.stop(); active=Set.of(); }
    public String status() {
        var events=blocker.events();
        return blocker.mode()+" · "+(blocker.isRunning()?"monitoring "+blocker.selectedTargets().size()+" target(s)":"inactive")+" · "+scope
                +"\n"+(events.isEmpty()?"Pause stops monitoring; Resume restarts authorized targets.":events.getLast());
    }
    public List<String> events() { return blocker.events(); }
    @Override public void close() { stop(); blocker.close(); }
}
