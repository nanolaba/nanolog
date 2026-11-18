package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.ERROR;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LOG.captureSystemStreams()}: lines printed to {@code System.out} and {@code System.err} become entries.
 */
class SystemStreamsTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();

    private final Map<String, String> environment = new HashMap<>();
    private final Function<Throwable, StackTraceElement[]> originalStackTrace = CallerDetection.ThrowableWalk.stackTraceOf;
    private PrintStream testOut;
    private PrintStream testErr;

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
        testOut = System.out;
        testErr = System.err;
    }

    @AfterEach
    public void tearDown() {
        LOG.restoreSystemStreams();
        CallerDetection.ThrowableWalk.stackTraceOf = originalStackTrace;
        System.clearProperty(DefaultLoggerCreator.PROPERTY_CAPTURE_SYSTEM_STREAMS);
        DefaultLoggerCreator.environment = System::getenv;
        LOG.init();
    }

    private static List<String> messages(MemoryLogger memory) {
        return memory.getMessages();
    }

    @Test
    public void testLinesBecomeEntries() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.captureSystemStreams();

            int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
            System.out.println("Started on port 8080");
            System.err.println("Disk almost full");

            assertEquals(Arrays.asList("Started on port 8080", "Disk almost full"), messages(memory));
            LogEntry out = memory.getEntries().get(0);
            assertEquals(INFO, out.getLevel());
            assertEquals(SystemStreamsTest.class, out.getSourceClass());
            assertEquals("testLinesBecomeEntries", out.getCaller().getMethodName());
            assertEquals(line, out.getCaller().getLineNumber());
            assertEquals(ERROR, memory.getEntries().get(1).getLevel());
            assertEquals("", getOutAndClear() + getErrAndClear(), "nothing reaches the streams themselves");
        }
    }

    @Test
    public void testTheConsoleWritesToTheOriginalStreamsWithoutALoop() {
        LOG.init(new ConsoleLogger().setShowDate(false));
        LOG.captureSystemStreams();

        System.out.println("from println");
        LOG.info("direct");
        System.err.println("from the error stream");

        assertEquals("INFO [SystemStreamsTest] from println" + NL + "INFO [SystemStreamsTest] direct" + NL, getOutAndClear());
        assertEquals("ERROR [SystemStreamsTest] from the error stream" + NL, getErrAndClear());
    }

    @Test
    public void testALoggerThatPrintsDoesNotLoop() {
        LOG.init(entry -> System.out.println("custom: " + entry.getFormattedMessage()));
        LOG.captureSystemStreams();

        System.out.println("captured");
        LOG.info("logged");

        assertEquals("custom: captured" + NL + "custom: logged" + NL, getOutAndClear());
    }

    @Test
    public void testALoggerThatPrintsWhileAskedForTheLevelDoesNotLoop() {
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
            }

            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
                System.out.println("asked for " + level);
                return false;
            }
        });
        LOG.captureSystemStreams();

        System.out.println("printed");

        assertEquals("asked for INFO" + NL, getOutAndClear(), "the level is asked outside the delivery of an entry");
    }

    @Test
    public void testPartialLinesSeparatorsAndFlush() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.captureSystemStreams();

            System.out.print("par");
            System.out.print("tial\n");
            System.out.print("windows\r\nunix\n");
            System.out.print("\n");
            System.out.print("pending");
            assertEquals(Arrays.asList("partial", "windows", "unix"), messages(memory), "an empty line is no entry");
            System.out.flush();
            System.out.flush();
            System.out.printf("%s and %d%n", "printf", 2);
            System.out.println("Привет ✓ {} \\{}");
            System.out.write('b');
            System.out.write('y');
            System.out.write('\n');

            assertEquals(Arrays.asList("partial", "windows", "unix", "pending", "printf and 2", "Привет ✓ {} \\{}", "by"), messages(memory));
            assertEquals(SystemStreamsTest.class, memory.getEntries().get(4).getSourceClass(), "the frames of the formatter are skipped");
        }
    }

    @Test
    public void testAStackTraceBecomesErrorEntries() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.captureSystemStreams();

            new IllegalStateException("boom").printStackTrace();

            LogEntry first = memory.getEntries().get(0);
            assertEquals("java.lang.IllegalStateException: boom", first.getFormattedMessage());
            assertEquals(ERROR, first.getLevel());
            assertEquals(SystemStreamsTest.class, first.getSourceClass(), "the frames of Throwable are skipped");
            assertTrue(memory.getEntries().get(1).getFormattedMessage().startsWith("\tat com.nanolaba.logging.SystemStreamsTest."),
                       memory.getEntries().get(1).getFormattedMessage());
        }
    }

    @Test
    public void testTheLinesOfEachThreadStayApart() throws InterruptedException {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.captureSystemStreams();

            System.out.print("main ");
            Thread other = new Thread(() -> System.out.println("other"));
            other.start();
            other.join();
            System.out.println("line");

            assertEquals(Arrays.asList("other", "main line"), messages(memory));
        }
    }

    @Test
    public void testWithoutACallerTheStreamIsTheSource() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.captureSystemStreams();
            CallerDetection.ThrowableWalk.stackTraceOf = t -> new StackTraceElement[0];

            System.out.println("nobody");
            System.err.println("nobody either");

            assertEquals(SystemOut.class, memory.getEntries().get(0).getSourceClass());
            assertEquals(SystemErr.class, memory.getEntries().get(1).getSourceClass());
        }
    }

    @Test
    public void testAFrameOfAClassThatCannotBeLoadedIsSkipped() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.captureSystemStreams();
            StackTraceElement here = new Throwable().getStackTrace()[0];
            CallerDetection.ThrowableWalk.stackTraceOf = t -> new StackTraceElement[]{
                    new StackTraceElement("com.acme.Missing", "print", "Missing.java", 1), here};

            System.out.println("through an unknown class");

            assertEquals(SystemStreamsTest.class, memory.getEntries().get(0).getSourceClass());
            assertEquals(here, memory.getEntries().get(0).getCaller());
        }
    }

    @Test
    public void testALoggerThatFlushesReachesABufferedStream() {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        System.setOut(new PrintStream(new java.io.BufferedOutputStream(bytes), false));
        try {
            LOG.init(entry -> {
                System.out.print("seen " + entry.getFormattedMessage());
                System.out.flush();
            });
            LOG.captureSystemStreams();

            LOG.info("x");

            assertEquals("seen x", bytes.toString());
        } finally {
            LOG.restoreSystemStreams();
            System.setOut(testOut);
        }
    }

    @Test
    public void testALineAtADisabledLevelIsDropped() {
        List<String> logged = new java.util.ArrayList<>();
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
                logged.add(entry.getFormattedMessage());
            }

            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
                return level == ERROR;
            }
        });
        LOG.captureSystemStreams();

        System.out.println("info is off");
        System.err.println("error is on");

        assertEquals(Collections.singletonList("error is on"), logged);
        assertEquals("", getOutAndClear() + getErrAndClear());
    }

    @Test
    public void testDiagnosticsGoToTheOriginalStream() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.captureSystemStreams();

            DefaultLoggerCreator.printDiagnostic("a diagnostic");
            DefaultLoggerCreator.printNotice("a notice");

            assertEquals(Collections.emptyList(), memory.getEntries());
            assertEquals("[NANOLOG] a diagnostic" + NL + "[NANOLOG] a notice" + NL, getErrAndClear());
        }
    }

    @Test
    public void testCaptureAndRestore() {
        LOG.captureSystemStreams();
        PrintStream capturing = System.out;
        assertNotSame(testOut, capturing);
        assertNotSame(testErr, System.err);

        LOG.captureSystemStreams();
        assertSame(capturing, System.out, "capturing twice changes nothing");

        LOG.restoreSystemStreams();
        assertSame(testOut, System.out);
        assertSame(testErr, System.err);
        LOG.restoreSystemStreams();
        assertSame(testOut, System.out, "restoring twice changes nothing");
    }

    @Test
    public void testRestoreLeavesAStreamThatWasReplacedMeanwhile() {
        LOG.captureSystemStreams();
        PrintStream other = new PrintStream(new java.io.ByteArrayOutputStream());
        System.setOut(other);

        LOG.restoreSystemStreams();

        assertSame(other, System.out);
        assertSame(testErr, System.err);
        System.setOut(testOut);
    }

    @Test
    public void testTheSetting() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_CAPTURE_SYSTEM_STREAMS, "true");
        LOG.init();
        assertNotSame(testOut, System.out);
        LOG.restoreSystemStreams();

        System.clearProperty(DefaultLoggerCreator.PROPERTY_CAPTURE_SYSTEM_STREAMS);
        environment.put("NANOLOG_CAPTURESYSTEMSTREAMS", "maybe");
        LOG.init();
        assertSame(testOut, System.out);
        assertEquals("[NANOLOG] Invalid value \"maybe\" of \"nanolog.captureSystemStreams\": expected true or false; " +
                     "set by the environment variable NANOLOG_CAPTURESYSTEMSTREAMS" + NL, getErrAndClear());
    }
}
