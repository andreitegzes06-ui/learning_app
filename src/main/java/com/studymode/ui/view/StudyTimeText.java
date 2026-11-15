package com.studymode.ui.view;

/** Shared display formatting; never changes or rounds up recorded focus seconds. */
public final class StudyTimeText {
    private StudyTimeText() { }
    public static String duration(long seconds) { return "%dh %02dm %02ds".formatted(seconds / 3600, (seconds % 3600) / 60, seconds % 60); }
    public static String countdown(long remainingNanos) {
        long seconds = remainingNanos <= 0 ? 0 : (remainingNanos - 1) / 1_000_000_000L + 1;
        return "%02d:%02d".formatted(seconds / 60, seconds % 60);
    }
}
