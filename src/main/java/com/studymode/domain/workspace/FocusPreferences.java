package com.studymode.domain.workspace;

import com.studymode.domain.Validation;

public record FocusPreferences(int focusMinutes, int shortBreakMinutes, int longBreakMinutes, int longBreakEvery) {
    public FocusPreferences {
        Validation.range(focusMinutes, 1, 240, "Focus minutes");
        Validation.range(shortBreakMinutes, 1, 60, "Short break minutes");
        Validation.range(longBreakMinutes, 1, 60, "Long break minutes");
        Validation.range(longBreakEvery, 1, 12, "Long break interval");
    }

    public static FocusPreferences defaults() { return new FocusPreferences(25, 5, 15, 4); }
}
