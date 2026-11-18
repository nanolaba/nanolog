package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code nanolog.strict}: diagnostics for calls whose {@code {}} placeholders and arguments do not match, whose arguments
 * repeat the message of their exception, or whose template is not a constant.
 */
class StrictModeTest extends AbstractLoggerTest {

    private final Map<String, String> environment = new HashMap<>();
    private MemoryLogger memory;

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
        DefaultLoggerCreator.forgetDiagnostics();
    }

    @AfterEach
    public void tearDown() {
        DefaultLoggerCreator.environment = System::getenv;
        System.clearProperty(DefaultLoggerCreator.PROPERTY_STRICT);
        LOG.init();
        DefaultLoggerCreator.forgetDiagnostics();
    }

    private void enableStrictMode(String value) {
        System.setProperty(DefaultLoggerCreator.PROPERTY_STRICT, value);
        LOG.init();
        memory = new MemoryLogger();
        LOG.init(memory);
    }

    @Test
    public void testOffByDefault() {
        LOG.init();
        LOG.init(new MemoryLogger());

        LOG.info("a {} b {}", 1);
        LOG.info("a {}", 1, 2);
        IllegalStateException e = new IllegalStateException("boom");
        LOG.error("Failed: {}", e.getMessage(), e);
        for (int id = 1; id < 3; id++) {
            LOG.info("User " + id + " from {}", "host");
        }

        assertEquals("", getErrAndClear());
    }

    @Test
    public void testAnArgumentRepeatingTheMessageOfTheExceptionIsReported() {
        enableStrictMode("true");
        IllegalStateException e = new IllegalStateException("boom");

        int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        LOG.error("Failed: {}", e.getMessage(), e);
        assertEquals("[NANOLOG] com.nanolaba.logging.StrictModeTest.testAnArgumentRepeatingTheMessageOfTheExceptionIsReported(StrictModeTest.java:" +
                     line + "): argument 1 repeats the message of the exception" + System.lineSeparator(), getErrAndClear());

        LOG.warn(e, "Failed {} times: {}", 3, e.getMessage());
        assertTrue(getErrAndClear().endsWith("): argument 2 repeats the message of the exception" + System.lineSeparator()));

        LOG.error("{} / {}", e.getMessage(), e.getMessage(), e);
        String both = getErrAndClear();
        assertTrue(both.contains("): argument 1 repeats") && both.contains("): argument 2 repeats"), both);

        LOG.once().error("Once: {}", e.getMessage(), e);
        assertTrue(getErrAndClear().endsWith("): argument 1 repeats the message of the exception" + System.lineSeparator()));

        assertEquals(5, memory.getDiagnostics().size());
        // the entries themselves are logged exactly as without the check
        assertEquals(Arrays.asList("Failed: boom", "Failed 3 times: boom", "boom / boom", "Once: boom"), memory.getMessages());
    }

    @Test
    public void testArgumentsThatDoNotRepeatTheMessageAreNotReported() {
        enableStrictMode("true");
        IllegalStateException e = new IllegalStateException("boom");

        LOG.error("Failed: {}", "other", e);
        LOG.error("Failed: {}", "boom");
        LOG.error("Failed: {}", null, new IllegalStateException());
        LOG.error("Failed: {}", "", new IllegalStateException(""));
        LOG.error("Code {}", 42, new IllegalStateException("42"));
        LOG.error("Failed: {}", LOG.kv("reason", "boom"), e);
        LOG.error("Failed: {}", "boom", new IllegalStateException("boom") {
            @Override
            public String getMessage() {
                throw new UnsupportedOperationException("no message");
            }
        });

        assertEquals("", getErrAndClear());
        memory.assertNoDiagnostics();
        assertEquals(7, memory.getEntries().size());
    }

    /**
     * @return the line of the call
     */
    private int logUser(int id) {
        LOG.info("User " + id + " logged in from {}", "10.0.0.1");
        return new Throwable().getStackTrace()[0].getLineNumber() - 1;
    }

    @Test
    public void testATemplateThatIsNotAConstantIsReported() {
        enableStrictMode("true");

        int line = 0;
        for (int id = 17; id < 20; id++) {
            line = logUser(id);
        }

        assertEquals("[NANOLOG] com.nanolaba.logging.StrictModeTest.logUser(StrictModeTest.java:" + line +
                     "): the template is not a constant: \"User 17 logged in from {}\"" + System.lineSeparator(), getErrAndClear(),
                     "standard error reports the call site once");
        List<String> diagnostics = memory.getDiagnostics();
        assertEquals(2, diagnostics.size(), "the logger gets every call with another template");
        assertEquals(diagnostics.get(0), diagnostics.get(1));
        assertEquals(Arrays.asList("User 17 logged in from 10.0.0.1", "User 18 logged in from 10.0.0.1", "User 19 logged in from 10.0.0.1"),
                     memory.getMessages());

        logUser(17);
        assertEquals(2, memory.getDiagnostics().size(), "the first template again is not another one");
    }

    @Test
    public void testALimitedStatementWithATemplateThatIsNotAConstantIsReported() {
        enableStrictMode("true");

        for (int i = 0; i < 2; i++) {
            LOG.atMostEvery(0, TimeUnit.SECONDS).info("Step " + i + " of {}", 2);
        }

        assertTrue(getErrAndClear().endsWith("): the template is not a constant: \"Step 0 of {}\"" + System.lineSeparator()));
        assertEquals(1, memory.getDiagnostics().size());
    }

    private void timedStep(boolean fail) {
        try {
            LOG.timed("step", () -> {
                if (fail) {
                    throw new IllegalStateException("step");
                }
            });
        } catch (IllegalStateException ignored) {
            // the timer has written its line
        }
    }

    @Test
    public void testConstantTemplatesAndCallsWithoutACallSiteAreNotReported() {
        enableStrictMode("true");

        for (int i = 0; i < 3; i++) {
            LOG.info("Step {} of {}", i, 3);
            LOG.info("Step " + i + " done");
            LOG.info("Order " + i, LOG.kv("step", i));
            LOG.info(InfoObject.class, "Explicit " + i + " {}", i);
        }
        LOG.info("One template {}", 1);
        LOG.info("Another template {}", 2);
        // a timer writes "took" and "failed after" at one call site, with its name as an argument
        timedStep(false);
        timedStep(true);

        assertEquals("", getErrAndClear());
        memory.assertNoDiagnostics();
    }

    @Test
    public void testMismatchesAreReportedWithTheCallSite() {
        enableStrictMode("true");

        int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        LOG.info("a {} b {}", 1);
        assertEquals("[NANOLOG] com.nanolaba.logging.StrictModeTest.testMismatchesAreReportedWithTheCallSite(StrictModeTest.java:" + line +
                     "): 2 placeholders, 1 argument in \"a {} b {}\"" + System.lineSeparator(), getErrAndClear());

        LOG.info("a {}", 1, 2);
        assertTrue(getErrAndClear().endsWith("): 1 placeholder, 2 arguments in \"a {}\"" + System.lineSeparator()));

        LOG.info("none", 1);
        assertTrue(getErrAndClear().endsWith("): 0 placeholders, 1 argument in \"none\"" + System.lineSeparator()));

        // the entries themselves are logged exactly as without the check
        assertEquals(Arrays.asList("a 1 b {}", "a 1", "none"), memory.getMessages());
    }

    @Test
    public void testEachTemplateIsReportedOnce() {
        enableStrictMode("true");

        for (int i = 0; i < 3; i++) {
            LOG.info("loop {} {}", i);
        }

        String err = getErrAndClear();
        assertEquals(1, err.split(System.lineSeparator()).length, err);
        assertEquals(3, memory.getEntries().size());
    }

    @Test
    public void testMatchingCallsAreNotReported() {
        enableStrictMode("true");
        IllegalStateException e = new IllegalStateException("boom");

        LOG.info("a {} b {}", 1, 2);
        LOG.info("plain text with {} and no arguments");
        LOG.info(new HashMap<>());
        LOG.info("literal \\{} and {}", 1);
        LOG.error("Failed {}", 1, e);
        LOG.error("Failed", e);
        LOG.error("Cause: {}", e);

        assertEquals("", getErrAndClear());
    }

    @Test
    public void testTrailingThrowableDoesNotHideExtraArguments() {
        enableStrictMode("true");

        LOG.error("a {}", 1, 2, new IllegalStateException("boom"));

        assertTrue(getErrAndClear().endsWith("): 1 placeholder, 2 arguments in \"a {}\"" + System.lineSeparator()));
    }

    @Test
    public void testSourceWithoutCallSite() {
        enableStrictMode("true");

        LOG.warn(InfoObject.class, "explicit {} {}", 1);
        assertEquals("[NANOLOG] com.nanolaba.logging.util.InfoObject: 2 placeholders, 1 argument in \"explicit {} {}\"" + System.lineSeparator(),
                     getErrAndClear());

        LOG.log(INFO, null, null, () -> "no class {} {}", 1);
        assertEquals("[NANOLOG] unknown source: 2 placeholders, 1 argument in \"no class {} {}\"" + System.lineSeparator(), getErrAndClear());
    }

    @Test
    public void testAnyMessageObject() {
        enableStrictMode("true");

        LOG.log(INFO, InfoObject.class, null, () -> new StringBuilder("builder {} {}"), 1);
        assertTrue(getErrAndClear().endsWith(": 2 placeholders, 1 argument in \"builder {} {}\"" + System.lineSeparator()));

        LOG.log(INFO, InfoObject.class, null, () -> new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("broken");
            }
        }, 1);
        assertTrue(getErrAndClear().endsWith(": 0 placeholders, 1 argument in \"[FAILED toString()]\"" + System.lineSeparator()));
    }

    @Test
    public void testDisabledLevelsAreNotChecked() {
        enableStrictMode("true");
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
            }

            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
                return false;
            }
        });

        LOG.info("a {} b {}", 1);

        assertEquals("", getErrAndClear());
    }

    @Test
    public void testPropertyValues() {
        enableStrictMode("TRUE");
        LOG.info("upper {} {}", 1);
        assertTrue(getErrAndClear().contains("2 placeholders"));

        enableStrictMode("false");
        LOG.info("off {} {}", 1);
        assertEquals("", getErrAndClear());

        enableStrictMode("yes");
        assertEquals("[NANOLOG] Invalid value \"yes\" of \"nanolog.strict\": expected true or false; set as a system property" + System.lineSeparator(), getErrAndClear());
        LOG.info("invalid {} {}", 1);
        assertEquals("", getErrAndClear(), "an invalid value leaves the check off");

        System.clearProperty(DefaultLoggerCreator.PROPERTY_STRICT);
        environment.put("NANOLOG_STRICT", "true");
        LOG.init();
        LOG.init(new MemoryLogger());
        LOG.info("environment {} {}", 1);
        assertTrue(getErrAndClear().contains("2 placeholders"));
    }

    @Test
    public void testEachFindingIsPrintedOncePerCallSite() {
        enableStrictMode("true");

        for (int i = 0; i < 3; i++) {
            LOG.info("t {} {}", 1);
        }
        assertEquals(1, getErrAndClear().split(System.lineSeparator()).length, "a loop prints its finding once");

        LOG.info("t {} {}", 1);
        String other = getErrAndClear();
        assertTrue(other.contains("StrictModeTest.java:") && other.contains("2 placeholders, 1 argument in \"t {} {}\""),
                   "the same template at another call site is another finding: " + other);
        assertEquals(4, memory.getDiagnostics().size(), "the logger gets every occurrence");
    }

    private void mismatched(int value) {
        LOG.info("{} and {}", value);
    }

    @Test
    public void testEveryOccurrenceReachesTheLoggerWhileStandardErrorGetsTheFirst() {
        enableStrictMode("true");

        mismatched(1);
        mismatched(2);

        List<String> diagnostics = memory.getDiagnostics();
        assertEquals(2, diagnostics.size());
        assertEquals(diagnostics.get(0), diagnostics.get(1));
        assertTrue(diagnostics.get(0).startsWith("com.nanolaba.logging.StrictModeTest.mismatched(StrictModeTest.java:"), diagnostics.get(0));
        assertTrue(diagnostics.get(0).endsWith("): 2 placeholders, 1 argument in \"{} and {}\""), diagnostics.get(0));
        assertEquals("[NANOLOG] " + diagnostics.get(0) + System.lineSeparator(), getErrAndClear(), "standard error reports a template once");

        AssertionError error = assertThrows(AssertionError.class, memory::assertNoDiagnostics);
        assertEquals("Expected no diagnostics, but Nanolog reported 2:" + System.lineSeparator() +
                     diagnostics.get(0) + System.lineSeparator() + diagnostics.get(1), error.getMessage());
    }

    @Test
    public void testTemplateReportedEarlierInTheJvmStillReachesTheTest() {
        enableStrictMode("true");
        mismatched(1);
        getErrAndClear();

        try (MemoryLogger log = MemoryLogger.install()) {
            mismatched(2);

            assertEquals(1, log.getDiagnostics().size());
            assertThrows(AssertionError.class, log::assertNoDiagnostics);
            assertEquals("", getErrAndClear(), "the template was printed already");
        }
    }

    @Test
    public void testMatchingCallsLeaveNoDiagnostics() {
        enableStrictMode("true");

        LOG.info("{} and {}", 1, 2);
        LOG.info("plain");

        memory.assertNoDiagnostics();
        assertEquals(Collections.emptyList(), memory.getDiagnostics());
    }

    @Test
    public void testCompositeForwardsDiagnosticsToItsDelegates() {
        enableStrictMode("true");
        MemoryLogger first = new MemoryLogger();
        MemoryLogger second = new MemoryLogger();
        LOG.init(new CompositeLogger(first, entry -> {
        }, second));

        mismatched(1);

        assertEquals(1, first.getDiagnostics().size());
        assertEquals(first.getDiagnostics(), second.getDiagnostics(), "a delegate without onDiagnostic is skipped quietly");
    }

    @Test
    public void testThrowingOnDiagnosticNeverBreaksTheCall() {
        enableStrictMode("true");
        LOG.clearReportedLoggerFailures();
        List<String> logged = new ArrayList<>();
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
                logged.add(entry.getFormattedMessage());
            }

            @Override
            public void onDiagnostic(String message) {
                throw new IllegalStateException("broken");
            }
        });

        mismatched(1);

        assertEquals(Collections.singletonList("1 and {}"), logged, "the entry is logged all the same");
        String err = getErrAndClear();
        assertTrue(err.contains("2 placeholders, 1 argument"), err);
        assertTrue(err.contains("threw java.lang.IllegalStateException: broken; its diagnostics are printed to System.err only"), err);
        LOG.clearReportedLoggerFailures();
    }

    @Test
    public void testCompositeKeepsDeliveringWhenADelegateThrows() {
        enableStrictMode("true");
        LOG.clearReportedLoggerFailures();
        MemoryLogger after = new MemoryLogger();
        LOG.init(new CompositeLogger(new ILogger() {
            @Override
            public void log(LogEntry entry) {
            }

            @Override
            public void onDiagnostic(String message) {
                throw new IllegalStateException("broken");
            }
        }, after));

        mismatched(1);

        assertEquals(1, after.getDiagnostics().size());
        assertTrue(getErrAndClear().contains("threw java.lang.IllegalStateException: broken; the other loggers of the CompositeLogger still receive its entries"));
        LOG.clearReportedLoggerFailures();
    }
}
