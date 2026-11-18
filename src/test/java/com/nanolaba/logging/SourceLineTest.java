package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code nanolog.console.showSourceLine}: the source line of the call under each entry.
 */
class SourceLineTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();

    @BeforeEach
    public void setUp() {
        DbgSource.clearCache();
    }

    @AfterEach
    public void tearDown() {
        System.clearProperty(ConsoleLogger.PROPERTY_SHOW_SOURCE_LINE);
        System.clearProperty(DefaultLoggerCreator.PROPERTY_SOURCES);
        DbgSource.clearCache();
        LOG.init();
    }

    private static ConsoleLogger console() {
        return new ConsoleLogger().setShowDate(false).setShowSourceLine(true).setStream("out");
    }

    private static StackTraceElement here() {
        return new Throwable().getStackTrace()[1];
    }

    // ENTRIES

    @Test
    public void testTheLineOfTheCallIsPrintedUnderTheEntry() {
        LOG.init(console());
        LOG.warn("Charge of {} failed, retrying", 42);
        assertEquals("WARN [SourceLineTest] Charge of 42 failed, retrying" + NL +
                     "    > LOG.warn(\"Charge of {} failed, retrying\", 42);" + NL, getOutAndClear());
    }

    @Test
    public void testOffByDefault() {
        ConsoleLogger console = new ConsoleLogger().setShowDate(false);
        assertFalse(console.isShowSourceLine());
        assertSame(console, console.setShowSourceLine(true));
        assertTrue(console.isShowSourceLine());
        LOG.init(new ConsoleLogger().setShowDate(false));
        LOG.info("plain");
        assertEquals("INFO [SourceLineTest] plain" + NL, getOutAndClear());

        System.setProperty(ConsoleLogger.PROPERTY_SHOW_SOURCE_LINE, "true");
        assertTrue(new ConsoleLogger().isShowSourceLine());
    }

    @Test
    public void testNothingIsPrintedWithoutASourceLine() {
        ConsoleLogger console = console();
        assertEquals("INFO [InfoObject] explicit", console.createLogString(new LogEntry(INFO, InfoObject.class, null, "explicit")),
                     "an explicit class has no call site");
        StackTraceElement gone = new StackTraceElement("com.nowhere.Gone", "run", "Gone.java", 3);
        assertEquals("INFO [InfoObject] gone", console.createLogString(LogEntry.create(INFO, InfoObject.class, gone, null, "gone", new Object[0])));
        StackTraceElement beyond = new StackTraceElement(SourceLineTest.class.getName(), "x", "SourceLineTest.java", 100000);
        assertEquals("INFO [SourceLineTest] beyond", console.createLogString(LogEntry.create(INFO, SourceLineTest.class, beyond, null, "beyond", new Object[0])));
        StackTraceElement blank = new StackTraceElement(SourceLineTest.class.getName(), "x", "SourceLineTest.java", 2);
        assertEquals("INFO [SourceLineTest] blank", console.createLogString(LogEntry.create(INFO, SourceLineTest.class, blank, null, "blank", new Object[0])),
                     "line 2 of this file is empty");
        LogEntry dbg = LogEntry.createDbg(SourceLineTest.class, here(), "x = 1", 0, LogEntry.LOG_BOUNDARY);
        assertFalse(console.createLogString(dbg).contains("    > "), "LOG.dbg prints its expression already");
    }

    @Test
    public void testAMessageEndingWithALineBreakGetsNoSpaceBeforeTheException() {
        ConsoleLogger console = new ConsoleLogger().setShowDate(false).setShowLevel(false).setShowSource(false);
        String text = console.createLogString(new LogEntry(INFO, null, new IllegalStateException("boom"), "Failed:\n"));
        assertTrue(text.startsWith("Failed:\njava.lang.IllegalStateException: boom"), text);
    }

    @Test
    public void testEscapeModeKeepsOneLine() {
        LOG.init(console().setMultiline("escape"));
        LOG.info("one line");
        String escaped = NL.replace("\r", "\\r").replace("\n", "\\n");
        assertEquals("INFO [SourceLineTest] one line" + escaped + "    > LOG.info(\"one line\");" + NL, getOutAndClear());
    }

    // EXCEPTIONS

    private static IllegalStateException fail() {
        return new IllegalStateException("boom");
    }

    @Test
    public void testTheTopApplicationFramesOfAnExceptionShowTheirLines() {
        IllegalStateException boom = fail();
        String text = console().createLogString(LogEntry.create(INFO, SourceLineTest.class, here(), boom, "failed", new Object[0]));
        String[] lines = text.split("\r?\n");
        assertEquals("INFO [SourceLineTest] failed", lines[0]);
        assertEquals("    > String text = console().createLogString(LogEntry.create(INFO, SourceLineTest.class, here(), boom, \"failed\", new Object[0]));",
                     lines[1]);
        assertEquals("java.lang.IllegalStateException: boom", lines[2], "the exception starts on a line of its own");
        assertTrue(lines[3].startsWith("\tat com.nanolaba.logging.SourceLineTest.fail(SourceLineTest.java:"), lines[3]);
        assertEquals("\t    > return new IllegalStateException(\"boom\");", lines[4]);
        assertTrue(lines[5].startsWith("\tat com.nanolaba.logging.SourceLineTest.testTheTopApplicationFramesOfAnExceptionShowTheirLines"), lines[5]);
        assertEquals("\t    > IllegalStateException boom = fail();", lines[6]);
        assertFalse(lines[7].contains("    > "), "JDK and runner frames have no source here");
        assertEquals(3, sources(text).length, "the call site and two frames: JDK frames get no line, not even an empty one");
    }

    @Test
    public void testAtMostThreeFramesAndTheCausesToo() {
        StackTraceElement[] frames = {frame1(), frame2(), frame3(), frame4()};
        IllegalStateException cause = new IllegalStateException("cause");
        cause.setStackTrace(new StackTraceElement[]{frame5()});
        IllegalStateException top = new IllegalStateException("top", cause);
        top.setStackTrace(frames);
        String text = new ConsoleLogger().setShowSourceLine(true).stackTraceOf(top);
        assertEquals(Arrays.asList("> return here(); // 1", "> return here(); // 2", "> return here(); // 3"),
                     Arrays.asList(sources(text)), "the first three frames with a source, the cause included in the count");

        IllegalStateException shortTop = new IllegalStateException("short", cause);
        shortTop.setStackTrace(new StackTraceElement[]{frame1()});
        assertEquals(Arrays.asList("> return here(); // 1", "> return here(); // 5"), Arrays.asList(sources(new ConsoleLogger().setShowSourceLine(true)
                                                                                                                      .stackTraceOf(shortTop))));
        IllegalStateException twice = new IllegalStateException("twice");
        twice.setStackTrace(new StackTraceElement[]{frame1(), frame1()});
        assertEquals(1, sources(new ConsoleLogger().setShowSourceLine(true).stackTraceOf(twice)).length, "a frame is annotated once");

        assertEquals(0, sources(new ConsoleLogger().stackTraceOf(top)).length, "off: the trace is as printed");
        assertEquals(1, sources(new ConsoleLogger().setShowSourceLine(true).setExceptionMaxFrames(1).stackTraceOf(new IllegalStateException())).length,
                     "a shortened trace is annotated too");
    }

    @Test
    public void testATraceThatCannotBeReadStaysAsPrinted() {
        IllegalStateException broken = new IllegalStateException("broken frames") {
            @Override
            public StackTraceElement[] getStackTrace() {
                throw new UnsupportedOperationException();
            }
        };
        String text = new ConsoleLogger().setShowSourceLine(true).stackTraceOf(broken);
        assertEquals(ConsoleLogger.printedStackTrace(broken), text);
    }

    @Test
    public void testACycleOfCausesEnds() {
        IllegalStateException a = new IllegalStateException("a");
        IllegalStateException b = new IllegalStateException("b", a);
        a.initCause(b);
        a.setStackTrace(new StackTraceElement[0]);
        b.setStackTrace(new StackTraceElement[0]);
        String text = new ConsoleLogger().setShowSourceLine(true).stackTraceOf(a);
        assertTrue(text.contains("CIRCULAR REFERENCE"), text);
    }

    private static String[] sources(String trace) {
        return Arrays.stream(trace.split("\r?\n")).filter(line -> line.trim().startsWith(">")).map(String::trim).toArray(String[]::new);
    }

    private static StackTraceElement frame1() {
        return here(); // 1
    }

    private static StackTraceElement frame2() {
        return here(); // 2
    }

    private static StackTraceElement frame3() {
        return here(); // 3
    }

    private static StackTraceElement frame4() {
        return here(); // 4
    }

    private static StackTraceElement frame5() {
        return here(); // 5
    }

    // SOURCES THAT DO NOT MATCH

    @Test
    public void testAStaleOrLongSourceLine(@org.junit.jupiter.api.io.TempDir Path sources) throws IOException {
        Path file = sources.resolve("com/nanolaba/logging/SourceLineTest.java");
        Files.createDirectories(file.getParent());
        char[] longLine = new char[300];
        Arrays.fill(longLine, 'x');
        char[] exactLine = new char[DbgSource.MAX_LINE];
        Arrays.fill(exactLine, 'y');
        Files.write(file, Arrays.asList("line one", new String(longLine), "   indented line   ", new String(exactLine)), StandardCharsets.UTF_8);
        System.setProperty(DefaultLoggerCreator.PROPERTY_SOURCES, sources.toString());
        StackTraceElement third = new StackTraceElement(SourceLineTest.class.getName(), "x", "SourceLineTest.java", 3);
        StackTraceElement second = new StackTraceElement(SourceLineTest.class.getName(), "x", "SourceLineTest.java", 2);

        assertNull(DbgSource.lineText(third, SourceLineTest.class), "a source newer than its class is stale");

        DbgSource.clearCache();
        Files.setLastModifiedTime(file, FileTime.fromMillis(0));
        assertEquals("indented line", DbgSource.lineText(third, SourceLineTest.class));
        char[] cut = new char[DbgSource.MAX_LINE];
        Arrays.fill(cut, 'x');
        assertEquals(new String(cut) + "...", DbgSource.lineText(second, SourceLineTest.class));
        StackTraceElement first = new StackTraceElement(SourceLineTest.class.getName(), "x", "SourceLineTest.java", 1);
        assertEquals("line one", DbgSource.lineText(first, SourceLineTest.class));
        StackTraceElement fourth = new StackTraceElement(SourceLineTest.class.getName(), "x", "SourceLineTest.java", 4);
        assertEquals(new String(exactLine), DbgSource.lineText(fourth, SourceLineTest.class), "exactly the limit is not cut");
        StackTraceElement fifth = new StackTraceElement(SourceLineTest.class.getName(), "x", "SourceLineTest.java", 5);
        assertNull(DbgSource.lineText(fifth, SourceLineTest.class), "past the last line");
        StackTraceElement zero = new StackTraceElement(SourceLineTest.class.getName(), "x", "SourceLineTest.java", 0);
        assertNull(DbgSource.lineText(zero, SourceLineTest.class));

        System.setProperty(DefaultLoggerCreator.PROPERTY_SOURCES, "");
        DbgSource.clearCache();
        assertNull(DbgSource.lineText(third, SourceLineTest.class), "an empty nanolog.sources turns the reading off");
    }

    @Test
    public void testAFrameCannotLeadOutOfTheSources(@org.junit.jupiter.api.io.TempDir Path root) throws IOException {
        Path sources = root.resolve("src");
        Files.createDirectories(sources.resolve("com/nanolaba/logging"));
        Path secret = root.resolve("secret.txt");
        Files.write(secret, Arrays.asList("TOP SECRET"), StandardCharsets.UTF_8);
        Files.setLastModifiedTime(secret, FileTime.fromMillis(0));
        Files.write(sources.resolve("secret.txt"), Arrays.asList("INSIDE"), StandardCharsets.UTF_8);
        Files.setLastModifiedTime(sources.resolve("secret.txt"), FileTime.fromMillis(0));
        System.setProperty(DefaultLoggerCreator.PROPERTY_SOURCES, sources.toString());
        for (String file : new String[]{"../../../../secret.txt", "..\\..\\..\\..\\secret.txt", "../../../secret.txt", "/secret.txt"}) {
            StackTraceElement forged = new StackTraceElement(SourceLineTest.class.getName(), "x", file, 1);
            assertNull(DbgSource.lineText(forged, SourceLineTest.class), file);
            assertNull(DbgSource.read(forged, Arrays.asList(sources), null), file);
        }
        StackTraceElement forgedClass = new StackTraceElement("com.nanolaba.logging.....secret", "x", "Secret.java", 1);
        assertNull(DbgSource.read(forgedClass, Arrays.asList(sources), null));
    }

    @Test
    public void testTheCacheOfLinesIsBounded() {
        int max = DbgSource.maxCachedLines;
        try {
            DbgSource.maxCachedLines = 1;
            StackTraceElement one = new StackTraceElement("com.nowhere.One", "x", "One.java", 1);
            StackTraceElement two = new StackTraceElement("com.nowhere.Two", "x", "Two.java", 1);
            assertNull(DbgSource.lineText(one, SourceLineTest.class));
            assertNull(DbgSource.lineText(two, SourceLineTest.class));
            assertNull(DbgSource.lineText(two, SourceLineTest.class));
            assertEquals(1, DbgSource.cachedLines(), "past the limit lines are read, not kept");
        } finally {
            DbgSource.maxCachedLines = max;
        }
    }

    // JSON

    @Test
    public void testJsonPutsTheLineIntoTheCaller() {
        JsonLogger json = new JsonLogger();
        json.setShowSourceLine(true);
        LogEntry entry = LogEntry.create(INFO, SourceLineTest.class, here(), null, "m", new Object[0]);
        String line = json.createLogString(entry);
        assertTrue(line.contains(",\"source\":\"LogEntry entry = LogEntry.create(INFO, SourceLineTest.class, here(), null, \\\"m\\\", new Object[0]);\"}"),
                   line);
        assertFalse(new JsonLogger().createLogString(entry).contains("\"source\""), "off by default");
        StackTraceElement gone = new StackTraceElement("com.nowhere.Gone", "run", "Gone.java", 3);
        assertFalse(json.createLogString(LogEntry.create(INFO, InfoObject.class, gone, null, "m", new Object[0])).contains("\"source\""));
    }
}
