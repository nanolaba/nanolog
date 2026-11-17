package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import com.nanolaba.logging.util.StrictJson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.helpers.BasicMDCAdapter;
import org.slf4j.spi.LocationAwareLogger;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.WARN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();

    @AfterEach
    public void reset() {
        System.clearProperty(ConsoleLogger.PROPERTY_SHOW_CONTEXT);
        Contexts.CURRENT.remove();
        LOG.init();
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static List<Map<String, Object>> contexts(MemoryLogger memory) {
        List<Map<String, Object>> contexts = new ArrayList<>();
        for (LogEntry entry : memory.getEntries()) {
            contexts.add(entry.getContext());
        }
        return contexts;
    }

    // THE SCOPE

    @Test
    public void testEveryEntryOfTheScopeCarriesTheContext() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            LOG.info("before");
            try (LOG.Context ignored = LOG.context("requestId", "r-17")) {
                LOG.info("Charging {}", 42);
                LOG.warn("second");
            }
            LOG.info("after");

            assertEquals(Arrays.asList(map(), map("requestId", "r-17"), map("requestId", "r-17"), map()), contexts(memory));
            assertThrows(UnsupportedOperationException.class, () -> memory.getEntries().get(1).getContext().put("x", 1),
                         "the context of an entry is a snapshot nobody can change");
            assertThrows(UnsupportedOperationException.class, () -> memory.getEntries().get(0).getContext().put("x", 1));
        }
        assertNull(Contexts.CURRENT.get(), "a thread without a context keeps no thread local");
    }

    @Test
    public void testAnEntryCreatedDirectlyTakesTheContextOfItsThread() {
        try (LOG.Context ignored = LOG.context("job", 7)) {
            assertEquals(map("job", 7), new LogEntry(INFO, InfoObject.class, null, "direct").getContext());
        }
        assertEquals(map(), new LogEntry(INFO, InfoObject.class, null, "direct").getContext());
    }

    @Test
    public void testNestedScopesAddAndRestoreTheirKeys() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            try (LOG.Context a = LOG.context("requestId", "r-1")) {
                try (LOG.Context b = LOG.context("user", "u-1")) {
                    try (LOG.Context c = LOG.context("requestId", "r-2")) {
                        LOG.info("inner");
                    }
                    LOG.info("restored");
                }
                LOG.info("outer");
            }
            LOG.info("none");

            List<Map<String, Object>> contexts = contexts(memory);
            assertEquals(Arrays.asList(map("requestId", "r-2", "user", "u-1"), map("requestId", "r-1", "user", "u-1"),
                                       map("requestId", "r-1"), map()), contexts);
            assertEquals(Arrays.asList("requestId", "user"), new ArrayList<>(contexts.get(0).keySet()),
                         "a key set again keeps its place");
        }
    }

    @Test
    public void testClosingOutOfOrderRemovesOnlyItsOwnKey() {
        LOG.Context a = LOG.context("a", 1);
        LOG.Context b = LOG.context("b", 2);
        a.close();
        assertEquals(map("b", 2), Contexts.current());
        b.close();
        assertEquals(map(), Contexts.current());
        assertNull(Contexts.CURRENT.get());
    }

    @Test
    public void testClosingAgainChangesNothing() {
        LOG.Context first = LOG.context("k", "v");
        first.close();
        try (LOG.Context ignored = LOG.context("k", "w")) {
            first.close();
            assertEquals(map("k", "w"), Contexts.current());
        }
    }

    @Test
    public void testAKeyThatHeldNullGetsNullBack() {
        try (LOG.Context outer = LOG.context("k", null)) {
            assertEquals(map("k", null), Contexts.current());
            try (LOG.Context inner = LOG.context("k", "v")) {
                assertEquals(map("k", "v"), Contexts.current());
            }
            assertEquals(map("k", null), Contexts.current());
            assertTrue(Contexts.current().containsKey("k"));
        }
        assertFalse(Contexts.current().containsKey("k"));
    }

    @Test
    public void testANullKeyChangesNothing() {
        try (LOG.Context ignored = LOG.context(null, "value")) {
            assertEquals(map(), Contexts.current());
            assertNull(Contexts.CURRENT.get());
        }
        try (LOG.Context outer = LOG.context("k", "v")) {
            LOG.context(null, "value").close();
            assertEquals(map("k", "v"), Contexts.current());
        }
    }

    @Test
    public void testOtherThreadsDoNotSeeTheContext() throws Exception {
        try (LOG.Context ignored = LOG.context("requestId", "r-17")) {
            Map<String, Object>[] seen = new Map[1];
            Thread thread = new Thread(() -> seen[0] = new LogEntry(INFO, InfoObject.class, null, "x").getContext());
            thread.start();
            thread.join();
            assertEquals(map(), seen[0], "a plain thread local: a new thread does not inherit the context");
        }
    }

    // WRAP

    @Test
    public void testAWrappedTaskRunsWithTheContextOfItsCreation() {
        Map<String, Object>[] seen = new Map[2];
        Runnable task;
        try (LOG.Context ignored = LOG.context("requestId", "r-17")) {
            task = LOG.wrap(() -> {
                seen[0] = Contexts.current();
            });
            try (LOG.Context later = LOG.context("late", true)) {
                task.run();
            }
        }
        assertEquals(map("requestId", "r-17"), seen[0], "a snapshot taken by wrap, not the context of the running thread");

        task.run();
        assertEquals(map("requestId", "r-17"), seen[0]);
        assertNull(Contexts.CURRENT.get(), "the context of the task is cleared when it ends");

        try (LOG.Context own = LOG.context("worker", 1)) {
            task.run();
            assertEquals(map("worker", 1), Contexts.current(), "the thread gets its own context back");
        }

        Runnable empty = LOG.wrap(() -> {
            seen[1] = Contexts.current();
        });
        try (LOG.Context own = LOG.context("worker", 1)) {
            empty.run();
            assertEquals(map(), seen[1], "a task wrapped without a context runs without one");
            assertEquals(map("worker", 1), Contexts.current());
        }
    }

    @Test
    public void testAWrappedTaskThatThrowsStillRestoresTheContext() {
        Runnable failing;
        Callable<String> failingCall;
        try (LOG.Context ignored = LOG.context("requestId", "r-17")) {
            failing = LOG.wrap((Runnable) () -> {
                throw new IllegalStateException("boom");
            });
            failingCall = LOG.wrap(() -> {
                throw new java.io.IOException("io");
            });
        }
        try (LOG.Context own = LOG.context("worker", 1)) {
            assertEquals("boom", assertThrows(IllegalStateException.class, failing::run).getMessage());
            assertEquals(map("worker", 1), Contexts.current());
            assertEquals("io", assertThrows(java.io.IOException.class, failingCall::call).getMessage());
            assertEquals(map("worker", 1), Contexts.current());
        }
        assertNull(Contexts.CURRENT.get());
    }

    @Test
    public void testAWrappedCallableReturnsItsValue() throws Exception {
        Callable<Map<String, Object>> call;
        try (LOG.Context ignored = LOG.context("job", "nightly")) {
            call = LOG.wrap(Contexts::current);
        }
        assertEquals(map("job", "nightly"), call.call());
        assertNull(Contexts.CURRENT.get());
        try (LOG.Context own = LOG.context("worker", 1)) {
            assertEquals(map("job", "nightly"), call.call());
            assertEquals(map("worker", 1), Contexts.current());
        }
    }

    @Test
    public void testTheContextCrossesAnExecutor() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (MemoryLogger memory = MemoryLogger.install()) {
            try (LOG.Context ignored = LOG.context("requestId", "r-17")) {
                executor.submit(LOG.wrap(() -> LOG.info("runnable"))).get();
                assertEquals("r-17", executor.submit(LOG.wrap(() -> {
                    LOG.info("callable");
                    return Contexts.current().get("requestId");
                })).get());
                executor.submit(() -> LOG.info("not wrapped")).get();
            }
            executor.submit(() -> LOG.info("after")).get();

            assertEquals(Arrays.asList(map("requestId", "r-17"), map("requestId", "r-17"), map(), map()), contexts(memory),
                         "the worker keeps nothing from a wrapped task");
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    // CONSOLE

    private static ConsoleLogger console() {
        return new ConsoleLogger().setShowDate(false);
    }

    @Test
    public void testTheConsolePrintsThePairsAfterTheMessage() {
        LOG.init(console());
        try (LOG.Context a = LOG.context("requestId", "r-17"); LOG.Context b = LOG.context("attempt", 2)) {
            LOG.info(InfoObject.class, "Charging {}", 42);
        }
        LOG.info(InfoObject.class, "no context");

        assertEquals("INFO [InfoObject] Charging 42 requestId=r-17 attempt=2" + NL +
                     "INFO [InfoObject] no context" + NL, getOutAndClear());
    }

    @Test
    public void testTheContextComesAfterTheSkippedCountAndBeforeTheStackTrace() {
        ConsoleLogger console = console();
        try (LOG.Context ignored = LOG.context("requestId", "r-17")) {
            assertEquals("WARN [InfoObject] Queue is full [skipped 3] requestId=r-17",
                         console.createLogString(LogEntry.create(WARN, InfoObject.class, null, null, "Queue is full", new Object[0], 3)));
            String withTrace = console.createLogString(LogEntry.create(WARN, InfoObject.class, null, new IllegalStateException("boom"),
                                                                       null, new Object[0]));
            assertTrue(withTrace.startsWith("WARN [InfoObject] requestId=r-17 java.lang.IllegalStateException: boom"), withTrace);
        }
    }

    @Test
    public void testValuesWithSpacesQuotesOrEqualsAreQuoted() {
        ConsoleLogger console = console().setShowLevel(false).setShowSource(false);
        Object broken = new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException();
            }
        };
        String[][] cases = {
                {"plain", "plain"},
                {"r-17", "r-17"},
                {"John Smith", "\"John Smith\""},
                {"", "\"\""},
                {"a=b", "\"a=b\""},
                {"say\"hi", "\"say\\\"hi\""},
                {"C:\\temp", "C:\\temp"},
                {"C:\\Program Files", "\"C:\\\\Program Files\""},
                {"tab\there", "\"tab\there\""},
                {"\u001f", "\"\u001f\""},
                {"!", "!"},
        };
        for (String[] c : cases) {
            try (LOG.Context ignored = LOG.context("k", c[0])) {
                assertEquals("m k=" + c[1], console.createLogString(new LogEntry(INFO, null, null, "m")), c[0]);
            }
        }
        try (LOG.Context a = LOG.context("none", null); LOG.Context b = LOG.context("n", 42);
             LOG.Context c = LOG.context("array", new int[]{1, 2}); LOG.Context d = LOG.context("broken", broken)) {
            assertEquals("m none=null n=42 array=\"[1, 2]\" broken=\"[FAILED toString()]\"",
                         console.createLogString(new LogEntry(INFO, null, null, "m")));
        }
    }

    @Test
    public void testEscapeModeKeepsAnEntryOnOneLine() {
        ConsoleLogger console = console().setShowSource(false).setMultiline("escape");
        try (LOG.Context ignored = LOG.context("note", "two\nlines")) {
            assertEquals("INFO m note=\"two\\nlines\"", console.createLogString(new LogEntry(INFO, null, null, "m")));
        }
    }

    @Test
    public void testShowContextHidesThePairs() {
        ConsoleLogger console = console();
        assertTrue(console.isShowContext(), "on by default");
        assertSame(console, console.setShowContext(false));
        assertFalse(console.isShowContext());
        try (LOG.Context ignored = LOG.context("requestId", "r-17")) {
            assertEquals("INFO [InfoObject] m", console.createLogString(new LogEntry(INFO, InfoObject.class, null, "m")));

            System.setProperty(ConsoleLogger.PROPERTY_SHOW_CONTEXT, "false");
            assertFalse(new ConsoleLogger().isShowContext());
            System.setProperty(ConsoleLogger.PROPERTY_SHOW_CONTEXT, "true");
            assertTrue(new ConsoleLogger().isShowContext());
        }
    }

    // JSON

    @Test
    public void testJsonWritesANestedObjectWithTypedValues() {
        JsonLogger json = new JsonLogger();
        BigDecimal odd = new BigDecimal("1") {
            @Override
            public String toString() {
                return "not a number";
            }
        };
        Map<String, Object> pairs = map("requestId", "r-17", "attempt", 2, "retry", true, "ratio", 0.5, "none", null,
                                        "big", new BigInteger("12345678901234567890"), "price", new BigDecimal("129.90"),
                                        "long", 5L, "short", (short) 3, "byte", (byte) 1, "float", 1.5f,
                                        "nan", Double.NaN, "inf", Float.POSITIVE_INFINITY, "minus", Double.NEGATIVE_INFINITY,
                                        "odd", odd, "char", 'c', "list", Arrays.asList(1, 2), "quote\"key", "v");
        List<LOG.Context> scopes = new ArrayList<>();
        for (Map.Entry<String, Object> pair : pairs.entrySet()) {
            scopes.add(LOG.context(pair.getKey(), pair.getValue()));
        }
        String line = json.createLogString(LogEntry.create(INFO, InfoObject.class, null, null, "Charge of {}", new Object[]{42}, 3));
        for (LOG.Context scope : scopes) {
            scope.close();
        }

        assertTrue(line.contains(",\"args\":[\"42\"],\"context\":{\"requestId\":\"r-17\",\"attempt\":2,\"retry\":true," +
                                 "\"ratio\":0.5,\"none\":null,\"big\":12345678901234567890,\"price\":129.90,\"long\":5," +
                                 "\"short\":3,\"byte\":1,\"float\":1.5,\"nan\":\"NaN\",\"inf\":\"Infinity\"," +
                                 "\"minus\":\"-Infinity\",\"odd\":\"not a number\",\"char\":\"c\",\"list\":\"[1, 2]\"," +
                                 "\"quote\\\"key\":\"v\"},\"skipped\":3}"), line);
        Map<String, Object> parsed = (Map<String, Object>) StrictJson.parseObject(line).get("context");
        assertEquals(new BigDecimal("2"), parsed.get("attempt"));
        assertEquals(Boolean.TRUE, parsed.get("retry"));
        assertTrue(parsed.containsKey("none"));
        assertNull(parsed.get("none"));
        assertEquals("r-17", parsed.get("requestId"));
    }

    @Test
    public void testJsonLeavesTheContextOutWhenThereIsNone() {
        String line = new JsonLogger().createLogString(new LogEntry(INFO, InfoObject.class, null, "m"));
        assertFalse(line.contains("context"), line);
    }

    // SLF4J

    @Test
    public void testSlf4jSeesTheContextInTheMdcForTheCallOnly() {
        BasicMDCAdapter mdc = new BasicMDCAdapter();
        List<Map<String, String>> seen = new ArrayList<>();
        Logger backend = (Logger) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{LocationAwareLogger.class},
                                                         (proxy, method, args) -> {
                                                             if (method.getName().startsWith("is")) {
                                                                 return true;
                                                             }
                                                             Map<String, String> copy = mdc.getCopyOfContextMap();
                                                             seen.add(copy == null ? Collections.<String, String>emptyMap() : new HashMap<>(copy));
                                                             return null;
                                                         });
        Slf4jLogger logger = new Slf4jLogger(name -> backend, mdc);
        mdc.put("requestId", "outer");
        mdc.put("other", "kept");

        try (LOG.Context a = LOG.context("requestId", "r-17"); LOG.Context b = LOG.context("attempt", 2);
             LOG.Context c = LOG.context("none", null)) {
            logger.log(new LogEntry(INFO, InfoObject.class, null, "with context"));
        }
        logger.log(new LogEntry(INFO, InfoObject.class, null, "without"));

        Map<String, String> during = new HashMap<>();
        during.put("requestId", "r-17");
        during.put("attempt", "2");
        during.put("none", "null");
        during.put("other", "kept");
        Map<String, String> outside = new HashMap<>();
        outside.put("requestId", "outer");
        outside.put("other", "kept");
        assertEquals(Arrays.asList(during, outside), seen);
        assertEquals(outside, mdc.getCopyOfContextMap(), "values of the MDC are put back, new keys removed");
    }

    @Test
    public void testSlf4jRestoresTheMdcWhenTheBackendThrows() {
        BasicMDCAdapter mdc = new BasicMDCAdapter();
        Logger backend = (Logger) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Logger.class},
                                                         (proxy, method, args) -> {
                                                             if (method.getName().startsWith("is")) {
                                                                 return true;
                                                             }
                                                             throw new IllegalStateException("backend");
                                                         });
        Slf4jLogger logger = new Slf4jLogger(name -> backend, mdc);
        mdc.put("first", "1");
        try (LOG.Context a = LOG.context("first", "a"); LOG.Context b = LOG.context("second", "b")) {
            assertThrows(IllegalStateException.class, () -> logger.log(new LogEntry(WARN, InfoObject.class, null, "m")));
        }
        assertEquals(Collections.singletonMap("first", "1"), mdc.getCopyOfContextMap());
    }

    // MEMORY LOGGER

    @Test
    public void testAssertionMessagesShowTheContextWhateverTheSettings() {
        System.setProperty(ConsoleLogger.PROPERTY_SHOW_CONTEXT, "false");
        try (MemoryLogger memory = MemoryLogger.install()) {
            try (LOG.Context ignored = LOG.context("requestId", "r-17")) {
                LOG.info(InfoObject.class, "Charging {}", 42);
            }
            AssertionError error = assertThrows(AssertionError.class, () -> memory.assertLogged(WARN, "Charging"));
            assertTrue(error.getMessage().endsWith("INFO [InfoObject] Charging 42 requestId=r-17"), error.getMessage());
        }
    }
}
