package com.nanolaba.logging;

public interface ILogger {

    void log(LogEntry entry);

    default boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
        return true;
    }
}
