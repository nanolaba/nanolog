package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.ERROR;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.WARN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LOG.unit(...)}: one line per unit of work, the wide events.
 */
class UnitTest extends AbstractLoggerTest {

    private final long[] now = {1_000_000_000L};
    private final List<Thread> hooks = new ArrayList<>();
    private double random;
    private LongSupplier originalClock;
    private DoubleSupplier originalRandom;

    @BeforeEach
    public void setUp() {
        originalClock = LOG.nanoClock;
        originalRandom = Units.random;
        LOG.nanoClock = () -> now[0];
        Units.random = () -> random;
        Summary.shutdownHooks = hooks::add;
        Summary.reset();
    }

    @AfterEach
    public void tearDown() {
        LOG.nanoClock = originalClock;
        Units.random = originalRandom;
        Summary.shutdownHooks = Runtime.getRuntime()::addShutdownHook;
        Summary.mode = Summary.Mode.OFF;
        Summary.reset();
        System.clearProperty(DefaultLoggerCreator.PROPERTY_UNIT_SAMPLE);
        System.clearProperty(DefaultLoggerCreator.PROPERTY_UNIT_SLOW_MS);
        Units.sample = 1;
        Units.slowNanos = Long.MAX_VALUE;
        assertTrue(Units.OPEN.isEmpty(), "a test left a unit open");
        Contexts.CURRENT.remove();
        LOG.init();
    }

    private static long ms(long millis) {
        return millis * 1_000_000L;
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static String console(LogEntry entry) {
        return new ConsoleLogger().setShowDate(false).createLogString(entry);
    }

    private static LogEntry last(MemoryLogger memory) {
        List<LogEntry> entries = memory.getEntries();
        return entries.get(entries.size() - 1);
    }

    private static String key(LogEntry entry) {
        return (String) entry.getFields().get("error.key");
    }

    // ONE LINE

    @Test
    public void testAUnitWritesOneLineWithItsOutcomeDurationAndFields() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
            try (LOG.Unit u = LOG.unit("checkout")) {
                assertSame(u, u.put("orderId", "A-17"));
                u.put("items", 3);
                now[0] += ms(182);
            }

            assertEquals(1, memory.getEntries().size());
            LogEntry entry = memory.getEntries().get(0);
            assertEquals(INFO, entry.getLevel());
            assertEquals(UnitTest.class, entry.getSourceClass());
            assertEquals(line, entry.getCaller().getLineNumber(), "the call site is where the unit was opened");
            assertEquals("checkout ok 182 ms", entry.getFormattedMessage());
            assertEquals(map("unit", "checkout", "outcome", "ok", "duration_ms", 182L, "orderId", "A-17", "items", 3), entry.getFields());
            assertNull(entry.getThrowable());
            assertTrue(entry.isUnitLine());
            assertEquals(Units.Scope.class.getName(), entry.getCallerBoundary());
            assertEquals("INFO [UnitTest] checkout ok 182 ms orderId=A-17 items=3", console(entry));
            String json = new JsonLogger().createLogString(entry);
            assertTrue(json.contains("\"fields\":{\"unit\":\"checkout\",\"outcome\":\"ok\",\"duration_ms\":182,\"orderId\":\"A-17\",\"items\":3}"), json);
        }
        assertNull(Contexts.CURRENT.get(), "a closed unit leaves nothing in the thread");
    }

    @Test
    public void testFieldsTheUnitSetsItselfCannotBeReplaced() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            try (LOG.Unit u = LOG.unit("job")) {
                for (String reserved : new String[]{"unit", "outcome", "duration_ms", "parent", "warn", "error", "error.type",
                                                    "error.message", "error.key"}) {
                    u.put(reserved, "mine");
                }
                u.put(null, "no key");
                u.put("broken", new Object() {
                    @Override
                    public String toString() {
                        throw new IllegalStateException();
                    }
                });
            }
            assertEquals("INFO [UnitTest] job ok 0 ms broken=\"[FAILED toString()]\"", console(last(memory)));
        }
    }

    @Test
    public void testClosingAgainOrPuttingAfterwardsChangesNothing() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.Unit u = LOG.unit("once");
            u.close();
            u.put("late", 1);
            u.fail(new IllegalStateException());
            u.close();
            assertEquals(1, memory.getEntries().size());
            assertEquals(map("unit", "once", "outcome", "ok", "duration_ms", 0L), memory.getEntries().get(0).getFields());
        }
    }

    // OUTCOME AND COUNTS

    @Test
    public void testWarningsAreCountedAndErrorsFailTheUnit() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            try (LOG.Unit u = LOG.unit("import")) {
                LOG.warn("slow disk");
                LOG.info("fine");
                LOG.warn("slow disk");
            }
            LogEntry imported = last(memory);
            assertEquals(INFO, imported.getLevel());
            assertEquals("INFO [UnitTest] import ok 0 ms warn=2", console(imported));

            IOException timeout = new IOException("timeout");
            try (LOG.Unit u = LOG.unit("charge")) {
                now[0] += ms(7);
                LOG.error(timeout, "Charge of {} failed", 42);
                LOG.error("second");
            }
            LogEntry charged = last(memory);
            assertEquals(ERROR, charged.getLevel());
            assertNull(charged.getThrowable(), "the trace was printed with the error itself");
            assertEquals("ERROR [UnitTest] charge error 7 ms error=2 error.type=java.io.IOException error.message=timeout error.key=" +
                         key(charged), console(charged));
            assertTrue(key(charged).matches("[0-9a-f]{8}"), key(charged));

            try (LOG.Unit u = LOG.unit("plain")) {
                LOG.error("Nothing to charge for {}", "A-17");
            }
            LogEntry plain = last(memory);
            assertEquals(map("unit", "plain", "outcome", "error", "duration_ms", 0L, "error", 1,
                             "error.message", "Nothing to charge for A-17", "error.key", key(plain)), plain.getFields());

            try (LOG.Unit u = LOG.unit("nothing")) {
                LOG.error(new IllegalStateException((String) null));
            }
            assertFalse(last(memory).getFields().containsKey("error.message"), "an exception without a message adds none");
        }
    }

    private static void failSameWay(int i) {
        LOG.error("Charge of {} failed", i);
    }

    @Test
    public void testTheErrorKeyGroupsFailuresByTemplateAndCallSite() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            List<String> keys = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                try (LOG.Unit u = LOG.unit("loop")) {
                    failSameWay(i);
                }
                keys.add(key(last(memory)));
            }
            try (LOG.Unit u = LOG.unit("other")) {
                LOG.error("Charge of {} failed", 1);
            }
            keys.add(key(last(memory)));
            try (LOG.Unit u = LOG.unit("thrown")) {
                u.fail(new IllegalStateException("one"));
            }
            keys.add(key(last(memory)));
            try (LOG.Unit u = LOG.unit("thrown")) {
                u.fail(new IllegalStateException("two"));
            }
            keys.add(key(last(memory)));

            assertEquals(keys.get(0), keys.get(1), "other arguments, the same template and call site");
            assertNotEquals(keys.get(0), keys.get(2), "another call site");
            assertNotEquals(keys.get(2), keys.get(3));
            assertNotEquals(keys.get(3), keys.get(4), "another line threw");
        }
    }

    @Test
    public void testFailAttachesTheException() {
        IllegalStateException boom = new IllegalStateException("boom");
        try (MemoryLogger memory = MemoryLogger.install()) {
            try (LOG.Unit u = LOG.unit("job")) {
                u.fail(boom);
                u.fail(new RuntimeException("later"));
                u.fail(null);
            }
            LogEntry job = last(memory);
            assertEquals(ERROR, job.getLevel());
            assertSame(boom, job.getThrowable());
            assertEquals("error", job.getFields().get("outcome"));
            assertFalse(job.getFields().containsKey("error"), "no ERROR entry was logged");
            assertEquals("java.lang.IllegalStateException", job.getFields().get("error.type"));
            assertEquals("boom", job.getFields().get("error.message"));

            try (LOG.Unit u = LOG.unit("first")) {
                LOG.error("logged first");
                u.fail(boom);
            }
            LogEntry first = last(memory);
            assertSame(boom, first.getThrowable());
            assertEquals("logged first", first.getFields().get("error.message"), "the first error describes the unit");
            assertFalse(first.getFields().containsKey("error.type"));

            Throwable broken = new IllegalStateException() {
                @Override
                public String getMessage() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public StackTraceElement[] getStackTrace() {
                    throw new UnsupportedOperationException();
                }
            };
            try (LOG.Unit u = LOG.unit("broken")) {
                u.fail(broken);
            }
            assertEquals("[FAILED getMessage()]", last(memory).getFields().get("error.message"));
            assertTrue(key(last(memory)).matches("[0-9a-f]{8}"));

            Throwable noTrace = new RuntimeException("no trace", null, false, false) {
            };
            try (LOG.Unit u = LOG.unit("noTrace")) {
                u.fail(noTrace);
            }
            String withoutTrace = key(last(memory));
            Throwable nullTrace = new RuntimeException("null trace") {
                @Override
                public StackTraceElement[] getStackTrace() {
                    return null;
                }
            };
            try (LOG.Unit u = LOG.unit("nullTrace")) {
                u.fail(nullTrace);
            }
            assertTrue(withoutTrace.matches("[0-9a-f]{8}"));
            assertEquals(Units.key(noTrace.getClass().getName() + " at unknown source"), withoutTrace);
            assertEquals(Units.key(nullTrace.getClass().getName() + " at unknown source"), key(last(memory)));
        }
    }

    // RUN

    @Test
    public void testRunRecordsAThrownExceptionAndRethrowsIt() {
        IllegalStateException boom = new IllegalStateException("boom");
        AssertionError failed = new AssertionError("failed");
        try (MemoryLogger memory = MemoryLogger.install()) {
            assertSame(boom, assertThrows(IllegalStateException.class, () -> LOG.unit("job").run((Runnable) () -> {
                throw boom;
            })));
            assertSame(boom, last(memory).getThrowable());
            assertEquals("error", last(memory).getFields().get("outcome"));

            assertSame(failed, assertThrows(AssertionError.class, () -> LOG.unit("calc", () -> {
                throw failed;
            })));
            assertSame(failed, last(memory).getThrowable());

            assertEquals(42, (int) LOG.unit("answer").put("q", "life").run(() -> {
                now[0] += ms(3);
                return 42;
            }));
            assertEquals("INFO [UnitTest] answer ok 3 ms q=life", console(last(memory)));

            assertEquals("v", LOG.unit("value", () -> "v"));
            assertEquals("value", last(memory).getFields().get("unit"));

            LOG.unit("void", () -> LOG.warn("inside"));
            assertEquals(map("unit", "void", "outcome", "ok", "duration_ms", 0L, "warn", 1), last(memory).getFields());

            LOG.unit("runnable").run(() -> LOG.info("inside"));
            assertEquals("runnable", last(memory).getFields().get("unit"));
        }
        assertNull(Contexts.CURRENT.get());
    }

    @Test
    public void testTheShortFormsDetectTheCaller() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
            LOG.unit("short", () -> LOG.info("x"));
            assertEquals(UnitTest.class, last(memory).getSourceClass());
            assertEquals(line, last(memory).getCaller().getLineNumber());
            LOG.unit("short", () -> 1);
            assertEquals(line + 3, last(memory).getCaller().getLineNumber());
        }
    }

    // NESTING AND THREADS

    @Test
    public void testNestedUnitsNameTheirParent() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            try (LOG.Unit parent = LOG.unit("checkout")) {
                try (LOG.Unit child = LOG.unit("charge")) {
                    LOG.error("declined");
                    now[0] += ms(10);
                }
                LOG.warn("after the child");
                now[0] += ms(5);
            }

            List<LogEntry> entries = memory.getEntries();
            assertEquals(4, entries.size());
            assertEquals("ERROR [UnitTest] charge error 10 ms parent=checkout error=1 error.message=declined error.key=" +
                         key(entries.get(1)), console(entries.get(1)));
            assertEquals(map("unit", "checkout", "outcome", "error", "duration_ms", 15L, "warn", 1, "error", 1,
                             "error.message", "declined", "error.key", key(entries.get(1))), entries.get(3).getFields(),
                         "the error inside the child counts once, the line of the child not at all");
        }
        assertNull(Contexts.CURRENT.get());
    }

    @Test
    public void testUnitsClosedOutOfOrderLeaveTheThreadClean() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.Unit a = LOG.unit("a");
            LOG.Unit b = LOG.unit("b");
            a.close();
            LOG.warn("w");
            b.close();
            assertNull(Contexts.CURRENT.get());
            assertEquals(map("unit", "b", "outcome", "ok", "duration_ms", 0L, "parent", "a", "warn", 1), last(memory).getFields());

            try (LOG.Unit outer = LOG.unit("outer")) {
                LOG.Unit inner = LOG.unit("inner");
                inner.close();
                LOG.Unit sibling = LOG.unit("sibling");
                sibling.close();
                assertEquals("outer", last(memory).getFields().get("parent"), "closing a child makes its parent current again");
            }
        }
    }

    @Test
    public void testAUnitCrossesAnExecutorThroughWrap() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (MemoryLogger memory = MemoryLogger.install()) {
            try (LOG.Unit u = LOG.unit("batch")) {
                executor.submit(LOG.wrap(() -> LOG.error("item failed"))).get();
                executor.submit(LOG.wrap(() -> {
                    try (LOG.Unit item = LOG.unit("item")) {
                        now[0] += ms(1);
                    }
                })).get();
                executor.submit(() -> LOG.warn("not wrapped")).get();
            }
            assertNull(executor.submit(() -> Contexts.CURRENT.get()).get(), "the worker keeps nothing");

            List<LogEntry> entries = memory.getEntries();
            assertEquals("batch", entries.get(1).getFields().get("parent"));
            assertEquals(map("unit", "batch", "outcome", "error", "duration_ms", 1L, "error", 1, "error.message", "item failed",
                             "error.key", key(entries.get(3))), entries.get(3).getFields());
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void testTheLineCarriesTheContext() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            try (LOG.Context c = LOG.context("requestId", "r-17"); LOG.Unit u = LOG.unit("checkout")) {
                LOG.info("inside");
            }
            assertEquals("INFO [UnitTest] checkout ok 0 ms requestId=r-17", console(last(memory)));
        }
    }

    // LEVELS

    @Test
    public void testTheLineFollowsTheLevels() {
        LOG.init(new ConsoleLogger().setShowDate(false).setLevel(UnitTest.class.getName(), WARN));
        try (LOG.Unit u = LOG.unit("quiet")) {
            LOG.info("dropped");
        }
        assertEquals("", getOutAndClear(), "an ok unit is an INFO line");
        try (LOG.Unit u = LOG.unit("loud")) {
            u.fail(new IllegalStateException("boom"));
        }
        assertTrue(getErrAndClear().startsWith("ERROR [UnitTest] loud error 0 ms error.type=java.lang.IllegalStateException"));
    }

    // SAMPLING

    @Test
    public void testSamplingKeepsEveryFailedOrSlowUnit() {
        Units.sample = 0.5;
        Units.slowNanos = ms(100);
        try (MemoryLogger memory = MemoryLogger.install()) {
            random = 0.7;
            LOG.unit("dropped").close();
            random = 0.5;
            LOG.unit("dropped at the boundary").close();
            assertEquals(0, memory.getEntries().size());

            random = 0.3;
            LOG.unit("kept").close();
            assertEquals("kept", last(memory).getFields().get("unit"));

            random = 0.9;
            try (LOG.Unit u = LOG.unit("failed")) {
                u.fail(new IllegalStateException());
            }
            assertEquals("failed", last(memory).getFields().get("unit"));

            try (LOG.Unit u = LOG.unit("slow")) {
                now[0] += ms(100);
            }
            assertEquals("slow", last(memory).getFields().get("unit"));
            try (LOG.Unit u = LOG.unit("fast")) {
                now[0] += ms(99);
            }
            assertEquals("slow", last(memory).getFields().get("unit"));

            LOG.unit("interrupted");
            Units.interruptOpen();
            assertEquals("interrupted", last(memory).getFields().get("unit"));
        }
        assertTrue(Units.OPEN.isEmpty());
        Contexts.CURRENT.remove();
    }

    @Test
    public void testTheDefaultRandomDrawsFractions() {
        boolean nonZero = false;
        for (int i = 0; i < 20; i++) {
            double value = originalRandom.getAsDouble();
            assertTrue(value >= 0 && value < 1, String.valueOf(value));
            nonZero |= value != 0;
        }
        assertTrue(nonZero);
    }

    @Test
    public void testSettingsOfSampling() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_UNIT_SAMPLE, " 0.05 ");
        System.setProperty(DefaultLoggerCreator.PROPERTY_UNIT_SLOW_MS, "500");
        LOG.init();
        assertEquals(0.05, Units.sample);
        assertEquals(ms(500), Units.slowNanos);

        String[][] valid = {{"0", "0"}, {"1", "0"}, {"0.5", "99999999999999999"}};
        for (String[] values : valid) {
            System.setProperty(DefaultLoggerCreator.PROPERTY_UNIT_SAMPLE, values[0]);
            System.setProperty(DefaultLoggerCreator.PROPERTY_UNIT_SLOW_MS, values[1]);
            LOG.init();
            assertEquals(Double.parseDouble(values[0]), Units.sample);
            assertEquals(TimeUnit.MILLISECONDS.toNanos(Long.parseLong(values[1])), Units.slowNanos);
        }
        assertEquals(Long.MAX_VALUE, Units.slowNanos, "saturated, not overflowed");
        assertEquals("", getErrAndClear());

        for (String sample : new String[]{"1.5", "-0.1", "half", "NaN"}) {
            System.setProperty(DefaultLoggerCreator.PROPERTY_UNIT_SAMPLE, sample);
            LOG.init();
            assertEquals(1.0, Units.sample);
            assertTrue(getErrAndClear().contains("Invalid value \"" + sample + "\" of \"nanolog.unit.sample\": expected a fraction from 0 to 1"));
        }
        for (String slow : new String[]{"-1", "soon"}) {
            System.setProperty(DefaultLoggerCreator.PROPERTY_UNIT_SLOW_MS, slow);
            LOG.init();
            assertEquals(Long.MAX_VALUE, Units.slowNanos);
            assertTrue(getErrAndClear().contains("Invalid value \"" + slow + "\" of \"nanolog.unit.slowMs\": expected a number of milliseconds"));
        }

        System.clearProperty(DefaultLoggerCreator.PROPERTY_UNIT_SAMPLE);
        System.clearProperty(DefaultLoggerCreator.PROPERTY_UNIT_SLOW_MS);
        LOG.init();
        assertEquals(1.0, Units.sample);
        assertEquals(Long.MAX_VALUE, Units.slowNanos);
        getErrAndClear();
    }

    // THE END OF THE RUN

    @Test
    public void testOpenUnitsAreWrittenAsInterruptedAtTheEndOfTheRun() {
        Summary.mode = Summary.Mode.ON;
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.Unit server = LOG.unit("server");
            LOG.Unit request = LOG.unit("request");
            assertEquals(1, hooks.size(), "the first unit registers the hook of the end of the run");
            LOG.unit("another").close();
            assertEquals(1, hooks.size());
            List<LOG.Unit> steps = new ArrayList<>();
            for (int i = 1; i <= 4; i++) {
                steps.add(LOG.unit("step" + i));
            }
            now[0] += ms(30);

            hooks.get(0).run();

            List<LogEntry> entries = memory.getEntries();
            List<Object> names = new ArrayList<>();
            for (LogEntry entry : entries) {
                names.add(entry.getFields().get("unit"));
            }
            assertEquals(java.util.Arrays.asList("another", "step4", "step3", "step2", "step1", "request", "server"), names,
                         "the innermost first");
            assertEquals("ERROR [UnitTest] request interrupted 30 ms parent=server", console(entries.get(5)));
            assertEquals("ERROR [UnitTest] server interrupted 30 ms", console(entries.get(6)));
            String summary = getErrAndClear();
            assertTrue(summary.contains("Run summary: 7 entries: 6 ERROR, 1 INFO"), summary);
            assertTrue(summary.contains(": unit request"), summary);

            for (int i = steps.size() - 1; i >= 0; i--) {
                steps.get(i).close();
            }
            request.close();
            server.close();
            assertEquals(7, memory.getEntries().size(), "a unit is written once");
        }
        assertNull(Contexts.CURRENT.get());
    }

    @Test
    public void testOpenUnitsAreInterruptedInnermostFirstWhateverTheOrderOfTheSet() {
        // the open units are a hash set, whose order may happen to be the right one: ten rounds leave no such luck
        try (MemoryLogger memory = MemoryLogger.install()) {
            for (int round = 0; round < 10; round++) {
                List<LOG.Unit> units = new ArrayList<>();
                for (int i = 1; i <= 4; i++) {
                    units.add(LOG.unit("u" + i));
                }

                Units.interruptOpen();

                List<Object> names = new ArrayList<>();
                for (LogEntry entry : memory.getEntries()) {
                    names.add(entry.getFields().get("unit"));
                }
                assertEquals(java.util.Arrays.asList("u4", "u3", "u2", "u1"), names, "round " + round);
                for (int i = units.size() - 1; i >= 0; i--) {
                    units.get(i).close();
                }
                memory.clear();
            }
        }
        assertNull(Contexts.CURRENT.get());
    }
}
