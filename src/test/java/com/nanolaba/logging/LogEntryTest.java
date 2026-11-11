package com.nanolaba.logging;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.ERROR;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogEntryTest {

    @Test
    public void testMessageFormatting() {
        assertEquals("null", getFormattedMessage(null));
        assertEquals("", getFormattedMessage(""));
        assertEquals("123", getFormattedMessage("123"));
        assertEquals("123", getFormattedMessage(123));
        assertEquals("true", getFormattedMessage(true));
        assertEquals("test", getFormattedMessage("test", 123));
        assertEquals("123", getFormattedMessage("{}", 123));
        assertEquals("test 123", getFormattedMessage("test {}", 123));
        assertEquals("test 123 {}", getFormattedMessage("test {} {}", 123));
        assertEquals("test 123 null", getFormattedMessage("test {} {}", 123, null));
        assertEquals("test 123 456", getFormattedMessage("test {} {}", 123, 456));
        assertEquals("test 123 {} {}", getFormattedMessage("test {} {} {}", 123));
        assertEquals("test {} 123 {}", getFormattedMessage("test \\{} {} {}", 123));
        assertEquals("test {} {} 123", getFormattedMessage("test \\{} \\{} {}", 123));
        assertEquals("test {} {} 123", getFormattedMessage("test \\{} \\{} {}", 123, 456));
        assertEquals("test {} {} 123 456", getFormattedMessage("test \\{} \\{} {} {}", 123, 456));

        assertEquals("test [true, false, true]", getFormattedMessage("test {}", new boolean[]{true, false, true}));
        assertEquals("test [1, 2, 3]", getFormattedMessage("test {}", new byte[]{1, 2, 3}));
        assertEquals("test [1, 2, 3]", getFormattedMessage("test {}", new char[]{'1', '2', '3'}));
        assertEquals("test [1, 2, 3]", getFormattedMessage("test {}", new int[]{1, 2, 3}));
        assertEquals("test [1, 2, 3]", getFormattedMessage("test {}", new long[]{1, 2, 3}));
        assertEquals("test [1, 2, 3]", getFormattedMessage("test {}", new short[]{1, 2, 3}));
        assertEquals("test [1.0, 2.0, 3.0]", getFormattedMessage("test {}", new float[]{1, 2, 3}));
        assertEquals("test [1.0, 2.0, 3.0]", getFormattedMessage("test {}", new double[]{1, 2, 3}));
        assertEquals("test [1.0, 2.0, 3.0]", getFormattedMessage("test {}", new double[]{1, 2, 3}, new Object[]{"A", "B"}));
        assertEquals("test [1.0, 2.0, 3.0] [A, B]", getFormattedMessage("test {} {}", new double[]{1, 2, 3}, new Object[]{"A", "B"}));
        assertEquals("test {} [1.0, 2.0, 3.0]", getFormattedMessage("test \\{} {}", new double[]{1, 2, 3}, new Object[]{"A", "B"}));
        assertEquals("test {} [1.0, 2.0, 3.0] [A, B]", getFormattedMessage("test \\{} {} {}", new double[]{1, 2, 3}, new Object[]{"A", "B"}));

        assertEquals("\\123", getFormattedMessage("\\\\{}", 123));
        assertEquals("test \\123", getFormattedMessage("test \\\\{}", 123));
        assertEquals("test \\123 {} {}", getFormattedMessage("test \\\\{} {} {}", 123));

        assertEquals("test {} [1.0, 2.0, 3.0] [A, B, [AA, BB]] 123", getFormattedMessage("test \\{} {} {} {}", new double[]{1, 2, 3}, new Object[]{"A", "B", new Object[]{"AA", "BB"}}, "123"));

        assertEquals("test [FAILED toString()]", getFormattedMessage("test {}", new BrokenToString()));
    }

    @Test
    public void testBrokenToStringInsideObjectArray() {
        assertEquals("test [A, [FAILED toString()], [B, [FAILED toString()]], [1, 2]]",
                     getFormattedMessage("test {}", (Object) new Object[]{"A", new BrokenToString(), new Object[]{"B", new BrokenToString()}, new int[]{1, 2}}));
    }

    @Test
    public void testToStringNeverThrows() {
        LogEntry entry = new LogEntry(ERROR, getClass(), new BrokenThrowable(), new BrokenToString(), "A", new BrokenToString(), new Object[]{"B", new BrokenToString()});

        assertEquals("LogEntry{level=ERROR, sourceClass=com.nanolaba.logging.LogEntryTest, throwable=[FAILED toString()], message=[FAILED toString()], " +
                     "args=[A, [FAILED toString()], [B, [FAILED toString()]]]}", entry.toString());
    }

    @Test
    public void testTrailingThrowableAfterSubstitutedArguments() {
        IOException e = new IOException("timeout");
        LogEntry entry = new LogEntry(ERROR, getClass(), null, "Failed to load {}", "order", e);

        assertSame(e, entry.getThrowable());
        assertArrayEquals(new Object[]{"order"}, entry.getArgs());
        assertEquals("Failed to load order", entry.getFormattedMessage());
    }

    @Test
    public void testTrailingThrowableWithoutPlaceholders() {
        IOException e = new IOException("timeout");
        LogEntry entry = new LogEntry(ERROR, getClass(), null, "Failed", e);

        assertSame(e, entry.getThrowable());
        assertArrayEquals(new Object[0], entry.getArgs());
        assertEquals("Failed", entry.getFormattedMessage());
    }

    @Test
    public void testTrailingThrowableConsumedByPlaceholderStaysAnArgument() {
        IOException e = new IOException("timeout");
        LogEntry entry = new LogEntry(ERROR, getClass(), null, "Cause: {}", e);

        assertNull(entry.getThrowable());
        assertArrayEquals(new Object[]{e}, entry.getArgs());
        assertEquals("Cause: java.io.IOException: timeout", entry.getFormattedMessage());
    }

    @Test
    public void testExplicitThrowableWinsOverTrailingOne() {
        IOException explicit = new IOException("explicit");
        IOException trailing = new IOException("trailing");
        LogEntry entry = new LogEntry(ERROR, getClass(), explicit, "text", trailing);

        assertSame(explicit, entry.getThrowable());
        assertArrayEquals(new Object[]{trailing}, entry.getArgs());
    }

    @Test
    public void testEscapedPlaceholderDoesNotConsumeTrailingThrowable() {
        IOException e = new IOException("timeout");
        LogEntry entry = new LogEntry(ERROR, getClass(), null, "Literal \\{} and {}", 1, e);

        assertSame(e, entry.getThrowable());
        assertArrayEquals(new Object[]{1}, entry.getArgs());
        // the escape is processed exactly as it was before the throwable was taken out of the arguments
        assertEquals("Literal {} and 1", entry.getFormattedMessage());

        LogEntry onlyEscaped = new LogEntry(ERROR, getClass(), null, "Literal \\{}", e);
        assertSame(e, onlyEscaped.getThrowable());
        assertEquals("Literal {}", onlyEscaped.getFormattedMessage());
    }

    @Test
    public void testDoubleEscapedPlaceholderConsumesTrailingThrowable() {
        IOException e = new IOException("timeout");
        LogEntry entry = new LogEntry(ERROR, getClass(), null, "Path \\\\{}", e);

        assertNull(entry.getThrowable());
        assertArrayEquals(new Object[]{e}, entry.getArgs());
        assertEquals("Path \\java.io.IOException: timeout", entry.getFormattedMessage());
    }

    @Test
    public void testTrailingThrowableEdgeCases() {
        IOException e = new IOException("timeout");

        // a non-throwable last argument is never taken out
        LogEntry notThrowable = new LogEntry(ERROR, getClass(), null, "a {}", 1, "extra");
        assertNull(notThrowable.getThrowable());
        assertArrayEquals(new Object[]{1, "extra"}, notThrowable.getArgs());

        // a throwable that is not the last argument is never taken out
        LogEntry notLast = new LogEntry(ERROR, getClass(), null, "a", e, "extra");
        assertNull(notLast.getThrowable());
        assertArrayEquals(new Object[]{e, "extra"}, notLast.getArgs());

        LogEntry noArgs = new LogEntry(ERROR, getClass(), null, "a");
        assertNull(noArgs.getThrowable());
        assertArrayEquals(new Object[0], noArgs.getArgs());

        LogEntry nullArgs = new LogEntry(ERROR, getClass(), null, "a", (Object[]) null);
        assertNull(nullArgs.getThrowable());
        assertNull(nullArgs.getArgs());

        LogEntry nullMessage = new LogEntry(ERROR, getClass(), null, null, e);
        assertSame(e, nullMessage.getThrowable());
        assertArrayEquals(new Object[0], nullMessage.getArgs());
        assertEquals("null", nullMessage.getFormattedMessage());

        // the placeholders are counted in the string form of any message object
        LogEntry builderMessage = new LogEntry(ERROR, getClass(), null, new StringBuilder("a {} b {}"), 1, 2, e);
        assertSame(e, builderMessage.getThrowable());
        assertArrayEquals(new Object[]{1, 2}, builderMessage.getArgs());
        assertEquals("a 1 b 2", builderMessage.getFormattedMessage());

        LogEntry brokenMessage = new LogEntry(ERROR, getClass(), null, new BrokenToString(), e);
        assertSame(e, brokenMessage.getThrowable());
        assertEquals("[FAILED toString()]", brokenMessage.getFormattedMessage());
    }

    @Test
    public void testTimestampIsTakenAtCreation() {
        long before = System.currentTimeMillis();
        LogEntry entry = new LogEntry(ERROR, getClass(), null, "now");
        long after = System.currentTimeMillis();

        assertTrue(entry.getTimestamp() >= before && entry.getTimestamp() <= after, String.valueOf(entry.getTimestamp()));
    }

    @Test
    public void testThreadNameIsTakenAtCreation() throws InterruptedException {
        assertEquals(Thread.currentThread().getName(), new LogEntry(ERROR, getClass(), null, "here").getThreadName());

        LogEntry[] fromWorker = new LogEntry[1];
        Thread worker = new Thread(() -> fromWorker[0] = new LogEntry(ERROR, getClass(), null, "there"), "nanolog-worker");
        worker.start();
        worker.join();
        assertEquals("nanolog-worker", fromWorker[0].getThreadName());
    }

    @Test
    public void testCountPlaceholders() {
        assertEquals(0, LogEntry.countPlaceholders(null));
        assertEquals(0, LogEntry.countPlaceholders(""));
        assertEquals(0, LogEntry.countPlaceholders("no placeholders"));
        assertEquals(1, LogEntry.countPlaceholders("{}"));
        assertEquals(2, LogEntry.countPlaceholders("a {} b {}"));
        assertEquals(2, LogEntry.countPlaceholders("{}{}"));
        assertEquals(1, LogEntry.countPlaceholders("\\{} {}"));
        assertEquals(0, LogEntry.countPlaceholders("\\{}\\{}"));
        assertEquals(1, LogEntry.countPlaceholders("\\\\{}"));
        assertEquals(1, LogEntry.countPlaceholders("{{}}"));
    }

    private String getFormattedMessage(Object message, Object... args) {
        return new LogEntry(ERROR, getClass(), null, message, args).getFormattedMessage();
    }

    private static class BrokenToString {
        @Override
        public String toString() {
            throw new RuntimeException();
        }
    }

    private static class BrokenThrowable extends RuntimeException {
        @Override
        public String toString() {
            throw new RuntimeException();
        }
    }
}