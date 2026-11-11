package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import com.nanolaba.logging.util.StrictJson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonLoggerTest extends AbstractLoggerTest {

    @AfterEach
    public void clearProperties() {
        System.clearProperty(DefaultLoggerCreator.PROPERTY_LOGGER_CLASS);
    }

    @Test
    public void testLineLayout() {
        MemoryLogger memory = new MemoryLogger();
        LOG.init(new CompositeLogger(memory, new JsonLogger()));

        LOG.info(InfoObject.class, "Charge of {} failed", 42);

        String expected = "{\"ts\":\"" + JsonLogger.formatTimestamp(memory.getEntries().get(0).getTimestamp()) + "\"," +
                          "\"level\":\"INFO\"," +
                          "\"logger\":\"com.nanolaba.logging.util.InfoObject\"," +
                          "\"thread\":\"" + Thread.currentThread().getName() + "\"," +
                          "\"message\":\"Charge of 42 failed\"," +
                          "\"template\":\"Charge of {} failed\"," +
                          "\"args\":[\"42\"]}";
        assertEquals(expected + System.lineSeparator(), getOutAndClear());
    }

    @Test
    public void testTimestampFormat() {
        assertEquals("1970-01-01T00:00:00.000Z", JsonLogger.formatTimestamp(0));
        assertEquals("1970-01-01T00:00:01.234Z", JsonLogger.formatTimestamp(1234));
        assertEquals("1971-01-01T00:00:00.000Z", JsonLogger.formatTimestamp(365L * 24 * 60 * 60 * 1000));
    }

    @Test
    public void testErrorGoesToStandardError() {
        LOG.init(new JsonLogger());

        LOG.error(InfoObject.class, "broken");

        assertEquals("", getOutAndClear());
        assertEquals("ERROR", StrictJson.parseObject(getErrAndClear().trim()).get("level"));
    }

    @Test
    public void testExceptionStaysOnOneLine() {
        LOG.init(new JsonLogger());

        LOG.warn(InfoObject.class, new IOException("line 1\nline 2"), "multi\nline message");

        String output = getOutAndClear();
        assertTrue(output.endsWith(System.lineSeparator()));
        String line = output.substring(0, output.length() - System.lineSeparator().length());
        assertFalse(line.contains("\n") || line.contains("\r"), "one entry must be exactly one line: " + line);

        Map<String, Object> json = StrictJson.parseObject(line);
        assertEquals("multi\nline message", json.get("message"));
        assertFalse(json.containsKey("template"), "a message without arguments has no template");
        assertFalse(json.containsKey("args"));

        @SuppressWarnings("unchecked")
        Map<String, Object> exception = (Map<String, Object>) json.get("exception");
        assertEquals(Arrays.asList("class", "message", "stackTrace"), Arrays.asList(exception.keySet().toArray()));
        assertEquals("java.io.IOException", exception.get("class"));
        assertEquals("line 1\nline 2", exception.get("message"));
        String stackTrace = (String) exception.get("stackTrace");
        assertTrue(stackTrace.startsWith("java.io.IOException: line 1\nline 2" + System.lineSeparator() +
                                         "\tat com.nanolaba.logging.JsonLoggerTest.testExceptionStaysOnOneLine"), stackTrace);
    }

    @Test
    public void testExceptionWithoutMessage() {
        LOG.init(new JsonLogger());

        LOG.error(InfoObject.class, new IllegalStateException());

        Map<String, Object> json = StrictJson.parseObject(getErrAndClear().trim());
        assertFalse(json.containsKey("message"), "a throwable-only entry has no message");
        @SuppressWarnings("unchecked")
        Map<String, Object> exception = (Map<String, Object>) json.get("exception");
        assertEquals("java.lang.IllegalStateException", exception.get("class"));
        assertFalse(exception.containsKey("message"));
    }

    @Test
    public void testEscaping() {
        String message = "q\" b\\ n\n r\r t\t b\b f\f c\u0001 u\u001f ls  ps  e😀 slash/ end";

        // checked before the stream encodes it: the console charset of the test JVM may not have the emoji
        String line = new JsonLogger().createLogString(new LogEntry(INFO, InfoObject.class, null, message));
        assertTrue(line.contains("\"message\":\"q\\\" b\\\\ n\\n r\\r t\\t b\\b f\\f c\\u0001 u\\u001f ls\\u2028 ps\\u2029 e😀 slash/ end\""), line);
        assertEquals(message, StrictJson.parseObject(line).get("message"));
    }

    @Test
    public void testArgumentsAreRenderedLikeTheMessage() {
        LOG.init(new JsonLogger());

        LOG.info(InfoObject.class, "{} {} {}", new int[]{1, 2}, null, "a\"b");

        Map<String, Object> json = StrictJson.parseObject(getOutAndClear().trim());
        assertEquals("[1, 2] null a\"b", json.get("message"));
        assertEquals("{} {} {}", json.get("template"));
        assertEquals(Arrays.asList("[1, 2]", "null", "a\"b"), json.get("args"));
    }

    @Test
    public void testArgumentsWithoutMessage() {
        LOG.init(new JsonLogger());

        LOG.log(INFO, InfoObject.class, null, null, 1);

        Map<String, Object> json = StrictJson.parseObject(getOutAndClear().trim());
        assertFalse(json.containsKey("message"));
        assertFalse(json.containsKey("template"));
        assertEquals(Collections.singletonList("1"), json.get("args"));
    }

    @Test
    public void testMissingParts() {
        JsonLogger logger = new JsonLogger();

        logger.log(new LogEntry(null, null, null, "bare"));

        Map<String, Object> json = StrictJson.parseObject(getOutAndClear().trim());
        assertEquals(Arrays.asList("ts", "thread", "message"), Arrays.asList(json.keySet().toArray()),
                     "an entry without level and source class has neither field");
    }

    @Test
    public void testCaller() {
        LOG.init(new JsonLogger());

        int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        LOG.info("located");

        Map<String, Object> json = StrictJson.parseObject(getOutAndClear().trim());
        @SuppressWarnings("unchecked")
        Map<String, Object> caller = (Map<String, Object>) json.get("caller");
        assertEquals(JsonLoggerTest.class.getName(), caller.get("class"));
        assertEquals("testCaller", caller.get("method"));
        assertEquals("JsonLoggerTest.java", caller.get("file"));
        assertEquals(new BigDecimal(line), caller.get("line"));
        assertEquals(Arrays.asList("ts", "level", "logger", "thread", "message", "caller"), Arrays.asList(json.keySet().toArray()));
    }

    @Test
    public void testCallerWithoutDebugInformation() {
        JsonLogger logger = new JsonLogger();

        logger.log(LogEntry.create(INFO, InfoObject.class, new StackTraceElement("com.acme.Foo", "bar", null, -1), null, "m", new Object[0]));
        @SuppressWarnings("unchecked")
        Map<String, Object> caller = (Map<String, Object>) StrictJson.parseObject(getOutAndClear().trim()).get("caller");
        assertEquals(Arrays.asList("class", "method"), Arrays.asList(caller.keySet().toArray()));

        logger.log(LogEntry.create(INFO, InfoObject.class, new StackTraceElement("com.acme.Foo", "bar", "Foo.java", 0), null, "m", new Object[0]));
        @SuppressWarnings("unchecked")
        Map<String, Object> withLineZero = (Map<String, Object>) StrictJson.parseObject(getOutAndClear().trim()).get("caller");
        assertEquals(BigDecimal.ZERO, withLineZero.get("line"));
    }

    @Test
    public void testBrokenObjectsNeverThrow() {
        LOG.init(new JsonLogger());

        LOG.error(InfoObject.class, new BrokenThrowable(), new BrokenToString());

        Map<String, Object> json = StrictJson.parseObject(getErrAndClear().trim());
        assertEquals("[FAILED toString()]", json.get("message"));
        @SuppressWarnings("unchecked")
        Map<String, Object> exception = (Map<String, Object>) json.get("exception");
        assertEquals(BrokenThrowable.class.getName(), exception.get("class"));
        assertEquals("[FAILED getMessage()]", exception.get("message"));
        assertEquals("[FAILED printStackTrace()]", exception.get("stackTrace"));

        LOG.info(InfoObject.class, "broken {}", new BrokenToString());
        Map<String, Object> withArgument = StrictJson.parseObject(getOutAndClear().trim());
        assertEquals(Collections.singletonList("[FAILED toString()]"), withArgument.get("args"));
    }

    @Test
    public void testBrokenTemplateNeverThrows() {
        LOG.init(new JsonLogger());

        LOG.log(INFO, InfoObject.class, null, BrokenToString::new, 1);

        Map<String, Object> json = StrictJson.parseObject(getOutAndClear().trim());
        assertEquals("[FAILED toString()]", json.get("template"));
    }

    @Test
    public void testLevelsAreThoseOfConsoleLogger() {
        LOG.init(new JsonLogger().setInfoEnabled(false));

        LOG.info(InfoObject.class, "hidden");
        LOG.warn(InfoObject.class, "shown");

        List<String> lines = Arrays.asList(getOutAndClear().trim().split(System.lineSeparator()));
        assertEquals(1, lines.size());
        assertEquals("shown", StrictJson.parseObject(lines.get(0)).get("message"));
    }

    @Test
    public void testSelectableBySystemProperty() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_LOGGER_CLASS, JsonLogger.class.getName());
        LOG.init();

        assertEquals(JsonLogger.class, LOG.getLogger().getClass());
    }

    private static class BrokenToString {
        @Override
        public String toString() {
            throw new IllegalStateException("broken toString");
        }
    }

    private static class BrokenThrowable extends RuntimeException {
        @Override
        public String getMessage() {
            throw new IllegalStateException("broken getMessage");
        }

        @Override
        public String toString() {
            throw new IllegalStateException("broken toString");
        }
    }
}
