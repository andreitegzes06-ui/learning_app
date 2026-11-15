package com.studymode.domain;

import java.util.Objects;

/** Shared value validation; independent of UI and persistence. */
public final class Validation {
    private Validation() { }

    public static <T> T required(T value, String field) {
        if (value == null) throw new IllegalArgumentException(field + " is required.");
        return value;
    }

    public static String text(String value, String field, int maximum) {
        String normalized = required(value, field).strip();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > maximum
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " must contain 1–" + maximum + " characters without control characters.");
        }
        return normalized;
    }

    public static String description(String value) {
        String normalized = Objects.requireNonNullElse(value, "").strip();
        if (normalized.length() > 2000 || normalized.codePoints()
                .anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            throw new IllegalArgumentException("Description must contain at most 2000 characters.");
        }
        return normalized;
    }

    public static int range(int value, int minimum, int maximum, String field) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(field + " must be between " + minimum + " and " + maximum + ".");
        }
        return value;
    }
}
