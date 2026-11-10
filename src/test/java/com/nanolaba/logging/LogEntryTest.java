package com.nanolaba.logging;

import org.junit.jupiter.api.Test;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;

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