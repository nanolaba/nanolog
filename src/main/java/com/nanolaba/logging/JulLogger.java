package com.nanolaba.logging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Writes entries to {@code java.util.logging}, for an application that configures its logging there: each entry goes
 * to the JUL logger named after its source class, an entry without one to the root logger. Levels, handlers and
 * formatters are configured in JUL ({@code logging.properties}); {@link #isEnabled} asks it. Chosen with
 * {@code nanolog.logger=com.nanolaba.logging.JulLogger}.
 * <p>
 * The levels map to {@code FINEST}, {@code FINE}, {@code INFO}, {@code WARNING} and {@code SEVERE}. The message is
 * passed formatted, without parameters, followed by the number of calls a rate-limited statement skipped, the fields
 * of {@link LOG#kv(String, Object)} and the context, as {@link ConsoleLogger} writes them, since JUL has no place for
 * them. The record names the call site as its source class and method, so a formatter with {@code %2$s} shows the code
 * that called {@code LOG}.
 * <p>
 * Do not combine it with {@link NanologHandler}, which sends JUL to Nanolog: the two make a loop, which is reported
 * once and cut. On the module path the application needs the module {@code java.logging}.
 */
public class JulLogger implements ILogger {

    /**
     * The JUL loggers in use, held so that JUL keeps the levels set on them.
     */
    private final Map<String, Logger> loggers = new ConcurrentHashMap<>();

    /**
     * Creates a logger that writes to {@code java.util.logging}; {@code nanolog.logger} calls it.
     */
    public JulLogger() {
        // the JUL loggers are looked up when the first entry of each source class comes
    }

    /**
     * Writes the entry to the JUL logger of its source class, if JUL logs its level there.
     *
     * @param entry the entry
     */
    @Override
    public void log(LogEntry entry) {
        Logger logger = loggerOf(entry.getSourceClass());
        Level level = levelOf(entry.getLevel());
        if (!logger.isLoggable(level)) {
            return;
        }
        LogRecord record = new LogRecord(level, message(entry));
        record.setLoggerName(logger.getName());
        record.setThrown(entry.getThrowable());
        StackTraceElement caller = entry.getCaller();
        Class sourceClass = entry.getSourceClass();
        // set even when unknown: JUL would otherwise take a frame of Nanolog for the caller
        record.setSourceClassName(caller != null ? caller.getClassName() : sourceClass != null ? sourceClass.getName() : null);
        record.setSourceMethodName(caller != null ? caller.getMethodName() : null);
        logger.log(record);
    }

    private static String message(LogEntry entry) {
        StringBuilder text = new StringBuilder(entry.getMessage() == null ? "" : entry.getFormattedMessage());
        if (entry.getSkippedCount() > 0) {
            append(text, "[skipped " + entry.getSkippedCount() + "]");
        }
        for (Map.Entry<String, Object> field : entry.getTrailingFields().entrySet()) {
            append(text, ConsoleLogger.pair(field.getKey(), field.getValue()));
        }
        for (Map.Entry<String, Object> pair : entry.getContext().entrySet()) {
            append(text, ConsoleLogger.pair(pair.getKey(), pair.getValue()));
        }
        return text.toString();
    }

    private static void append(StringBuilder text, String part) {
        if (text.length() > 0) {
            text.append(' ');
        }
        text.append(part);
    }

    /**
     * Asks the JUL logger of the source class whether it logs the level.
     *
     * @param level       the level of the entry
     * @param sourceClass the source class; {@code null} asks the root logger
     * @return the answer of JUL
     */
    @Override
    public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
        return loggerOf(sourceClass).isLoggable(levelOf(level));
    }

    private Logger loggerOf(Class sourceClass) {
        return loggers.computeIfAbsent(sourceClass == null ? "" : sourceClass.getName(), Logger::getLogger);
    }

    static Level levelOf(LogEntry.LogEntryLevel level) {
        switch (level) {
            case TRACE:
                return Level.FINEST;
            case DEBUG:
                return Level.FINE;
            case WARN:
                return Level.WARNING;
            case ERROR:
                return Level.SEVERE;
            default:
                return Level.INFO;
        }
    }
}
