package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code nanolog.diagnostics}: how often the {@code [NANOLOG]} diagnostics are printed, and where a value came from.
 */
class DiagnosticsTest extends AbstractLoggerTest {

    private static final String SHOW_DATE = "Invalid value \"maybe\" of \"nanolog.console.showDate\": expected true or false";

    private final Map<String, String> environment = new HashMap<>();

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
        DefaultLoggerCreator.forgetDiagnostics();
    }

    @AfterEach
    public void tearDown() {
        DefaultLoggerCreator.environment = System::getenv;
        DefaultLoggerCreator.diagnostics = DefaultLoggerCreator.Diagnostics.ONCE;
        DefaultLoggerCreator.maxRemembered = 10_000;
        for (String key : new String[]{DefaultLoggerCreator.PROPERTY_DIAGNOSTICS, ConsoleLogger.PROPERTY_SHOW_DATE, ConsoleLogger.PROPERTY_SHOW_SOURCE,
                                       "nanolog.colour",
                                       DefaultLoggerCreator.PROPERTY_LOGGER_CONFIG_FILE, DefaultLoggerCreator.PROPERTY_STRICT}) {
            System.clearProperty(key);
        }
        DefaultLoggerCreator.forgetDiagnostics();
        LOG.clearReportedLoggerFailures();
        LOG.init();
        getErrAndClear();
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i != -1; i = text.indexOf(part, i + 1)) {
            count++;
        }
        return count;
    }

    private static void use(String mode) {
        DefaultLoggerCreator.diagnostics = DefaultLoggerCreator.Diagnostics.valueOf(mode);
    }

    // CONFIGURATION

    @Test
    public void testOncePrintsEachDiagnosticOncePerJvm() {
        System.setProperty(ConsoleLogger.PROPERTY_SHOW_DATE, "maybe");
        new ConsoleLogger();
        new ConsoleLogger();
        String err = getErrAndClear();
        assertEquals(1, count(err, SHOW_DATE), err);
        assertTrue(err.contains("[NANOLOG] " + SHOW_DATE + "; set as a system property"), err);
    }

    @Test
    public void testAllPrintsEveryTime() {
        use("ALL");
        System.setProperty(ConsoleLogger.PROPERTY_SHOW_DATE, "maybe");
        new ConsoleLogger();
        new ConsoleLogger();
        assertEquals(2, count(getErrAndClear(), SHOW_DATE));
    }

    @Test
    public void testOffPrintsNothing() {
        use("OFF");
        System.setProperty(ConsoleLogger.PROPERTY_SHOW_DATE, "maybe");
        new ConsoleLogger();
        assertEquals("", getErrAndClear());
        DefaultLoggerCreator.printNotice("a notice is not a diagnostic");
        assertEquals("[NANOLOG] a notice is not a diagnostic" + System.lineSeparator(), getErrAndClear());
    }

    @Test
    public void testTheSettingIsRead() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_DIAGNOSTICS, "ALL");
        LOG.init();
        assertEquals(DefaultLoggerCreator.Diagnostics.ALL, DefaultLoggerCreator.diagnostics);
        System.setProperty(DefaultLoggerCreator.PROPERTY_DIAGNOSTICS, " off ");
        LOG.init();
        assertEquals(DefaultLoggerCreator.Diagnostics.OFF, DefaultLoggerCreator.diagnostics);
        System.clearProperty(DefaultLoggerCreator.PROPERTY_DIAGNOSTICS);
        LOG.init();
        assertEquals(DefaultLoggerCreator.Diagnostics.ONCE, DefaultLoggerCreator.diagnostics, "once by default");
        getErrAndClear();

        System.setProperty(DefaultLoggerCreator.PROPERTY_DIAGNOSTICS, "loud");
        LOG.init();
        LOG.init();
        String err = getErrAndClear();
        assertEquals(1, count(err, "Invalid value \"loud\" of \"nanolog.diagnostics\": expected once, all or off"), err);
        assertEquals(DefaultLoggerCreator.Diagnostics.ONCE, DefaultLoggerCreator.diagnostics);
    }

    @Test
    public void testTheSettingInTheFileAppliesToTheReportOnTheFile() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_LOGGER_CONFIG_FILE, "quiet.properties");
        LOG.init();
        assertEquals("", getErrAndClear(), "the unknown key of the file is not reported: the file says off");
        assertEquals(DefaultLoggerCreator.Diagnostics.OFF, DefaultLoggerCreator.diagnostics);
    }

    @Test
    public void testTheEnvironmentAppliesBeforeTheFile() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_LOGGER_CONFIG_FILE, "missing.properties");
        environment.put("NANOLOG_DIAGNOSTICS", "off");
        LOG.init();
        assertEquals("", getErrAndClear(), "a file that cannot be found is not reported");
    }

    // WHERE A VALUE CAME FROM

    @Test
    public void testTheSourceOfAnInvalidValueIsNamed() {
        environment.put("NANOLOG_CONSOLE_SHOWDATE", "maybe");
        new ConsoleLogger();
        assertTrue(getErrAndClear().contains(SHOW_DATE + "; set by the environment variable NANOLOG_CONSOLE_SHOWDATE"));

        System.setProperty(DefaultLoggerCreator.PROPERTY_LOGGER_CONFIG_FILE, "diagnostics.properties");
        LOG.init();
        String err = getErrAndClear();
        assertTrue(err.contains("Invalid value \"perhaps\" of \"nanolog.console.showSource\": expected true or false; set in ") &&
                   err.contains("diagnostics.properties"), err);

        System.setProperty(ConsoleLogger.PROPERTY_SHOW_SOURCE, "otherwise");
        new ConsoleLogger();
        assertTrue(getErrAndClear().contains("Invalid value \"otherwise\" of \"nanolog.console.showSource\": expected true or false; set as a system property"),
                   "a system property set later is no longer the value of the file");
    }

    @Test
    public void testTheMemoryOfPrintedDiagnosticsIsBounded() {
        DefaultLoggerCreator.maxRemembered = 1;
        DefaultLoggerCreator.printDiagnostic("first");
        DefaultLoggerCreator.printDiagnostic("second");
        DefaultLoggerCreator.printDiagnostic("second");
        DefaultLoggerCreator.printDiagnostic("first");
        String err = getErrAndClear();
        assertEquals(1, count(err, "first"));
        assertEquals(2, count(err, "second"), "past the limit a diagnostic is printed, not remembered");
    }

    // STRICT MODE

    private MemoryLogger strict() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_STRICT, "true");
        LOG.init();
        MemoryLogger memory = new MemoryLogger();
        LOG.init(memory);
        getErrAndClear();
        return memory;
    }

    private static void mismatch() {
        LOG.info(InfoObject.class, "{} and {}", 1);
    }

    @Test
    public void testStrictModeFollowsTheSetting() {
        MemoryLogger memory = strict();
        mismatch();
        mismatch();
        assertEquals(1, count(getErrAndClear(), "2 placeholders, 1 argument"));

        use("ALL");
        mismatch();
        mismatch();
        assertEquals(2, count(getErrAndClear(), "2 placeholders, 1 argument"));

        use("OFF");
        mismatch();
        assertEquals("", getErrAndClear());
        assertEquals(5, memory.getDiagnostics().size(), "the logger gets every finding whatever the setting");
    }

    // LOGGERS

    private static final class Broken implements ILogger {
        @Override
        public void log(LogEntry entry) {
            throw new IllegalStateException("broken");
        }
    }

    @Test
    public void testLoggerFailuresFollowTheSetting() {
        LOG.init(new Broken());
        LOG.info(InfoObject.class, "one");
        LOG.info(InfoObject.class, "two");
        assertEquals(1, count(getErrAndClear(), "[NANOLOG] Logger " + Broken.class.getName() + " threw"));

        use("ALL");
        LOG.info(InfoObject.class, "three");
        LOG.info(InfoObject.class, "four");
        assertEquals(2, count(getErrAndClear(), "[NANOLOG] Logger " + Broken.class.getName() + " threw"));

        use("OFF");
        LOG.info(InfoObject.class, "five");
        String err = getErrAndClear();
        assertFalse(err.contains("[NANOLOG]"), err);
        assertTrue(err.contains("five"), "the entry still goes to System.err");
    }

    @Test
    public void testALoggerWithoutLevelControlFollowsTheSetting() {
        LOG.init(entry -> {
        });
        LOG.setLevel("INFO");
        LOG.setLevel("INFO");
        assertEquals(1, count(getErrAndClear(), "does not implement ILevelControl"));
        use("ALL");
        LOG.setLevel("INFO");
        LOG.setLevel("INFO");
        assertEquals(2, count(getErrAndClear(), "does not implement ILevelControl"));
    }

    @Test
    public void testALoggerThatCannotBeCreated() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_LOGGER_CLASS, "com.nowhere.Missing");
        try {
            LOG.init();
            String err = getErrAndClear();
            assertTrue(err.contains("[NANOLOG] Property \"nanolog.logger\" has been defined, but an object of class \"com.nowhere.Missing\" " +
                                    "cannot be created: java.lang.ClassNotFoundException: com.nowhere.Missing; set as a system property"), err);
            assertTrue(err.contains("\tat "), "with the stack trace");
            DefaultLoggerCreator.forgetDiagnostics();
            System.setProperty(DefaultLoggerCreator.PROPERTY_DIAGNOSTICS, "off");
            LOG.init();
            assertEquals("", getErrAndClear(), "neither the line nor the stack trace");
        } finally {
            System.clearProperty(DefaultLoggerCreator.PROPERTY_LOGGER_CLASS);
        }
    }
}
