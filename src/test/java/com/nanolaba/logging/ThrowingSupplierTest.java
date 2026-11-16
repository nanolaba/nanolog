package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A lazy message whose supplier throws must not escape from the logging call: the entry is logged with a
 * {@code [FAILED supplier: ...]} message instead.
 */
public class ThrowingSupplierTest {

    private static final Supplier<Object> BROKEN = () -> {
        throw new IllegalStateException("supplier failed");
    };

    private final List<String> entries = new ArrayList<>();

    @BeforeEach
    public void install() {
        LOG.init(entry -> entries.add(entry.toString()));
    }

    @AfterEach
    public void restore() {
        LOG.init();
    }

    private static String entry(String level, String throwable, String message, String args) {
        return "LogEntry{level=" + level + ", sourceClass=com.nanolaba.logging.ThrowingSupplierTest, throwable=" + throwable +
               ", message=" + message + ", args=" + args + "}";
    }

    @Test
    public void testThrowingSupplierIsLoggedAsFailureAtEveryLevel() {
        LOG.trace(BROKEN);
        LOG.debug(BROKEN);
        LOG.info(BROKEN);
        LOG.warn(BROKEN);
        LOG.error(BROKEN);

        String failed = "[FAILED supplier: java.lang.IllegalStateException: supplier failed]";
        assertEquals(Arrays.asList(
                entry("TRACE", "null", failed, "[]"),
                entry("DEBUG", "null", failed, "[]"),
                entry("INFO", "null", failed, "[]"),
                entry("WARN", "null", failed, "[]"),
                entry("ERROR", "null", failed, "[]")), entries);
    }

    @Test
    public void testThrowingSupplierKeepsTheThrowableOfTheEntry() {
        LOG.error(new RuntimeException("ex"), BROKEN);
        LOG.warn(ThrowingSupplierTest.class, new RuntimeException("ex2"), BROKEN);

        assertEquals(Arrays.asList(
                entry("ERROR", "java.lang.RuntimeException: ex", "[FAILED supplier: java.lang.IllegalStateException: supplier failed]", "[]"),
                entry("WARN", "java.lang.RuntimeException: ex2", "[FAILED supplier: java.lang.IllegalStateException: supplier failed]", "[]")),
                entries);
    }

    @Test
    public void testThrowingSupplierKeepsTheArgumentsOfTheEntry() {
        LOG.log(LogEntry.LogEntryLevel.INFO, ThrowingSupplierTest.class, null, BROKEN, "a", 1);

        assertEquals(Arrays.asList(
                entry("INFO", "null", "[FAILED supplier: java.lang.IllegalStateException: supplier failed]", "[a, 1]")),
                entries);
    }

    @Test
    public void testSupplierThrowingAnErrorIsLoggedAsFailure() {
        LOG.info(() -> {
            throw new AssertionError("boom");
        });

        assertEquals(Arrays.asList(entry("INFO", "null", "[FAILED supplier: java.lang.AssertionError: boom]", "[]")), entries);
    }

    @Test
    public void testSupplierThrowingAnExceptionWithBrokenToString() {
        LOG.info(() -> {
            throw new RuntimeException() {
                @Override
                public String toString() {
                    throw new IllegalStateException("broken toString");
                }
            };
        });

        assertEquals(Arrays.asList(entry("INFO", "null", "[FAILED supplier: [FAILED toString()]]", "[]")), entries);
    }

    @Test
    public void testThrowingSupplierIsEvaluatedOnce() {
        AtomicInteger calls = new AtomicInteger();
        LOG.init(entry -> {
            entry.getMessage();
            entry.getFormattedMessage();
            entries.add(entry.toString());
        });

        LOG.info(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("supplier failed");
        });

        assertEquals(1, calls.get());
        assertEquals(Arrays.asList(entry("INFO", "null", "[FAILED supplier: java.lang.IllegalStateException: supplier failed]", "[]")), entries);
    }
}
