package com.studymode.platform.windows;
import java.util.List;
import java.util.function.BooleanSupplier;
/** Native boundary: deterministic fakes exercise the same monitor and safety decisions. */
public interface WindowsProcessGateway {
    void prepareScan();
    List<Long> processIds();
    WindowsProcessIdentity inspect(long pid);
    int requestClose(WindowsProcessIdentity expected, BooleanSupplier stillAllowed);
    String currentOwnerSid();
    int currentSession();
}
