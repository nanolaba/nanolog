package com.nanolaba.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Sends the records of {@code java.util.logging} to Nanolog, so the libraries that log through JUL appear in the
 * format of the application, in {@link JsonLogger} and in the captures of {@link MemoryLogger}. On Java 9+ the same
 * goes for {@code System.Logger}, which the JDK sends to JUL while the module {@code java.logging} is present.
 * <p>
 * {@link #install()} replaces the handlers of the root logger, the console handler among them, by this one; or, in
 * {@code logging.properties}: {@code handlers=com.nanolaba.logging.NanologHandler}. Each record becomes an entry of
 * the code that called JUL, with the call site, the message formatted as JUL formats it ({@code {0}} parameters and
 * resource bundles) and the exception; {@code SEVERE} becomes {@code ERROR}, {@code WARNING} {@code WARN},
 * {@code INFO} {@code INFO}, {@code CONFIG} and {@code FINE} {@code DEBUG}, {@code FINER} and {@code FINEST}
 * {@code TRACE}.
 * <p>
 * JUL decides first: a record below the level of its JUL logger, {@code INFO} by default, never reaches Nanolog, so
 * to see the {@code FINE} records of a library, lower its level in JUL as well. Lowering the level of the root logger
 * instead makes JUL build a record for every disabled call of every library, which costs far more than a disabled
 * call of Nanolog.
 * <p>
 * Do not combine it with {@link JulLogger}, which writes Nanolog to JUL: the loop is reported once and the record
 * dropped.
 */
public class NanologHandler extends Handler {

    /**
     * Formats the message of a record as JUL does: its resource bundle and its {@code {0}} parameters.
     */
    private static final Formatter MESSAGES = new SimpleFormatter();

    /**
     * Set while the thread passes a record to Nanolog: a record that comes back is a loop.
     */
    private static final ThreadLocal<Boolean> PUBLISHING = new ThreadLocal<>();

    // guarded by the class
    private static NanologHandler installed;
    private static final List<Handler> REPLACED = new ArrayList<>();

    /**
     * Creates the handler; {@code logging.properties} does, through {@code handlers=}.
     */
    public NanologHandler() {
        // the level and the filter of a handler are ALL and none by default: Nanolog decides with its own levels
    }

    /**
     * Sends every record of JUL to Nanolog: the handlers of the root logger are replaced by a {@code NanologHandler}.
     * Installing again changes nothing.
     */
    public static synchronized void install() {
        if (installed == null) {
            Logger root = Logger.getLogger("");
            for (Handler handler : root.getHandlers()) {
                root.removeHandler(handler);
                REPLACED.add(handler);
            }
            installed = new NanologHandler();
            root.addHandler(installed);
        }
    }

    /**
     * Removes the handler that {@link #install()} added and puts back the handlers it replaced. Uninstalling again
     * changes nothing.
     */
    public static synchronized void uninstall() {
        if (installed != null) {
            Logger root = Logger.getLogger("");
            root.removeHandler(installed);
            for (Handler handler : REPLACED) {
                root.addHandler(handler);
            }
            REPLACED.clear();
            installed = null;
        }
    }

    /**
     * Writes the record as an entry of Nanolog, if the level and the filter of this handler let it through.
     *
     * @param record the record; {@code null} is ignored
     */
    @Override
    public void publish(LogRecord record) {
        // Handler.isLoggable of Java 8 throws on null
        if (record == null) {
            return;
        }
        Level level = record.getLevel();
        if (!isLoggable(record)) {
            return;
        }
        if (PUBLISHING.get() != null) {
            DefaultLoggerCreator.printDiagnostic("java.util.logging loops back into Nanolog: JulLogger writes to JUL and NanologHandler sends JUL "
                                                 + "to Nanolog; the record is dropped");
            return;
        }
        PUBLISHING.set(Boolean.TRUE);
        String callerClass = record.getSourceClassName();
        Class caller = CallerDetection.ThrowableWalk.resolveClass(callerClass);
        Class source = caller != null ? caller : CallerDetection.ThrowableWalk.resolveClass(record.getLoggerName());
        LOG.writeLine(levelOf(level), source == null ? null : CallerDetection.topLevel(source),
                      frameOf(callerClass, record.getSourceMethodName()), record.getThrown(), MESSAGES.formatMessage(record));
        PUBLISHING.remove();
    }

    /**
     * The frame of the call of JUL on the current stack, to have its line; without it, the class and the method JUL
     * names, and no line.
     */
    private static StackTraceElement frameOf(String className, String methodName) {
        if (className == null || methodName == null) {
            return null;
        }
        for (StackTraceElement frame : CallerDetection.ThrowableWalk.stackTraceOf.apply(new Throwable())) {
            if (frame.getClassName().equals(className) && frame.getMethodName().equals(methodName)) {
                return frame;
            }
        }
        return new StackTraceElement(className, methodName, null, -1);
    }

    static LogEntry.LogEntryLevel levelOf(Level level) {
        int value = level.intValue();
        if (value >= Level.SEVERE.intValue()) {
            return LogEntry.LogEntryLevel.ERROR;
        } else if (value >= Level.WARNING.intValue()) {
            return LogEntry.LogEntryLevel.WARN;
        } else if (value >= Level.INFO.intValue()) {
            return LogEntry.LogEntryLevel.INFO;
        } else if (value >= Level.FINE.intValue()) {
            return LogEntry.LogEntryLevel.DEBUG;
        }
        return LogEntry.LogEntryLevel.TRACE;
    }

    /**
     * Flushes the logger of Nanolog, as {@link LOG#flush()} does.
     */
    @Override
    public void flush() {
        LOG.flush();
    }

    /**
     * Flushes the logger of Nanolog; JUL closes its handlers when the JVM exits.
     */
    @Override
    public void close() {
        flush();
    }
}
