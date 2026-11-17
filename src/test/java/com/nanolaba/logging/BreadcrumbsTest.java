package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.nanolaba.logging.LOG.kv;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.DEBUG;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.ERROR;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.TRACE;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.WARN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code nanolog.buffer}: entries at low levels are held back and written only before an error.
 */
class BreadcrumbsTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();

    private java.util.function.LongSupplier originalClock;

    @BeforeEach
    public void setUp() {
        originalClock = LOG.nanoClock;
        LOG.nanoClock = () -> 0L;
        Breadcrumbs.threshold = DEBUG.ordinal();
        Breadcrumbs.size = 200;
        Summary.shutdownHooks = thread -> {
        };
        Summary.reset();
    }

    @AfterEach
    public void tearDown() {
        LOG.nanoClock = originalClock;
        Breadcrumbs.threshold = -1;
        Breadcrumbs.size = 200;
        Breadcrumbs.drainThread();
        System.clearProperty(DefaultLoggerCreator.PROPERTY_BUFFER);
        System.clearProperty(DefaultLoggerCreator.PROPERTY_BUFFER_SIZE);
        Summary.mode = Summary.Mode.OFF;
        Summary.shutdownHooks = Runtime.getRuntime()::addShutdownHook;
        Summary.reset();
        Contexts.CURRENT.remove();
        LOG.init();
    }

    private static List<String> messages(MemoryLogger memory) {
        List<String> messages = new ArrayList<>();
        for (LogEntry entry : memory.getEntries()) {
            messages.add(entry.getFormattedMessage());
        }
        return messages;
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    // HELD BACK UNTIL AN ERROR

    @Test
    public void testLowLevelsWaitForAnError() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.debug("loaded {} items", 3);
            LOG.trace("traced");
            LOG.info("visible");
            LOG.warn("warned");
            assertEquals(Arrays.asList("visible", "warned"), messages(memory));

            LOG.error("Charge failed");
            assertEquals(Arrays.asList("visible", "warned", "--- 2 earlier DEBUG entries ---", "loaded 3 items", "traced", "Charge failed"),
                         messages(memory));
            LogEntry header = memory.getEntries().get(2);
            assertEquals(DEBUG, header.getLevel(), "the header has the highest level it announces");
            assertNull(header.getSourceClass());
            assertTrue(memory.getEntries().get(3).isBreadcrumb());
            assertFalse(memory.getEntries().get(5).isBreadcrumb());

            memory.clear();
            LOG.trace("one");
            LOG.error("again");
            assertEquals(Arrays.asList("--- 1 earlier TRACE entry ---", "one", "again"), messages(memory));
            assertEquals(TRACE, memory.getEntries().get(0).getLevel());

            memory.clear();
            LOG.error("nothing held back");
            assertEquals(Collections.singletonList("nothing held back"), messages(memory));
        }
    }

    @Test
    public void testTheRingKeepsTheNewestEntries() {
        Breadcrumbs.size = 3;
        try (MemoryLogger memory = MemoryLogger.install()) {
            for (int i = 1; i <= 5; i++) {
                LOG.debug("d{}", i);
            }
            LOG.error("failed");
            assertEquals(Arrays.asList("--- 3 earlier DEBUG entries, 2 older dropped ---", "d3", "d4", "d5", "failed"), messages(memory));

            memory.clear();
            LOG.debug("fresh");
            LOG.error("failed");
            assertEquals(Arrays.asList("--- 1 earlier DEBUG entry ---", "fresh", "failed"), messages(memory), "the count of dropped starts over");
        }
    }

    @Test
    public void testLogDbgIsNeverHeldBack() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.dbg();
            assertEquals(1, memory.getEntries().size());
            assertTrue(memory.getEntries().get(0).isDbg());
        }
    }

    @Test
    public void testAnEntryWithoutALevelIsNotHeldBack() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.log(null, InfoObject.class, null, () -> "no level");
            assertEquals(Collections.singletonList("no level"), messages(memory));
        }
    }

    @Test
    public void testOffHoldsNothingBack() {
        Breadcrumbs.threshold = -1;
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.debug("direct");
            LOG.error("failed");
            assertEquals(Arrays.asList("direct", "failed"), messages(memory));
        }
    }

    // LEVELS

    @Test
    public void testHeldBackLevelsCountAsEnabled() {
        ConsoleLogger console = new ConsoleLogger().setShowDate(false).setStream("out").setLevel("INFO");
        LOG.init(console);
        AtomicInteger built = new AtomicInteger();

        assertTrue(LOG.isDebugEnabled());
        assertTrue(LOG.isTraceEnabled(InfoObject.class));
        LOG.debug(() -> "lazy " + built.incrementAndGet());
        LOG.once().debug("limited");
        LOG.debug(InfoObject.class, "explicit");
        assertEquals(1, built.get(), "the message is built when it is held back");
        assertEquals("", getOutAndClear());

        LOG.error("failed");
        assertEquals("DEBUG --- 3 earlier DEBUG entries ---" + NL +
                     "DEBUG [BreadcrumbsTest] lazy 1" + NL +
                     "DEBUG [BreadcrumbsTest] limited" + NL +
                     "DEBUG [InfoObject] explicit" + NL +
                     "ERROR [BreadcrumbsTest] failed" + NL, getOutAndClear());

        Breadcrumbs.threshold = -1;
        assertFalse(LOG.isDebugEnabled());
        assertFalse(LOG.isTraceEnabled(InfoObject.class));
        LOG.debug(() -> "lazy " + built.incrementAndGet());
        assertEquals(1, built.get());
        LOG.clearLimits();
    }

    @Test
    public void testLogDbgFollowsTheLoggerNotTheBuffer() {
        LOG.init(new ConsoleLogger().setShowDate(false).setStream("out").setLevel("INFO"));
        LOG.dbg();
        assertEquals("", getOutAndClear(), "DEBUG is off in the logger, and LOG.dbg is not held back");
    }

    @Test
    public void testACaptureSeesEntriesAtOnce() {
        try (MemoryLogger memory = MemoryLogger.install(); MemoryLogger capture = MemoryLogger.captureForwarding()) {
            LOG.debug("captured");
            assertEquals(Collections.singletonList("captured"), messages(capture));
            assertEquals(Collections.emptyList(), messages(memory), "the logger behind the capture waits for an error");
            LOG.error("failed");
            assertEquals(Arrays.asList("--- 1 earlier DEBUG entry ---", "captured", "failed"), messages(memory));
        }
        LOG.init(new ConsoleLogger().setShowDate(false).setStream("out").setLevel("INFO"));
        try (MemoryLogger capture = MemoryLogger.capture()) {
            LOG.debug("only captured");
            assertEquals(Collections.singletonList("only captured"), messages(capture));
        }
        LOG.error("failed");
        assertEquals("ERROR [BreadcrumbsTest] failed" + NL, getOutAndClear(), "a capture that keeps the entry holds nothing back");
    }

    @Test
    public void testHeldBackEntriesAreCountedOnce() {
        Summary.mode = Summary.Mode.ON;
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.debug("held");
            LOG.error("failed");
        }
        assertTrue(Summary.lines().get(0).startsWith("Run summary: 2 entries: 1 ERROR, 1 DEBUG"), Summary.lines().get(0));
    }

    // SNAPSHOTS

    @Test
    public void testArgumentsAreRenderedWhenHeldBack() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            List<String> items = new ArrayList<>(Collections.singletonList("a"));
            try (LOG.Context ignored = LOG.context("items", items)) {
                LOG.debug("items {}", items, kv("count", 1), kv("list", items), kv("none", null), kv("flag", true));
            }
            items.add("b");
            LOG.error("failed");

            LogEntry held = memory.getEntries().get(1);
            assertEquals("items [a]", held.getFormattedMessage());
            assertEquals(Collections.singletonList("[a]"), Arrays.asList(held.getArgs()));
            assertEquals("[a]", held.getFields().get("list"));
            assertEquals(1, held.getFields().get("count"), "numbers keep their type");
            assertEquals(true, held.getFields().get("flag"));
            assertTrue(held.getFields().containsKey("none"));
            assertEquals("[a]", held.getTrailingFields().get("list"));
            assertEquals("[a]", held.getContext().get("items"));
            assertTrue(new JsonLogger().createLogString(held).contains("\"breadcrumb\":true"));
            assertFalse(new JsonLogger().createLogString(memory.getEntries().get(2)).contains("breadcrumb"));
        }
    }

    @Test
    public void testASnapshotKeepsWhatTheEntryWas() {
        IllegalStateException boom = new IllegalStateException("boom");
        StackTraceElement caller = new StackTraceElement("com.acme.Cart", "load", "Cart.java", 7);
        LogEntry entry = LogEntry.create(DEBUG, InfoObject.class, caller, boom, "loaded {}", new Object[]{kv("n", 3), 'c', new int[]{1}}, 4);
        LogEntry snapshot = entry.snapshot();

        assertEquals(entry.getTimestamp(), snapshot.getTimestamp());
        assertEquals(entry.getThreadName(), snapshot.getThreadName());
        assertSame(caller, snapshot.getCaller());
        assertSame(InfoObject.class, snapshot.getSourceClass());
        assertSame(boom, snapshot.getThrowable());
        assertEquals(DEBUG, snapshot.getLevel());
        assertEquals(4, snapshot.getSkippedCount());
        assertEquals(entry.getCallerBoundary(), snapshot.getCallerBoundary());
        assertEquals("loaded {}", snapshot.getMessage());
        assertEquals("loaded 3", snapshot.getFormattedMessage());
        assertEquals("n", ((LOG.Field) snapshot.getArgs()[0]).getKey());
        assertEquals(3, ((LOG.Field) snapshot.getArgs()[0]).getValue());
        assertEquals('c', snapshot.getArgs()[1]);
        assertEquals("[1]", snapshot.getArgs()[2]);
        assertTrue(snapshot.isBreadcrumb());
        assertFalse(entry.isBreadcrumb());

        assertNull(new LogEntry(INFO, null, null, "m", (Object[]) null).snapshot().getArgs());
        Object message = new Object() {
            @Override
            public String toString() {
                return "an object";
            }
        };
        assertEquals("an object", new LogEntry(INFO, null, null, message).snapshot().getMessage());
        assertNull(new LogEntry(INFO, null, null, null).snapshot().getMessage());

        LogEntry dbg = LogEntry.createDbg(InfoObject.class, caller, "x = 1", 0, LogEntry.LOG_BOUNDARY).snapshot();
        assertTrue(dbg.isDbg());
        try (LOG.Unit u = LOG.unit("unit")) {
            LogEntry inUnit = new LogEntry(INFO, null, null, "m");
            assertSame(inUnit.getUnit(), inUnit.snapshot().getUnit());
        }
    }

    @Test
    public void testLongTextIsCut() {
        String longText = repeat('x', Breadcrumbs.MAX_CHARS + 500);
        LogEntry snapshot = new LogEntry(DEBUG, null, null, longText, kv("v", longText), longText).snapshot();
        String cut = repeat('x', Breadcrumbs.MAX_CHARS) + "... (+500 more chars)";
        assertEquals(cut, snapshot.getFormattedMessage());
        assertEquals(cut, snapshot.getFields().get("v"));
        assertEquals(cut, snapshot.getArgs()[0]);
        String exact = repeat('y', Breadcrumbs.MAX_CHARS);
        assertEquals(exact, new LogEntry(DEBUG, null, null, exact).snapshot().getFormattedMessage());
    }

    @Test
    public void testTheConsoleShowsTheTimeAnEntryWasHeldBack() {
        SimpleDateFormat year = new SimpleDateFormat("yyyy");
        year.setTimeZone(TimeZone.getTimeZone("UTC"));
        ConsoleLogger console = new ConsoleLogger() {
            @Override
            protected Date getCurrentDate() {
                return new Date(0);
            }
        }.setDateFormat(year).setShowLevel(false).setShowSource(false);
        LogEntry entry = new LogEntry(DEBUG, null, null, "m");
        assertEquals("1970 m", console.createLogString(entry));
        assertEquals(year.format(new Date(entry.getTimestamp())) + " m", console.createLogString(entry.snapshot()));
    }

    // SCOPES

    @Test
    public void testEachThreadHasItsOwnRing() throws Exception {
        try (MemoryLogger memory = MemoryLogger.install()) {
            Thread other = new Thread(() -> LOG.debug("other thread"));
            other.start();
            other.join();
            LOG.debug("this thread");
            LOG.error("failed");
            assertEquals(Arrays.asList("--- 1 earlier DEBUG entry ---", "this thread", "failed"), messages(memory));
        }
    }

    @Test
    public void testAUnitHasItsOwnRing() throws Exception {
        try (MemoryLogger memory = MemoryLogger.install()) {
            try (LOG.Unit u = LOG.unit("fine")) {
                LOG.debug("dropped with the unit");
            }
            LOG.error("outside");
            assertEquals(Arrays.asList("fine ok 0 ms", "outside"), messages(memory), "a successful unit drops its entries");

            memory.clear();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try (LOG.Unit u = LOG.unit("failing")) {
                LOG.debug("kept");
                executor.submit(LOG.wrap(() -> LOG.debug("from a task"))).get();
                LOG.error("failed");
                LOG.debug("after the error");
            } finally {
                executor.shutdown();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
            assertEquals(Arrays.asList("failed", "failing error 0 ms", "--- 3 earlier DEBUG entries ---", "kept", "from a task",
                                       "after the error"), messages(memory), "a failed unit writes its entries after its line");

            memory.clear();
            try (LOG.Unit parent = LOG.unit("parent")) {
                LOG.debug("parent crumb");
                try (LOG.Unit child = LOG.unit("child")) {
                    LOG.debug("child crumb");
                }
                parent.fail(new IllegalStateException());
            }
            assertEquals(Arrays.asList("child ok 0 ms", "parent error 0 ms", "--- 1 earlier DEBUG entry ---", "parent crumb"), messages(memory));

            memory.clear();
            LOG.unit("open");
            LOG.debug("before the end");
            Units.interruptOpen();
            assertEquals(Arrays.asList("open interrupted 0 ms", "--- 1 earlier DEBUG entry ---", "before the end"), messages(memory));
            Contexts.CURRENT.remove();

            memory.clear();
            LOG.error("the thread ring is empty");
            assertEquals(Collections.singletonList("the thread ring is empty"), messages(memory));
        }
    }

    // SETTINGS

    @Test
    public void testSettings() {
        String[][] levels = {{"debug", "1"}, {"TRACE", "0"}, {" Info ", "2"}, {"WARN", "3"}, {"off", "-1"}};
        for (String[] level : levels) {
            System.setProperty(DefaultLoggerCreator.PROPERTY_BUFFER, level[0]);
            LOG.init();
            assertEquals(Integer.parseInt(level[1]), Breadcrumbs.threshold, level[0]);
        }
        System.clearProperty(DefaultLoggerCreator.PROPERTY_BUFFER);
        LOG.init();
        assertEquals(-1, Breadcrumbs.threshold, "off by default");
        assertEquals("", getErrAndClear());

        System.setProperty(DefaultLoggerCreator.PROPERTY_BUFFER, "ERROR");
        LOG.init();
        assertEquals(-1, Breadcrumbs.threshold);
        assertTrue(getErrAndClear().contains("Invalid value \"ERROR\" of \"nanolog.buffer\": expected off, TRACE, DEBUG, INFO or WARN"));

        System.setProperty(DefaultLoggerCreator.PROPERTY_BUFFER_SIZE, "50");
        LOG.init();
        assertEquals(50, Breadcrumbs.size);
        System.setProperty(DefaultLoggerCreator.PROPERTY_BUFFER_SIZE, "0");
        LOG.init();
        assertEquals(200, Breadcrumbs.size);
        assertTrue(getErrAndClear().contains("Invalid value \"0\" of \"nanolog.buffer.size\": expected a positive number"));
    }

    @Test
    public void testAWarnBufferHoldsWarningsBack() {
        Breadcrumbs.threshold = WARN.ordinal();
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.info("i");
            LOG.warn("w");
            assertEquals(Collections.emptyList(), messages(memory));
            LOG.error("e");
            assertEquals(Arrays.asList("--- 2 earlier WARN entries ---", "i", "w", "e"), messages(memory));
            assertEquals(WARN, memory.getEntries().get(0).getLevel());
            assertEquals(ERROR, memory.getEntries().get(3).getLevel());
        }
    }
}
