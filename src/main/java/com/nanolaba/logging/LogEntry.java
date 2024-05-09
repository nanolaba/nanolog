package com.nanolaba.logging;

import java.util.Arrays;

public class LogEntry {

    public enum LogEntryLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    private final LogEntryLevel level;
    private final Class sourceClass;
    private final Throwable throwable;
    private final Object message;
    private final Object[] args;

    public LogEntry(LogEntryLevel level, Class sourceClass, Throwable throwable, Object message, Object... args) {
        this.level = level;
        this.sourceClass = sourceClass;
        this.throwable = throwable;
        this.message = message;
        this.args = args;
    }

    public LogEntryLevel getLevel() {
        return level;
    }

    public Class getSourceClass() {
        return sourceClass;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public Object getMessage() {
        return message;
    }

    public Object[] getArgs() {
        return args;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
               "level=" + level +
               ", sourceClass=" + (sourceClass == null ? null : sourceClass.getCanonicalName()) +
               ", throwable=" + throwable +
               ", message=" + message +
               ", args=" + Arrays.toString(args) +
               '}';
    }
}
