package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LOG.lazy(...)}: one argument computed only for an entry that is written, with the template kept.
 */
class LazyArgumentTest extends AbstractLoggerTest {

    private final AtomicInteger calls = new AtomicInteger();
    private MemoryLogger memory;

    @BeforeEach
    public void install() {
        memory = MemoryLogger.install();
    }

    @AfterEach
    public void close() {
        memory.close();
    }

    private Supplier<Object> counted(Object value) {
        return () -> {
            calls.incrementAndGet();
            return value;
        };
    }

    @Test
    public void testADisabledLevelNeverAsks() {
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
            }

            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
                return level != LogEntry.LogEntryLevel.DEBUG;
            }
        });

        LOG.debug("Stats: {}", LOG.lazy(counted(42)));
        LOG.debug(InfoObject.class, "Stats: {}", LOG.lazy(counted(42)));

        assertEquals(0, calls.get());
    }

    @Test
    public void testAnEnabledLevelAsksOnceAndKeepsTheTemplate() {
        LOG.Lazy stats = LOG.lazy(counted(42));

        LOG.info("Stats: {}", stats);

        LogEntry entry = memory.getEntries().get(0);
        assertEquals("Stats: 42", entry.getFormattedMessage());
        assertEquals("Stats: {}", entry.getMessage());
        assertSame(stats, entry.getArgs()[0], "the argument stays an argument of the template");
        String json = new JsonLogger().createLogString(entry);
        assertTrue(json.contains("\"template\":\"Stats: {}\",\"args\":[\"42\"]"), json);
        assertEquals("42", stats.toString());
        LOG.info("Again: {}", stats);
        assertEquals("Again: 42", memory.getMessages().get(1));
        assertEquals(1, calls.get(), "the value is computed once");
    }

    @Test
    public void testTheValueIsFormattedLikeAnArgument() {
        LOG.info("{} {} {}", LOG.lazy(() -> new int[]{1, 2}), LOG.lazy(() -> null), LOG.lazy(null));

        assertEquals("[1, 2] null null", memory.getMessages().get(0));
    }

    @Test
    public void testASupplierThatThrowsIsReportedInTheText() {
        LOG.Lazy failing = LOG.lazy(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("no stats");
        });

        LOG.info("Stats: {}", failing);
        new JsonLogger().createLogString(memory.getEntries().get(0));

        assertEquals("Stats: [FAILED supplier: java.lang.IllegalStateException: no stats]", memory.getMessages().get(0));
        assertEquals(1, calls.get(), "a failure is kept as well");
    }

    @Test
    public void testAMaskedFieldIsNeverAsked() {
        LOG.info("Login", LOG.kv("token", LOG.lazy(counted("abc"))));

        assertEquals("***", String.valueOf(memory.getEntries().get(0).getFields().get("token")));
        assertEquals(0, calls.get(), "a field named like a secret is masked before anything formats it");
    }
}
