package com.studymode.application.port;

/** Nonblocking delivery. Implementations must enqueue work and never hold up timer persistence. */
@FunctionalInterface
public interface StudyNotifications {
    enum Event { FOCUS_COMPLETED, BREAK_COMPLETED, ACHIEVEMENT_UNLOCKED }
    void publish(Event event, String detail, boolean sound);
}
