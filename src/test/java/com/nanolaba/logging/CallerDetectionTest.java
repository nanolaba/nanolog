package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * How {@code LOG} finds the caller: a {@code StackWalker} on Java 9+, the stack trace of a {@code Throwable} on Java 8
 * and whenever the walker fails. Both locators must find the same caller.
 */
class CallerDetectionTest {

    private final CallerDetection.Locator original = LOG.callerLocator;
    private final List<LogEntry> entries = new ArrayList<>();

    @AfterEach
    public void restore() {
        LOG.callerLocator = original;
        LOG.init();
    }

    static boolean isJava9OrLater() {
        return !System.getProperty("java.specification.version").startsWith("1.");
    }

    /**
     * The locators this JVM can run: the {@code Throwable} walk always, the stack walker from Java 9, the legacy walk on
     * Java 8.
     */
    static Stream<CallerDetection.Locator> locators() {
        return isJava9OrLater()
               ? Stream.of(CallerDetection.ThrowableWalk.INSTANCE, new CallerDetection.StackWalk())
               : Stream.of(CallerDetection.ThrowableWalk.INSTANCE, new CallerDetection.LegacyWalk());
    }

    @Test
    public void testStackWalkerIsUsedFromJava9() {
        // best() is asked directly as well: LOG calls it only once, when its class initialises
        if (isJava9OrLater()) {
            assertEquals(CallerDetection.StackWalk.class, CallerDetection.best().getClass());
            assertEquals(CallerDetection.StackWalk.class, LOG.callerLocator.getClass());
        } else {
            assertEquals(CallerDetection.LegacyWalk.class, CallerDetection.best().getClass());
            assertEquals(CallerDetection.LegacyWalk.class, LOG.callerLocator.getClass());
        }
    }

    @ParameterizedTest
    @MethodSource("locators")
    public void testAStackOfNanologFramesOnlyHasNoCaller(CallerDetection.Locator locator) throws InterruptedException {
        LoggerThread thread = new LoggerThread(locator);
        thread.start();
        thread.join();
        assertSame(CallerDetection.Caller.NONE, thread.found);
    }

    @Test
    public void testTheLegacyWalkGivesWayWhenTheFramesDriftApart() throws ReflectiveOperationException {
        org.junit.jupiter.api.Assumptions.assumeFalse(isJava9OrLater(), "the legacy walk runs on Java 8 only");
        CallerDetection.Caller marker = new CallerDetection.Caller(String.class, null);
        CallerDetection.Locator fallback = CallerDetection.LegacyWalk.fallback;
        try {
            CallerDetection.LegacyWalk.fallback = () -> marker;
            CallerDetection.LegacyWalk walk = new CallerDetection.LegacyWalk();
            assertEquals(CallerDetectionTest.class, walk.find().sourceClass, "called directly, the frames agree");
            // Reflection.getCallerClass skips the frames of Method.invoke, a Throwable does not
            java.lang.reflect.Method find = CallerDetection.LegacyWalk.class.getMethod("find");
            assertSame(marker, find.invoke(walk));
        } finally {
            CallerDetection.LegacyWalk.fallback = fallback;
        }
    }

    @Test
    public void testPreferredLocatorThatCannotBeCreatedGivesWayToTheFallback() {
        Supplier<CallerDetection.Locator> missing = () -> {
            throw new NoClassDefFoundError("java/lang/StackWalker");
        };

        assertSame(CallerDetection.ThrowableWalk.INSTANCE, CallerDetection.firstWorking(missing, CallerDetection.ThrowableWalk.INSTANCE));
    }

    @Test
    public void testPreferredLocatorThatFailsToFindGivesWayToTheFallback() {
        CallerDetection.Locator failing = () -> {
            throw new UnsupportedOperationException("no stack walking here");
        };

        assertSame(CallerDetection.ThrowableWalk.INSTANCE, CallerDetection.firstWorking(() -> failing, CallerDetection.ThrowableWalk.INSTANCE));
    }

    @Test
    public void testWorkingPreferredLocatorIsChosen() {
        CallerDetection.Locator working = () -> CallerDetection.Caller.NONE;

        assertSame(working, CallerDetection.firstWorking(() -> working, CallerDetection.ThrowableWalk.INSTANCE));
    }

    @Test
    public void testLocatorThatFailsWhileLoggingGivesWayToTheThrowableWalk() {
        LOG.init(entries::add);
        LOG.callerLocator = () -> {
            throw new IllegalStateException("walk failed");
        };

        LOG.info("still detected");

        assertEquals(CallerDetectionTest.class, entries.get(0).getSourceClass());
        assertEquals("testLocatorThatFailsWhileLoggingGivesWayToTheThrowableWalk", entries.get(0).getCaller().getMethodName());
    }

    @ParameterizedTest
    @MethodSource("locators")
    public void testEveryLocatorFindsTheCallerWhenAskedDirectly(CallerDetection.Locator locator) {
        // LOG falls back to the Throwable walk when a locator fails, so only a direct call shows that it works
        CallerDetection.Caller caller = locator.find();

        assertEquals(CallerDetectionTest.class, caller.sourceClass);
        assertEquals(CallerDetectionTest.class.getName(), caller.frame.getClassName());
        assertEquals("testEveryLocatorFindsTheCallerWhenAskedDirectly", caller.frame.getMethodName());
    }

    @ParameterizedTest
    @MethodSource("locators")
    public void testEveryLocatorFindsTheSameCaller(CallerDetection.Locator locator) {
        LOG.init(entries::add);
        LOG.callerLocator = locator;

        LOG.info("direct");
        new Nested().log();
        Runnable lambda = () -> LOG.info("from a lambda");
        lambda.run();

        assertEquals(3, entries.size());
        for (LogEntry entry : entries) {
            assertEquals(CallerDetectionTest.class, entry.getSourceClass(), entry.getFormattedMessage());
        }
        assertFrame(entries.get(0), CallerDetectionTest.class.getName(), "testEveryLocatorFindsTheSameCaller");
        assertFrame(entries.get(1), Nested.class.getName(), "log");
        assertEquals(CallerDetectionTest.class.getName(), entries.get(2).getCaller().getClassName());
        assertEquals("CallerDetectionTest.java", entries.get(2).getCaller().getFileName());
    }

    @ParameterizedTest
    @MethodSource("locators")
    public void testEveryLocatorSkipsTheFramesOfLoggers(CallerDetection.Locator locator) {
        LOG.callerLocator = locator;
        SelfLoggingLogger logger = new SelfLoggingLogger();
        LOG.init(logger);

        LOG.info("outer");

        assertEquals(2, logger.entries.size());
        assertEquals(CallerDetectionTest.class, logger.entries.get(1).getSourceClass(),
                     "the entry the logger logged itself is attributed to the code that called LOG, not to the logger");
    }

    private static void assertFrame(LogEntry entry, String className, String methodName) {
        assertEquals(className, entry.getCaller().getClassName());
        assertEquals(methodName, entry.getCaller().getMethodName());
        assertEquals("CallerDetectionTest.java", entry.getCaller().getFileName());
    }

    private static class Nested {
        void log() {
            LOG.info("from a nested class");
        }
    }

    /**
     * Logs a second entry through {@code LOG} while it writes the first, without an explicit class.
     */
    private static class SelfLoggingLogger implements ILogger {

        final List<LogEntry> entries = new ArrayList<>();

        @Override
        public void log(LogEntry entry) {
            entries.add(entry);
            if (entries.size() == 1) {
                LOG.info("inner");
            }
        }
    }
}
