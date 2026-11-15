package com.studymode.application.error;

public class FocusModeException extends RuntimeException {
    public FocusModeException(String message) { super(message); }
    public FocusModeException(String message, Throwable cause) { super(message, cause); }
}
