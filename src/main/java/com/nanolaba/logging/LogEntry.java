package com.nanolaba.logging;

import java.util.Arrays;
import java.util.function.Supplier;

public class LogEntry {

    public enum LogEntryLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    private final LogEntryLevel level;
    private final Supplier<Class> sourceClass;
    private final Throwable throwable;
    private final Supplier<Object> message;
    private final Object[] args;

    public LogEntry(LogEntryLevel level, Supplier<Class> sourceClass, Throwable throwable, Supplier<Object> message, Object... args) {
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
        return sourceClass == null ? null : sourceClass.get();
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public Object getMessage() {
        return message == null ? null : message.get();
    }

    public Object[] getArgs() {
        return args;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
               "level=" + level +
               ", sourceClass=" + (sourceClass == null ? null : (sourceClass.get() == null ? null : sourceClass.get().getCanonicalName())) +
               ", throwable=" + throwable +
               ", message=" + (message == null ? null : message.get()) +
               ", args=" + Arrays.toString(args) +
               '}';
    }
}
