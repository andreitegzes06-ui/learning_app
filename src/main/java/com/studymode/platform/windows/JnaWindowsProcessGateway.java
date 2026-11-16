package com.studymode.platform.windows;

import com.studymode.application.error.FocusModeException;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.util.function.BooleanSupplier;

/** In-process native queries and WM_CLOSE only. No termination rights or privilege changes. */
public final class JnaWindowsProcessGateway implements WindowsProcessGateway {
    public interface CriticalProcessQuery extends StdCallLibrary {
        boolean IsProcessCritical(WinNT.HANDLE process,IntByReference critical);
    }
    public interface WindowCloseDispatch extends StdCallLibrary {
        boolean PostMessage(WinDef.HWND window,int message,WinDef.WPARAM word,WinDef.LPARAM value);
    }
    private final CriticalProcessQuery critical;
    private final WindowCloseDispatch dispatch;
    private final String owner;
    private final int session;
    private Set<Long> services=Set.of();
    public JnaWindowsProcessGateway() {
        if (!System.getProperty("os.name","").startsWith("Windows")) throw new FocusModeException("Windows application monitoring is unsupported on this operating system.");
        critical=Native.load("kernel32",CriticalProcessQuery.class,W32APIOptions.DEFAULT_OPTIONS);
        dispatch=Native.load("user32",WindowCloseDispatch.class,W32APIOptions.DEFAULT_OPTIONS);
        owner=inspectToken(Kernel32.INSTANCE.GetCurrentProcess()).owner();
        session=session(Kernel32.INSTANCE.GetCurrentProcessId());
    }
    private record TokenIdentity(String owner,boolean interactive,boolean service) { }
    private TokenIdentity inspectToken(WinNT.HANDLE process) {
        WinNT.HANDLEByReference ref=new WinNT.HANDLEByReference();
        if (!Advapi32.INSTANCE.OpenProcessToken(process,WinNT.TOKEN_QUERY,ref)) throw failure("read process ownership");
        try {
            String sid=Advapi32Util.getTokenAccount(ref.getValue()).sidString;
            var groups=Arrays.stream(Advapi32Util.getTokenGroups(ref.getValue())).map(a -> a.sidString).toList();
            return new TokenIdentity(sid,groups.contains("S-1-5-4"),groups.contains("S-1-5-6"));
        } finally { Kernel32.INSTANCE.CloseHandle(ref.getValue()); }
    }
    @Override public void prepareScan() {
        Set<Long> found=new HashSet<>();
        try(var manager=new W32ServiceManager()) {
            manager.open(Winsvc.SC_MANAGER_ENUMERATE_SERVICE);
            for(var service:manager.enumServicesStatusExProcess(WinNT.SERVICE_WIN32,Winsvc.SERVICE_STATE_ALL,null))
                if(service.ServiceStatusProcess.dwProcessId!=0) found.add(Integer.toUnsignedLong(service.ServiceStatusProcess.dwProcessId));
        }
        services=Set.copyOf(found);
    }
    @Override public List<Long> processIds() { try(var all=ProcessHandle.allProcesses()) { return all.map(ProcessHandle::pid).toList(); } }
    @Override public String currentOwnerSid() { return owner; }
    @Override public int currentSession() { return session; }
    private int session(int pid) {
        var value=new IntByReference(); if(!Kernel32.INSTANCE.ProcessIdToSessionId(pid,value)) throw failure("verify Windows session"); return value.getValue();
    }
    @Override public WindowsProcessIdentity inspect(long pid) {
        WinNT.HANDLE handle=Kernel32.INSTANCE.OpenProcess(0x1000,false,(int)pid);
        if(handle==null) throw failure("inspect PID "+pid+" (it may have exited or access was denied)");
        try {
            char[] name=new char[32768]; var size=new IntByReference(name.length);
            if(!Kernel32.INSTANCE.QueryFullProcessImageName(handle,0,name,size)) throw failure("verify executable path");
            Path path;
            try { path=Path.of(new String(name,0,size.getValue())).toRealPath(); }
            catch(IOException e) { throw new FocusModeException("Executable path cannot be verified for PID "+pid,e); }
            var created=new WinBase.FILETIME(); var exit=new WinBase.FILETIME(); var kernel=new WinBase.FILETIME(); var user=new WinBase.FILETIME();
            if(!Kernel32.INSTANCE.GetProcessTimes(handle,created,exit,kernel,user)) throw failure("verify process creation time");
            var isCritical=new IntByReference();
            if(!critical.IsProcessCritical(handle,isCritical)) throw failure("verify critical process status");
            var token=inspectToken(handle);
            boolean protectedLineage=false;
            var process=ProcessHandle.of(pid).orElseThrow(() -> new FocusModeException("Process exited while inspecting PID "+pid));
            var parent=process.parent(); Set<Long> seen=new HashSet<>();
            while(parent.isPresent() && seen.add(parent.get().pid())) {
                    var ancestor=parent.get();
                    var command=ancestor.info().command();
                    if(services.contains(ancestor.pid()) || command.isEmpty()) { protectedLineage=true; break; }
                    String file=Path.of(command.get()).getFileName().toString().toLowerCase(Locale.ROOT);
                    if(Set.of("services.exe","svchost.exe").contains(file)) { protectedLineage=true; break; }
                    parent=ancestor.parent();
            }
            long stamp=(Integer.toUnsignedLong(created.dwHighDateTime)<<32)|Integer.toUnsignedLong(created.dwLowDateTime);
            return new WindowsProcessIdentity(pid,stamp,path,token.owner(),session((int)pid),isCritical.getValue()!=0,
                    token.service()||services.contains(pid),token.interactive(),protectedLineage);
        } finally { Kernel32.INSTANCE.CloseHandle(handle); }
    }
    @Override public int requestClose(WindowsProcessIdentity expected,BooleanSupplier stillAllowed) {
        prepareScan();
        // Reopen and revalidate immediately before window dispatch; PID alone is never identity.
        if(!stillAllowed.getAsBoolean() || !expected.equals(inspect(expected.pid()))) throw new FocusModeException("Process identity changed or blocking stopped; close refused.");
        int[] requested={0}; RuntimeException[] failure={null};
        boolean enumerated=User32.INSTANCE.EnumWindows((window,data) -> {
          try {
            if(!stillAllowed.getAsBoolean()) return false;
            var pid=new IntByReference(); User32.INSTANCE.GetWindowThreadProcessId(window,pid);
            if(Integer.toUnsignedLong(pid.getValue())==expected.pid() && User32.INSTANCE.IsWindowVisible(window)) {
                if(!expected.equals(inspect(expected.pid())) || !stillAllowed.getAsBoolean()) return false;
                if(!dispatch.PostMessage(window,0x0010,new WinDef.WPARAM(0),new WinDef.LPARAM(0))) throw failure("request graceful window close");
                requested[0]++;
            }
            return true;
          } catch(RuntimeException e) { failure[0]=e; return false; }
        },null);
        if(failure[0]!=null) throw failure[0];
        if(!enumerated && stillAllowed.getAsBoolean()) throw failure("enumerate application windows");
        return requested[0];
    }
    private FocusModeException failure(String action) { return new FocusModeException("Could not "+action+" (Windows error "+Kernel32.INSTANCE.GetLastError()+"). No force-close was attempted."); }
}
