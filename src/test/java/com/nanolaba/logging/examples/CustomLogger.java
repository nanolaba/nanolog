package com.nanolaba.logging.examples;

import com.nanolaba.logging.ILogger;
import com.nanolaba.logging.LogEntry;

// nrg:begin:custom
public class CustomLogger implements ILogger {

    @Override
    public void log(LogEntry entry) {
        // write the entry wherever you need: a file, a socket, a queue, ...
        System.out.println(entry.getLevel() + " " + entry.getFormattedMessage());
    }

    @Override
    public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
        // called before the LogEntry is built and before any Supplier is evaluated:
        // return false to suppress entries of this level for this class
        return level != LogEntry.LogEntryLevel.TRACE;
    }

    @Override
    public boolean mayBeEnabled(LogEntry.LogEntryLevel level) {
        // asked before the source class is detected: false spares the stack walk too,
        // so return false only for a level that isEnabled rejects for every class
        return level != LogEntry.LogEntryLevel.TRACE;
    }
}
// nrg:end:custom
