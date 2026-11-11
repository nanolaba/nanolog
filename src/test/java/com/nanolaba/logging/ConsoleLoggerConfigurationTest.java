package com.nanolaba.logging;

import com.nanolaba.logging.util.DebugObject;
import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.DEBUG;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.ERROR;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.TRACE;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.WARN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ConsoleLogger} settings from system properties, environment variables and {@code nanolog.properties}.
 */
class ConsoleLoggerConfigurationTest extends AbstractLoggerTest {

    private final Map<String, String> environment = new HashMap<>();

    @BeforeEach
    public void fakeEnvironment() {
        DefaultLoggerCreator.environment = environment::get;
    }

    @AfterEach
    public void cleanUp() {
        DefaultLoggerCreator.environment = System::getenv;
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith(DefaultLoggerCreator.PROPERTY_PREFIX)) {
                System.clearProperty(key);
            }
        }
    }

    @Test
    public void testDefaultsWithoutSettings() {
        ConsoleLogger logger = new ConsoleLogger();

        assertLevels(logger, true, true, true, true, true);
        assertTrue(logger.isShowLevel());
        assertTrue(logger.isShowDate());
        assertTrue(logger.isShowSource());
        assertFalse(logger.isShowSourceFullName());
        assertFalse(logger.isShowLocation());
        assertEquals("dd.MM.yyyy HH:mm:ss", logger.getDateFormat().toPattern());
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testGlobalLevel() {
        System.setProperty("nanolog.level", "TRACE");
        assertLevels(new ConsoleLogger(), true, true, true, true, true);

        System.setProperty("nanolog.level", "DEBUG");
        assertLevels(new ConsoleLogger(), false, true, true, true, true);

        System.setProperty("nanolog.level", "INFO");
        assertLevels(new ConsoleLogger(), false, false, true, true, true);

        System.setProperty("nanolog.level", "WARN");
        assertLevels(new ConsoleLogger(), false, false, false, true, true);

        System.setProperty("nanolog.level", "ERROR");
        assertLevels(new ConsoleLogger(), false, false, false, false, true);

        System.setProperty("nanolog.level", "OFF");
        assertLevels(new ConsoleLogger(), false, false, false, false, false);

        System.setProperty("nanolog.level", " warn ");
        assertLevels(new ConsoleLogger(), false, false, false, true, true);

        assertEquals("", getErrAndClear());
    }

    @Test
    public void testInvalidGlobalLevel() {
        System.setProperty("nanolog.level", "LOUD");
        ConsoleLogger logger = new ConsoleLogger();

        assertLevels(logger, true, true, true, true, true);
        assertEquals("[NANOLOG] Invalid value \"LOUD\" of \"nanolog.level\": expected TRACE, DEBUG, INFO, WARN, ERROR or OFF" +
                     System.lineSeparator(), getErrAndClear());
    }

    @Test
    public void testDisplayOptions() {
        System.setProperty("nanolog.console.showLevel", "false");
        System.setProperty("nanolog.console.showDate", "FALSE");
        System.setProperty("nanolog.console.showSource", "false");
        System.setProperty("nanolog.console.showSourceFullName", "true");
        System.setProperty("nanolog.console.showLocation", " True ");
        System.setProperty("nanolog.console.dateFormat", "yyyy-MM-dd");
        ConsoleLogger logger = new ConsoleLogger();

        assertFalse(logger.isShowLevel());
        assertFalse(logger.isShowDate());
        assertFalse(logger.isShowSource());
        assertTrue(logger.isShowSourceFullName());
        assertTrue(logger.isShowLocation());
        assertEquals("yyyy-MM-dd", logger.getDateFormat().toPattern());
        assertEquals("", getErrAndClear());
    }

    @Test
    public void testInvalidDisplayOptions() {
        System.setProperty("nanolog.console.showDate", "yes");
        System.setProperty("nanolog.console.showSourceFullName", "yes");
        System.setProperty("nanolog.console.dateFormat", "qqq");
        ConsoleLogger logger = new ConsoleLogger();

        // an invalid value keeps the default, whichever it is
        assertTrue(logger.isShowDate());
        assertFalse(logger.isShowSourceFullName());
        assertEquals("dd.MM.yyyy HH:mm:ss", logger.getDateFormat().toPattern());
        String err = getErrAndClear();
        assertTrue(err.contains("[NANOLOG] Invalid value \"yes\" of \"nanolog.console.showDate\": expected true or false"), err);
        assertTrue(err.contains("[NANOLOG] Invalid value \"qqq\" of \"nanolog.console.dateFormat\": Illegal pattern character 'q'"), err);
    }

    @Test
    public void testSettersOverrideSettings() {
        System.setProperty("nanolog.level", "ERROR");
        System.setProperty("nanolog.console.showDate", "false");

        ConsoleLogger logger = new ConsoleLogger().setInfoEnabled(true).setShowDate(true);

        assertTrue(logger.isInfoEnabled());
        assertTrue(logger.isShowDate());
    }

    @Test
    public void testPackageLevelBeatsGlobalLevel() {
        System.setProperty("nanolog.level", "WARN");
        System.setProperty("nanolog.level.com.nanolaba.logging.util", "DEBUG");
        ConsoleLogger logger = new ConsoleLogger();

        assertFalse(logger.isEnabled(TRACE, InfoObject.class));
        assertTrue(logger.isEnabled(DEBUG, InfoObject.class));
        assertTrue(logger.isEnabled(ERROR, InfoObject.class));

        assertFalse(logger.isEnabled(INFO, LOG.class), "outside the package the global level applies");
        assertTrue(logger.isEnabled(WARN, LOG.class));

        assertFalse(logger.isEnabled(INFO, null), "an entry without a source class follows the global level");
        assertTrue(logger.isEnabled(WARN, null));
    }

    @Test
    public void testPackageLevelBeatsSetters() {
        System.setProperty("nanolog.level.com.nanolaba.logging.util", "TRACE");
        ConsoleLogger logger = new ConsoleLogger().setTraceEnabled(false).setDebugEnabled(false);

        assertTrue(logger.isEnabled(TRACE, InfoObject.class));
        assertFalse(logger.isEnabled(TRACE, LOG.class));
    }

    @Test
    public void testLongestPrefixWins() {
        System.setProperty("nanolog.level.com.nanolaba", "ERROR");
        System.setProperty("nanolog.level.com.nanolaba.logging.util", "TRACE");
        System.setProperty("nanolog.level.com.nanolaba.logging.util.InfoObject", "WARN");
        ConsoleLogger logger = new ConsoleLogger();

        assertFalse(logger.isEnabled(INFO, InfoObject.class));
        assertTrue(logger.isEnabled(WARN, InfoObject.class));

        assertTrue(logger.isEnabled(TRACE, DebugObject.class));

        assertFalse(logger.isEnabled(WARN, LOG.class));
        assertTrue(logger.isEnabled(ERROR, LOG.class));

        // a second lookup of the same class gives the same answer
        assertFalse(logger.isEnabled(INFO, InfoObject.class));
        assertTrue(logger.isEnabled(TRACE, DebugObject.class));
    }

    @Test
    public void testPrefixMatchesWholeSegmentsOnly() {
        System.setProperty("nanolog.level.com.nanolaba.logging.util.Info", "OFF");
        System.setProperty("nanolog.level.com.nanolab", "OFF");
        ConsoleLogger logger = new ConsoleLogger();

        assertTrue(logger.isEnabled(TRACE, InfoObject.class));
        assertTrue(logger.isEnabled(TRACE, LOG.class));
    }

    @Test
    public void testClassRuleCoversNestedClasses() {
        System.setProperty("nanolog.level." + ConsoleLoggerConfigurationTest.class.getName(), "ERROR");
        ConsoleLogger logger = new ConsoleLogger();

        assertFalse(logger.isEnabled(WARN, Nested.class));
        assertTrue(logger.isEnabled(ERROR, Nested.class));
        assertFalse(logger.isEnabled(WARN, Nested.Deeper.class));
    }

    @Test
    public void testClassWithoutAPackage() {
        System.setProperty("nanolog.level.com", "OFF");
        ConsoleLogger logger = new ConsoleLogger();

        // a class without a package matches no package rule
        assertTrue(logger.isEnabled(TRACE, int.class));
        assertFalse(logger.isEnabled(ERROR, InfoObject.class));
    }

    @Test
    public void testLevelList() {
        System.setProperty("nanolog.level", "OFF");
        System.setProperty("nanolog.levels", " com.nanolaba.logging.util = debug ,, com.nanolaba.logging.LOG=ERROR,");
        ConsoleLogger logger = new ConsoleLogger();

        assertFalse(logger.isEnabled(TRACE, InfoObject.class));
        assertTrue(logger.isEnabled(DEBUG, InfoObject.class));
        assertFalse(logger.isEnabled(WARN, LOG.class));
        assertTrue(logger.isEnabled(ERROR, LOG.class));
        assertFalse(logger.isEnabled(ERROR, ConsoleLogger.class));
        assertEquals("", getErrAndClear(), "empty items of the list are not errors");
    }

    @Test
    public void testSinglePropertyBeatsTheList() {
        System.setProperty("nanolog.levels", "com.nanolaba.logging.util=ERROR");
        System.setProperty("nanolog.level.com.nanolaba.logging.util", "TRACE");

        assertTrue(new ConsoleLogger().isEnabled(TRACE, InfoObject.class));
    }

    @Test
    public void testInvalidLevelRules() {
        System.setProperty("nanolog.level", "WARN");
        System.setProperty("nanolog.levels", "broken,=INFO,com.nanolaba.logging.util=LOUD,com.nanolaba.logging.LOG=ERROR");
        System.setProperty("nanolog.level.com.nanolaba.logging.ConsoleLogger", "QUIET");
        ConsoleLogger logger = new ConsoleLogger();

        assertFalse(logger.isEnabled(TRACE, InfoObject.class), "an invalid rule is ignored, the global level applies");
        assertTrue(logger.isEnabled(WARN, InfoObject.class));
        assertFalse(logger.isEnabled(TRACE, ConsoleLogger.class), "an invalid rule is ignored, the global level applies");
        assertFalse(logger.isEnabled(WARN, LOG.class), "the valid rules still apply");

        String err = getErrAndClear();
        assertTrue(err.contains("[NANOLOG] Invalid item \"broken\" of \"nanolog.levels\": expected <package or class>=<level>"), err);
        assertTrue(err.contains("[NANOLOG] Invalid item \"=INFO\" of \"nanolog.levels\": expected <package or class>=<level>"), err);
        assertTrue(err.contains("[NANOLOG] Invalid value \"LOUD\" of \"nanolog.levels\": expected TRACE, DEBUG, INFO, WARN, ERROR or OFF"), err);
        assertTrue(err.contains("[NANOLOG] Invalid value \"QUIET\" of \"nanolog.level.com.nanolaba.logging.ConsoleLogger\""), err);
    }

    @Test
    public void testEnvironmentVariables() {
        environment.put("NANOLOG_LEVEL", "ERROR");
        environment.put("NANOLOG_LEVELS", "com.nanolaba.logging.util=TRACE");
        environment.put("NANOLOG_CONSOLE_SHOWDATE", "false");
        environment.put("NANOLOG_CONSOLE_SHOWLOCATION", "true");
        environment.put("NANOLOG_CONSOLE_DATEFORMAT", "yyyy");
        ConsoleLogger logger = new ConsoleLogger();

        assertLevels(logger, false, false, false, false, true);
        assertTrue(logger.isEnabled(TRACE, InfoObject.class));
        assertFalse(logger.isShowDate());
        assertTrue(logger.isShowLocation());
        assertEquals("yyyy", logger.getDateFormat().toPattern());
    }

    @Test
    public void testSystemPropertiesBeatEnvironmentVariables() {
        environment.put("NANOLOG_LEVEL", "ERROR");
        environment.put("NANOLOG_CONSOLE_SHOWDATE", "false");
        System.setProperty("nanolog.level", "INFO");
        System.setProperty("nanolog.console.showDate", "true");
        ConsoleLogger logger = new ConsoleLogger();

        assertLevels(logger, false, false, true, true, true);
        assertTrue(logger.isShowDate());
    }

    @Test
    public void testSettingsFromTheConfigurationFile() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_LOGGER_CONFIG_FILE, "levels.properties");
        LOG.init();

        ConsoleLogger logger = (ConsoleLogger) LOG.getLogger();
        assertLevels(logger, false, false, false, true, true);
        assertTrue(logger.isEnabled(DEBUG, InfoObject.class));
        assertFalse(logger.isShowDate());

        LOG.debug(InfoObject.class, "shown");
        LOG.debug(LOG.class, "hidden");
        assertEquals("DEBUG [InfoObject] shown" + System.lineSeparator(), getOutAndClear());
    }

    @Test
    public void testEnvironmentBeatsTheConfigurationFile() {
        environment.put("NANOLOG_LEVEL", "ERROR");
        System.setProperty(DefaultLoggerCreator.PROPERTY_LOGGER_CONFIG_FILE, "levels.properties");
        LOG.init();

        ConsoleLogger logger = (ConsoleLogger) LOG.getLogger();
        assertLevels(logger, false, false, false, false, true);
        assertEquals(null, System.getProperty("nanolog.level"), "a key set in the environment is not copied from the file");
        assertEquals("DEBUG", System.getProperty("nanolog.level.com.nanolaba.logging.util"));
    }

    private static void assertLevels(ConsoleLogger logger, boolean trace, boolean debug, boolean info, boolean warn, boolean error) {
        assertEquals(trace, logger.isTraceEnabled(), "TRACE");
        assertEquals(debug, logger.isDebugEnabled(), "DEBUG");
        assertEquals(info, logger.isInfoEnabled(), "INFO");
        assertEquals(warn, logger.isWarnEnabled(), "WARN");
        assertEquals(error, logger.isErrorEnabled(), "ERROR");
    }

    private static class Nested {
        private static class Deeper {
        }
    }
}
