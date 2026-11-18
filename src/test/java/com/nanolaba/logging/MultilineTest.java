package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code nanolog.console.multiline}: what {@link ConsoleLogger} does with line breaks and control characters, so an
 * entry cannot pass for several and user data cannot forge an entry or reach the terminal as an escape sequence.
 */
class MultilineTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();

    private final Map<String, String> environment = new HashMap<>();

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
    }

    @AfterEach
    public void tearDown() {
        DefaultLoggerCreator.environment = System::getenv;
        System.clearProperty(ConsoleLogger.PROPERTY_MULTILINE);
        LOG.init();
    }

    private static ConsoleLogger console(String multiline) {
        return new ConsoleLogger().setShowDate(false).setShowSource(false).setMultiline(multiline);
    }

    private static String line(ConsoleLogger logger, String message) {
        return logger.createLogString(new LogEntry(LogEntry.LogEntryLevel.INFO, MultilineTest.class, null, message));
    }

    private static Throwable trace(String message) {
        IllegalStateException cause = new IllegalStateException("cause");
        cause.setStackTrace(new StackTraceElement[]{new StackTraceElement("com.acme.Db", "query", "Db.java", 7)});
        IllegalStateException error = new IllegalStateException(message, cause);
        error.setStackTrace(new StackTraceElement[]{new StackTraceElement("com.acme.Shop", "pay", "Shop.java", 3)});
        return error;
    }

    @Test
    public void testKeepIsTheDefaultAndChangesNothing() {
        ConsoleLogger logger = new ConsoleLogger().setShowDate(false).setShowSource(false);

        assertEquals("keep", logger.getMultiline());
        assertEquals("INFO a\nb\u001b[31m", line(logger, "a\nb\u001b[31m"));
    }

    @Test
    public void testIndentStartsEveryFurtherLineWithATab() {
        ConsoleLogger logger = console("indent");

        assertEquals("INFO a" + NL + "\tb" + NL + "\tc" + NL + "\td", line(logger, "a\nb\r\nc\rd"));
        assertEquals("INFO a" + NL + "\tERROR forged", line(logger, "a\nERROR forged"));
        assertEquals("INFO a" + NL + "\talready indented", line(logger, "a\n\talready indented"));
        assertEquals("INFO a" + NL + "\t" + NL + "\tb", line(logger, "a\n\nb"), "an empty line is indented too");
        assertEquals("INFO trailing", line(logger, "trailing\r\n"));
    }

    @Test
    public void testEscapeKeepsAnEntryOnOnePhysicalLine() {
        ConsoleLogger logger = console("escape");

        assertEquals("INFO a\\nb\\r\\nc\\rd", line(logger, "a\nb\r\nc\rd"));
        assertEquals("INFO trailing", line(logger, "trailing\n\n"));
        assertEquals("INFO ", line(logger, "\n\n\n"), "a message of line breaks only");
        ConsoleLogger bare = new ConsoleLogger().setShowLevel(false).setShowDate(false).setShowSource(false);
        assertEquals("", line(bare.setMultiline("escape"), "\r\n"), "an entry of line breaks only");
        assertEquals("", line(bare.setMultiline("indent"), "\n"));
    }

    @Test
    public void testControlCharactersAreEscapedInBothModes() {
        String hostile = "\u001b[31mred\u0007 \u007f \u009b2J \u2028 \u2029 \u0000 tab\tkept";
        String escaped = "\\u001b[31mred\\u0007 \\u007f \\u009b2J \\u2028 \\u2029 \\u0000 tab\tkept";

        assertEquals("INFO " + escaped, line(console("indent"), hostile));
        assertEquals("INFO " + escaped, line(console("escape"), hostile));
        assertEquals("INFO \u00e9\u4e2d\u00a0text", line(console("escape"), "\u00e9\u4e2d\u00a0text"), "printable text stays");
    }

    @Test
    public void testTheStackTraceFollowsTheMode() {
        LogEntry entry = new LogEntry(LogEntry.LogEntryLevel.ERROR, MultilineTest.class, trace("bad\ninput"), "Failed");

        assertEquals("ERROR Failed java.lang.IllegalStateException: bad" + NL +
                     "\tinput" + NL +
                     "\tat com.acme.Shop.pay(Shop.java:3)" + NL +
                     "\tCaused by: java.lang.IllegalStateException: cause" + NL +
                     "\tat com.acme.Db.query(Db.java:7)", console("indent").createLogString(entry));
        String escape = console("escape").createLogString(entry);
        assertFalse(escape.contains("\n") || escape.contains("\r"), escape);
        String nl = NL.replace("\r", "\\r").replace("\n", "\\n");
        assertEquals("ERROR Failed java.lang.IllegalStateException: bad\\ninput" + nl + "\tat com.acme.Shop.pay(Shop.java:3)" +
                     nl + "Caused by: java.lang.IllegalStateException: cause" + nl + "\tat com.acme.Db.query(Db.java:7)",
                     escape);
    }

    @Test
    public void testAMessageWhoseToStringFailsStaysReadable() {
        Object broken = new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("no text");
            }
        };

        LogEntry entry = new LogEntry(LogEntry.LogEntryLevel.INFO, MultilineTest.class, null, broken);
        assertEquals("INFO [FAILED toString()]", console("indent").createLogString(entry));
    }

    @Test
    public void testTheSettingAndTheSetter() {
        System.setProperty(ConsoleLogger.PROPERTY_MULTILINE, " Escape ");
        assertEquals("escape", new ConsoleLogger().getMultiline());
        assertEquals("", getErrAndClear());

        System.setProperty(ConsoleLogger.PROPERTY_MULTILINE, "wrap");
        assertEquals("keep", new ConsoleLogger().getMultiline());
        assertEquals("[NANOLOG] Invalid value \"wrap\" of \"nanolog.console.multiline\": expected keep, indent or escape; set as a system property" + NL,
                     getErrAndClear());

        ConsoleLogger logger = new ConsoleLogger();
        getErrAndClear();
        assertSame(logger, logger.setMultiline(" INDENT "));
        assertEquals("indent", logger.getMultiline());
        logger.setMultiline("wrap");
        logger.setMultiline(null);
        assertEquals("indent", logger.getMultiline(), "an invalid value changes nothing");
        assertEquals("[NANOLOG] Invalid value \"wrap\" in setMultiline: expected keep, indent or escape; nothing changed" + NL +
                     "[NANOLOG] Invalid value null in setMultiline: expected keep, indent or escape; nothing changed" + NL,
                     getErrAndClear());
    }

    @Test
    public void testJsonLinesAreNotTouched() {
        JsonLogger json = new JsonLogger();
        json.setMultiline("indent");
        LogEntry entry = new LogEntry(LogEntry.LogEntryLevel.INFO, MultilineTest.class, null, "a\nb");

        assertTrue(json.createLogString(entry).contains("\"message\":\"a\\nb\""), json.createLogString(entry));
    }

    @Test
    public void testAgentModeWritesOneLinePerEntryUnlessSet() {
        ConsoleLogger console = new ConsoleLogger();
        console.useAgentDefaults();
        assertEquals("escape", console.getMultiline());

        System.setProperty(ConsoleLogger.PROPERTY_MULTILINE, "keep");
        console = new ConsoleLogger();
        console.useAgentDefaults();
        assertEquals("keep", console.getMultiline());

        System.setProperty(ConsoleLogger.PROPERTY_MULTILINE, "wrap");
        console = new ConsoleLogger();
        getErrAndClear();
        console.useAgentDefaults();
        assertEquals("escape", console.getMultiline());
        assertEquals("", getErrAndClear(), "reported by the constructor already");
    }

    @Test
    public void testTheConsolePrintsTheLine() {
        LOG.init(console("escape"));

        LOG.info("first\nsecond");

        assertEquals("INFO first\\nsecond" + NL, getOutAndClear());
    }
}
