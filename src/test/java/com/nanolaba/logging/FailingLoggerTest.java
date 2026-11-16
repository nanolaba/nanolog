package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.DEBUG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An exception from the installed logger must not escape from {@code LOG}: it is reported once per logger class, and
 * the entry is printed to {@code System.err} instead of being lost.
 */
class FailingLoggerTest extends AbstractLoggerTest {

    private static final String DIAGNOSTIC = "[NANOLOG] Logger com.nanolaba.logging.FailingLoggerTest$ThrowingLogger " +
                                             "threw java.lang.RuntimeException: logger failed; " +
                                             "entries it fails to write are printed to System.err";

    @BeforeEach
    public void forgetReportedFailures() {
        LOG.clearReportedLoggerFailures();
    }

    @AfterEach
    public void restore() {
        LOG.init();
        LOG.clearReportedLoggerFailures();
    }

    private static List<String> lines(String text) {
        return text.isEmpty() ? Collections.emptyList() : Arrays.asList(text.split(System.lineSeparator()));
    }

    private static void assertFallbackLine(String level, String message, String line) {
        assertTrue(line.matches(level + " \\d\\d\\.\\d\\d\\.\\d{4} \\d\\d:\\d\\d:\\d\\d \\[FailingLoggerTest] " + message), line);
    }

    @Test
    public void testThrowingLoggerDoesNotEscapeAndTheEntryGoesToStdErr() {
        LOG.init(new ThrowingLogger(new RuntimeException("logger failed")));

        LOG.info("hello {}", 1);

        List<String> err = lines(getErrAndClear());
        assertEquals(2, err.size(), err.toString());
        assertEquals(DIAGNOSTIC, err.get(0));
        assertFallbackLine("INFO", "hello 1", err.get(1));
        assertEquals("", getOutAndClear(), "the fallback writes every level to System.err");
    }

    @Test
    public void testFailureIsReportedOncePerLoggerClass() {
        LOG.init(new ThrowingLogger(new RuntimeException("logger failed")));

        LOG.warn("first");
        LOG.init(new ThrowingLogger(new RuntimeException("logger failed")));
        LOG.error("second");

        List<String> err = lines(getErrAndClear());
        assertEquals(3, err.size(), err.toString());
        assertEquals(DIAGNOSTIC, err.get(0));
        assertFallbackLine("WARN", "first", err.get(1));
        assertFallbackLine("ERROR", "second", err.get(2));
    }

    @Test
    public void testErrorFromLoggerIsCaughtToo() {
        LOG.init(new ThrowingLogger(new AssertionError("boom")));

        LOG.info("still logged");

        List<String> err = lines(getErrAndClear());
        assertEquals(2, err.size(), err.toString());
        assertEquals("[NANOLOG] Logger com.nanolaba.logging.FailingLoggerTest$ThrowingLogger threw java.lang.AssertionError: boom; " +
                     "entries it fails to write are printed to System.err", err.get(0));
        assertFallbackLine("INFO", "still logged", err.get(1));
    }

    @Test
    public void testThrowingIsEnabledCountsAsEnabled() {
        ThrowingIsEnabledLogger logger = new ThrowingIsEnabledLogger();
        LOG.init(logger);

        LOG.debug("debug message");

        assertTrue(LOG.isTraceEnabled());
        assertTrue(LOG.isDebugEnabled(FailingLoggerTest.class));
        assertEquals(Collections.singletonList("debug message"), logger.messages);
        assertEquals(Collections.singletonList("[NANOLOG] Logger com.nanolaba.logging.FailingLoggerTest$ThrowingIsEnabledLogger " +
                                               "threw java.lang.IllegalStateException: isEnabled failed; " +
                                               "entries it fails to write are printed to System.err"),
                     lines(getErrAndClear()));
    }

    @Test
    public void testThrowingMayBeEnabledCountsAsEnabled() {
        ThrowingMayBeEnabledLogger logger = new ThrowingMayBeEnabledLogger();
        LOG.init(logger);

        LOG.debug("debug message");

        assertTrue(LOG.isTraceEnabled());
        assertEquals(Collections.singletonList("debug message"), logger.messages);
        assertEquals(Collections.singletonList("[NANOLOG] Logger com.nanolaba.logging.FailingLoggerTest$ThrowingMayBeEnabledLogger " +
                                               "threw java.lang.IllegalStateException: mayBeEnabled failed; " +
                                               "entries it fails to write are printed to System.err"),
                     lines(getErrAndClear()));
    }

    @Test
    public void testCompositeTreatsThrowingMayBeEnabledAsEnabled() {
        CompositeLogger composite = new CompositeLogger(new ThrowingMayBeEnabledLogger());

        assertTrue(composite.mayBeEnabled(DEBUG));
        assertEquals(Collections.singletonList("[NANOLOG] Logger com.nanolaba.logging.FailingLoggerTest$ThrowingMayBeEnabledLogger " +
                                               "threw java.lang.IllegalStateException: mayBeEnabled failed; " +
                                               "the other loggers of the CompositeLogger still receive its entries"),
                     lines(getErrAndClear()));
    }

    @Test
    public void testCompositeKeepsFeedingTheOtherLoggersWhenOneThrows() {
        ThrowingIsEnabledLogger recording = new ThrowingIsEnabledLogger();
        LOG.init(new CompositeLogger(new ThrowingLogger(new RuntimeException("logger failed")), recording));

        LOG.info("a");
        LOG.info("b");

        assertEquals(Arrays.asList("a", "b"), recording.messages, "a throwing isEnabled counts as enabled in the composite too");
        assertEquals(Arrays.asList(
                             "[NANOLOG] Logger com.nanolaba.logging.FailingLoggerTest$ThrowingLogger threw java.lang.RuntimeException: logger failed; " +
                             "the other loggers of the CompositeLogger still receive its entries",
                             "[NANOLOG] Logger com.nanolaba.logging.FailingLoggerTest$ThrowingIsEnabledLogger threw java.lang.IllegalStateException: isEnabled failed; " +
                             "the other loggers of the CompositeLogger still receive its entries"),
                     lines(getErrAndClear()));
    }

    @Test
    public void testCompositeTreatsThrowingIsEnabledAsEnabled() {
        CompositeLogger composite = new CompositeLogger(new ThrowingIsEnabledLogger());

        assertTrue(composite.isEnabled(DEBUG, FailingLoggerTest.class));
    }

    /**
     * Throws from {@code log}, the typical broken custom logger or failing backend.
     */
    static class ThrowingLogger implements ILogger {

        private final Throwable failure;

        ThrowingLogger(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public void log(LogEntry entry) {
            if (failure instanceof Error) {
                throw (Error) failure;
            }
            throw (RuntimeException) failure;
        }
    }

    /**
     * Throws from {@code isEnabled}, but writes entries fine.
     */
    static class ThrowingIsEnabledLogger implements ILogger {

        final List<String> messages = new ArrayList<>();

        @Override
        public void log(LogEntry entry) {
            messages.add(entry.getFormattedMessage());
        }

        @Override
        public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
            throw new IllegalStateException("isEnabled failed");
        }
    }

    /**
     * Throws from {@code mayBeEnabled}, but accepts and writes every entry.
     */
    static class ThrowingMayBeEnabledLogger implements ILogger {

        final List<String> messages = new ArrayList<>();

        @Override
        public void log(LogEntry entry) {
            messages.add(entry.getFormattedMessage());
        }

        @Override
        public boolean mayBeEnabled(LogEntry.LogEntryLevel level) {
            throw new IllegalStateException("mayBeEnabled failed");
        }
    }
}
