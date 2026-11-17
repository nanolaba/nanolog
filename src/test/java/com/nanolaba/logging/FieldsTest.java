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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nanolaba.logging.LOG.kv;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.ERROR;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.WARN;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LOG.kv(key, value)}: named arguments, the fields of an entry.
 */
class FieldsTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();

    private static final Object BROKEN = new Object() {
        @Override
        public String toString() {
            throw new IllegalStateException("broken");
        }
    };

    @AfterEach
    public void reset() {
        System.clearProperty(DefaultLoggerCreator.PROPERTY_STRICT);
        Contexts.CURRENT.remove();
        LOG.init();
        LOG.clearReportedTemplates();
    }

    private static Map<String, Object> map(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static LogEntry entry(String message, Object... args) {
        return new LogEntry(INFO, InfoObject.class, null, message, args);
    }

    private static String console(LogEntry entry) {
        return new ConsoleLogger().setShowDate(false).createLogString(entry);
    }

    // THE VALUE TYPE

    @Test
    public void testAFieldReadsAsItsValue() {
        LOG.Field field = kv("orderId", "A-17");
        assertEquals("orderId", field.getKey());
        assertEquals("A-17", field.getValue());
        assertEquals("A-17", field.toString());
        assertEquals("[1, 2]", kv("ids", new int[]{1, 2}).toString(), "rendered like a {} argument");
        assertEquals("null", kv("none", null).toString());
        assertEquals("[FAILED toString()]", kv("broken", BROKEN).toString());
        assertNull(kv(null, 1).getKey());
    }

    // FIELDS IN AND BEYOND THE PLACEHOLDERS

    @Test
    public void testAFieldInAPlaceholderPrintsItsValueOnly() {
        LogEntry entry = entry("Loaded {} items from {}", kv("count", 3), kv("source", "db"));

        assertEquals("Loaded 3 items from db", entry.getFormattedMessage());
        assertEquals(map("count", 3, "source", "db"), entry.getFields());
        assertEquals(map(), entry.getTrailingFields());
        assertEquals(2, entry.getArgs().length);
        assertEquals("INFO [InfoObject] Loaded 3 items from db", console(entry));
    }

    @Test
    public void testAFieldBeyondThePlaceholdersIsAppended() {
        LogEntry entry = entry("Payment accepted", kv("orderId", "A-17"));

        assertEquals("Payment accepted", entry.getFormattedMessage());
        assertEquals(map("orderId", "A-17"), entry.getFields());
        assertEquals(map("orderId", "A-17"), entry.getTrailingFields());
        assertArrayEquals(new Object[0], entry.getArgs(), "a field no placeholder takes is not an argument");
        assertEquals("INFO [InfoObject] Payment accepted orderId=A-17", console(entry));
        assertThrows(UnsupportedOperationException.class, () -> entry.getFields().put("x", 1));
        assertThrows(UnsupportedOperationException.class, () -> entry.getTrailingFields().put("x", 1));
    }

    @Test
    public void testFieldsMixWithPlainArguments() {
        LogEntry first = entry("{} loaded", 5, kv("source", "db"), kv("took", 12));
        assertEquals("INFO [InfoObject] 5 loaded source=db took=12", console(first));
        assertEquals(Collections.singletonList(5), Arrays.asList(first.getArgs()));
        assertEquals(map("source", "db", "took", 12), first.getFields());

        LogEntry second = entry("{} from {}", kv("count", 1), 7, kv("user", "John Smith"));
        assertEquals("INFO [InfoObject] 1 from 7 user=\"John Smith\"", console(second));
        assertEquals(2, second.getArgs().length);
        assertSame(7, second.getArgs()[1]);
        assertEquals(map("count", 1, "user", "John Smith"), second.getFields());
        assertEquals(map("user", "John Smith"), second.getTrailingFields());

        LogEntry escaped = entry("\\{} {}", kv("a", 1), kv("b", 2));
        assertEquals("INFO [InfoObject] {} 1 b=2", console(escaped), "an escaped placeholder takes no field");
    }

    @Test
    public void testAKeyGivenTwiceKeepsTheLastValue() {
        LogEntry entry = entry("x", kv("a", 1), kv("b", 2), kv("a", 3));
        assertEquals(map("a", 3, "b", 2), entry.getFields());
        assertEquals(Arrays.asList("a", "b"), new ArrayList<>(entry.getFields().keySet()));
    }

    @Test
    public void testAFieldWithoutAKeyIsAPlainArgument() {
        LogEntry placed = entry("value {}", kv(null, 5));
        assertEquals("value 5", placed.getFormattedMessage());
        assertEquals(map(), placed.getFields());

        LogEntry extra = entry("value", kv(null, 5));
        assertEquals(map(), extra.getFields());
        assertEquals(1, extra.getArgs().length);
        assertEquals("INFO [InfoObject] value", console(extra));
    }

    @Test
    public void testAnEntryWithoutFieldsHasEmptyMaps() {
        LogEntry entry = entry("{} plain", 1);
        assertEquals(map(), entry.getFields());
        assertEquals(map(), entry.getTrailingFields());
        assertEquals(map(), new LogEntry(INFO, null, null, "no args", (Object[]) null).getFields());
        assertNull(new LogEntry(INFO, null, null, "no args", (Object[]) null).getArgs());
    }

    @Test
    public void testAFieldWithABrokenToString() {
        assertEquals("broken [FAILED toString()]", entry("broken {}", kv("value", BROKEN)).getFormattedMessage());
        assertEquals("INFO [InfoObject] broken value=\"[FAILED toString()]\"", console(entry("broken", kv("value", BROKEN))));
        assertTrue(new JsonLogger().createLogString(entry("broken", kv("value", BROKEN)))
                                   .contains("\"fields\":{\"value\":\"[FAILED toString()]\"}"));
    }

    // THE TRAILING THROWABLE

    @Test
    public void testAThrowableAfterTheFieldsIsTheException() {
        IllegalStateException e = new IllegalStateException("boom");

        LogEntry trailing = new LogEntry(ERROR, InfoObject.class, null, "Payment failed", kv("orderId", "A-17"), e);
        assertSame(e, trailing.getThrowable());
        assertEquals(map("orderId", "A-17"), trailing.getFields());
        assertArrayEquals(new Object[0], trailing.getArgs());
        assertTrue(console(trailing).startsWith("ERROR [InfoObject] Payment failed orderId=A-17 java.lang.IllegalStateException: boom"),
                   console(trailing));

        LogEntry placed = new LogEntry(ERROR, InfoObject.class, null, "Failed {}", kv("orderId", "A-17"), e);
        assertSame(e, placed.getThrowable());
        assertEquals("Failed A-17", placed.getFormattedMessage());

        LogEntry consumed = new LogEntry(ERROR, InfoObject.class, null, "Failed {} {}", kv("orderId", "A-17"), e);
        assertNull(consumed.getThrowable(), "a placeholder takes the throwable, as in SLF4J");
        assertEquals("Failed A-17 java.lang.IllegalStateException: boom", consumed.getFormattedMessage());
    }

    // THE ORDER OF THE PARTS

    @Test
    public void testFieldsComeAfterTheSkippedCountAndBeforeTheContext() {
        try (LOG.Context ignored = LOG.context("requestId", "r-17")) {
            LogEntry entry = LogEntry.create(WARN, InfoObject.class, null, null, "Queue is full", new Object[]{kv("queue", "orders")}, 3);
            assertEquals("WARN [InfoObject] Queue is full [skipped 3] queue=orders requestId=r-17", console(entry));
        }
    }

    @Test
    public void testThroughLogTheConsolePrintsTheFields() {
        LOG.init(new ConsoleLogger().setShowDate(false));
        LOG.info(InfoObject.class, "Payment accepted", kv("orderId", "A-17"), kv("amount", new BigDecimal("42.50")));
        assertEquals("INFO [InfoObject] Payment accepted orderId=A-17 amount=42.50" + NL, getOutAndClear());
    }

    // JSON

    @Test
    public void testJsonWritesEveryFieldWithItsType() {
        JsonLogger json = new JsonLogger();
        String placed = json.createLogString(entry("Loaded {} items from {}", kv("count", 3), kv("source", "db")));
        assertTrue(placed.contains("\"message\":\"Loaded 3 items from db\",\"template\":\"Loaded {} items from {}\"," +
                                   "\"args\":[\"3\",\"db\"],\"fields\":{\"count\":3,\"source\":\"db\"}"), placed);

        String trailing = json.createLogString(entry("Payment accepted", kv("orderId", "A-17"), kv("retry", true),
                                                     kv("ratio", Double.NaN), kv("none", null)));
        assertTrue(trailing.contains("\"message\":\"Payment accepted\",\"fields\":{\"orderId\":\"A-17\",\"retry\":true," +
                                     "\"ratio\":\"NaN\",\"none\":null}"), trailing);
        assertFalse(trailing.contains("template"), "without arguments the message is no template");
        Map<String, Object> fields = (Map<String, Object>) StrictJson.parseObject(trailing).get("fields");
        assertEquals(Boolean.TRUE, fields.get("retry"));

        try (LOG.Context ignored = LOG.context("requestId", "r-17")) {
            String both = json.createLogString(entry("x", kv("a", 1)));
            assertTrue(both.contains("\"fields\":{\"a\":1},\"context\":{\"requestId\":\"r-17\"}"), both);
        }
        assertFalse(json.createLogString(entry("{}", 1)).contains("fields"));
    }

    // STRICT MODE

    @Test
    public void testStrictModeDoesNotCountFieldsBeyondThePlaceholders() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_STRICT, "true");
        LOG.init();
        MemoryLogger memory = new MemoryLogger();
        LOG.init(memory);

        LOG.info(InfoObject.class, "Payment accepted", kv("orderId", "A-17"));
        LOG.info(InfoObject.class, "Loaded {} items", kv("count", 3), kv("source", "db"));
        assertEquals(Collections.emptyList(), memory.getDiagnostics());

        LOG.info(InfoObject.class, "{} and {}", kv("a", 1));
        LOG.info(InfoObject.class, "plain", 5, kv("b", 2));
        LOG.info(InfoObject.class, "keyless", kv(null, 2));
        assertEquals(Arrays.asList(InfoObject.class.getName() + ": 2 placeholders, 1 argument in \"{} and {}\"",
                                   InfoObject.class.getName() + ": 0 placeholders, 1 argument in \"plain\"",
                                   InfoObject.class.getName() + ": 0 placeholders, 1 argument in \"keyless\""),
                     memory.getDiagnostics());
        getErrAndClear();
    }

    // SLF4J

    @Test
    public void testSlf4jGetsTheFieldsInTheText() {
        List<String> messages = new ArrayList<>();
        Logger backend = (Logger) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{LocationAwareLogger.class},
                                                         (proxy, method, args) -> {
                                                             if (method.getName().startsWith("is")) {
                                                                 return true;
                                                             }
                                                             messages.add((String) args[3]);
                                                             return null;
                                                         });
        Slf4jLogger logger = new Slf4jLogger(name -> backend, new BasicMDCAdapter());

        logger.log(entry("Payment accepted", kv("orderId", "A-17"), kv("user", "John Smith")));
        logger.log(entry("Loaded {} items", kv("count", 3)));
        logger.log(LogEntry.create(WARN, InfoObject.class, null, null, "Queue is full", new Object[]{kv("queue", "orders")}, 3));
        logger.log(new LogEntry(INFO, InfoObject.class, null, null, kv("only", 1)));

        assertEquals(Arrays.asList("Payment accepted orderId=A-17 user=\"John Smith\"", "Loaded 3 items",
                                   "Queue is full [skipped 3] queue=orders", "only=1"), messages);
    }

    // MEMORY LOGGER AND toString()

    @Test
    public void testAssertionMessagesAndToStringShowTheFields() {
        try (MemoryLogger memory = MemoryLogger.install()) {
            try (LOG.Context ignored = LOG.context("requestId", "r-17")) {
                LOG.info(InfoObject.class, "Payment accepted", kv("orderId", "A-17"));
            }
            AssertionError error = assertThrows(AssertionError.class, () -> memory.assertLogged(WARN, "Payment"));
            assertTrue(error.getMessage().endsWith("INFO [InfoObject] Payment accepted orderId=A-17 requestId=r-17"),
                       error.getMessage());
            assertEquals("LogEntry{level=INFO, sourceClass=com.nanolaba.logging.util.InfoObject, throwable=null, " +
                         "message=Payment accepted, args=[], fields={orderId=A-17}, context={requestId=r-17}}",
                         memory.getEntries().get(0).toString());
        }
        assertEquals("LogEntry{level=INFO, sourceClass=null, throwable=null, message=m, args=[], " +
                     "fields={a=[FAILED toString()], b=[1, 2]}}",
                     new LogEntry(INFO, null, null, "m", kv("a", BROKEN), kv("b", new int[]{1, 2})).toString());
    }
}
