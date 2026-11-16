package com.studymode.platform.windows;

import com.studymode.application.error.FocusModeException;
import com.studymode.application.port.DistractionBlocker;
import com.studymode.domain.workspace.BlockedApplication;
import java.io.IOException;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Best-effort foreground-app close monitor. Closing is always graceful, never forced. */
public final class WindowsDistractionBlocker implements DistractionBlocker {
    private final boolean dryRun;
    private final Clock clock;
    private final Supplier<WindowsProcessGateway> factory;
    private final WindowsProcessSafetyPolicy policy;
    private final ScheduledExecutorService worker;
    private final List<String> log=new ArrayList<>();
    private final AtomicLong generation=new AtomicLong();
    private volatile boolean running;
    private volatile Set<BlockedApplication> targets=Set.of();
    private volatile ScheduledFuture<?> future;
    private volatile WindowsProcessGateway gateway;
    private final java.util.concurrent.atomic.AtomicBoolean starting=new java.util.concurrent.atomic.AtomicBoolean();
    private final long intervalMillis;
    public WindowsDistractionBlocker(boolean dryRun,Clock clock) {
        this(dryRun,clock,JnaWindowsProcessGateway::new,new WindowsProcessSafetyPolicy(),1000);
    }
    public WindowsDistractionBlocker(boolean dryRun,Clock clock,Supplier<WindowsProcessGateway> factory,
            WindowsProcessSafetyPolicy policy,long intervalMillis) {
        this.dryRun=dryRun; this.clock=clock; this.factory=factory; this.policy=policy; this.intervalMillis=intervalMillis;
        worker=Executors.newSingleThreadScheduledExecutor(r -> { var t=new Thread(r,"studymode-windows-monitor"); t.setDaemon(true); return t; });
    }
    @Override public String mode() { return dryRun ? "WINDOWS DRY RUN" : "WINDOWS CLOSE"; }
    @Override public void validateTarget(BlockedApplication target) {
        try {
            Path path=target.executablePath().toRealPath();
            if(!Files.isRegularFile(path) || path.toString().startsWith("\\")) throw new FocusModeException("Select an existing local executable file.");
            if(policy.protectedExecutable(path)) throw new FocusModeException("Study Mode, its runtime, shells and protected Windows executables cannot be blocked.");
        } catch(IOException | SecurityException e) { throw new FocusModeException("Cannot verify the selected executable: "+target.executablePath(),e); }
    }
    @Override public void start(Set<BlockedApplication> selected) {
        if(running || !starting.compareAndSet(false,true)) throw new FocusModeException("Windows monitoring is already active or starting.");
        long token=generation.incrementAndGet();
        try {
            Set<BlockedApplication> enabled=new HashSet<>();
            for(var target:selected) if(target.enabled()) { validateTarget(target); enabled.add(target); }
            if(enabled.isEmpty()) { targets=Set.of(); record("No enabled targets; monitor inactive."); return; }
            WindowsProcessGateway opened=factory.get();
            // Native initialization must succeed before enabling any monitoring.
            opened.prepareScan();
            Set<String> attempted=new HashSet<>();
            synchronized(this) {
                if(generation.get()!=token) return;
                gateway=opened; targets=Set.copyOf(enabled); running=true;
                record(mode()+" started for "+targets.size()+" verified selection(s). Pause/Stop cancels monitoring.");
                future=worker.scheduleWithFixedDelay(() -> scan(token,opened,attempted),0,intervalMillis,TimeUnit.MILLISECONDS);
            }
        } catch(RuntimeException | LinkageError e) {
            running=false; targets=Set.of(); record("Monitoring unavailable: "+e.getMessage());
            throw new FocusModeException("Windows monitoring could not start: "+e.getMessage()+". Choose simulated mode or fix access; no privileges were changed.",e);
        } finally { starting.set(false); }
    }
    private void scan(long token,WindowsProcessGateway opened,Set<String> attempted) {
        if(!allowed(token)) return;
        try {
            opened.prepareScan(); int inaccessible=0; String firstFailure="";
            for(long pid:opened.processIds()) {
                if(!allowed(token)) return;
                WindowsProcessIdentity process;
                try { process=opened.inspect(pid); }
                catch(RuntimeException e) { inaccessible++; if(firstFailure.isEmpty()) firstFailure=e.getMessage(); continue; }
                boolean selected=false;
                for(var target:targets) {
                    try { if(Files.isSameFile(target.executablePath(),process.executable())) { selected=true; break; } }
                    catch(IOException | SecurityException e) { record("Target unavailable: "+target.executablePath()+" — "+e.getMessage()); }
                }
                if(!selected) continue; // Never act based on an executable name.
                String key=process.pid()+":"+process.created();
                if(attempted.contains(key)) continue;
                String refusal=policy.refusal(process,opened.currentOwnerSid(),opened.currentSession());
                if(!refusal.isEmpty()) { attempted.add(key); record("REFUSED PID "+pid+": "+refusal); continue; }
                if(!allowed(token)) return;
                try {
                    if(dryRun) record("DRY RUN detected verified PID "+pid+" at "+process.executable()+"; no close sent.");
                    else {
                        int windows=opened.requestClose(process,() -> allowed(token));
                        record(windows==0 ? "No visible closable windows for PID "+pid+"; no force-close attempted."
                                : "Requested graceful close of PID "+pid+" ("+windows+" window(s)); the app may prompt, ignore or remain open.");
                    }
                    attempted.add(key);
                } catch(RuntimeException e) { attempted.add(key); record("Close refused/failed for PID "+pid+": "+e.getMessage()); }
            }
            if(inaccessible>0) record("Skipped "+inaccessible+" inaccessible/exited process identities. "+firstFailure);
        } catch(RuntimeException | LinkageError e) {
            synchronized(this) {
                if(allowed(token)) { running=false; generation.incrementAndGet(); var f=future; if(f!=null) f.cancel(false);
                    record("MONITOR FAILED — no further close requests: "+e.getMessage()); }
            }
        }
    }
    private boolean allowed(long token) { return running && generation.get()==token && !Thread.currentThread().isInterrupted(); }
    @Override public synchronized void stop() {
        running=false; generation.incrementAndGet(); var f=future; if(f!=null) f.cancel(false);
        targets=Set.of(); record("Monitoring stopped. An already dispatched close request cannot be recalled.");
    }
    @Override public boolean isRunning() { return running; }
    @Override public Set<BlockedApplication> selectedTargets() { return targets; }
    @Override public synchronized List<String> events() { return List.copyOf(log); }
    private synchronized void record(String value) {
        String message=clock.instant()+" "+value;
        if(!log.isEmpty() && log.getLast().substring(log.getLast().indexOf(' ')+1).equals(value)) return;
        if(log.size()==200) log.removeFirst(); log.add(message);
    }
    @Override public void close() { stop(); worker.shutdownNow(); }
}
