package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shorter stack traces: {@code nanolog.exception.exclude} collapses runs of uninteresting frames,
 * {@code nanolog.exception.maxFrames} caps the frames of each throwable. Without either, the output is exactly that
 * of {@code printStackTrace}.
 */
class ShortStackTraceTest extends AbstractLoggerTest {

    private static final String N = System.lineSeparator();

    private final Map<String, String> environment = new HashMap<>();

    @BeforeEach
    public void fakeEnvironment() {
        DefaultLoggerCreator.environment = environment::get;
    }

    @AfterEach
    public void cleanUp() {
        DefaultLoggerCreator.environment = System::getenv;
        System.clearProperty(ConsoleLogger.PROPERTY_EXCEPTION_EXCLUDE);
        System.clearProperty(ConsoleLogger.PROPERTY_EXCEPTION_MAX_FRAMES);
    }

    private static StackTraceElement frame(String className, int line) {
        return new StackTraceElement(className, "m" + line, className.substring(className.lastIndexOf('.') + 1) + ".java", line);
    }

    private static <T extends Throwable> T withFrames(T throwable, StackTraceElement... frames) {
        throwable.setStackTrace(frames);
        return throwable;
    }

    private static String printed(Throwable throwable) {
        StringWriter out = new StringWriter();
        try (PrintWriter writer = new PrintWriter(out)) {
            throwable.printStackTrace(writer);
        }
        return out.toString();
    }

    private static String at(StackTraceElement frame) {
        return "\tat " + frame + N;
    }

    // the printer

    @Test
    public void testRunsOfExcludedFramesCollapse() {
        StackTraceElement main = frame("a.App", 1);
        StackTraceElement helper = frame("a.App", 5);
        StackTraceElement single = frame("org.junit.W", 6);
        StackTraceElement last = frame("a.App", 7);
        IllegalStateException e = withFrames(new IllegalStateException("boom"),
                                             main, frame("org.junit.X", 2), frame("org.junit.Y", 3), frame("org.junit.Z", 4),
                                             helper, single, last);

        assertEquals("java.lang.IllegalStateException: boom" + N +
                     at(main) +
                     "\t... 3 frames excluded" + N +
                     at(helper) +
                     at(single) +
                     at(last),
                     ShortStackTrace.of(e, Collections.singletonList("org.junit"), 0),
                     "a single excluded frame is kept, a run of two or more becomes one line");
    }

    @Test
    public void testFirstFrameIsAlwaysKept() {
        StackTraceElement first = frame("org.junit.A", 1);
        StackTraceElement app = frame("a.App", 4);
        IllegalStateException e = withFrames(new IllegalStateException("boom"), first, frame("org.junit.B", 2), frame("org.junit.C", 3), app);

        assertEquals("java.lang.IllegalStateException: boom" + N + at(first) + "\t... 2 frames excluded" + N + at(app),
                     ShortStackTrace.of(e, Collections.singletonList("org.junit"), 0));
    }

    @Test
    public void testPrefixesMatchWholeNameSegments() {
        StackTraceElement exact = frame("a.App", 2);
        StackTraceElement nested = frame("a.App$Inner", 3);
        StackTraceElement partial = frame("a.Application", 4);
        IllegalStateException e = withFrames(new IllegalStateException("boom"), frame("z.Start", 1), exact, nested, partial, frame("z.End", 5));

        assertEquals("java.lang.IllegalStateException: boom" + N + at(frame("z.Start", 1)) +
                     "\t... 2 frames excluded" + N + at(partial) + at(frame("z.End", 5)),
                     ShortStackTrace.of(e, Arrays.asList("a.App", "z.Sta"), 0),
                     "a.App covers a.App and a.App$Inner, not a.Application; z.Sta covers nothing");
    }

    @Test
    public void testMaxFramesCapsEachThrowable() {
        StackTraceElement[] frames = new StackTraceElement[10];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = frame("a.App", i + 1);
        }
        IllegalStateException e = withFrames(new IllegalStateException("boom"), frames);

        assertEquals("java.lang.IllegalStateException: boom" + N + at(frames[0]) + at(frames[1]) + at(frames[2]) + "\t... 7 more" + N,
                     ShortStackTrace.of(e, Collections.<String>emptyList(), 3));
        assertEquals(printed(e), ShortStackTrace.of(e, Collections.<String>emptyList(), 10), "a limit the trace does not reach changes nothing");
    }

    @Test
    public void testExcludedRunsDoNotCountAgainstMaxFrames() {
        StackTraceElement main = frame("a.App", 1);
        StackTraceElement helper = frame("a.App", 4);
        IllegalStateException e = withFrames(new IllegalStateException("boom"),
                                             main, frame("org.junit.X", 2), frame("org.junit.Y", 3), helper, frame("a.App", 5), frame("a.App", 6));

        assertEquals("java.lang.IllegalStateException: boom" + N + at(main) + "\t... 2 frames excluded" + N + at(helper) + "\t... 2 more" + N,
                     ShortStackTrace.of(e, Collections.singletonList("org.junit"), 2));
    }

    @Test
    public void testCausesAndSuppressedKeepTheLayoutOfPrintStackTrace() {
        StackTraceElement shared1 = frame("a.Main", 90);
        StackTraceElement shared2 = frame("a.Main", 91);
        IllegalStateException top = withFrames(new IllegalStateException("top"), frame("a.Top", 1), frame("a.Top", 2), shared1, shared2);
        IllegalArgumentException cause = withFrames(new IllegalArgumentException("cause"), frame("a.Cause", 3), shared1, shared2);
        UnsupportedOperationException suppressed = withFrames(new UnsupportedOperationException("suppressed"), frame("a.Closer", 4), frame("a.Top", 2), shared1, shared2);
        RuntimeException deepest = withFrames(new RuntimeException("deepest"), frame("a.Deep", 5), frame("a.Cause", 3), shared1, shared2);
        top.initCause(cause);
        top.addSuppressed(suppressed);
        cause.initCause(deepest);

        // no frame is excluded and the limit is never reached, so the text is exactly that of printStackTrace
        assertEquals(printed(top), ShortStackTrace.of(top, Collections.singletonList("nothing.matches"), 100));
    }

    @Test
    public void testFramesInCommonAreCountedWhenTheTraceIsCapped() {
        StackTraceElement x = frame("a.Main", 90);
        IllegalStateException top = withFrames(new IllegalStateException("top"), x);
        IllegalArgumentException cause = withFrames(new IllegalArgumentException("cause"),
                                                    frame("a.Cause", 1), frame("a.Cause", 2), frame("a.Cause", 3), x);
        top.initCause(cause);

        assertEquals("java.lang.IllegalStateException: top" + N + at(x) +
                     "Caused by: java.lang.IllegalArgumentException: cause" + N + at(frame("a.Cause", 1)) + "\t... 3 more" + N,
                     ShortStackTrace.of(top, Collections.<String>emptyList(), 1),
                     "two frames cut and one frame in common with the enclosing trace, whose only frame it is");
    }

    @Test
    public void testCauseWhoseWholeTraceIsShared() {
        StackTraceElement x = frame("a.Main", 90);
        StackTraceElement y = frame("a.Main", 91);
        IllegalStateException top = withFrames(new IllegalStateException("top"), frame("a.Top", 1), x, y);
        IllegalArgumentException cause = withFrames(new IllegalArgumentException("cause"), x, y);
        top.initCause(cause);

        assertEquals(printed(top), ShortStackTrace.of(top, Collections.singletonList("nothing.matches"), 0));
        assertTrue(ShortStackTrace.of(top, Collections.singletonList("nothing.matches"), 0).endsWith("Caused by: java.lang.IllegalArgumentException: cause" + N + "\t... 2 more" + N));
    }

    @Test
    public void testCircularCauseIsMarked() {
        IllegalStateException first = withFrames(new IllegalStateException("first"), frame("a.First", 1));
        IllegalArgumentException second = withFrames(new IllegalArgumentException("second"), frame("a.Second", 2));
        first.initCause(second);
        second.initCause(first);

        assertEquals("java.lang.IllegalStateException: first" + N + at(frame("a.First", 1)) +
                     "Caused by: java.lang.IllegalArgumentException: second" + N + at(frame("a.Second", 2)) +
                     "Caused by: [CIRCULAR REFERENCE: java.lang.IllegalStateException: first]" + N,
                     ShortStackTrace.of(first, Collections.singletonList("nothing.matches"), 0));
    }

    @Test
    public void testPrinterLetsFailuresThrough() {
        RuntimeException broken = new RuntimeException() {
            @Override
            public StackTraceElement[] getStackTrace() {
                throw new IllegalStateException("broken getStackTrace");
            }
        };

        assertThrows(IllegalStateException.class, () -> ShortStackTrace.of(broken, Collections.singletonList("org.junit"), 0),
                     "the logger catches it and falls back to printStackTrace");
    }

    // the logger

    @Test
    public void testWithoutSettingsTheOutputIsThatOfPrintStackTrace() {
        ConsoleLogger logger = new ConsoleLogger();
        RuntimeException custom = new RuntimeException("custom") {
            @Override
            public void printStackTrace(PrintWriter s) {
                s.println("printed by the exception itself");
            }
        };
        IllegalStateException nested = new IllegalStateException("top", new IllegalArgumentException("cause"));
        nested.addSuppressed(new UnsupportedOperationException("suppressed"));

        assertEquals("printed by the exception itself" + N, logger.stackTraceOf(custom));
        assertEquals(printed(nested), logger.stackTraceOf(nested));
        assertEquals(Collections.<String>emptyList(), logger.getExceptionExclude());
        assertEquals(0, logger.getExceptionMaxFrames());
    }

    @Test
    public void testSettersShortenTheTrace() {
        StackTraceElement main = frame("a.App", 1);
        IllegalStateException e = withFrames(new IllegalStateException("boom"), main, frame("org.junit.X", 2), frame("org.junit.Y", 3));
        ConsoleLogger logger = new ConsoleLogger();

        assertSame(logger, logger.setExceptionExclude(" org.junit ", "", null));
        assertEquals(Collections.singletonList("org.junit"), logger.getExceptionExclude());
        assertEquals("java.lang.IllegalStateException: boom" + N + at(main) + "\t... 2 frames excluded" + N, logger.stackTraceOf(e));

        logger.setExceptionExclude();
        assertSame(logger, logger.setExceptionMaxFrames(1));
        assertEquals(1, logger.getExceptionMaxFrames());
        assertEquals("java.lang.IllegalStateException: boom" + N + at(main) + "\t... 2 more" + N, logger.stackTraceOf(e));

        logger.setExceptionMaxFrames(-5);
        assertEquals(0, logger.getExceptionMaxFrames(), "zero or less is no limit");
        logger.setExceptionExclude((String[]) null);
        assertEquals(Collections.<String>emptyList(), logger.getExceptionExclude());
        assertEquals(printed(e), logger.stackTraceOf(e));
    }

    @Test
    public void testFailingPrinterFallsBackToPrintStackTrace() {
        RuntimeException brokenTrace = new RuntimeException("broken getStackTrace") {
            @Override
            public StackTraceElement[] getStackTrace() {
                throw new IllegalStateException("broken");
            }
        };
        RuntimeException brokenText = new RuntimeException() {
            @Override
            public String toString() {
                throw new IllegalStateException("broken toString");
            }
        };
        ConsoleLogger logger = new ConsoleLogger().setExceptionExclude("org.junit");

        // printStackTrace reads the frames itself, without the overridden getStackTrace()
        assertEquals(printed(brokenTrace), logger.stackTraceOf(brokenTrace));
        assertEquals("[FAILED printStackTrace()]", logger.stackTraceOf(brokenText));
    }

    @Test
    public void testSettingsFromPropertiesAndEnvironment() {
        System.setProperty(ConsoleLogger.PROPERTY_EXCEPTION_EXCLUDE, " org.junit , sun.reflect ,, ");
        System.setProperty(ConsoleLogger.PROPERTY_EXCEPTION_MAX_FRAMES, " 40 ");
        ConsoleLogger logger = new ConsoleLogger();
        assertEquals(Arrays.asList("org.junit", "sun.reflect"), logger.getExceptionExclude());
        assertEquals(40, logger.getExceptionMaxFrames());

        cleanUp();
        DefaultLoggerCreator.environment = environment::get;
        environment.put("NANOLOG_EXCEPTION_EXCLUDE", "jdk.internal.reflect");
        environment.put("NANOLOG_EXCEPTION_MAXFRAMES", "0");
        logger = new ConsoleLogger();
        assertEquals(Collections.singletonList("jdk.internal.reflect"), logger.getExceptionExclude());
        assertEquals(0, logger.getExceptionMaxFrames());
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testInvalidMaxFramesKeepsNoLimit() {
        for (String invalid : new String[]{"-3", "many"}) {
            System.setProperty(ConsoleLogger.PROPERTY_EXCEPTION_MAX_FRAMES, invalid);

            assertEquals(0, new ConsoleLogger().getExceptionMaxFrames());
            assertEquals("[NANOLOG] Invalid value \"" + invalid + "\" of \"nanolog.exception.maxFrames\": expected a number of frames, 0 for no limit; set as a system property" + N,
                         getErrAndClear());
        }
    }

    @Test
    public void testJsonLoggerShortensItsStackTraceField() {
        StackTraceElement main = frame("a.App", 1);
        IllegalStateException e = withFrames(new IllegalStateException("boom"), main, frame("org.junit.X", 2), frame("org.junit.Y", 3));
        JsonLogger logger = new JsonLogger();
        logger.setExceptionExclude("org.junit");

        logger.log(new LogEntry(LogEntry.LogEntryLevel.INFO, ShortStackTraceTest.class, e, "failed"));

        Map<String, Object> json = com.nanolaba.logging.util.StrictJson.parseObject(getOutAndClear().trim());
        @SuppressWarnings("unchecked")
        Map<String, Object> exception = (Map<String, Object>) json.get("exception");
        assertEquals("java.lang.IllegalStateException: boom" + N + at(main) + "\t... 2 frames excluded" + N, exception.get("stackTrace"));
    }

    @Test
    public void testOnlyNonEmptyPrefixesAreKept() {
        List<String> prefixes = new ConsoleLogger().setExceptionExclude("  ", "a.b").getExceptionExclude();

        assertEquals(Collections.singletonList("a.b"), prefixes);
        assertThrows(UnsupportedOperationException.class, () -> prefixes.add("c"));
    }
}
