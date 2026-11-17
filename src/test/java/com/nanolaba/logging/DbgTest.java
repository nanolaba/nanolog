package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.DEBUG;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code LOG.dbg(...)}: prints the expression, its value and the call site, and returns the value. The expressions
 * are read from this very file, so the tests depend on how their calls are written.
 */
class DbgTest extends AbstractLoggerTest {

    private static final String NL = System.lineSeparator();

    private final CallerDetection.Locator originalLocator = LOG.callerLocator;
    private final Consumer<Thread> originalHooks = Dbg.shutdownHooks;
    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private MemoryLogger memory;

    @BeforeEach
    public void install() {
        Dbg.clearCalls();
        Dbg.shutdownHooks = hook -> { };
        DbgSource.clearCache();
        Dump.clearCache();
        LOG.clearLimits();
        LOG.nanoClock = now::get;
        memory = MemoryLogger.install();
    }

    @AfterEach
    public void restore() {
        memory.close();
        Dbg.shutdownHooks = originalHooks;
        Dbg.clearCalls();
        LOG.callerLocator = originalLocator;
        LOG.nanoClock = System::nanoTime;
        System.clearProperty(DefaultLoggerCreator.PROPERTY_SOURCES);
        System.clearProperty(DefaultLoggerCreator.PROPERTY_STRICT);
        DbgSource.clearCache();
        LOG.clearLimits();
        LOG.init();
    }

    private LogEntry only() {
        List<LogEntry> entries = memory.getEntries();
        assertEquals(1, entries.size(), entries.toString());
        return entries.get(0);
    }

    private String message() {
        return only().getFormattedMessage();
    }

    private static int lineOf(Throwable here) {
        return here.getStackTrace()[0].getLineNumber();
    }

    @Test
    public void testPrintsTheExpressionAndItsValueAndReturnsIt() {
        int answer = LOG.dbg(6 * 7); int line = lineOf(new Throwable());

        assertEquals(42, answer);
        LogEntry entry = only();
        assertEquals("6 * 7 = 42 (Integer)", entry.getFormattedMessage());
        assertEquals(DEBUG, entry.getLevel());
        assertTrue(entry.isDbg());
        assertSame(DbgTest.class, entry.getSourceClass());
        assertEquals("testPrintsTheExpressionAndItsValueAndReturnsIt", entry.getCaller().getMethodName());
        assertEquals(line, entry.getCaller().getLineNumber());
        assertEquals(0, entry.getArgs().length, "the message is complete, there is nothing to format");
        assertEquals(LOG.class.getName(), entry.getCallerBoundary());
        assertTrue(entry.toString().endsWith(", dbg=true}"), entry.toString());
    }

    @Test
    public void testOtherEntriesAreNotDbg() {
        LOG.debug("plain");

        assertFalse(only().isDbg());
        assertFalse(only().toString().contains("dbg"));
    }

    @Test
    public void testValueIsReturnedUnchanged() {
        List<String> list = new ArrayList<>();

        assertSame(list, LOG.dbg(list));
        assertNull(LOG.dbg((Object) null));

        assertEquals("list = [] (ArrayList)", memory.getEntries().get(0).getFormattedMessage());
        assertEquals("(Object) null = null", memory.getEntries().get(1).getFormattedMessage());
    }

    @Test
    public void testSeveralValues() {
        String name = "alice";
        LOG.dbg(name, name.length());
        LOG.dbg(1, 2L, 3.5);

        assertEquals("name = \"alice\", name.length() = 5 (Integer)", memory.getEntries().get(0).getFormattedMessage());
        assertEquals("1 = 1 (Integer), 2L = 2 (Long), 3.5 = 3.5 (Double)", memory.getEntries().get(1).getFormattedMessage());
    }

    @Test
    public void testMultiLineArgumentsAndChains() {
        String text = " padded ";
        LOG.dbg(text
                        .trim()
                        .toUpperCase());

        assertEquals("text.trim().toUpperCase() = \"PADDED\"", message());
    }

    @Test
    public void testNoArguments() {
        LOG.dbg(); int line = lineOf(new Throwable());

        assertEquals("reached", message());
        assertEquals(line, only().getCaller().getLineNumber());
    }

    @Test
    public void testLambda() {
        Supplier<Integer> twice = () -> LOG.dbg(1 + 1);

        assertEquals(2, (int) twice.get());
        assertEquals("1 + 1 = 2 (Integer)", message());
    }

    static final class Nested {
        static final int VALUE = 7;

        static void call() {
            LOG.dbg(Nested.VALUE);
        }
    }

    @Test
    public void testNestedClassIsFoundInTheFileOfItsTopLevelClass() {
        Nested.call();

        assertEquals("Nested.VALUE = 7 (Integer)", message());
        assertSame(DbgTest.class, only().getSourceClass());
    }

    @Test
    public void testTwoCallsOnOneLineGiveOnlyTheValues() {
        int sum = LOG.dbg(1) + LOG.dbg(2);

        assertEquals(3, sum);
        assertEquals("1 (Integer)", memory.getEntries().get(0).getFormattedMessage());
        assertEquals("2 (Integer)", memory.getEntries().get(1).getFormattedMessage());
    }

    @Test
    public void testGenericArgumentIsOneExpression() {
        LOG.dbg(new HashMap<String, Integer>());

        assertEquals("new HashMap<String, Integer>() = {} (HashMap)", message());
    }

    @Test
    public void testArrays() {
        String[] names = {"a", "b"};

        assertSame(names, LOG.dbg(names));
        LOG.dbg(new int[]{1, 2});

        assertEquals("names = [\"a\", \"b\"] (String[])", memory.getEntries().get(0).getFormattedMessage());
        assertEquals("new int[]{1, 2} = [1, 2] (int[])", memory.getEntries().get(1).getFormattedMessage());
    }

    @Test
    public void testSpreadArrayDoesNotMatchTheTextSoOnlyTheValuesArePrinted() {
        LOG.dbg(1, 2, new Object[]{3, 4});

        assertEquals("1 (Integer), 2 (Integer), 3 (Integer), 4 (Integer)", message());
    }

    @Test
    public void testNullForTheRestIsOneNullValue() {
        LOG.dbg(1, 2, (Object[]) null);

        assertEquals("1 = 1 (Integer), 2 = 2 (Integer), (Object[]) null = null", message());
    }

    @Test
    public void testStringsAndCharsAreQuotedAndEscaped() {
        LOG.dbg("say \"hi\"\n");
        LOG.dbg('q');
        LOG.dbg('\'');
        LOG.dbg('"');
        LOG.dbg('\n');

        assertEquals("\"say \\\"hi\\\"\\n\" = \"say \\\"hi\\\"\\n\"", memory.getEntries().get(0).getFormattedMessage());
        assertEquals("'q' = 'q'", memory.getEntries().get(1).getFormattedMessage());
        assertEquals("'\\'' = '\\''", memory.getEntries().get(2).getFormattedMessage());
        assertEquals("'\"' = '\"'", memory.getEntries().get(3).getFormattedMessage());
        assertEquals("'\\n' = '\\n'", memory.getEntries().get(4).getFormattedMessage());
    }

    static final class Point {
        @Override
        public String toString() {
            return "Point[1, 2]";
        }
    }

    static final class Broken {
        @Override
        public String toString() {
            throw new IllegalStateException("broken");
        }
    }

    enum Mode {
        PLAIN,
        SPECIAL {
            @Override
            int weight() {
                return 2;
            }
        };

        int weight() {
            return 1;
        }
    }

    @Test
    public void testTypeIsLeftOutWhenTheTextStartsWithIt() {
        LOG.dbg(new Point());
        LOG.dbg(new Broken());

        assertEquals("new Point() = Point[1, 2]", memory.getEntries().get(0).getFormattedMessage());
        assertEquals("new Broken() = [FAILED toString()] (Broken)", memory.getEntries().get(1).getFormattedMessage());
    }

    static final class Invoice {
        long id = 17;
        List<String> lines = Collections.singletonList("A-1");
        Point point = new Point();
        String password = "s3cr3t";
    }

    @Test
    public void testObjectsWithoutToStringAreDumpedFieldByField() {
        LOG.dbg(new Invoice());

        assertEquals("new Invoice() = Invoice{id=17, lines=[\"A-1\"], point=Point[1, 2], password=***}", message());
    }

    @Test
    public void testAStringPassedToDbgIsNeverCut() {
        String text = String.join("", Collections.nCopies(Dump.MAX_STRING + 1, "a"));

        LOG.dbg(text);

        assertEquals("text = \"" + text + "\"", message());
    }

    @Test
    public void testEnumConstantsAreTypedByTheirEnum() {
        LOG.dbg(Mode.PLAIN);
        LOG.dbg(Mode.SPECIAL);

        assertEquals("Mode.PLAIN = PLAIN (Mode)", memory.getEntries().get(0).getFormattedMessage());
        assertEquals("Mode.SPECIAL = SPECIAL (Mode)", memory.getEntries().get(1).getFormattedMessage(), "a constant with a body");
    }

    @Test
    public void testMessageWithoutText() {
        assertEquals("1 (Integer), \"a\"", Dbg.message(null, new Object[]{1, "a"}));
        assertEquals("1 (Integer)", Dbg.message("", new Object[]{1}), "the line now holds dbg() without arguments");
        assertEquals("reached", Dbg.message("x", new Object[0]));
    }

    @Test
    public void testWithoutSourcesOnlyTheValuesArePrinted() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_SOURCES, "");

        LOG.dbg(6 * 7);

        assertEquals("42 (Integer)", message());
    }

    @Test
    public void testWithoutACallSite() {
        LOG.callerLocator = () -> CallerDetection.Caller.NONE;

        assertEquals(42, (int) LOG.dbg(42));

        assertEquals("42 (Integer)", message());
        assertNull(only().getSourceClass());
        assertNull(only().getCaller());
    }

    @Test
    public void testStrictModeIgnoresBracesInTheValues() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_STRICT, "true");
        LOG.init();
        LOG.init(memory);

        LOG.dbg("{} {}");

        memory.assertNoDiagnostics();
        assertEquals("", getErrAndClear());
    }

    // levels

    @Test
    public void testDisabledLevelCostsNoStackWalk() {
        LOG.init(new ConsoleLogger());
        LOG.setLevel(INFO);
        CountingLocator counting = new CountingLocator(originalLocator);
        LOG.callerLocator = counting;

        assertEquals(42, (int) LOG.dbg(42));
        LOG.dbg(1, 2);
        LOG.dbg();
        assertEquals(42, (int) LOG.once().dbg(42));
        LOG.once().dbg(1, 2);
        LOG.once().dbg();

        assertEquals(0, counting.walks.get());
        assertEquals("", getOutAndClear());
    }

    @Test
    public void testPackageRulesApply() {
        LOG.init(new ConsoleLogger());
        LOG.setLevel(DbgTest.class.getName(), INFO);

        assertEquals(42, (int) LOG.dbg(42));
        LOG.dbg(1, 2);
        LOG.dbg();
        assertEquals(42, (int) LOG.once().dbg(42));
        LOG.once().dbg(1, 2);
        LOG.once().dbg();

        assertEquals("", getOutAndClear());
    }

    // output

    @Test
    public void testConsoleOutputMarksTheLineAndShowsTheCallSite() {
        LOG.init(new ConsoleLogger().setShowDate(false));

        LOG.dbg(6 * 7); int line = lineOf(new Throwable());
        LOG.debug("plain");

        assertEquals("DBG [DbgTest.testConsoleOutputMarksTheLineAndShowsTheCallSite(DbgTest.java:" + line + ")] 6 * 7 = 42 (Integer)" + NL +
                     "DEBUG [DbgTest] plain" + NL, getOutAndClear());
    }

    @Test
    public void testJsonOutputHasADbgField() {
        LOG.init(new JsonLogger());

        LOG.dbg(6 * 7);
        LOG.debug("plain");

        String[] lines = getOutAndClear().split(NL);
        assertTrue(lines[0].contains("\"level\":\"DEBUG\",\"dbg\":true,\"logger\":\"com.nanolaba.logging.DbgTest\""), lines[0]);
        assertTrue(lines[0].contains("\"message\":\"6 * 7 = 42 (Integer)\""), lines[0]);
        assertFalse(lines[1].contains("dbg"), lines[1]);
    }

    // rate limiting

    private void limited(int i) {
        LOG.atMostEvery(10, TimeUnit.SECONDS).dbg(i, i * 2);
    }

    @Test
    public void testRateLimitedCalls() {
        for (int i = 0; i < 3; i++) {
            assertEquals(i, (int) LOG.once().dbg(i));
            limited(i);
            LOG.once().dbg();
        }
        now.addAndGet(TimeUnit.SECONDS.toNanos(10));
        limited(3);

        List<LogEntry> entries = memory.getEntries();
        assertEquals(4, entries.size(), entries.toString());
        assertEquals("i = 0 (Integer)", entries.get(0).getFormattedMessage());
        assertEquals("i = 0 (Integer), i * 2 = 0 (Integer)", entries.get(1).getFormattedMessage());
        assertEquals("reached", entries.get(2).getFormattedMessage());
        assertEquals("i = 3 (Integer), i * 2 = 6 (Integer)", entries.get(3).getFormattedMessage());
        assertEquals(0, entries.get(1).getSkippedCount());
        assertEquals(2, entries.get(3).getSkippedCount());
        for (LogEntry entry : entries) {
            assertTrue(entry.isDbg());
            assertEquals(LOG.Limited.class.getName(), entry.getCallerBoundary());
        }
    }
}
