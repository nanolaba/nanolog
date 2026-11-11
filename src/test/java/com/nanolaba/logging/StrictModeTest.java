package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code nanolog.strict}: diagnostics for calls whose {@code {}} placeholders and arguments do not match.
 */
class StrictModeTest extends AbstractLoggerTest {

    private final Map<String, String> environment = new HashMap<>();
    private MemoryLogger memory;

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
        LOG.clearReportedTemplates();
    }

    @AfterEach
    public void tearDown() {
        DefaultLoggerCreator.environment = System::getenv;
        System.clearProperty(DefaultLoggerCreator.PROPERTY_STRICT);
        LOG.init();
        LOG.clearReportedTemplates();
    }

    private void enableStrictMode(String value) {
        System.setProperty(DefaultLoggerCreator.PROPERTY_STRICT, value);
        LOG.init();
        memory = new MemoryLogger();
        LOG.init(memory);
    }

    @Test
    public void testOffByDefault() {
        LOG.init();
        LOG.init(new MemoryLogger());

        LOG.info("a {} b {}", 1);
        LOG.info("a {}", 1, 2);

        assertEquals("", getErrAndClear());
    }

    @Test
    public void testMismatchesAreReportedWithTheCallSite() {
        enableStrictMode("true");

        int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        LOG.info("a {} b {}", 1);
        assertEquals("[NANOLOG] com.nanolaba.logging.StrictModeTest.testMismatchesAreReportedWithTheCallSite(StrictModeTest.java:" + line +
                     "): 2 placeholders, 1 argument in \"a {} b {}\"" + System.lineSeparator(), getErrAndClear());

        LOG.info("a {}", 1, 2);
        assertTrue(getErrAndClear().endsWith("): 1 placeholder, 2 arguments in \"a {}\"" + System.lineSeparator()));

        LOG.info("none", 1);
        assertTrue(getErrAndClear().endsWith("): 0 placeholders, 1 argument in \"none\"" + System.lineSeparator()));

        // the entries themselves are logged exactly as without the check
        assertEquals(Arrays.asList("a 1 b {}", "a 1", "none"), memory.getMessages());
    }

    @Test
    public void testEachTemplateIsReportedOnce() {
        enableStrictMode("true");

        for (int i = 0; i < 3; i++) {
            LOG.info("loop {} {}", i);
        }

        String err = getErrAndClear();
        assertEquals(1, err.split(System.lineSeparator()).length, err);
        assertEquals(3, memory.getEntries().size());
    }

    @Test
    public void testMatchingCallsAreNotReported() {
        enableStrictMode("true");
        IllegalStateException e = new IllegalStateException("boom");

        LOG.info("a {} b {}", 1, 2);
        LOG.info("plain text with {} and no arguments");
        LOG.info(new HashMap<>());
        LOG.info("literal \\{} and {}", 1);
        LOG.error("Failed {}", 1, e);
        LOG.error("Failed", e);
        LOG.error("Cause: {}", e);

        assertEquals("", getErrAndClear());
    }

    @Test
    public void testTrailingThrowableDoesNotHideExtraArguments() {
        enableStrictMode("true");

        LOG.error("a {}", 1, 2, new IllegalStateException("boom"));

        assertTrue(getErrAndClear().endsWith("): 1 placeholder, 2 arguments in \"a {}\"" + System.lineSeparator()));
    }

    @Test
    public void testSourceWithoutCallSite() {
        enableStrictMode("true");

        LOG.warn(InfoObject.class, "explicit {} {}", 1);
        assertEquals("[NANOLOG] com.nanolaba.logging.util.InfoObject: 2 placeholders, 1 argument in \"explicit {} {}\"" + System.lineSeparator(),
                     getErrAndClear());

        LOG.log(INFO, null, null, () -> "no class {} {}", 1);
        assertEquals("[NANOLOG] unknown source: 2 placeholders, 1 argument in \"no class {} {}\"" + System.lineSeparator(), getErrAndClear());
    }

    @Test
    public void testAnyMessageObject() {
        enableStrictMode("true");

        LOG.log(INFO, InfoObject.class, null, () -> new StringBuilder("builder {} {}"), 1);
        assertTrue(getErrAndClear().endsWith(": 2 placeholders, 1 argument in \"builder {} {}\"" + System.lineSeparator()));

        LOG.log(INFO, InfoObject.class, null, () -> new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("broken");
            }
        }, 1);
        assertTrue(getErrAndClear().endsWith(": 0 placeholders, 1 argument in \"[FAILED toString()]\"" + System.lineSeparator()));
    }

    @Test
    public void testDisabledLevelsAreNotChecked() {
        enableStrictMode("true");
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
            }

            @Override
            public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
                return false;
            }
        });

        LOG.info("a {} b {}", 1);

        assertEquals("", getErrAndClear());
    }

    @Test
    public void testPropertyValues() {
        enableStrictMode("TRUE");
        LOG.info("upper {} {}", 1);
        assertTrue(getErrAndClear().contains("2 placeholders"));

        enableStrictMode("false");
        LOG.info("off {} {}", 1);
        assertEquals("", getErrAndClear());

        enableStrictMode("yes");
        assertEquals("[NANOLOG] Invalid value \"yes\" of \"nanolog.strict\": expected true or false" + System.lineSeparator(), getErrAndClear());
        LOG.info("invalid {} {}", 1);
        assertEquals("", getErrAndClear(), "an invalid value leaves the check off");

        System.clearProperty(DefaultLoggerCreator.PROPERTY_STRICT);
        environment.put("NANOLOG_STRICT", "true");
        LOG.init();
        LOG.init(new MemoryLogger());
        LOG.info("environment {} {}", 1);
        assertTrue(getErrAndClear().contains("2 placeholders"));
    }

    @Test
    public void testReportedTemplatesAreBounded() {
        enableStrictMode("true");
        int limit = 1000;

        for (int i = 0; i < limit; i++) {
            LOG.info("t" + i + " {} {}", 1);
        }
        assertEquals(limit, getErrAndClear().split(System.lineSeparator()).length);

        LOG.info("t0 {} {}", 1);
        assertEquals("", getErrAndClear(), "the oldest template is still remembered while the limit is not exceeded");

        LOG.info("t" + limit + " {} {}", 1);
        assertTrue(getErrAndClear().contains("t" + limit));

        LOG.info("t0 {} {}", 1);
        assertTrue(getErrAndClear().contains("t0 {} {}"), "beyond the limit the oldest template is forgotten and reported again");
    }
}
