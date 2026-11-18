package com.nanolaba.logging;

import com.nanolaba.logging.util.TraceIdProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LOG.addContextProvider(...)} and {@code nanolog.context.provider}: keys from a context of their own, such as
 * the trace ID of OpenTelemetry, added to every entry.
 */
class ContextProviderTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();

    private final Map<String, String> environment = new HashMap<>();
    private MemoryLogger memory;

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
        memory = MemoryLogger.install();
    }

    @AfterEach
    public void tearDown() {
        memory.close();
        Contexts.forgetProviders();
        System.clearProperty(DefaultLoggerCreator.PROPERTY_CONTEXT_PROVIDER);
        System.clearProperty(DefaultLoggerCreator.PROPERTY_DIAGNOSTICS);
        DefaultLoggerCreator.environment = System::getenv;
        LOG.init();
    }

    /**
     * {@code LOG.init()}, which reads the settings, then the memory logger again.
     */
    private void init() {
        LOG.init();
        LOG.init(memory);
    }

    private Map<String, Object> context() {
        return memory.getEntries().get(memory.getEntries().size() - 1).getContext();
    }

    private static Map<String, Object> map(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    @Test
    public void testAProviderAddsItsKeysToEveryEntry() {
        AtomicReference<String> traceId = new AtomicReference<>("4bf92f35");
        LOG.addContextProvider(() -> Collections.singletonMap("traceId", traceId.get()));

        LOG.info("first");
        assertEquals(map("traceId", "4bf92f35"), context());

        traceId.set("a3ce929d");
        LOG.info("second");
        assertEquals(map("traceId", "a3ce929d"), context(), "the provider is asked for every entry");
        assertThrows(UnsupportedOperationException.class, () -> context().put("other", 1));
    }

    @Test
    public void testExplicitContextComesFirstAndWins() {
        LOG.addContextProvider(() -> map("traceId", "t-1", "requestId", "from the provider"));

        try (LOG.Context ignored = LOG.context("requestId", "r-17")) {
            LOG.info("inside");
        }

        assertEquals(Arrays.asList("requestId", "traceId"), Arrays.asList(context().keySet().toArray()));
        assertEquals(map("requestId", "r-17", "traceId", "t-1"), context());
    }

    @Test
    public void testTheFirstProviderWins() {
        LOG.addContextProvider(() -> map("traceId", "first", "spanId", null));
        LOG.addContextProvider(() -> map("traceId", "second", "spanId", "second", "tenant", "acme"));

        LOG.info("merged");

        assertEquals(map("traceId", "first", "spanId", null, "tenant", "acme"), context(), "a null value is a value");
    }

    @Test
    public void testNothingToAdd() {
        LOG.addContextProvider(() -> null);
        LOG.addContextProvider(Collections::emptyMap);
        Map<String, Object> withNullKey = new HashMap<>();
        withNullKey.put(null, "ignored");
        withNullKey.put("tenant", "acme");
        LOG.addContextProvider(() -> withNullKey);

        LOG.info("with a null key");
        assertEquals(map("tenant", "acme"), context());

        Contexts.forgetProviders();
        LOG.addContextProvider(() -> null);
        try (LOG.Context ignored = LOG.context("requestId", "r-17")) {
            LOG.info("only the explicit context");
            assertEquals(map("requestId", "r-17"), context());
            assertSame(Contexts.current(), context(), "the map of the thread itself, not a copy");
        }
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testDisabledLevelsDoNotCallTheProviders() {
        AtomicInteger calls = new AtomicInteger();
        LOG.addContextProvider(() -> {
            calls.incrementAndGet();
            return Collections.singletonMap("traceId", "t-1");
        });
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
            }

            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
                return level != LogEntry.LogEntryLevel.DEBUG;
            }
        });

        LOG.debug("off");
        assertEquals(0, calls.get());

        LOG.info("on");
        assertEquals(1, calls.get());
    }

    @Test
    public void testAddingAndRemoving() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<Map<String, ?>> provider = () -> {
            calls.incrementAndGet();
            return Collections.singletonMap("traceId", "t-1");
        };
        LOG.addContextProvider(provider);
        LOG.addContextProvider(provider);
        LOG.addContextProvider(null);

        LOG.info("once");
        assertEquals(1, calls.get(), "a provider added twice is asked once");

        LOG.removeContextProvider(() -> Collections.singletonMap("other", 1));
        LOG.removeContextProvider(null);
        LOG.info("still there");
        assertEquals(map("traceId", "t-1"), context());

        LOG.removeContextProvider(provider);
        LOG.info("removed");
        assertEquals(Collections.emptyMap(), context());
        assertEquals(2, calls.get());
    }

    private static final class Failing implements Supplier<Map<String, ?>> {

        @Override
        public Map<String, ?> get() {
            throw new IllegalStateException("tracer down");
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testAFailingProviderIsReportedOnceAndItsKeysAreLeftOut() {
        LOG.addContextProvider(new Failing());
        Map raw = new LinkedHashMap();
        raw.put("partial", "left out");
        raw.put(42, "not a string key");
        LOG.addContextProvider(() -> raw);
        LOG.addContextProvider(() -> Collections.singletonMap("tenant", "acme"));

        LOG.info("first");
        String err = getErrAndClear();
        assertTrue(err.startsWith("[NANOLOG] Context provider com.nanolaba.logging.ContextProviderTest$Failing threw " +
                                  "java.lang.IllegalStateException: tracer down; entries are logged without its keys" + NL), err);
        assertEquals(2, err.split(NL).length, err);
        assertTrue(err.contains("threw java.lang.ClassCastException"), err);

        LOG.info("second");
        assertEquals("", getErrAndClear(), "each provider is reported once");
        assertEquals(Arrays.asList("first", "second"), memory.getMessages(), "the entries are logged");
        assertEquals(map("tenant", "acme"), context(), "a failing provider adds none of its keys");

        Contexts.forgetProviders();
        DefaultLoggerCreator.forgetDiagnostics();
        LOG.info("forgotten");
        assertEquals(Collections.emptyMap(), context(), "no provider is asked once they are forgotten");
        LOG.addContextProvider(new Failing());
        LOG.info("failing again");
        assertTrue(getErrAndClear().contains("ContextProviderTest$Failing threw"), "the failures are forgotten as well");
    }

    @Test
    public void testEveryFailureIsReportedWithDiagnosticsAll() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_DIAGNOSTICS, "all");
        init();
        LOG.addContextProvider(new Failing());

        LOG.info("first");
        LOG.info("second");

        assertEquals(2, getErrAndClear().split(NL).length);
    }

    @Test
    public void testAProviderThatLogsDoesNotCallItself() {
        LOG.addContextProvider(() -> {
            LOG.info("inside the provider");
            return Collections.singletonMap("traceId", "t-1");
        });

        LOG.info("outside");

        assertEquals(Arrays.asList("inside the provider", "outside"), memory.getMessages());
        assertEquals(Collections.emptyMap(), memory.getEntries().get(0).getContext());
        assertEquals(map("traceId", "t-1"), context());

        LOG.info("again");
        assertEquals(map("traceId", "t-1"), context(), "the providers are asked again after the provider returned");
    }

    @Test
    public void testKeysNamedLikeSecretsAreMasked() {
        LOG.addContextProvider(() -> Collections.singletonMap("authToken", "abc"));

        LOG.info("masked");

        assertEquals(map("authToken", LOG.SECRET), context());
    }

    @Test
    public void testConfiguredProviders() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_CONTEXT_PROVIDER, " com.nanolaba.logging.util.TraceIdProvider ,");
        init();
        LOG.addContextProvider(() -> map("traceId", "added", "tenant", "acme"));

        LOG.info("configured");
        assertEquals(map("traceId", "configured", "tenant", "acme"), context(), "configured providers come first");
        assertEquals("", getErrAndClear());

        System.clearProperty(DefaultLoggerCreator.PROPERTY_CONTEXT_PROVIDER);
        init();
        LOG.info("added only");
        assertEquals(map("traceId", "added", "tenant", "acme"), context(), "LOG.init() reads the setting again and keeps the added ones");
    }

    @Test
    public void testInvalidConfiguredProvidersAreReported() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_CONTEXT_PROVIDER, "com.acme.Missing,java.lang.Object,java.lang.Integer," +
                                                                           TraceIdProvider.class.getName());
        init();

        String expected = "expected a class implementing Supplier<Map<String, ?>> with a public no-argument constructor";
        assertEquals("[NANOLOG] Invalid value \"com.acme.Missing\" of \"nanolog.context.provider\": " + expected +
                     " (java.lang.ClassNotFoundException: com.acme.Missing); set as a system property" + NL +
                     "[NANOLOG] Invalid value \"java.lang.Object\" of \"nanolog.context.provider\": " + expected +
                     " (java.lang.ClassCastException: Cannot cast java.lang.Object to java.util.function.Supplier); set as a system property" + NL +
                     "[NANOLOG] Invalid value \"java.lang.Integer\" of \"nanolog.context.provider\": " + expected +
                     " (java.lang.NoSuchMethodException: java.lang.Integer.<init>()); set as a system property" + NL,
                     getErrAndClear());

        LOG.info("the valid one applies");
        assertEquals(map("traceId", "configured"), context());
    }
}
