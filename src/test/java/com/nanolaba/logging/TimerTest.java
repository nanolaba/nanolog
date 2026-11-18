package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.DEBUG;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LOG.time(...)} and {@code LOG.timed(...)}: the duration of a block as one line.
 */
class TimerTest extends AbstractLoggerTest {

    private final long[] now = {1_000_000_000L};
    private final AtomicInteger reads = new AtomicInteger();
    private LongSupplier originalClock;

    @BeforeEach
    public void setUp() {
        originalClock = LOG.nanoClock;
        LOG.nanoClock = () -> {
            reads.incrementAndGet();
            return now[0];
        };
    }

    @AfterEach
    public void tearDown() {
        LOG.nanoClock = originalClock;
        LOG.init();
    }

    private static long ms(long millis) {
        return TimeUnit.MILLISECONDS.toNanos(millis);
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    public void testABlockIsTimed() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
            try (LOG.Timer t = LOG.time("Load users")) {
                now[0] += ms(123);
            }
            LogEntry entry = memory.getEntries().get(0);
            assertEquals(INFO, entry.getLevel(), "INFO by default");
            assertEquals("Load users took 123 ms", entry.getFormattedMessage());
            assertEquals(map("timer", "Load users", "duration_ms", 123L), entry.getFields());
            assertEquals(TimerTest.class, entry.getSourceClass());
            assertEquals(line, entry.getCaller().getLineNumber(), "the call site of LOG.time");
            assertEquals(Timers.Timing.class.getName(), entry.getCallerBoundary());
            assertTrue(new JsonLogger().createLogString(entry).contains("\"fields\":{\"timer\":\"Load users\",\"duration_ms\":123}"));
        }
    }

    @Test
    public void testAnExplicitLevelAndClosingTwice() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.Timer t = LOG.time(DEBUG, "Parse");
            now[0] += ms(2);
            t.close();
            t.close();
            assertEquals(1, memory.getEntries().size());
            assertEquals(DEBUG, memory.getEntries().get(0).getLevel());
            assertEquals("Parse took 2 ms", memory.getEntries().get(0).getFormattedMessage());

            LOG.time(null, "no level").close();
            assertEquals(1, memory.getEntries().size(), "a null level logs nothing");
        }
    }

    @Test
    public void testADisabledLevelReadsNoClock() {
        LOG.init(new ConsoleLogger().setLevel("WARN"));
        reads.set(0);
        LOG.Timer off = LOG.time("off");
        off.close();
        LOG.timed("off", () -> {
        });
        assertEquals(0, reads.get(), "a level that is off everywhere costs no clock read");

        LOG.init(new ConsoleLogger().setLevel("WARN").setLevel("com.acme", "INFO"));
        LOG.time("off here").close();
        assertEquals(0, reads.get(), "on for another package only: the class decides, still no clock read");
        IllegalStateException boom = new IllegalStateException("boom");
        assertSame(boom, assertThrows(IllegalStateException.class, () -> LOG.timed("off", () -> {
            throw boom;
        })), "a timer that is off passes the exception on as it is");
        assertEquals("", getOutAndClear());
    }

    @Test
    public void testALevelSwitchedOffMeanwhileWritesNothing() {
        ConsoleLogger console = new ConsoleLogger().setShowDate(false);
        LOG.init(console);
        LOG.Timer t = LOG.time("switched");
        console.setLevel("WARN");
        t.close();
        assertEquals("", getOutAndClear());
    }

    @Test
    public void testTheLambdaFormsReturnAndReportAnException() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
            assertEquals(42, (int) LOG.timed("Count users", () -> {
                now[0] += ms(12);
                return 42;
            }));
            assertEquals("Count users took 12 ms", memory.getEntries().get(0).getFormattedMessage());
            assertEquals(line, memory.getEntries().get(0).getCaller().getLineNumber());

            LOG.timed("Warm up", () -> now[0] += ms(1));
            assertEquals("Warm up took 1 ms", memory.getEntries().get(1).getFormattedMessage());

            LOG.timed("Run", (Runnable) () -> now[0] += ms(3));
            assertEquals("Run took 3 ms", memory.getEntries().get(2).getFormattedMessage());

            IllegalStateException boom = new IllegalStateException("boom");
            assertSame(boom, assertThrows(IllegalStateException.class, () -> LOG.timed("Fail", () -> {
                now[0] += ms(5);
                throw boom;
            })));
            LogEntry failed = memory.getEntries().get(3);
            assertEquals("Fail failed after 5 ms", failed.getFormattedMessage());
            assertSame(boom, failed.getThrowable());
            assertEquals(INFO, failed.getLevel());

            AssertionError error = new AssertionError("error");
            assertSame(error, assertThrows(AssertionError.class, () -> LOG.timed("Error", (Runnable) () -> {
                throw error;
            })));
            assertSame(error, memory.getEntries().get(4).getThrowable());
            assertNull(memory.getEntries().get(0).getThrowable());
        }
    }
}
