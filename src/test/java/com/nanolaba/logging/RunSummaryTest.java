package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.nanolaba.logging.DefaultLoggerCreator.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The run summary: a short digest at the end of the run, which survives when an agent keeps only the end of the output.
 */
class RunSummaryTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();
    private static final String FILE = "RunSummaryTest.java:";

    @TempDir
    Path root;

    private final Map<String, String> environment = new HashMap<>();
    private final Supplier<Set<String>> originalNames = DefaultLoggerCreator.environmentNames;
    private final Consumer<Thread> originalHooks = Summary.shutdownHooks;
    private final List<Thread> hooks = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
        DefaultLoggerCreator.environmentNames = environment::keySet;
        Summary.shutdownHooks = hooks::add;
        Summary.reset();
        DbgSource.clearCache();
        LOG.clearLimits();
        RunLog.reset();
        Agent.reset();
    }

    @AfterEach
    public void tearDown() {
        DefaultLoggerCreator.environment = System::getenv;
        DefaultLoggerCreator.environmentNames = originalNames;
        for (String property : new String[]{PROPERTY_SUMMARY, PROPERTY_AGENT, PROPERTY_RUN, PROPERTY_RUN_DIR}) {
            System.clearProperty(property);
        }
        Summary.mode = Summary.Mode.OFF;
        Summary.reset();
        Summary.shutdownHooks = originalHooks;
        RunLog.reset();
        Agent.reset();
        LOG.clearLimits();
        LOG.init();
    }

    private static int lineOf(Throwable here) {
        return here.getStackTrace()[0].getLineNumber();
    }

    /**
     * Turns the summary on and logs to memory, where nothing is printed.
     */
    private void on() {
        Summary.mode = Summary.Mode.ON;
        LOG.init(new MemoryLogger());
    }

    private static IOException timeout() {
        IOException e = new IOException("timeout");
        e.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("com.acme.PaymentService", "charge", "PaymentService.java", 87),
                new StackTraceElement("org.junit.Runner", "run", "Runner.java", 1),
                new StackTraceElement("org.junit.Runner", "runAll", "Runner.java", 2)});
        return e;
    }

    // SWITCHING IT ON

    @Test
    public void testOffByDefaultWithoutAnAgent() {
        LOG.init();

        assertSame(Summary.Mode.OFF, Summary.mode);
        assertTrue(hooks.isEmpty());
        LOG.error("not counted");
        assertEquals(Arrays.asList("Run summary: no entries"), Summary.lines());
    }

    @Test
    public void testOnByDefaultInAgentMode() {
        environment.put("CLAUDECODE", "1");
        System.setProperty(PROPERTY_RUN, "off");
        LOG.init();

        assertSame(Summary.Mode.ON, Summary.mode);
        assertEquals(1, hooks.size(), "the hook prints it at exit");
        assertEquals("nanolog-summary", hooks.get(0).getName());
        LOG.init();
        assertEquals(1, hooks.size(), "one hook per JVM");
    }

    @Test
    public void testTheSettingDecides() {
        environment.put("CLAUDECODE", "1");
        System.setProperty(PROPERTY_RUN, "off");
        System.setProperty(PROPERTY_SUMMARY, " OFF ");
        LOG.init();
        assertSame(Summary.Mode.OFF, Summary.mode);
        assertTrue(hooks.isEmpty());

        environment.clear();
        System.setProperty(PROPERTY_SUMMARY, "errors");
        LOG.init();
        assertSame(Summary.Mode.ERRORS, Summary.mode);
        assertEquals(1, hooks.size());

        System.setProperty(PROPERTY_SUMMARY, "on");
        LOG.init();
        assertSame(Summary.Mode.ON, Summary.mode);
        getErrAndClear();

        System.setProperty(PROPERTY_SUMMARY, "always");
        LOG.init();
        assertSame(Summary.Mode.OFF, Summary.mode);
        assertEquals("[NANOLOG] Invalid value \"always\" of \"nanolog.summary\": expected off, on or errors; set as a system property" + NL,
                     getErrAndClear());
    }

    // CONTENTS

    @Test
    public void testCountsByLevelAndGroupsWarningsAndErrorsByCallSite() {
        on();
        LOG.trace("t");
        LOG.debug("d");
        LOG.info("i");
        LOG.info("i");
        int retry = lineOf(new Throwable()) + 2;
        for (int i = 1; i <= 3; i++) {
            LOG.warn("Retry {} of {}", i, 3);
        }
        int charge = lineOf(new Throwable()) + 1;
        LOG.error(timeout(), "Charge of {} failed", 17);
        int second = lineOf(new Throwable()) + 1;
        LOG.error("Second");

        assertEquals(Arrays.asList(
                "Run summary: 9 entries: 2 ERROR, 3 WARN, 2 INFO, 1 DEBUG, 1 TRACE",
                "  ERROR x1 at " + FILE + charge + ": Charge of {} failed",
                "      java.io.IOException: timeout",
                "          at com.acme.PaymentService.charge(PaymentService.java:87)",
                "          ... 2 frames excluded",
                "  ERROR x1 at " + FILE + second + ": Second",
                "  WARN x3 at " + FILE + retry + ": Retry {} of {}"), Summary.lines());
    }

    @Test
    public void testAnEntryThatACaptureKeepsFromTheLoggerIsNotCounted() {
        on();
        LOG.info("before the capture");
        try (MemoryLogger capture = MemoryLogger.capture()) {
            LOG.warn("a warning the test provokes and asserts on");
            assertEquals(1, capture.getEntries().size());
        }
        LOG.info("after the capture");

        assertEquals(Arrays.asList("Run summary: 2 entries: 2 INFO"), Summary.lines(),
                     "the summary counts what reaches the logger and the run log, as #97 asks");
    }

    /**
     * A logger with {@code DEBUG} off.
     */
    private static final class WithoutDebug extends MemoryLogger {
        @Override
        public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
            return level != LogEntry.LogEntryLevel.DEBUG;
        }
    }

    @Test
    public void testAnEntryThatACapturePassesOnIsCounted() {
        Summary.mode = Summary.Mode.ON;
        LOG.init(new WithoutDebug());
        int line;
        try (MemoryLogger capture = MemoryLogger.captureForwarding()) {
            LOG.debug("only the capture takes it");
            line = lineOf(new Throwable()) + 1;
            LOG.warn("passed on to the logger");
            assertEquals(2, capture.getEntries().size());
        }

        assertEquals(Arrays.asList("Run summary: 1 entry: 1 WARN", "  WARN x1 at " + FILE + line + ": passed on to the logger"),
                     Summary.lines());
    }

    @Test
    public void testOnlyTheFirstErrorWithAnExceptionShowsItsTrace() {
        on();
        LOG.warn(new IllegalStateException("a warning"), "Not shown");
        LOG.error("No exception");
        int first = lineOf(new Throwable()) + 1;
        LOG.error(timeout(), "First");
        LOG.error(new IllegalArgumentException("later"), "Later");

        List<String> lines = Summary.lines();
        assertEquals(Arrays.asList(
                "  ERROR x1 at " + FILE + first + ": First",
                "      java.io.IOException: timeout"), lines.subList(2, 4));
        assertEquals(1, lines.stream().filter(line -> line.contains("Exception")).count(), String.join(NL, lines));
    }

    @Test
    public void testACallWithAClassIsGroupedByTheClassAndTheMessage() {
        on();
        LOG.warn(String.class, "Explicit");
        LOG.warn(String.class, "Other");

        assertEquals(Arrays.asList("  WARN x1 at java.lang.String: Explicit", "  WARN x1 at java.lang.String: Other"),
                     Summary.lines().subList(1, 3), "without a call site, two messages are two kinds");
    }

    @Test
    public void testMessagesBuiltFromDataAtOneCallSiteAreOneKind() {
        on();
        int merge = lineOf(new Throwable()) + 2;
        for (int id = 1; id <= 4; id++) {
            LOG.warn("Merge of #" + id + " into #" + (id + 100) + " failed");
        }
        int delivery = lineOf(new Throwable()) + 2;
        for (int id = 1; id <= 4; id++) {
            LOG.warn("Delivery to {} failed", "user" + id + "@example.com");
        }

        assertEquals(Arrays.asList(
                "Run summary: 8 entries: 8 WARN",
                "  WARN x4 at " + FILE + merge + ": Merge of #1 into #101 failed (4 different messages)",
                "  WARN x4 at " + FILE + delivery + ": Delivery to {} failed"), Summary.lines());
    }

    @Test
    public void testTheDifferentMessagesOfACallSiteAreCountedUpToALimit() {
        on();
        for (int i = 0; i < Summary.MAX_MESSAGES; i++) {
            LOG.warn("Exactly " + i);
        }
        for (int i = 0; i <= Summary.MAX_MESSAGES; i++) {
            LOG.warn("More " + i);
        }

        List<String> lines = Summary.lines();
        assertTrue(lines.get(1).endsWith(": Exactly 0 (" + Summary.MAX_MESSAGES + " different messages)"), lines.get(1));
        assertTrue(lines.get(2).endsWith(": More 0 (" + Summary.MAX_MESSAGES + "+ different messages)"), lines.get(2));
    }

    @Test
    public void testAnEntryWithoutAMessageShowsItsException() {
        on();
        LOG.error(new IllegalStateException("broken"));

        assertTrue(Summary.lines().get(1).endsWith(": java.lang.IllegalStateException: broken"), Summary.lines().get(1));
    }

    @Test
    public void testAnEntryWithoutLevelSourceOrCallSiteIsCountedOnly() {
        Summary.count(new LogEntry(null, null, null, "odd"));
        Summary.count(new LogEntry(LogEntry.LogEntryLevel.WARN, null, null, null));

        assertEquals(Arrays.asList("Run summary: 2 entries: 1 WARN", "  WARN x1 at unknown source: "), Summary.lines());
    }

    @Test
    public void testALongMessageIsCut() {
        on();
        char[] text = new char[Summary.MAX_TEXT + 1];
        Arrays.fill(text, 'x');
        LOG.warn(new String(text));
        char[] fits = new char[Summary.MAX_TEXT];
        Arrays.fill(fits, 'y');
        LOG.warn(new String(fits));

        List<String> lines = Summary.lines();
        assertTrue(lines.get(1).endsWith(": " + new String(text, 0, Summary.MAX_TEXT) + "..."), lines.get(1));
        assertTrue(lines.get(2).endsWith(": " + new String(fits)), lines.get(2));
    }

    @Test
    public void testAMessageWithSeveralLinesShowsItsFirstLine() {
        on();
        int alert = lineOf(new Throwable()) + 1;
        LOG.warn("Retry limit reached for task 109\nURL: https://localhost:8080/task?id=109\nLimit: 1\nErrors:\n  1) provider 'x' not found");
        int windows = lineOf(new Throwable()) + 1;
        LOG.warn("Windows line\r\nSecond");
        int blank = lineOf(new Throwable()) + 1;
        LOG.warn("\n \r\nAfter blank lines\nDetails");
        int alone = lineOf(new Throwable()) + 1;
        LOG.warn("\nAlone after a line break");
        int trailing = lineOf(new Throwable()) + 1;
        LOG.warn("Blank lines after it\n  \n");

        assertEquals(Arrays.asList(
                "Run summary: 5 entries: 5 WARN",
                "  WARN x1 at " + FILE + alert + ": Retry limit reached for task 109...",
                "  WARN x1 at " + FILE + windows + ": Windows line...",
                "  WARN x1 at " + FILE + blank + ": After blank lines...",
                "  WARN x1 at " + FILE + alone + ": Alone after a line break",
                "  WARN x1 at " + FILE + trailing + ": Blank lines after it"), Summary.lines());
    }

    @Test
    public void testMessagesThatDifferAfterTheFirstLineAreDifferentMessages() {
        on();
        int site = lineOf(new Throwable()) + 2;
        for (int field = 1; field <= 3; field++) {
            LOG.warn("Validation failed\nfield " + field);
        }
        LOG.warn(String.class, "Explicit\nfirst");
        LOG.warn(String.class, "Explicit\nsecond");

        assertEquals(Arrays.asList(
                "Run summary: 5 entries: 5 WARN",
                "  WARN x3 at " + FILE + site + ": Validation failed... (3 different messages)",
                "  WARN x2 at java.lang.String: Explicit... (2 different messages)"), Summary.lines(),
                     "without a call site the line shown tells kinds apart");
    }

    @Test
    public void testAtMostTenKindsAreShown() {
        on();
        for (int i = 0; i < Summary.MAX_SHOWN + 2; i++) {
            // with a class there is no call site: the kinds are told apart by the message
            LOG.warn(RunSummaryTest.class, "Warning " + i);
        }

        List<String> lines = Summary.lines();
        assertEquals(Summary.MAX_SHOWN + 2, lines.size());
        assertTrue(lines.get(Summary.MAX_SHOWN).endsWith(": Warning " + (Summary.MAX_SHOWN - 1)), lines.get(Summary.MAX_SHOWN));
        assertEquals("  ... and 2 more kinds of WARN and ERROR", lines.get(Summary.MAX_SHOWN + 1));
    }

    @Test
    public void testTheKindsKeptAreBoundedButEveryEntryIsCounted() {
        on();
        for (int i = 0; i <= Summary.MAX_KEPT + 5; i++) {
            LOG.warn(RunSummaryTest.class, "Warning " + i % (Summary.MAX_KEPT + 5));
        }

        List<String> lines = Summary.lines();
        assertEquals("Run summary: " + (Summary.MAX_KEPT + 6) + " entries: " + (Summary.MAX_KEPT + 6) + " WARN", lines.get(0));
        assertTrue(lines.get(1).contains(" x2 "), "a kept kind is still counted: " + lines.get(1));
        assertEquals("  ... and " + (Summary.MAX_KEPT - Summary.MAX_SHOWN) + " more kinds of WARN and ERROR",
                     lines.get(lines.size() - 1));
    }

    @Test
    public void testALongTraceIsCut() {
        on();
        StackTraceElement[] frames = new StackTraceElement[Summary.MAX_TRACE_LINES * 2];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = new StackTraceElement("com.acme.Deep", "level" + i, "Deep.java", i + 1);
        }
        StackTraceElement[] causeFrames = new StackTraceElement[frames.length];
        for (int i = 0; i < causeFrames.length; i++) {
            causeFrames[i] = new StackTraceElement("com.acme.Other", "level" + i, "Other.java", i + 1);
        }
        IOException cause = new IOException("cause");
        cause.setStackTrace(causeFrames);
        IllegalStateException error = new IllegalStateException("top", cause);
        error.setStackTrace(frames);
        LOG.error(error, "Deep");

        List<String> lines = Summary.lines();
        assertEquals(2 + Summary.MAX_TRACE_LINES + 1, lines.size(), String.join(NL, lines));
        // 12 lines for each throwable: itself, 10 frames and the rest
        assertEquals("      ... " + (24 - Summary.MAX_TRACE_LINES) + " more lines", lines.get(lines.size() - 1));
    }

    @Test
    public void testATraceThatFitsIsShownWhole() {
        on();
        IOException cause = new IOException("cause");
        cause.setStackTrace(frames("com.acme.Cause", 3));
        IllegalStateException error = new IllegalStateException("top", cause);
        error.setStackTrace(frames("com.acme.Top", 10));
        LOG.error(error, "Fits");

        // 11 lines for the error, 4 for its cause
        List<String> lines = Summary.lines();
        assertEquals(2 + Summary.MAX_TRACE_LINES, lines.size(), String.join(NL, lines));
        assertEquals("          at com.acme.Cause.level2(Cause.java:3)", lines.get(lines.size() - 1));
    }

    @Test
    public void testOneLineOverIsShownInsteadOfItsCount() {
        // the line that would count it takes as much room as the line itself
        on();
        IOException cause = new IOException("cause");
        cause.setStackTrace(frames("com.acme.Cause", 4));
        IllegalStateException error = new IllegalStateException("top", cause);
        error.setStackTrace(frames("com.acme.Top", 10));
        LOG.error(error, "One over");

        // 11 lines for the error, 5 for its cause
        List<String> lines = Summary.lines();
        assertEquals(2 + Summary.MAX_TRACE_LINES + 1, lines.size(), String.join(NL, lines));
        assertEquals("          at com.acme.Cause.level3(Cause.java:4)", lines.get(lines.size() - 1));
    }

    @Test
    public void testTwoLinesOverAreCounted() {
        on();
        IOException cause = new IOException("cause");
        cause.setStackTrace(frames("com.acme.Cause", 5));
        IllegalStateException error = new IllegalStateException("top", cause);
        error.setStackTrace(frames("com.acme.Top", 10));
        LOG.error(error, "Two over");

        List<String> lines = Summary.lines();
        assertEquals(2 + Summary.MAX_TRACE_LINES + 1, lines.size(), String.join(NL, lines));
        assertEquals("      ... 2 more lines", lines.get(lines.size() - 1));
    }

    @Test
    public void testOneKindOverIsShownInsteadOfItsCount() {
        on();
        for (int i = 0; i < Summary.MAX_SHOWN + 1; i++) {
            LOG.warn(RunSummaryTest.class, "Warning " + i);
        }

        List<String> lines = Summary.lines();
        assertEquals(Summary.MAX_SHOWN + 2, lines.size(), String.join(NL, lines));
        assertTrue(lines.get(Summary.MAX_SHOWN + 1).endsWith(": Warning " + Summary.MAX_SHOWN), lines.get(Summary.MAX_SHOWN + 1));
    }

    private static StackTraceElement[] frames(String className, int count) {
        StackTraceElement[] frames = new StackTraceElement[count];
        String file = className.substring(className.lastIndexOf('.') + 1) + ".java";
        for (int i = 0; i < count; i++) {
            frames[i] = new StackTraceElement(className, "level" + i, file, i + 1);
        }
        return frames;
    }

    @Test
    public void testATraceThatCannotBeShortenedIsPrintedTheUsualWay() {
        on();
        LOG.error(new IllegalStateException("unreadable") {
            @Override
            public StackTraceElement[] getStackTrace() {
                throw new UnsupportedOperationException("no frames");
            }
        }, "Odd");

        List<String> lines = Summary.lines();
        assertEquals("      com.nanolaba.logging.RunSummaryTest$1: unreadable", lines.get(2));
        assertTrue(lines.get(3).startsWith("          at com.nanolaba.logging.RunSummaryTest."), lines.get(3));
    }

    @Test
    public void testTheFullLogIsNamed() {
        System.setProperty(PROPERTY_SUMMARY, "on");
        System.setProperty(PROPERTY_RUN, "on");
        System.setProperty(PROPERTY_RUN_DIR, root.toString());
        LOG.init();
        getErrAndClear();
        getOutAndClear();

        LOG.info("one");

        assertEquals("Run summary: 1 entry: 1 INFO; the full log is in " + root.resolve("latest.log").toAbsolutePath(),
                     Summary.lines().get(0));
    }

    @Test
    public void testAFullLogWhoseLatestWasTakenIsNamedByItsOwnFile() throws IOException {
        System.setProperty(PROPERTY_SUMMARY, "on");
        System.setProperty(PROPERTY_RUN, "on");
        System.setProperty(PROPERTY_RUN_DIR, root.toString());
        LOG.init();
        getErrAndClear();
        getOutAndClear();
        Path own;
        try (Stream<Path> files = Files.list(root.resolve("runs"))) {
            own = files.filter(file -> !file.toString().endsWith(".errors.log")).collect(Collectors.toList()).get(0);
        }
        // another fork of Surefire starts in the same directory and takes latest.log (#93)
        RunLog later = RunLog.open(root, false, 10, 1024 * 1024, "later");
        try {
            LOG.info("one");

            assertEquals("Run summary: 1 entry: 1 INFO; the full log is in " + own.toAbsolutePath(), Summary.lines().get(0));
        } finally {
            later.close();
        }
    }

    /**
     * Turns the summary and the run log on, in the temporary directory.
     */
    private void withRunLog() {
        System.setProperty(PROPERTY_SUMMARY, "on");
        System.setProperty(PROPERTY_RUN, "on");
        System.setProperty(PROPERTY_RUN_DIR, root.toString());
        LOG.init();
    }

    @Test
    public void testEveryKindIsListedInAFileThatTheSummaryNamesWhenItLeavesKindsOut() throws IOException {
        withRunLog();
        for (int i = Summary.MAX_SHOWN; i > 0; i--) {
            // the list follows the level and the line, not the order of the run, so two runs can be compared (#99)
            LOG.warn(RunSummaryTest.class, "Warning " + (char) ('a' + i));
        }
        int site = lineOf(new Throwable()) + 2;
        for (int id = 1; id <= 2; id++) {
            LOG.warn("Merge of #" + id + " failed");
        }
        LOG.error(RunSummaryTest.class, "Failed");
        getErrAndClear();
        getOutAndClear();

        Summary.printOnce();

        Path list = root.resolve("latest.summary.log");
        String printed = getErrAndClear();
        assertTrue(printed.endsWith("[NANOLOG]   ... and 2 more kinds of WARN and ERROR; the full list is in " +
                                    list.toAbsolutePath() + NL), printed);
        List<String> expected = new ArrayList<>();
        expected.add("ERROR x1 at com.nanolaba.logging.RunSummaryTest: Failed");
        expected.add("WARN x2 at " + FILE + site + ": Merge of #1 failed (2 different messages)");
        for (int i = 1; i <= Summary.MAX_SHOWN; i++) {
            expected.add("WARN x1 at com.nanolaba.logging.RunSummaryTest: Warning " + (char) ('a' + i));
        }
        assertEquals(expected, Files.readAllLines(list, StandardCharsets.UTF_8));
    }

    @Test
    public void testTheListIsWrittenButNotNamedWhenEveryKindIsShown() throws IOException {
        withRunLog();
        LOG.warn(RunSummaryTest.class, "Careful");
        getErrAndClear();
        getOutAndClear();

        Summary.printOnce();

        String printed = getErrAndClear();
        assertFalse(printed.contains("summary.log"), printed);
        assertEquals(Arrays.asList("WARN x1 at com.nanolaba.logging.RunSummaryTest: Careful"),
                     Files.readAllLines(root.resolve("latest.summary.log"), StandardCharsets.UTF_8));
    }

    @Test
    public void testWithoutTheSummaryNoListIsWritten() {
        System.setProperty(PROPERTY_RUN, "on");
        System.setProperty(PROPERTY_RUN_DIR, root.toString());
        LOG.init();
        LOG.warn(RunSummaryTest.class, "Careful");

        Summary.printOnce();

        assertFalse(Files.exists(root.resolve("latest.summary.log")));
    }

    @Test
    public void testTheDbgCallsThatRanAreListed() {
        on();
        int line = lineOf(new Throwable()) + 1;
        LOG.dbg("x");

        assertEquals("  1 LOG.dbg call ran in this JVM: " + FILE + line, Summary.lines().get(1));
    }

    // PRINTING

    @Test
    public void testItIsPrintedOnceWithThePrefixOfNanolog() {
        on();
        LOG.warn("Careful");
        LOG.error(timeout(), "Failed");

        Summary.printOnce();
        String printed = getErrAndClear();
        StringBuilder expected = new StringBuilder();
        for (String line : Summary.lines()) {
            expected.append("[NANOLOG] ").append(line).append(NL);
        }
        assertEquals(expected.toString(), printed);
        assertTrue(printed.contains("[NANOLOG]           at com.acme.PaymentService.charge"), "tabs become spaces: " + printed);

        Summary.printOnce();
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testItIsPrintedInOneWrite() {
        // Surefire relays each println of a fork as one piece, so two forks that end together do not mix their lines (#93)
        List<String> printed = new ArrayList<>();
        System.setErr(new PrintStream(new ByteArrayOutputStream()) {
            @Override
            public void println(String line) {
                printed.add(line);
            }
        });
        on();
        LOG.warn("Careful");
        LOG.error("Failed");

        Summary.printOnce();

        StringBuilder expected = new StringBuilder();
        for (String line : Summary.lines()) {
            expected.append(expected.length() == 0 ? "" : NL).append("[NANOLOG] ").append(line);
        }
        assertEquals(Arrays.asList(expected.toString()), printed);
    }

    @Test
    public void testWithoutTheSummaryOnlyTheDbgCallsArePrinted() {
        LOG.init(new MemoryLogger());
        Summary.printOnce();
        assertEquals("", getErrAndClear(), "nothing to say");

        Summary.reset();
        int line = lineOf(new Throwable()) + 1;
        LOG.dbg("x");
        Summary.printOnce();
        assertEquals("[NANOLOG] 1 LOG.dbg call ran in this JVM: " + FILE + line + NL, getErrAndClear());
    }

    @Test
    public void testErrorsPrintsTheSummaryOnlyAfterAWarning() {
        Summary.mode = Summary.Mode.ERRORS;
        LOG.init(new MemoryLogger());
        LOG.info("fine");
        Summary.printOnce();
        assertEquals("", getErrAndClear());

        Summary.reset();
        LOG.info("fine");
        LOG.warn("not fine");
        Summary.printOnce();
        assertTrue(getErrAndClear().startsWith("[NANOLOG] Run summary: 2 entries: 1 WARN, 1 INFO" + NL));

        Summary.reset();
        LOG.error("an error alone");
        Summary.printOnce();
        assertTrue(getErrAndClear().startsWith("[NANOLOG] Run summary: 1 entry: 1 ERROR" + NL));
    }

    @Test
    public void testExactlyTenKindsNeedNoMoreLine() {
        on();
        for (int i = 0; i < Summary.MAX_SHOWN; i++) {
            LOG.warn(RunSummaryTest.class, "Warning " + i);
        }

        assertEquals(1 + Summary.MAX_SHOWN, Summary.lines().size(), String.join(NL, Summary.lines()));
    }

    @Test
    public void testTheJunitListenerPrintsItBeforeTheHookCould() {
        on();
        LOG.warn("Careful");
        LOG.info("x");
        Summary.registerHook();

        new SummaryListener().testPlanExecutionFinished(null);
        assertTrue(getErrAndClear().startsWith("[NANOLOG] Run summary: 2 entries"));

        hooks.get(0).run();
        assertEquals("", getErrAndClear(), "the hook has nothing left to print");
    }

    @Test
    public void testTheHookPrintsItWithoutJunit() {
        on();
        LOG.warn("Careful");
        Summary.registerHook();

        hooks.get(0).run();
        assertTrue(getErrAndClear().startsWith("[NANOLOG] Run summary: 1 entry: 1 WARN" + NL));

        new SummaryListener().testPlanExecutionFinished(null);
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testAPlanThatEndsInsideALauncherSessionWaitsForTheSessionToClose() {
        on();
        SummarySessionListener session = new SummarySessionListener();
        SummaryListener plans = new SummaryListener();
        session.launcherSessionOpened(null);

        LOG.warn("Warning of the first class");
        plans.testPlanExecutionFinished(null);
        assertEquals("", getErrAndClear(), "Surefire runs the next class of the fork as another plan of the session");

        LOG.warn("Warning of the second class");
        plans.testPlanExecutionFinished(null);
        session.launcherSessionClosed(null);
        String err = getErrAndClear();
        assertTrue(err.startsWith("[NANOLOG] Run summary: 2 entries: 2 WARN" + NL), err);
        assertTrue(err.contains("Warning of the first class") && err.contains("Warning of the second class"), err);
    }

    @Test
    public void testASessionWithoutAPlanPrintsNothing() {
        on();
        LOG.warn("Careful");
        Summary.registerHook();
        SummarySessionListener session = new SummarySessionListener();

        session.launcherSessionOpened(null);
        session.launcherSessionClosed(null);
        assertEquals("", getErrAndClear(), "Surefire opens and closes a session in a JVM that runs no test");

        hooks.get(0).run();
        assertTrue(getErrAndClear().startsWith("[NANOLOG] Run summary: 1 entry: 1 WARN" + NL));
    }

    @Test
    public void testResetForgetsTheOpenSessions() {
        on();
        new SummarySessionListener().launcherSessionOpened(null);
        Summary.reset();

        LOG.warn("Careful");
        new SummaryListener().testPlanExecutionFinished(null);
        assertTrue(getErrAndClear().startsWith("[NANOLOG] Run summary: 1 entry: 1 WARN" + NL));
    }

    @Test
    public void testTheJunitPlatformFindsTheSessionListener() {
        boolean found = false;
        for (org.junit.platform.launcher.LauncherSessionListener listener
                : java.util.ServiceLoader.load(org.junit.platform.launcher.LauncherSessionListener.class)) {
            found |= listener instanceof SummarySessionListener;
        }
        assertTrue(found, "META-INF/services/org.junit.platform.launcher.LauncherSessionListener");
    }

    @Test
    public void testAHookThatCannotBeRegisteredIsIgnored() {
        Summary.shutdownHooks = hook -> {
            throw new IllegalStateException("Shutdown in progress");
        };

        Summary.registerHook();
        Summary.registerHook();
    }
}
