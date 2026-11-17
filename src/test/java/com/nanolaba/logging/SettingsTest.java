package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Settings Nanolog does not know are reported when {@code LOG} initialises, with a suggestion, instead of being ignored
 * without a word.
 */
class SettingsTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();
    private static final String FILE = "file:/app/classes/nanolog.properties";

    private final Map<String, String> environment = new HashMap<>();
    private final Supplier<Set<String>> originalNames = DefaultLoggerCreator.environmentNames;

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
        DefaultLoggerCreator.environmentNames = environment::keySet;
        // initialise LOG now: its static initializer would otherwise run the check once more in the first test
        LOG.init();
    }

    @AfterEach
    public void tearDown() {
        DefaultLoggerCreator.environment = System::getenv;
        DefaultLoggerCreator.environmentNames = originalNames;
        for (String key : new String[]{"nanolog.levle", "nanolog.console.showdate", DefaultLoggerCreator.PROPERTY_LOGGER_CONFIG_FILE}) {
            System.clearProperty(key);
        }
        LOG.init();
    }

    private static Map<String, String> map(String... keysAndValues) {
        Map<String, String> map = new TreeMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private static Map<String, String> none() {
        return Collections.emptyMap();
    }

    private static List<String> system(String... keysAndValues) {
        return Settings.findings(map(keysAndValues), none(), none(), FILE);
    }

    private static List<String> environment(String... keysAndValues) {
        return Settings.findings(none(), map(keysAndValues), none(), FILE);
    }

    private static List<String> file(String... keysAndValues) {
        return Settings.findings(none(), none(), map(keysAndValues), FILE);
    }

    private static List<String> list(String... findings) {
        return Arrays.asList(findings);
    }

    // THE KNOWN SETTINGS

    @Test
    public void testEveryPropertyConstantIsKnown() throws IllegalAccessException {
        Set<String> constants = new HashSet<>();
        for (Class<?> type : new Class<?>[]{DefaultLoggerCreator.class, ConsoleLogger.class}) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getName().startsWith("PROPERTY_") && !field.getName().equals("PROPERTY_PREFIX") &&
                    Modifier.isStatic(field.getModifiers())) {
                    constants.add((String) field.get(null));
                }
            }
        }

        assertEquals(constants, new HashSet<>(Settings.KNOWN));
        assertEquals(constants.size(), Settings.KNOWN.size(), "no key twice");
    }

    @Test
    public void testEveryKnownSettingIsInATableOfTheReadme() throws IOException {
        for (String readme : new String[]{"README.md", "README.ru.md"}) {
            String text = new String(Files.readAllBytes(Paths.get(readme)), StandardCharsets.UTF_8);
            for (String key : Settings.KNOWN) {
                assertTrue(text.contains("\n| `" + key + "` |"), key + " is missing from a settings table of " + readme);
            }
            assertTrue(text.contains("\n| `nanolog.level.<"), "the package rules are missing from " + readme);
        }
    }

    @Test
    public void testKnownSettingsAreNotReported() {
        Map<String, String> properties = new HashMap<>();
        Map<String, String> variables = new HashMap<>();
        Map<String, String> file = new HashMap<>();
        for (String key : Settings.KNOWN) {
            properties.put(key, "x");
            variables.put(DefaultLoggerCreator.toEnvironmentName(key), "x");
            file.put(key, "y");
        }
        properties.put("nanolog.level.com.acme", "DEBUG");
        file.put("nanolog.level.com.acme", "INFO");
        properties.put("java.version", "1.8");
        properties.put("nanologger", "x");
        variables.put("PATH", "/bin");
        variables.put("NANOLOGGER", "x");
        variables.put("nanolog_levle", "x");

        assertEquals(list(), Settings.findings(properties, variables, file, FILE));
    }

    // SYSTEM PROPERTIES AND THE FILE

    @Test
    public void testATypoIsReportedWithASuggestion() {
        assertEquals(list("Unknown setting \"nanolog.levle\" (system property); did you mean \"nanolog.level\"?"),
                     system("nanolog.levle", "WARN"));
        assertEquals(list("Unknown setting \"nanolog.levle\" (" + FILE + "); did you mean \"nanolog.level\"?"),
                     file("nanolog.levle", "WARN"));
    }

    @Test
    public void testSuggestions() {
        assertEquals(list("Unknown setting \"nanolog.consol.showDate\" (system property); did you mean \"nanolog.console.showDate\"?",
                          "Unknown setting \"nanolog.console.showdate\" (system property); did you mean \"nanolog.console.showDate\"?",
                          "Unknown setting \"nanolog.dumpxx\" (system property); did you mean \"nanolog.dump\"?",
                          "Unknown setting \"nanolog.exception.maxframes\" (system property); did you mean \"nanolog.exception.maxFrames\"?",
                          "Unknown setting \"nanolog.levels.com.acme\" (system property); did you mean \"nanolog.level.com.acme\"?",
                          "Unknown setting \"nanolog.levelz\" (system property); did you mean \"nanolog.level\"?",
                          "Unknown setting \"nanolog.levl.com.acme\" (system property); did you mean \"nanolog.level.com.acme\"?",
                          "Unknown setting \"nanolog.strickt\" (system property); did you mean \"nanolog.strict\"?"),
                     system("nanolog.console.showdate", "false", "nanolog.consol.showDate", "false", "nanolog.dumpxx", "auto",
                            "nanolog.exception.maxframes", "9", "nanolog.levelz", "INFO", "nanolog.levels.com.acme", "DEBUG",
                            "nanolog.levl.com.acme", "DEBUG", "nanolog.strickt", "true"));
    }

    @Test
    public void testNoSuggestionWhenNothingIsClose() {
        // one edit at most for a name shorter than six characters: "log" is two edits from "dbg"
        assertEquals(list("Unknown setting \"nanolog.console.color\" (system property)",
                          "Unknown setting \"nanolog.dmpxxx\" (system property)",
                          "Unknown setting \"nanolog.foo\" (system property)",
                          "Unknown setting \"nanolog.log\" (system property)"),
                     system("nanolog.console.color", "red", "nanolog.dmpxxx", "auto", "nanolog.foo", "1", "nanolog.log", "1",
                            "nanolog.lvl.com.acme", "DEBUG"));
    }

    @Test
    public void testKeysOfACustomLoggerAreNotReported() {
        assertEquals(list(), system("nanolog.mylogger.url", "http://localhost", "nanolog.mylogger.levle", "x"));
        assertEquals(list(), environment("NANOLOG_MYLOGGER_URL", "http://localhost"));
    }

    @Test
    public void testAKeyCopiedFromTheFileIsReportedOnceWithTheFile() {
        assertEquals(list("Unknown setting \"nanolog.levle\" (" + FILE + "); did you mean \"nanolog.level\"?"),
                     Settings.findings(map("nanolog.levle", "WARN"), none(), map("nanolog.levle", "WARN"), FILE));
        assertEquals(list("Unknown setting \"nanolog.levle\" (system property); did you mean \"nanolog.level\"?",
                          "Unknown setting \"nanolog.levle\" (" + FILE + "); did you mean \"nanolog.level\"?"),
                     Settings.findings(map("nanolog.levle", "ERROR"), none(), map("nanolog.levle", "WARN"), FILE));
    }

    @Test
    public void testUnprefixedKeysOfTheFile() {
        assertEquals(list("\"level\" in " + FILE + " is ignored: keys must start with \"nanolog.\" (did you mean \"nanolog.level\"?)",
                          "\"level.com.acme\" in " + FILE + " is ignored: keys must start with \"nanolog.\" (did you mean \"nanolog.level.com.acme\"?)",
                          "\"levle\" in " + FILE + " is ignored: keys must start with \"nanolog.\" (did you mean \"nanolog.level\"?)",
                          "\"other.property\" in " + FILE + " is ignored: keys must start with \"nanolog.\""),
                     file("level", "INFO", "level.com.acme", "DEBUG", "levle", "INFO", "other.property", "x"));
    }

    // ENVIRONMENT VARIABLES

    @Test
    public void testEnvironmentVariables() {
        assertEquals(list("Unknown environment variable NANOLOG_CONSOLE_COLOR",
                          "Unknown environment variable NANOLOG_CONSOLE_SHOWDATES; did you mean NANOLOG_CONSOLE_SHOWDATE?",
                          "Unknown environment variable NANOLOG_HOME",
                          "Unknown environment variable NANOLOG_LEVEL_COM_ACME; per-package levels go into NANOLOG_LEVELS=com.acme=DEBUG",
                          "Unknown environment variable NANOLOG_LEVLE; did you mean NANOLOG_LEVEL?"),
                     environment("NANOLOG_CONSOLE_COLOR", "red", "NANOLOG_CONSOLE_SHOWDATES", "false", "NANOLOG_HOME", "/opt",
                                 "NANOLOG_LEVEL_COM_ACME", "DEBUG", "NANOLOG_LEVLE", "WARN"));
    }

    // ORDER

    @Test
    public void testSystemPropertiesComeFirstThenTheEnvironmentThenTheFile() {
        assertEquals(list("Unknown setting \"nanolog.a\" (system property)",
                          "Unknown setting \"nanolog.b\" (system property)",
                          "Unknown environment variable NANOLOG_A",
                          "Unknown environment variable NANOLOG_B",
                          "\"a\" in " + FILE + " is ignored: keys must start with \"nanolog.\"",
                          "\"b\" in " + FILE + " is ignored: keys must start with \"nanolog.\""),
                     Settings.findings(map("nanolog.b", "1", "nanolog.a", "1"), map("NANOLOG_B", "1", "NANOLOG_A", "1"),
                                       map("b", "1", "a", "1"), FILE));
    }

    // EDIT DISTANCE

    @Test
    public void testDistance() {
        assertEquals(0, Settings.distance("", ""));
        assertEquals(3, Settings.distance("", "abc"));
        assertEquals(3, Settings.distance("abc", ""));
        assertEquals(0, Settings.distance("level", "level"));
        assertEquals(0, Settings.distance("aa", "aa"));
        assertEquals(1, Settings.distance("levle", "level"), "a transposition is one edit");
        assertEquals(1, Settings.distance("ab", "ba"));
        assertEquals(1, Settings.distance("lvel", "level"));
        assertEquals(1, Settings.distance("leveel", "level"));
        assertEquals(1, Settings.distance("lebel", "level"));
        assertEquals(3, Settings.distance("abc", "xyz"));
        assertEquals(3, Settings.distance("ca", "abc"), "a transposed pair is not edited again");
        assertEquals(2, Settings.distance("abcd", "badc"));
    }

    // WHEN LOG INITIALISES

    @Test
    public void testTheNamesOfTheEnvironmentAreTheRealOnes() {
        assertEquals(System.getenv().keySet(), originalNames.get());
    }

    @Test
    public void testInitReportsUnknownSettings() {
        System.setProperty("nanolog.levle", "WARN");
        environment.put("NANOLOG_LEVEL_COM_ACME", "DEBUG");
        getErrAndClear();

        LOG.init();

        assertEquals("[NANOLOG] Unknown setting \"nanolog.levle\" (system property); did you mean \"nanolog.level\"?" + NL +
                     "[NANOLOG] Unknown environment variable NANOLOG_LEVEL_COM_ACME; per-package levels go into NANOLOG_LEVELS=com.acme=DEBUG" + NL,
                     getErrAndClear());
    }

    @Test
    public void testInitReportsTheKeysOfTheFile() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_LOGGER_CONFIG_FILE, "unknown.properties");
        String location = getClass().getClassLoader().getResource("unknown.properties").toString();
        getErrAndClear();

        LOG.init();

        String expected = "[NANOLOG] \"level\" in " + location + " is ignored: keys must start with \"nanolog.\" (did you mean \"nanolog.level\"?)" + NL +
                          "[NANOLOG] Unknown setting \"nanolog.console.showdate\" (" + location + "); did you mean \"nanolog.console.showDate\"?" + NL;
        assertEquals(expected, getErrAndClear());

        LOG.init();
        assertEquals(expected, getErrAndClear(), "the key copied into system properties by the first init is not reported as a system property");
    }

    @Test
    public void testInitWithoutAFileReportsTheRest() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_LOGGER_CONFIG_FILE, "missing.properties");
        System.setProperty("nanolog.levle", "WARN");
        getErrAndClear();

        LOG.init();

        assertEquals("[NANOLOG] Property \"nanolog.config\" has been defined, but the file \"missing.properties\" cannot be found " + NL +
                     "[NANOLOG] Unknown setting \"nanolog.levle\" (system property); did you mean \"nanolog.level\"?" + NL,
                     getErrAndClear());
    }

    @Test
    public void testAFailingCheckDoesNotFailTheInit() {
        DefaultLoggerCreator.environmentNames = () -> {
            throw new SecurityException("getenv.*");
        };
        getErrAndClear();

        LOG.init();

        assertEquals("", getErrAndClear());
        assertEquals(ConsoleLogger.class, LOG.getLogger().getClass());
    }
}
