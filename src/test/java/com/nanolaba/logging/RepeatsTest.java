package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.nanolaba.logging.LOG.kv;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.ERROR;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.WARN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code nanolog.console.repeats}: consecutive repeats of an entry collapsed into one line with a count.
 */
class RepeatsTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();

    private final long[] now = {0};
    private LongSupplier originalClock;
    private final List<Thread> hooks = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        originalClock = LOG.nanoClock;
        LOG.nanoClock = () -> now[0];
        Summary.shutdownHooks = hooks::add;
        Summary.reset();
    }

    @AfterEach
    public void tearDown() {
        LOG.nanoClock = originalClock;
        Summary.shutdownHooks = Runtime.getRuntime()::addShutdownHook;
        Summary.mode = Summary.Mode.OFF;
        Summary.reset();
        System.clearProperty(ConsoleLogger.PROPERTY_REPEATS);
        LOG.init();
        getOutAndClear();
    }

    private static ConsoleLogger collapsing() {
        return new ConsoleLogger().setShowDate(false).setStream("out").setRepeats("collapse");
    }

    private static LogEntry entry(LogEntry.LogEntryLevel level, String message, Object... args) {
        return new LogEntry(level, InfoObject.class, null, message, args);
    }

    private static long ms(long millis) {
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }

    // COLLAPSING

    @Test
    public void testKeepIsTheDefault() {
        ConsoleLogger console = new ConsoleLogger().setShowDate(false).setStream("out");
        assertEquals("keep", console.getRepeats());
        console.log(entry(WARN, "same"));
        console.log(entry(WARN, "same"));
        assertEquals("WARN [InfoObject] same" + NL + "WARN [InfoObject] same" + NL, getOutAndClear());
    }

    @Test
    public void testRepeatsBecomeOneLineWithACount() {
        ConsoleLogger console = collapsing();
        console.log(entry(WARN, "Connection refused: {}", "db:5432"));
        now[0] = ms(300);
        console.log(entry(WARN, "Connection refused: {}", "db:5432"));
        now[0] = ms(812);
        console.log(entry(WARN, "Connection refused: {}", "db:5432"));
        assertEquals("WARN [InfoObject] Connection refused: db:5432" + NL, getOutAndClear(), "repeats are only counted");

        console.log(entry(INFO, "Connected"));
        console.log(entry(INFO, "Connected again"));
        assertEquals("WARN [InfoObject] Connection refused: db:5432 (repeated 2 more times in 812 ms)" + NL +
                     "INFO [InfoObject] Connected" + NL +
                     "INFO [InfoObject] Connected again" + NL, getOutAndClear());

        now[0] = ms(3000);
        console.log(entry(INFO, "Connected again"));
        console.log(entry(INFO, "other"));
        assertEquals("INFO [InfoObject] Connected again (repeated 1 more time in 2.2 s)" + NL + "INFO [InfoObject] other" + NL, getOutAndClear());
    }

    @Test
    public void testTheCountLineHasNoStackTrace() {
        ConsoleLogger console = collapsing();
        IllegalStateException boom = new IllegalStateException("boom");
        console.log(new LogEntry(ERROR, InfoObject.class, boom, "failed"));
        console.log(new LogEntry(ERROR, InfoObject.class, new IllegalStateException("boom"), "failed"));
        console.flushRepeats();
        String[] lines = getOutAndClear().split("\r?\n");
        assertEquals("ERROR [InfoObject] failed java.lang.IllegalStateException: boom", lines[0]);
        assertEquals("ERROR [InfoObject] failed (repeated 1 more time in 0 ms)", lines[lines.length - 1]);
        assertTrue(lines[1].startsWith("\tat "));
    }

    @Test
    public void testAnythingDifferentIsNoRepeat() {
        StackTraceElement here = new StackTraceElement("com.acme.A", "a", "A.java", 1);
        StackTraceElement there = new StackTraceElement("com.acme.A", "a", "A.java", 2);
        LogEntry base = LogEntry.create(WARN, InfoObject.class, here, null, "m", new Object[0]);
        List<LogEntry> different = new ArrayList<>(Arrays.asList(
                LogEntry.create(INFO, InfoObject.class, here, null, "m", new Object[0]),
                LogEntry.create(WARN, String.class, here, null, "m", new Object[0]),
                LogEntry.create(WARN, InfoObject.class, there, null, "m", new Object[0]),
                LogEntry.create(WARN, InfoObject.class, here, null, "other", new Object[0]),
                LogEntry.create(WARN, InfoObject.class, here, null, "m", new Object[0], 3),
                LogEntry.create(WARN, InfoObject.class, here, new IllegalStateException("x"), "m", new Object[0]),
                LogEntry.create(WARN, InfoObject.class, here, null, "m", new Object[]{kv("k", 1)}),
                LogEntry.createDbg(InfoObject.class, here, "m", 0, LogEntry.LOG_BOUNDARY)));
        try (LOG.Context ignored = LOG.context("requestId", "r-1")) {
            different.add(LogEntry.create(WARN, InfoObject.class, here, null, "m", new Object[0]));
        }
        for (LogEntry other : different) {
            assertTrue(ConsoleLogger.isRepeat(base, base), "an entry repeats itself");
            assertEquals(false, ConsoleLogger.isRepeat(base, other), other.toString());
            assertEquals(false, ConsoleLogger.isRepeat(other, base), other.toString());
        }
        LogEntry withError = LogEntry.create(WARN, InfoObject.class, here, new IllegalStateException("x"), "m", new Object[0]);
        assertTrue(ConsoleLogger.isRepeat(withError, LogEntry.create(WARN, InfoObject.class, here, new IllegalStateException("x"), "m", new Object[0])));
        assertEquals(false, ConsoleLogger.isRepeat(withError, LogEntry.create(WARN, InfoObject.class, here, new IllegalArgumentException("x"), "m",
                                                                                   new Object[0])), "another class");
        assertEquals(false, ConsoleLogger.isRepeat(withError, LogEntry.create(WARN, InfoObject.class, here, new IllegalStateException("y"), "m",
                                                                                   new Object[0])), "another message");
        Object broken = new Object() {
            @Override
            public boolean equals(Object o) {
                throw new IllegalStateException();
            }

            @Override
            public int hashCode() {
                return 1;
            }
        };
        LogEntry a = LogEntry.create(WARN, InfoObject.class, here, null, "m", new Object[]{kv("broken", broken)});
        LogEntry b = LogEntry.create(WARN, InfoObject.class, here, null, "m", new Object[]{kv("broken", broken)});
        assertEquals(false, ConsoleLogger.isRepeat(a, b), "a value whose equals throws: no repeat, and no exception");

        LogEntry debug = LogEntry.create(LogEntry.LogEntryLevel.DEBUG, InfoObject.class, here, null, "m", new Object[0]);
        LogEntry dbg = LogEntry.createDbg(InfoObject.class, here, "m", 0, LogEntry.LOG_BOUNDARY);
        assertEquals(false, ConsoleLogger.isRepeat(debug, dbg), "only LOG.dbg differs");
    }

    @Test
    public void testALongRunShowsItsCountEveryTenSeconds() {
        ConsoleLogger console = collapsing();
        console.log(entry(WARN, "busy"));
        now[0] = ms(5_000);
        console.log(entry(WARN, "busy"));
        now[0] = ms(10_000);
        console.log(entry(WARN, "busy"));
        assertEquals("WARN [InfoObject] busy" + NL, getOutAndClear(), "exactly ten seconds is not more than ten");
        now[0] = ms(10_001);
        console.log(entry(WARN, "busy"));
        assertEquals("WARN [InfoObject] busy (repeated 3 more times in 10.0 s)" + NL, getOutAndClear(), "the count so far");
        now[0] = ms(12_001);
        console.log(entry(WARN, "busy"));
        now[0] = ms(13_001);
        console.log(entry(WARN, "busy"));
        console.log(entry(WARN, "done"));
        assertEquals("WARN [InfoObject] busy (repeated 2 more times in 3.0 s)" + NL + "WARN [InfoObject] done" + NL, getOutAndClear(),
                     "a new count from the last line");

        console.log(entry(WARN, "second"));
        now[0] = ms(14_001);
        console.log(entry(WARN, "second"));
        console.log(entry(WARN, "third"));
        assertEquals("WARN [InfoObject] second" + NL + "WARN [InfoObject] second (repeated 1 more time in 1.0 s)" + NL +
                     "WARN [InfoObject] third" + NL, getOutAndClear(), "exactly a second is written in seconds");
    }

    // FLUSHING

    @Test
    public void testThePendingCountIsWrittenWhenTheLoggerIsReplacedOrTheRunEnds() {
        ConsoleLogger console = collapsing();
        LOG.init(console);
        LOG.warn(InfoObject.class, "pending");
        LOG.warn(InfoObject.class, "pending");
        LOG.init(new MemoryLogger());
        assertEquals("WARN [InfoObject] pending" + NL + "WARN [InfoObject] pending (repeated 1 more time in 0 ms)" + NL, getOutAndClear());

        CompositeLogger composite = new CompositeLogger(new MemoryLogger(), collapsing());
        LOG.init(composite);
        LOG.warn(InfoObject.class, "inside");
        LOG.warn(InfoObject.class, "inside");
        assertEquals(1, hooks.size(), "a repeat makes sure the end of the run is printed");
        hooks.get(0).run();
        assertEquals("WARN [InfoObject] inside" + NL + "WARN [InfoObject] inside (repeated 1 more time in 0 ms)" + NL, getOutAndClear());

        console.flushRepeats();
        console.log(entry(WARN, "pending"));
        assertEquals("WARN [InfoObject] pending" + NL, getOutAndClear(), "after a flush the entry is printed in full");
        LOG.init(entry -> {
        });
    }

    @Test
    public void testThreadsLoseNoCount() throws Exception {
        ConsoleLogger console = collapsing();
        Runnable burst = () -> {
            for (int i = 0; i < 500; i++) {
                console.log(entry(WARN, "burst"));
            }
        };
        Thread first = new Thread(burst);
        Thread second = new Thread(burst);
        first.start();
        second.start();
        first.join();
        second.join();
        console.flushRepeats();
        String out = getOutAndClear();
        Matcher counts = Pattern.compile("repeated (\\d+) more time").matcher(out);
        int total = 0;
        while (counts.find()) {
            total += Integer.parseInt(counts.group(1));
        }
        assertEquals(999, total, out);
        assertEquals(1, out.split("WARN \\[InfoObject] burst" + Pattern.quote(NL)).length - 1, "the entry is printed in full once");
    }

    // SETTINGS

    @Test
    public void testTheSettingAndTheSetter() {
        System.setProperty(ConsoleLogger.PROPERTY_REPEATS, " Collapse ");
        assertEquals("collapse", new ConsoleLogger().getRepeats());
        System.setProperty(ConsoleLogger.PROPERTY_REPEATS, "squash");
        assertEquals("keep", new ConsoleLogger().getRepeats());
        assertTrue(getErrAndClear().contains("Invalid value \"squash\" of \"nanolog.console.repeats\": expected keep or collapse"));

        ConsoleLogger console = new ConsoleLogger();
        assertSame(console, console.setRepeats("COLLAPSE"));
        assertEquals("collapse", console.getRepeats());
        console.setRepeats("squash");
        assertEquals("collapse", console.getRepeats());
        assertTrue(getErrAndClear().contains("Invalid value \"squash\" in setRepeats: expected keep or collapse; nothing changed"));
        console.setRepeats(null);
        assertTrue(getErrAndClear().contains("Invalid value null in setRepeats"));
        console.setRepeats("keep");
        assertEquals("keep", console.getRepeats());
    }

    @Test
    public void testJsonNeverCollapses() {
        JsonLogger json = new JsonLogger();
        json.setStream("out").setRepeats("collapse");
        json.log(entry(WARN, "same"));
        json.log(entry(WARN, "same"));
        assertEquals(2, getOutAndClear().split("\r?\n").length);
    }

    @Test
    public void testTheCountLineIsColouredAndEscapedLikeAnyLine() {
        ConsoleLogger console = collapsing().setColor("always").setMultiline("escape");
        console.log(entry(WARN, "two\nlines"));
        console.log(entry(WARN, "two\nlines"));
        console.flushRepeats();
        String out = getOutAndClear();
        assertTrue(out.endsWith(Colors.of(entry(WARN, "x")) + "WARN" + Colors.RESET + " [InfoObject] two\\nlines (repeated 1 more time in 0 ms)" + NL), out);
    }
}
