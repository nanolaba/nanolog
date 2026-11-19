package com.nanolaba.logging;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestInstancePostProcessor;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * {@link NanologExtension}: the log of a test is printed only when the test fails. The fixtures below are run by a
 * launcher of their own; run in any other way they are skipped.
 */
class NanologExtensionTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();

    /**
     * Set while a fixture runs through {@link #run}.
     */
    private static volatile boolean running;

    @TempDir
    Path root;

    private final Consumer<Thread> originalHooks = Summary.shutdownHooks;

    @BeforeEach
    public void setUp() {
        Summary.shutdownHooks = hook -> { };
        Summary.reset();
        RunLog.reset();
        NanologExtension.reset();
        LOG.init(new ConsoleLogger().setShowDate(false));
    }

    @AfterEach
    public void tearDown() {
        System.clearProperty(DefaultLoggerCreator.PROPERTY_JUNIT_OUTPUT);
        System.clearProperty(DefaultLoggerCreator.PROPERTY_RUN);
        System.clearProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR);
        NanologExtension.reset();
        RunLog.reset();
        Summary.mode = Summary.Mode.OFF;
        Summary.reset();
        Summary.shutdownHooks = originalHooks;
        LOG.init();
    }

    private static TestExecutionSummary run(Class<?> fixture, String... configuration) {
        LauncherDiscoveryRequestBuilder request = LauncherDiscoveryRequestBuilder.request().selectors(selectClass(fixture));
        for (int i = 0; i < configuration.length; i += 2) {
            request.configurationParameter(configuration[i], configuration[i + 1]);
        }
        LauncherDiscoveryRequest built = request.build();
        Launcher launcher = LauncherFactory.create(LauncherConfig.builder()
                                                                 .enableTestExecutionListenerAutoRegistration(false)
                                                                 .build());
        SummaryGeneratingListener summary = new SummaryGeneratingListener();
        running = true;
        try {
            launcher.execute(built, summary);
        } finally {
            running = false;
        }
        assertEquals(0, LOG.openCaptures(), "every buffer is closed");
        return summary.getSummary();
    }

    private static void onlyThroughRun() {
        Assumptions.assumeTrue(running, "a fixture of NanologExtensionTest");
    }

    // FIXTURES

    @ExtendWith(NanologExtension.class)
    static class Outcomes {

        @Test
        void passes() {
            onlyThroughRun();
            LOG.info("noise of a passing test");
        }

        @Test
        void fails() {
            onlyThroughRun();
            LOG.info("evidence");
            LOG.warn("more evidence");
            throw new AssertionError("failed on purpose");
        }

        @Test
        void isAborted() {
            onlyThroughRun();
            LOG.info("noise of an aborted test");
            Assumptions.assumeTrue(false);
        }

        @Test
        void failsSilently() {
            onlyThroughRun();
            throw new AssertionError("nothing logged");
        }
    }

    @Test
    public void testOnlyTheLogOfAFailingTestIsPrinted() {
        TestExecutionSummary summary = run(Outcomes.class);

        assertEquals(2, summary.getTotalFailureCount());
        assertEquals("--- log of Outcomes.fails ---" + NL +
                     "INFO [NanologExtensionTest] evidence" + NL +
                     "WARN [NanologExtensionTest] more evidence" + NL +
                     "--- end of the log of Outcomes.fails ---" + NL, getOutAndClear());
    }

    @Test
    public void testAllPrintsEveryLogAsItIsWritten() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_JUNIT_OUTPUT, " ALL ");

        assertEquals(2, run(Outcomes.class).getTotalFailureCount());
        run(ClassSetup.class);

        String out = getOutAndClear();
        assertTrue(out.contains("INFO [NanologExtensionTest] noise of a passing test" + NL), out);
        assertTrue(out.contains("INFO [NanologExtensionTest] evidence" + NL), out);
        assertTrue(out.contains("INFO [NanologExtensionTest] fixture loaded" + NL), out);
        assertFalse(out.contains("--- log of"), out);
    }

    @Test
    public void testNonePrintsNoLog() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_JUNIT_OUTPUT, "none");

        run(Outcomes.class);
        run(ClassWithAFailure.class);
        run(BrokenClassSetup.class);

        assertEquals("", getOutAndClear());
    }

    // THE LOG OF THE CLASS

    /**
     * Stands for {@code SpringExtension}, which starts the application context when JUnit creates the test instance,
     * before any {@code beforeEach} (#100).
     */
    static class ContextExtension implements TestInstancePostProcessor {

        @Override
        public void postProcessTestInstance(Object testInstance, ExtensionContext context) {
            if (running) {
                LOG.info("context of " + testInstance.getClass().getSimpleName());
            }
        }
    }

    @ExtendWith(ContextExtension.class)
    abstract static class ContextTest {
    }

    @ExtendWith(NanologExtension.class)
    static class ClassSetup extends ContextTest {

        @BeforeAll
        static void loadFixture() {
            onlyThroughRun();
            LOG.info("fixture loaded");
        }

        @AfterAll
        static void removeFixture() {
            if (running) {
                LOG.info("fixture removed");
            }
        }

        @Test
        void first() {
            onlyThroughRun();
            LOG.info("first test");
        }

        @Test
        void second() {
            onlyThroughRun();
            LOG.info("second test");
        }
    }

    @Test
    public void testTheLogOfAPassingClassGoesOnlyToTheRunLog() throws IOException {
        runLogOn();

        TestExecutionSummary summary = run(ClassSetup.class);

        assertEquals(2, summary.getTestsSucceededCount(), String.valueOf(summary.getFailures()));
        assertEquals("", getOutAndClear(), "the console stays quiet while the class passes");
        List<String> lines = Files.readAllLines(root.resolve("latest.log"), StandardCharsets.UTF_8);
        assertEquals(6, lines.size(), String.join(NL, lines));
        for (String message : new String[]{"fixture loaded", "first test", "second test", "fixture removed"}) {
            assertEquals(1, lines.stream().filter(line -> line.endsWith("] " + message)).count(), message);
        }
        assertEquals(2, lines.stream().filter(line -> line.endsWith("] context of ClassSetup")).count(),
                     "one test instance for each test");
    }

    @ExtendWith(NanologExtension.class)
    static class ClassWithAFailure extends ContextTest {

        @BeforeAll
        static void loadFixture() {
            onlyThroughRun();
            LOG.info("fixture loaded");
        }

        @Test
        void fails() {
            onlyThroughRun();
            LOG.info("evidence");
            throw new AssertionError("failed on purpose");
        }
    }

    @Test
    public void testTheLogOfAClassIsPrintedWhenOneOfItsTestsFails() {
        run(ClassWithAFailure.class);

        assertEquals("--- log of ClassWithAFailure.fails ---" + NL +
                     "INFO [NanologExtensionTest] evidence" + NL +
                     "--- end of the log of ClassWithAFailure.fails ---" + NL +
                     "--- log of class ClassWithAFailure ---" + NL +
                     "INFO [NanologExtensionTest] fixture loaded" + NL +
                     "INFO [NanologExtensionTest] context of ClassWithAFailure" + NL +
                     "--- end of the log of class ClassWithAFailure ---" + NL, getOutAndClear());
    }

    @ExtendWith(NanologExtension.class)
    static class BrokenClassSetup {

        @BeforeAll
        static void loadFixture() {
            onlyThroughRun();
            LOG.warn("database not reachable");
            throw new IllegalStateException("no database");
        }

        @Test
        void neverRuns() {
            onlyThroughRun();
        }
    }

    @Test
    public void testTheLogOfAClassIsPrintedWhenItsSetupFails() {
        TestExecutionSummary summary = run(BrokenClassSetup.class);

        assertEquals(1, summary.getTotalFailureCount());
        assertEquals("--- log of class BrokenClassSetup ---" + NL +
                     "WARN [NanologExtensionTest] database not reachable" + NL +
                     "--- end of the log of class BrokenClassSetup ---" + NL, getOutAndClear());
    }

    @ExtendWith(NanologExtension.class)
    static class Outer {

        @BeforeAll
        static void setUpOuter() {
            onlyThroughRun();
            LOG.info("outer set up");
        }

        @Nested
        @ExtendWith(ContextExtension.class)
        class Inner {

            @Test
            void passes() {
                onlyThroughRun();
                LOG.info("inner test");
            }
        }
    }

    @Test
    public void testANestedClassHoldsBackItsOwnLog() throws IOException {
        runLogOn();

        TestExecutionSummary summary = run(Outer.class);

        assertEquals(1, summary.getTestsSucceededCount(), String.valueOf(summary.getFailures()));
        assertEquals("", getOutAndClear());
        List<String> lines = Files.readAllLines(root.resolve("latest.log"), StandardCharsets.UTF_8);
        assertTrue(lines.stream().anyMatch(line -> line.endsWith("] context of Inner")), String.join(NL, lines));
        assertTrue(lines.stream().anyMatch(line -> line.endsWith("] inner test")), String.join(NL, lines));
        assertEquals(new HashSet<>(lines).size(), lines.size(), "each entry is held back once: " + String.join(NL, lines));
    }

    @Test
    public void testAnInvalidSettingIsReportedOnceAndGivesFailed() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_JUNIT_OUTPUT, "some");
        getErrAndClear();

        run(Outcomes.class);
        run(Outcomes.class);

        assertEquals("[NANOLOG] Invalid value \"some\" of \"nanolog.junit.output\": expected failed, all or none; set as a system property" + NL,
                     getErrAndClear());
        assertTrue(getOutAndClear().contains("--- log of Outcomes.fails ---"));
    }

    @Test
    public void testALoggerThatFailsWhileTheLogIsPrintedIsReported() {
        LOG.init(entry -> {
            throw new IllegalStateException("broken");
        });
        getErrAndClear();

        TestExecutionSummary summary = run(Outcomes.class);

        assertEquals(2, summary.getTotalFailureCount(), "only the failing tests fail");
        String err = getErrAndClear();
        assertEquals(1, err.split("the log of a failed test is not printed", -1).length - 1, err);
        LOG.clearReportedLoggerFailures();
    }

    @ExtendWith(NanologExtension.class)
    static class Repeated {

        @BeforeAll
        static void setUpRepetitions() {
            onlyThroughRun();
            LOG.info("set up the repetitions");
        }

        @RepeatedTest(2)
        void repeated() {
            onlyThroughRun();
            LOG.warn("attempt");
            throw new AssertionError("failed on purpose");
        }
    }

    @Test
    public void testATestWithADisplayNameOfItsOwnIsNamedByBoth() {
        run(Repeated.class);

        String out = getOutAndClear();
        assertTrue(out.startsWith("--- log of Repeated.repeated repetition 1 of 2 ---" + NL), out);
        assertTrue(out.contains("--- end of the log of Repeated.repeated repetition 2 of 2 ---" + NL), out);
        assertTrue(out.endsWith("--- log of class Repeated ---" + NL +
                                "INFO [NanologExtensionTest] set up the repetitions" + NL +
                                "--- end of the log of class Repeated ---" + NL),
                   "a repetition fails inside its template, inside the class: " + out);
    }

    @ExtendWith(NanologExtension.class)
    static class Parallel {

        @Test
        void passes() {
            onlyThroughRun();
            for (int i = 0; i < 200; i++) {
                LOG.info("passing");
            }
        }

        @Test
        void fails() {
            onlyThroughRun();
            for (int i = 0; i < 200; i++) {
                LOG.info("failing");
            }
            throw new AssertionError("failed on purpose");
        }
    }

    @Test
    public void testParallelTestsKeepTheirLogsApart() {
        run(Parallel.class, "junit.jupiter.execution.parallel.enabled", "true",
            "junit.jupiter.execution.parallel.mode.default", "concurrent");

        String out = getOutAndClear();
        assertFalse(out.contains("passing"), out);
        assertEquals(200, out.split("INFO \\[NanologExtensionTest\\] failing", -1).length - 1);
    }

    @ExtendWith(NanologExtension.class)
    static class Assertions {

        @Test
        void installsAMemoryLogger() {
            onlyThroughRun();
            try (MemoryLogger log = MemoryLogger.install()) {
                LOG.warn("seen by the test");

                log.assertLogged(LogEntry.LogEntryLevel.WARN, "seen by the test");
            }
        }

        @Test
        void capturesItsThread() {
            onlyThroughRun();
            try (MemoryLogger log = MemoryLogger.capture()) {
                LOG.debug("seen by the capture");

                log.assertLogged(LogEntry.LogEntryLevel.DEBUG, "seen by the capture");
            }
        }
    }

    @Test
    public void testTheAssertionsOfMemoryLoggerStillWork() {
        TestExecutionSummary summary = run(Assertions.class);

        assertEquals(2, summary.getTestsSucceededCount(), String.valueOf(summary.getFailures()));
        assertEquals("", getOutAndClear());
    }

    @Test
    public void testWithNoneTheRunLogStillGetsTheLogOfEveryTest() throws IOException {
        System.setProperty(DefaultLoggerCreator.PROPERTY_JUNIT_OUTPUT, "none");
        testTheRunLogGetsTheLogOfEveryTest();
    }

    /**
     * Turns the run log on, in the temporary directory.
     */
    private void runLogOn() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        LOG.init();
        getErrAndClear();
        getOutAndClear();
    }

    @Test
    public void testTheRunLogGetsTheLogOfEveryTest() throws IOException {
        runLogOn();

        run(Outcomes.class);

        List<String> lines = Files.readAllLines(root.resolve("latest.log"), StandardCharsets.UTF_8);
        assertEquals(4, lines.size(), String.join(NL, lines));
        for (String message : new String[]{"noise of a passing test", "evidence", "more evidence", "noise of an aborted test"}) {
            assertEquals(1, lines.stream().filter(line -> line.endsWith("] " + message)).count(), message);
        }
        assertFalse(getOutAndClear().contains("noise"));
    }

    @ExtendWith(NanologExtension.class)
    static class Provoked {

        @Test
        void assertsOnAWarning() {
            onlyThroughRun();
            LOG.info("before the capture");
            try (MemoryLogger log = MemoryLogger.capture()) {
                LOG.warn("provoked on purpose");

                log.assertLogged(LogEntry.LogEntryLevel.WARN, "provoked on purpose");
            }
        }
    }

    @Test
    public void testTheRunSummaryCountsWhatTheExtensionHoldsBackForTheRunLog() throws IOException {
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN, "on");
        System.setProperty(DefaultLoggerCreator.PROPERTY_RUN_DIR, root.toString());
        LOG.init();
        Summary.mode = Summary.Mode.ON;
        getErrAndClear();
        getOutAndClear();

        run(Provoked.class);

        List<String> lines = Files.readAllLines(root.resolve("latest.log"), StandardCharsets.UTF_8);
        assertEquals(2, lines.size(), "the buffer of the test passes the captured warning on: " + String.join(NL, lines));
        String header = Summary.lines().get(0);
        assertTrue(header.startsWith("Run summary: 2 entries: 1 WARN, 1 INFO;"), header);
    }
}
