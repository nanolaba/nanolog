package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.Console;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code nanolog.console.color}: coloured level words on a terminal, never in a pipe, a file or a JSON line.
 */
class ColorTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();
    private static final String ESC = "\u001b";

    private final Map<String, String> environment = new HashMap<>();
    private final Map<String, String> properties = new HashMap<>();

    /**
     * Stands for {@code java.io.Console}: its {@code isTerminal()} answers what the test says.
     */
    public static final class FakeConsole {

        private final Object answer;

        FakeConsole(Object answer) {
            this.answer = answer;
        }

        public Object isTerminal() {
            if (answer instanceof RuntimeException) {
                throw (RuntimeException) answer;
            }
            return answer;
        }
    }

    private static final Method IS_TERMINAL = Colors.method(FakeConsole.class, "isTerminal");

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
        Colors.properties = properties::get;
        properties.put("os.name", "Linux");
        terminal(true);
    }

    @AfterEach
    public void tearDown() {
        DefaultLoggerCreator.environment = System::getenv;
        Colors.console = System::console;
        Colors.properties = System::getProperty;
        Colors.isTerminal = Colors.method(Console.class, "isTerminal");
        System.clearProperty(ConsoleLogger.PROPERTY_COLOR);
        LOG.init();
    }

    /**
     * A console of JDK 8 to 21, where {@code System.console()} returns a console only for a terminal.
     */
    private static void terminal(boolean terminal) {
        Object console = terminal ? new Object() : null;
        Colors.console = () -> console;
        Colors.isTerminal = null;
    }

    private static ConsoleLogger console(String color) {
        return new ConsoleLogger().setShowDate(false).setShowSource(false).setColor(color);
    }

    private static LogEntry dbg() {
        return LogEntry.createDbg(ColorTest.class, null, "x = 1", 0, LogEntry.LOG_BOUNDARY);
    }

    private static String red(String word) {
        return ESC + "[31m" + word + ESC + "[0m";
    }

    @Test
    public void testAutoIsTheDefaultAndColoursOnlyTheLevelOnATerminal() {
        ConsoleLogger logger = new ConsoleLogger().setShowDate(false).setShowSource(false);
        assertEquals("auto", logger.getColor());

        LOG.init(logger);
        LOG.error("Failed");
        LOG.warn("Slow");
        LOG.info("Started");
        LOG.debug("Query");
        LOG.trace("Row");
        logger.log(dbg());

        assertEquals(red("ERROR") + " Failed" + NL, getErrAndClear());
        assertEquals(ESC + "[33mWARN" + ESC + "[0m Slow" + NL +
                     ESC + "[32mINFO" + ESC + "[0m Started" + NL +
                     ESC + "[2mDEBUG" + ESC + "[0m Query" + NL +
                     ESC + "[2mTRACE" + ESC + "[0m Row" + NL +
                     ESC + "[35mDBG" + ESC + "[0m x = 1" + NL, getOutAndClear());
    }

    @Test
    public void testAPipeGetsNoEscapeCodesInAutoMode() {
        terminal(false);
        LOG.init(new ConsoleLogger());

        LOG.error("Failed");
        LOG.info("Started");
        new ConsoleLogger().log(dbg());

        assertFalse(getErrAndClear().contains(ESC));
        assertFalse(getOutAndClear().contains(ESC));
    }

    @Test
    public void testEveryCombinationOfTheSettingAndTheEnvironment() {
        String[] modes = {"auto", "always", "never"};
        String[] noColors = {null, "", "1"};
        String[] forceColors = {null, "", "1", "3", "0", "false", " FALSE "};
        String[] terms = {null, "dumb", "xterm-256color"};
        int checked = 0;
        for (String mode : modes) {
            for (String noColor : noColors) {
                for (String forceColor : forceColors) {
                    for (String term : terms) {
                        for (boolean terminal : new boolean[]{true, false}) {
                            environment.clear();
                            put("NO_COLOR", noColor);
                            put("FORCE_COLOR", forceColor);
                            put("TERM", term);
                            terminal(terminal);

                            // the rules as the README states them
                            boolean expected;
                            if (!mode.equals("auto")) {
                                expected = mode.equals("always");
                            } else if (noColor != null && !noColor.isEmpty()) {
                                expected = false;
                            } else if (forceColor != null && !forceColor.isEmpty()) {
                                expected = !forceColor.trim().equals("0") && !forceColor.trim().equalsIgnoreCase("false");
                            } else {
                                expected = terminal && !"dumb".equals(term);
                            }

                            String line = console(mode).createLogString(new LogEntry(LogEntry.LogEntryLevel.WARN, ColorTest.class, null, "x"));
                            LOG.init(console(mode));
                            LOG.warn("x");
                            String printed = getOutAndClear();
                            String state = mode + ", NO_COLOR=" + noColor + ", FORCE_COLOR=" + forceColor + ", TERM=" + term +
                                           ", terminal=" + terminal;
                            assertEquals(expected, printed.contains(ESC), state);
                            assertEquals("WARN x", line, "the text of an entry has no colours: " + state);
                            checked++;
                        }
                    }
                }
            }
        }
        assertEquals(378, checked);
    }

    private void put(String name, String value) {
        if (value != null) {
            environment.put(name, value);
        }
    }

    @Test
    public void testTheTerminalProbe() {
        Colors.console = () -> null;
        assertFalse(Colors.isTerminal(), "no console: output redirected on JDK 8 to 21");

        Colors.console = Object::new;
        assertTrue(Colors.isTerminal(), "a console without isTerminal(): JDK 8 to 21");

        properties.put("os.name", "Windows 10");
        assertFalse(Colors.isTerminal(), "conhost shows escape codes as text unless the program enables them");
        properties.put("os.name", "Mac OS X");
        assertTrue(Colors.isTerminal());
        properties.remove("os.name");
        assertTrue(Colors.isTerminal(), "an unknown system");

        Colors.isTerminal = IS_TERMINAL;
        Colors.console = () -> new FakeConsole(true);
        assertTrue(Colors.isTerminal(), "JDK 22+: a console of a terminal");
        Colors.console = () -> new FakeConsole(false);
        assertFalse(Colors.isTerminal(), "JDK 22+: a console of redirected output");
        Colors.console = () -> new FakeConsole(null);
        assertFalse(Colors.isTerminal());
        Colors.console = () -> new FakeConsole(new IllegalStateException("no tty"));
        assertFalse(Colors.isTerminal(), "a probe that throws");
        Colors.console = Object::new;
        assertFalse(Colors.isTerminal(), "a console the method does not belong to");
    }

    @Test
    public void testTheProbeOfTheJvm() throws Exception {
        assertNull(Colors.method(Console.class, "noSuchMethod"));
        assertEquals(String.class.getMethod("isEmpty"), Colors.method(String.class, "isEmpty"));
        tearDown();
        assertSame(System.console(), Colors.console.get());
        assertEquals(System.getProperty("os.name"), Colors.properties.apply("os.name"));
        assertEquals(Colors.method(Console.class, "isTerminal"), Colors.isTerminal);
        assertFalse(Colors.isTerminal(), "the tests run with redirected output");
    }

    @Test
    public void testEscapeModeKeepsTheColourAndEscapesTheMessage() {
        LOG.init(console("always").setMultiline("escape"));

        LOG.error("a\nb" + ESC + "[2J");

        assertEquals(red("ERROR") + " a\\nb\\u001b[2J" + NL, getErrAndClear());
    }

    @Test
    public void testNothingIsColouredWithoutALevelWord() {
        LOG.init(console("always").setShowLevel(false));
        LOG.error("Failed");
        assertEquals("Failed" + NL, getErrAndClear());

        console("always").log(new LogEntry(null, ColorTest.class, null, "no level"));
        assertEquals("no level" + NL, getOutAndClear());
    }

    @Test
    public void testALevelWordTheTextDoesNotStartWithIsNotColoured() {
        ConsoleLogger bell = new ConsoleLogger() {
            @Override
            protected void writeLevel(StringWriter out, LogEntry entry) {
                out.append("\u0007").append(entry.getLevel().name());
            }
        }.setShowDate(false).setShowSource(false).setMultiline("escape").setColor("always");

        bell.log(new LogEntry(LogEntry.LogEntryLevel.INFO, ColorTest.class, null, "x"));

        assertEquals("\\u0007INFO x" + NL, getOutAndClear());
    }

    @Test
    public void testJsonLinesAreNeverColoured() {
        JsonLogger json = new JsonLogger();
        json.setColor("always");

        json.log(new LogEntry(LogEntry.LogEntryLevel.ERROR, ColorTest.class, null, "x"));

        String line = getErrAndClear();
        assertTrue(line.startsWith("{"), line);
        assertFalse(line.contains(ESC), line);
    }

    @Test
    public void testAssertionMessagesOfMemoryLoggerAreNeverColoured() {
        System.setProperty(ConsoleLogger.PROPERTY_COLOR, "always");
        MemoryLogger memory = new MemoryLogger();
        LOG.init(memory);
        LOG.error("Failed");

        AssertionError error = null;
        try {
            memory.assertLogged(LogEntry.LogEntryLevel.WARN, "Slow");
        } catch (AssertionError e) {
            error = e;
        }

        assertTrue(error != null && error.getMessage().contains("ERROR"), String.valueOf(error));
        assertFalse(error.getMessage().contains(ESC), error.getMessage());
    }

    @Test
    public void testTheSettingAndTheSetter() {
        System.setProperty(ConsoleLogger.PROPERTY_COLOR, " Never ");
        assertEquals("never", new ConsoleLogger().getColor());
        assertEquals("", getErrAndClear());

        environment.put("NANOLOG_CONSOLE_COLOR", "always");
        System.clearProperty(ConsoleLogger.PROPERTY_COLOR);
        assertEquals("always", new ConsoleLogger().getColor());

        System.setProperty(ConsoleLogger.PROPERTY_COLOR, "rainbow");
        assertEquals("auto", new ConsoleLogger().getColor());
        assertEquals("[NANOLOG] Invalid value \"rainbow\" of \"nanolog.console.color\": expected auto, always or never; set as a system property" + NL,
                     getErrAndClear());

        ConsoleLogger logger = new ConsoleLogger().setShowDate(false).setShowSource(false);
        getErrAndClear();
        assertSame(logger, logger.setColor(" ALWAYS "));
        assertEquals("always", logger.getColor());
        logger.setColor("rainbow");
        logger.setColor(null);
        assertEquals("always", logger.getColor(), "an invalid value changes nothing");
        assertEquals("[NANOLOG] Invalid value \"rainbow\" in setColor: expected auto, always or never; nothing changed" + NL +
                     "[NANOLOG] Invalid value null in setColor: expected auto, always or never; nothing changed" + NL,
                     getErrAndClear());

        logger.log(new LogEntry(LogEntry.LogEntryLevel.INFO, ColorTest.class, null, "x"));
        assertTrue(getOutAndClear().contains(ESC));
        logger.setColor("never");
        logger.log(new LogEntry(LogEntry.LogEntryLevel.INFO, ColorTest.class, null, "x"));
        assertEquals("INFO x" + NL, getOutAndClear(), "the setter decides again");
        terminal(false);
        logger.setColor("auto");
        logger.log(new LogEntry(LogEntry.LogEntryLevel.INFO, ColorTest.class, null, "x"));
        assertEquals("INFO x" + NL, getOutAndClear());
    }

    @Test
    public void testAgentModeTurnsColoursOffUnlessSet() {
        ConsoleLogger console = new ConsoleLogger().setShowDate(false).setShowSource(false);
        console.useAgentDefaults();
        assertEquals("never", console.getColor());
        console.log(new LogEntry(LogEntry.LogEntryLevel.ERROR, ColorTest.class, null, "x"));
        assertEquals("ERROR x" + NL, getErrAndClear(), "a coding agent may read through a terminal too");

        System.setProperty(ConsoleLogger.PROPERTY_COLOR, "auto");
        console = new ConsoleLogger().setShowDate(false).setShowSource(false);
        console.useAgentDefaults();
        assertEquals("auto", console.getColor());
        console.log(new LogEntry(LogEntry.LogEntryLevel.ERROR, ColorTest.class, null, "x"));
        assertEquals(red("ERROR") + " x" + NL, getErrAndClear());

        System.setProperty(ConsoleLogger.PROPERTY_COLOR, "rainbow");
        console = new ConsoleLogger();
        getErrAndClear();
        console.useAgentDefaults();
        assertEquals("never", console.getColor());
        assertEquals("", getErrAndClear(), "reported by the constructor already");
    }
}
