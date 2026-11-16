package com.studymode.platform.windows;
import java.nio.file.Path;
/** All native facts must be available before a close request is considered. */
public record WindowsProcessIdentity(long pid,long created,Path executable,String ownerSid,int session,
        boolean critical,boolean service,boolean interactive,boolean protectedLineage) { }
