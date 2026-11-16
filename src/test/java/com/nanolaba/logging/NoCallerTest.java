package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * When no stack frame qualifies as the caller, logging must not throw: the entry is logged without a source class
 * and without a call site.
 */
public class NoCallerTest {

    private final CallerDetection.Locator original = LOG.callerLocator;

    @AfterEach
    public void restore() {
        LOG.callerLocator = original;
        CallerDetection.ThrowableWalk.stackTraceOf = Throwable::getStackTrace;
        LOG.init();
    }

    @Test
    public void testEmptyStackTraceLogsWithoutSourceClassAndCallSite() {
        List<LogEntry> entries = new ArrayList<>();
        LOG.init(entries::add);
        // what -XX:-StackTraceInThrowable does to every Throwable
        LOG.callerLocator = CallerDetection.ThrowableWalk.INSTANCE;
        CallerDetection.ThrowableWalk.stackTraceOf = t -> new StackTraceElement[0];

        LOG.info("no stack {}", 1);

        assertEquals(1, entries.size());
        LogEntry entry = entries.get(0);
        assertNull(entry.getSourceClass());
        assertNull(entry.getCaller());
        assertEquals("no stack 1", entry.getFormattedMessage());
    }

    @Test
    public void testLevelCheckWithEmptyStackTraceAsksForNullSourceClass() {
        List<Class> asked = new ArrayList<>();
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
            }

            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
                asked.add(sourceClass);
                return false;
            }
        });
        LOG.callerLocator = CallerDetection.ThrowableWalk.INSTANCE;
        CallerDetection.ThrowableWalk.stackTraceOf = t -> new StackTraceElement[0];

        assertFalse(LOG.isWarnEnabled());
        assertEquals(Collections.<Class>singletonList(null), asked);
    }

    @Test
    public void testThreadWhoseOnlyFrameIsALoggerLogsWithoutSourceClass() throws InterruptedException {
        SelfLoggingThread thread = new SelfLoggingThread();
        LOG.init(thread);

        thread.start();
        thread.join();

        assertNull(thread.failure, () -> "LOG threw " + thread.failure);
        assertEquals(1, thread.entries.size());
        LogEntry entry = thread.entries.get(0);
        assertEquals("from a logger thread", entry.getFormattedMessage());
    }
}

/**
 * A top-level {@code ILogger} whose {@code run()} is the only frame below {@code LOG}: caller detection skips every
 * {@code ILogger} class, so nothing on its stack qualifies as the caller.
 */
class SelfLoggingThread extends Thread implements ILogger {

    final List<LogEntry> entries = Collections.synchronizedList(new ArrayList<>());
    volatile Throwable failure;

    @Override
    public void log(LogEntry entry) {
        entries.add(entry);
    }

    @Override
    public void run() {
        try {
            LOG.info("from a logger thread");
        } catch (Throwable t) {
            failure = t;
        }
    }
}
