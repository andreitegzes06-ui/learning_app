package com.studymode.platform.windows;

import java.nio.file.Path;
import java.util.*;

/** Conservative extra guards; native critical/owner/service checks are mandatory as well. */
public final class WindowsProcessSafetyPolicy {
    private static final Set<String> PROTECTED=Set.of("system","registry","idle","smss.exe","csrss.exe","wininit.exe",
            "winlogon.exe","services.exe","lsass.exe","lsaiso.exe","svchost.exe","dwm.exe","fontdrvhost.exe",
            "explorer.exe","sihost.exe","taskhostw.exe","taskmgr.exe","securityhealthservice.exe","msmpeng.exe",
            "java.exe","javaw.exe","studymode.exe","study-mode.exe","powershell.exe","pwsh.exe","cmd.exe",
            "conhost.exe","openconsole.exe","windowsterminal.exe");
    private final Set<Long> protectedPids;
    private final Set<Path> protectedPaths;
    public WindowsProcessSafetyPolicy() {
        var self=ProcessHandle.current(); Set<Long> pids=new HashSet<>(); Set<Path> paths=new HashSet<>();
        pids.add(self.pid()); self.info().command().ifPresent(p -> paths.add(Path.of(p).toAbsolutePath().normalize()));
        var parent=self.parent();
        while(parent.isPresent() && pids.add(parent.get().pid())) {
            parent.get().info().command().ifPresent(c -> paths.add(Path.of(c).toAbsolutePath().normalize()));
            parent=parent.get().parent();
        }
        protectedPids=Set.copyOf(pids); protectedPaths=Set.copyOf(paths);
    }
    public boolean protectedExecutable(Path path) {
        return path.getFileName()==null || PROTECTED.contains(path.getFileName().toString().toLowerCase(Locale.ROOT))
                || protectedPaths.contains(path.toAbsolutePath().normalize());
    }
    public String refusal(WindowsProcessIdentity p,String owner,int session) {
        if (p.pid()<=4 || protectedPids.contains(p.pid()) || protectedExecutable(p.executable())) return "Study Mode, runtime, shell or protected Windows process";
        if (p.critical()) return "critical process";
        if (p.service() || p.protectedLineage()) return "service or protected process lineage";
        if (!p.interactive() || p.session()<=0 || p.session()!=session) return "not the current interactive session";
        if (p.ownerSid()==null || !p.ownerSid().equals(owner)) return "different or unknown process owner";
        return "";
    }
}
