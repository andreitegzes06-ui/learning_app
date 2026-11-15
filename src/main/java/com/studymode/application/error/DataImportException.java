package com.studymode.application.error;

/** An invalid/incompatible snapshot or unsafe import attempt. Existing records are untouched. */
public final class DataImportException extends RuntimeException {
    public DataImportException(String message) { super(message); }
    public DataImportException(String message, Throwable cause) { super(message, cause); }
}
