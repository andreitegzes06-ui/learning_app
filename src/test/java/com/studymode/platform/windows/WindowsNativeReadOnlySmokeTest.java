package com.studymode.platform.windows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native Windows smoke: metadata queries and refusal only. Never closes another application. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named="studymode.windows.tests", matches="true")
class WindowsNativeReadOnlySmokeTest {
    @Test void nativeIdentityQueriesWorkAndStudyModeRuntimeIsAlwaysRefused() {
        var gateway=new JnaWindowsProcessGateway(); gateway.prepareScan();
        long pid=ProcessHandle.current().pid(); assertTrue(gateway.processIds().contains(pid));
        var self=gateway.inspect(pid);
        assertEquals(pid,self.pid()); assertEquals(gateway.currentOwnerSid(),self.ownerSid());
        assertEquals(gateway.currentSession(),self.session()); assertTrue(self.created()>0);
        assertTrue(self.executable().isAbsolute());
        assertFalse(new WindowsProcessSafetyPolicy().refusal(self,gateway.currentOwnerSid(),gateway.currentSession()).isEmpty());
    }
}
