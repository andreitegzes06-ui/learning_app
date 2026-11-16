package com.studymode.platform.windows;

import com.studymode.application.error.FocusModeException;
import com.studymode.domain.workspace.BlockedApplication;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

class WindowsDistractionBlockerTest {
    @TempDir Path root;
    private Path executable;
    private BlockedApplication app;
    @BeforeEach void setup() throws Exception { executable=Files.createFile(root.resolve("Selected.exe")); app=new BlockedApplication("Selected",executable); }
    private WindowsProcessIdentity identity(long pid,Path path) { return new WindowsProcessIdentity(pid,1,path,"owner",1,false,false,true,false); }
    private WindowsDistractionBlocker monitor(boolean dryRun,FakeWindows gateway) {
        return new WindowsDistractionBlocker(dryRun,Clock.systemUTC(),() -> gateway,new WindowsProcessSafetyPolicy(),20);
    }
    @Test void dryRunDetectsVerifiedExactPathWithoutClosingAndStopsCleanly() throws Exception {
        var gateway=new FakeWindows(); gateway.identities.put(900001L,identity(900001,executable));
        try(var monitor=monitor(true,gateway)) {
            monitor.start(Set.of(app)); await(() -> monitor.events().stream().anyMatch(e -> e.contains("DRY RUN detected")));
            assertEquals(0,gateway.requests.get()); assertTrue(monitor.isRunning());
            assertThrows(FocusModeException.class,() -> monitor.start(Set.of(app)));
            monitor.stop(); assertFalse(monitor.isRunning()); assertTrue(monitor.selectedTargets().isEmpty());
        }
    }
    @Test void sameExecutableNameAtDifferentPathIsNeverClosed() throws Exception {
        Path other=Files.createDirectories(root.resolve("other")).resolve("Selected.exe"); Files.createFile(other);
        var gateway=new FakeWindows(); gateway.identities.put(900001L,identity(900001,other));
        try(var monitor=monitor(false,gateway)) {
            monitor.start(Set.of(app)); await(() -> gateway.inspections.get()>2); monitor.stop(); assertEquals(0,gateway.requests.get());
        }
    }
    @Test void refusesCriticalServiceForeignOwnerForeignSessionNoninteractiveAndProtectedLineage() throws Exception {
        var gateway=new FakeWindows();
        gateway.identities.put(900001L,new WindowsProcessIdentity(900001,1,executable,"owner",1,true,false,true,false));
        gateway.identities.put(900002L,new WindowsProcessIdentity(900002,1,executable,"owner",1,false,true,true,false));
        gateway.identities.put(900003L,new WindowsProcessIdentity(900003,1,executable,"other",1,false,false,true,false));
        gateway.identities.put(900004L,new WindowsProcessIdentity(900004,1,executable,"owner",2,false,false,true,false));
        gateway.identities.put(900005L,new WindowsProcessIdentity(900005,1,executable,"owner",1,false,false,false,false));
        gateway.identities.put(900006L,new WindowsProcessIdentity(900006,1,executable,"owner",1,false,false,true,true));
        gateway.identities.put(ProcessHandle.current().pid(),identity(ProcessHandle.current().pid(),executable));
        try(var monitor=monitor(false,gateway)) {
            monitor.start(Set.of(app)); await(() -> monitor.events().stream().filter(e -> e.contains("REFUSED")).count()==7);
            assertEquals(0,gateway.requests.get());
        }
    }
    @Test void permissionFailureIsReportedWithoutForceOrMonitorCrash() throws Exception {
        var gateway=new FakeWindows(); gateway.identities.put(900001L,identity(900001,executable)); gateway.denyClose=true;
        try(var monitor=monitor(false,gateway)) {
            monitor.start(Set.of(app)); await(() -> monitor.events().stream().anyMatch(e -> e.contains("Permission denied")));
            assertTrue(monitor.isRunning()); assertEquals(0,gateway.requests.get());
        }
    }
    @Test void processExitDuringInspectionIsSkipped() throws Exception {
        var gateway=new FakeWindows(); gateway.identities.put(900001L,identity(900001,executable)); gateway.denyInspection=true;
        try(var monitor=monitor(false,gateway)) {
            monitor.start(Set.of(app)); await(() -> monitor.events().stream().anyMatch(e -> e.contains("inaccessible/exited")));
            assertEquals(0,gateway.requests.get()); assertTrue(monitor.isRunning());
        }
    }
    @Test void monitorFailureStopsFurtherRequests() throws Exception {
        var gateway=new FakeWindows(); gateway.failMonitoring=true;
        try(var monitor=monitor(false,gateway)) {
            monitor.start(Set.of(app)); await(() -> monitor.events().stream().anyMatch(e -> e.contains("MONITOR FAILED")));
            assertFalse(monitor.isRunning()); assertEquals(0,gateway.requests.get());
        }
    }
    @Test void stopCancelsAnInFlightScanBeforeDispatch() throws Exception {
        var gateway=new FakeWindows(); gateway.identities.put(900001L,identity(900001,executable)); gateway.holdDispatch=true;
        try(var monitor=monitor(false,gateway)) {
            monitor.start(Set.of(app)); assertTrue(gateway.dispatchEntered.await(3,TimeUnit.SECONDS)); monitor.stop();
            gateway.release.countDown(); await(() -> gateway.dispatchReturned.get());
            assertEquals(0,gateway.requests.get()); assertFalse(monitor.isRunning());
        } finally { gateway.release.countDown(); }
    }
    @Test void pidReuseOrIdentityChangeRefusesClose() throws Exception {
        var gateway=new FakeWindows(); gateway.identities.put(900001L,identity(900001,executable)); gateway.changeIdentity=true;
        try(var monitor=monitor(false,gateway)) {
            monitor.start(Set.of(app)); await(() -> monitor.events().stream().anyMatch(e -> e.contains("identity changed")));
            assertEquals(0,gateway.requests.get());
        }
    }
    @Test void disabledMissingAndProtectedSelectionsCannotStartMonitoring() throws Exception {
        var gateway=new FakeWindows();
        try(var monitor=monitor(false,gateway)) {
            monitor.start(Set.of(app.withEnabled(false))); assertFalse(monitor.isRunning()); assertEquals(0,gateway.preparations.get());
            assertThrows(FocusModeException.class,() -> monitor.start(Set.of(new BlockedApplication("Missing",root.resolve("missing.exe")))));
            Path protectedFile=Files.createFile(root.resolve("java.exe"));
            assertThrows(FocusModeException.class,() -> monitor.start(Set.of(new BlockedApplication("Runtime",protectedFile))));
            assertFalse(monitor.isRunning());
        }
    }
    @Test void unsupportedOrUnavailableNativeRuntimeProducesUsefulFailure() {
        try(var monitor=new WindowsDistractionBlocker(false,Clock.systemUTC(),() -> { throw new FocusModeException("Unsupported operating system"); },new WindowsProcessSafetyPolicy(),20)) {
            var error=assertThrows(FocusModeException.class,() -> monitor.start(Set.of(app)));
            assertTrue(error.getMessage().contains("Unsupported operating system")); assertFalse(monitor.isRunning());
        }
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while(!condition.getAsBoolean() && System.nanoTime()<deadline) Thread.sleep(10);
        assertTrue(condition.getAsBoolean(),"Timed out waiting for monitor result");
    }
    private static final class FakeWindows implements WindowsProcessGateway {
        final Map<Long,WindowsProcessIdentity> identities=new ConcurrentHashMap<>();
        final AtomicInteger requests=new AtomicInteger(),preparations=new AtomicInteger(),inspections=new AtomicInteger();
        final CountDownLatch dispatchEntered=new CountDownLatch(1),release=new CountDownLatch(1);
        final AtomicBoolean dispatchReturned=new AtomicBoolean();
        volatile boolean denyClose,denyInspection,failMonitoring,holdDispatch,changeIdentity;
        public void prepareScan() { if(preparations.incrementAndGet()>1&&failMonitoring) throw new FocusModeException("Native enumeration failure"); }
        public List<Long> processIds() { return List.copyOf(identities.keySet()); }
        public WindowsProcessIdentity inspect(long pid) { inspections.incrementAndGet(); if(denyInspection) throw new FocusModeException("Process exited / permission denied"); return identities.get(pid); }
        public String currentOwnerSid() { return "owner"; }
        public int currentSession() { return 1; }
        public int requestClose(WindowsProcessIdentity expected,BooleanSupplier allowed) {
            dispatchEntered.countDown();
            try {
                if(holdDispatch) assertTrue(release.await(3,TimeUnit.SECONDS));
                if(!allowed.getAsBoolean()) return 0;
                if(denyClose) throw new FocusModeException("Permission denied");
                if(changeIdentity) throw new FocusModeException("Process identity changed");
                requests.incrementAndGet(); return 1;
            } catch(InterruptedException e) { Thread.currentThread().interrupt(); return 0; }
            finally { dispatchReturned.set(true); }
        }
    }
}
