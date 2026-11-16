package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.DEBUG;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.ERROR;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.TRACE;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.WARN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Levels changed while the application runs: {@code LOG.setLevel}, {@code clearLevel}, {@code resetLevels},
 * {@code withLevel} and the same methods of {@link ConsoleLogger} and {@link CompositeLogger}.
 */
class RuntimeLevelsTest extends AbstractLoggerTest {

    private static final String UTIL = "com.nanolaba.logging.util";

    private final Map<String, String> environment = new HashMap<>();

    @BeforeEach
    public void fakeEnvironment() {
        DefaultLoggerCreator.environment = environment::get;
        LOG.clearReportedLoggerFailures();
    }

    @AfterEach
    public void cleanUp() {
        DefaultLoggerCreator.environment = System::getenv;
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith(DefaultLoggerCreator.PROPERTY_PREFIX)) {
                System.clearProperty(key);
            }
        }
        LOG.init();
        LOG.clearReportedLoggerFailures();
    }

    private static List<String> lines(String text) {
        return text.isEmpty() ? Collections.emptyList() : Arrays.asList(text.split(System.lineSeparator()));
    }

    private static void assertLevels(ConsoleLogger logger, boolean trace, boolean debug, boolean info, boolean warn, boolean error) {
        assertEquals(trace, logger.isTraceEnabled(), "TRACE");
        assertEquals(debug, logger.isDebugEnabled(), "DEBUG");
        assertEquals(info, logger.isInfoEnabled(), "INFO");
        assertEquals(warn, logger.isWarnEnabled(), "WARN");
        assertEquals(error, logger.isErrorEnabled(), "ERROR");
    }

    // ConsoleLogger

    @Test
    public void testGlobalLevel() {
        ConsoleLogger logger = new ConsoleLogger();

        assertSame(logger, logger.setLevel(WARN));
        assertLevels(logger, false, false, false, true, true);

        logger.setLevel(TRACE);
        assertLevels(logger, true, true, true, true, true);

        logger.setLevel(ERROR);
        assertLevels(logger, false, false, false, false, true);
    }

    @Test
    public void testGlobalLevelLeavesPackageRulesAlone() {
        ConsoleLogger logger = new ConsoleLogger().setLevel(UTIL, DEBUG).setLevel(ERROR);

        assertTrue(logger.isEnabled(DEBUG, InfoObject.class));
        assertFalse(logger.isEnabled(WARN, LOG.class));
    }

    @Test
    public void testPackageLevel() {
        System.setProperty("nanolog.level", "WARN");
        ConsoleLogger logger = new ConsoleLogger();
        assertFalse(logger.isEnabled(DEBUG, InfoObject.class));
        assertFalse(logger.mayBeEnabled(DEBUG));

        assertSame(logger, logger.setLevel(" " + UTIL + " ", DEBUG));

        assertTrue(logger.isEnabled(DEBUG, InfoObject.class), "a threshold cached before the change is not used");
        assertFalse(logger.isEnabled(TRACE, InfoObject.class));
        assertFalse(logger.isEnabled(DEBUG, LOG.class), "other packages keep the global level");
        assertTrue(logger.mayBeEnabled(DEBUG));

        logger.setLevel(UTIL, ERROR);
        assertFalse(logger.isEnabled(WARN, InfoObject.class), "a second call replaces the rule");
        assertFalse(logger.mayBeEnabled(DEBUG));
    }

    @Test
    public void testClearLevel() {
        System.setProperty("nanolog.level", "WARN");
        System.setProperty("nanolog.level." + UTIL, "TRACE");
        ConsoleLogger logger = new ConsoleLogger();
        assertTrue(logger.isEnabled(TRACE, InfoObject.class));

        assertSame(logger, logger.clearLevel(" " + UTIL));

        assertFalse(logger.isEnabled(INFO, InfoObject.class), "the global level applies again");
        assertTrue(logger.isEnabled(WARN, InfoObject.class));
        assertFalse(logger.mayBeEnabled(TRACE));

        logger.clearLevel("com.nanolaba.unknown");
        assertEquals("", getErrAndClear(), "clearing a name without a rule is not an error");
    }

    @Test
    public void testResetLevelsReadsTheSettingsAgain() {
        System.setProperty("nanolog.level", "WARN");
        System.setProperty("nanolog.level." + UTIL, "DEBUG");
        ConsoleLogger logger = new ConsoleLogger();
        logger.setLevel(TRACE).setLevel(UTIL, ERROR).setLevel("com.nanolaba.logging.LOG", TRACE).setInfoEnabled(false);

        assertSame(logger, logger.resetLevels());

        assertLevels(logger, false, false, false, true, true);
        assertTrue(logger.isEnabled(DEBUG, InfoObject.class), "the configured rule is back");
        assertFalse(logger.isEnabled(INFO, LOG.class), "a rule added at run time is gone");
        assertFalse(logger.mayBeEnabled(TRACE));

        System.setProperty("nanolog.level", "ERROR");
        logger.resetLevels();
        assertLevels(logger, false, false, false, false, true);
    }

    @Test
    public void testResetLevelsWithoutSettingsEnablesEverything() {
        ConsoleLogger logger = new ConsoleLogger().setLevel(ERROR).setLevel(UTIL, ERROR);

        logger.resetLevels();

        assertLevels(logger, true, true, true, true, true);
        assertTrue(logger.isEnabled(TRACE, InfoObject.class));
    }

    @Test
    public void testWithLevelRestoresTheRuleOnClose() {
        System.setProperty("nanolog.level", "WARN");
        System.setProperty("nanolog.level." + UTIL, "INFO");
        ConsoleLogger logger = new ConsoleLogger();

        try (LOG.LevelOverride outer = logger.withLevel(UTIL, TRACE)) {
            assertTrue(logger.isEnabled(TRACE, InfoObject.class));
            try (LOG.LevelOverride inner = logger.withLevel(UTIL, ERROR)) {
                assertFalse(logger.isEnabled(WARN, InfoObject.class));
            }
            assertTrue(logger.isEnabled(TRACE, InfoObject.class), "closing the inner override restores the outer one");
        }

        assertFalse(logger.isEnabled(DEBUG, InfoObject.class));
        assertTrue(logger.isEnabled(INFO, InfoObject.class), "the configured rule is back");
    }

    @Test
    public void testWithLevelOnANameWithoutARuleRemovesItOnClose() {
        ConsoleLogger logger = new ConsoleLogger().setLevel(WARN);
        LOG.LevelOverride override = logger.withLevel(UTIL, DEBUG);
        assertTrue(logger.isEnabled(DEBUG, InfoObject.class));

        override.close();
        assertFalse(logger.isEnabled(DEBUG, InfoObject.class));
        assertFalse(logger.mayBeEnabled(DEBUG));

        logger.setLevel(UTIL, TRACE);
        override.close();
        assertTrue(logger.isEnabled(TRACE, InfoObject.class), "a second close changes nothing");
    }

    @Test
    public void testWithLevelRestoresARuleAtOff() {
        System.setProperty("nanolog.level." + UTIL, "OFF");
        ConsoleLogger logger = new ConsoleLogger();

        logger.withLevel(UTIL, DEBUG).close();

        assertFalse(logger.isEnabled(ERROR, InfoObject.class));
    }

    @Test
    public void testInvalidArgumentsChangeNothing() {
        System.setProperty("nanolog.level", "WARN");
        ConsoleLogger logger = new ConsoleLogger();

        assertSame(logger, logger.setLevel((LogEntry.LogEntryLevel) null));
        assertSame(logger, logger.setLevel(null, DEBUG));
        logger.setLevel(" ", DEBUG);
        logger.setLevel(UTIL, null);
        assertSame(logger, logger.clearLevel(null));
        logger.clearLevel("");
        logger.withLevel(null, DEBUG).close();
        logger.withLevel(UTIL, null).close();

        assertLevels(logger, false, false, false, true, true);
        assertFalse(logger.mayBeEnabled(DEBUG));
        assertEquals(Arrays.asList(
                "[NANOLOG] Invalid level null in setLevel; nothing changed",
                "[NANOLOG] Invalid package or class name null in setLevel; nothing changed",
                "[NANOLOG] Invalid package or class name \" \" in setLevel; nothing changed",
                "[NANOLOG] Invalid level null for \"com.nanolaba.logging.util\" in setLevel; nothing changed",
                "[NANOLOG] Invalid package or class name null in clearLevel; nothing changed",
                "[NANOLOG] Invalid package or class name \"\" in clearLevel; nothing changed",
                "[NANOLOG] Invalid package or class name null in withLevel; nothing changed",
                "[NANOLOG] Invalid level null for \"com.nanolaba.logging.util\" in withLevel; nothing changed"),
                     lines(getErrAndClear()));
    }

    @Test
    public void testLevelStateIsSafelyPublished() throws NoSuchFieldException, NoSuchMethodException {
        // pitest mutates neither volatile nor synchronized, so the contract is asserted directly
        for (String field : new String[]{"traceEnabled", "debugEnabled", "infoEnabled", "warnEnabled", "errorEnabled", "rules"}) {
            assertTrue(Modifier.isVolatile(ConsoleLogger.class.getDeclaredField(field).getModifiers()),
                       "ConsoleLogger." + field + " must be volatile");
        }
        assertTrue(Modifier.isSynchronized(ConsoleLogger.class.getMethod("setLevel", LogEntry.LogEntryLevel.class).getModifiers()));
        assertTrue(Modifier.isSynchronized(ConsoleLogger.class.getMethod("resetLevels").getModifiers()));
        assertTrue(Modifier.isSynchronized(ConsoleLogger.class.getDeclaredMethod("replaceRule", String.class, Integer.class).getModifiers()),
                   "rule changes copy and swap the snapshot under one lock, so concurrent changes are not lost");
    }

    // LOG

    @Test
    public void testLogDelegatesToTheInstalledLogger() {
        ConsoleLogger logger = new ConsoleLogger();
        LOG.init(logger);

        LOG.setLevel(WARN);
        LOG.setLevel(UTIL, DEBUG);
        assertFalse(LOG.isInfoEnabled(LOG.class));
        assertTrue(LOG.isDebugEnabled(InfoObject.class));

        LOG.clearLevel(UTIL);
        assertFalse(LOG.isDebugEnabled(InfoObject.class));

        try (LOG.LevelOverride ignored = LOG.withLevel("com.nanolaba.logging", TRACE)) {
            LOG.trace("visible");
        }
        LOG.trace("hidden");

        LOG.resetLevels();
        assertTrue(LOG.isTraceEnabled(LOG.class));

        String out = getOutAndClear();
        assertTrue(out.contains("[RuntimeLevelsTest] visible"), out);
        assertFalse(out.contains("hidden"), out);
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testUnsupportedLoggerIsReportedOnceAndLeftUnchanged() {
        LOG.init(new PlainLogger());

        LOG.setLevel(DEBUG);
        LOG.setLevel(UTIL, DEBUG);
        LOG.clearLevel(UTIL);
        LOG.resetLevels();
        LOG.withLevel(UTIL, TRACE).close();

        List<String> diagnostic = Collections.singletonList("[NANOLOG] Logger com.nanolaba.logging.RuntimeLevelsTest$PlainLogger does not implement " +
                                                            "ILevelControl; LOG.setLevel, clearLevel, resetLevels and withLevel change nothing");
        assertEquals(diagnostic, lines(getErrAndClear()));

        LOG.clearReportedLoggerFailures();
        LOG.setLevel(INFO);
        assertEquals(diagnostic, lines(getErrAndClear()), "reported again once the reports are cleared");
    }

    @Test
    public void testCompositeWithTheSameDelegateTwiceRestoresItsRule() {
        ConsoleLogger logger = new ConsoleLogger().setLevel(WARN);
        CompositeLogger composite = new CompositeLogger(logger, logger);

        composite.withLevel(UTIL, DEBUG).close();

        assertFalse(logger.isEnabled(DEBUG, InfoObject.class), "the overrides close in reverse order");
    }

    // CompositeLogger

    @Test
    public void testCompositeForwardsToTheDelegatesThatSupportIt() {
        ConsoleLogger first = new ConsoleLogger();
        ConsoleLogger second = new ConsoleLogger();
        CompositeLogger composite = new CompositeLogger(first, new MemoryLogger(), second);
        LOG.init(composite);

        LOG.setLevel(WARN);
        assertLevels(first, false, false, false, true, true);
        assertLevels(second, false, false, false, true, true);

        LOG.setLevel(UTIL, DEBUG);
        assertTrue(first.isEnabled(DEBUG, InfoObject.class));
        assertTrue(second.isEnabled(DEBUG, InfoObject.class));

        LOG.clearLevel(UTIL);
        assertFalse(first.isEnabled(DEBUG, InfoObject.class));
        assertFalse(second.isEnabled(DEBUG, InfoObject.class));

        try (LOG.LevelOverride ignored = LOG.withLevel(UTIL, TRACE)) {
            assertTrue(first.isEnabled(TRACE, InfoObject.class));
            assertTrue(second.isEnabled(TRACE, InfoObject.class));
        }
        assertFalse(first.isEnabled(TRACE, InfoObject.class));
        assertFalse(second.isEnabled(TRACE, InfoObject.class));

        LOG.resetLevels();
        assertLevels(first, true, true, true, true, true);
        assertLevels(second, true, true, true, true, true);

        assertEquals("", getErrAndClear(), "a delegate without level control is skipped silently");
        assertSame(composite, composite.setLevel(INFO));
        assertSame(composite, composite.setLevel(UTIL, INFO));
        assertSame(composite, composite.clearLevel(UTIL));
        assertSame(composite, composite.resetLevels());
    }

    @Test
    public void testCompositeChecksTheArgumentsOnce() {
        ConsoleLogger first = new ConsoleLogger();
        CompositeLogger composite = new CompositeLogger(first, new ConsoleLogger());

        composite.setLevel((LogEntry.LogEntryLevel) null);
        composite.setLevel(null, DEBUG);
        composite.setLevel(UTIL, null);
        composite.clearLevel(" ");
        composite.withLevel(UTIL, null).close();

        assertLevels(first, true, true, true, true, true);
        assertEquals(Arrays.asList(
                "[NANOLOG] Invalid level null in setLevel; nothing changed",
                "[NANOLOG] Invalid package or class name null in setLevel; nothing changed",
                "[NANOLOG] Invalid level null for \"com.nanolaba.logging.util\" in setLevel; nothing changed",
                "[NANOLOG] Invalid package or class name \" \" in clearLevel; nothing changed",
                "[NANOLOG] Invalid level null for \"com.nanolaba.logging.util\" in withLevel; nothing changed"),
                     lines(getErrAndClear()));
    }

    /**
     * A logger without level control.
     */
    static class PlainLogger implements ILogger {
        @Override
        public void log(LogEntry entry) {
        }
    }
}
