package com.studymode.application.error;

public class StudyDataException extends RuntimeException {
    public StudyDataException(String message, Throwable cause) { super(message, cause); }
    public StudyDataException(String message) { super(message); }
}
