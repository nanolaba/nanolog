package com.nanolaba.logging;

import java.util.function.Supplier;

public interface ILogger {

    void log(LogEntry entry);

    default boolean isEnabled(LogEntry.LogEntryLevel level, Supplier<Class> sourceCLass) {
        return true;
    }
}
