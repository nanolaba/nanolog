package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.DEBUG;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.ERROR;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.TRACE;
import static com.nanolaba.logging.LogEntry.LogEntryLevel.WARN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code NanologHandler}: the records of java.util.logging, and through it of System.Logger, as Nanolog entries.
 */
class NanologHandlerTest extends AbstractLoggerTest {

    private final Map<String, String> environment = new HashMap<>();
    // a strong reference: java.util.logging forgets the settings of a logger that is collected
    private final Logger jul = Logger.getLogger(NanologHandlerTest.class.getName());
    private final NanologHandler handler = new NanologHandler();
    private final Logger root = Logger.getLogger("");
    private final List<Handler> rootHandlers = Arrays.asList(root.getHandlers());
    private final Level rootLevel = root.getLevel();
    private MemoryLogger memory;

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
        LOG.init();
        jul.setUseParentHandlers(false);
        jul.setLevel(Level.ALL);
        jul.addHandler(handler);
        memory = MemoryLogger.capture();
    }

    @AfterEach
    public void tearDown() {
        memory.close();
        jul.removeHandler(handler);
        jul.setUseParentHandlers(true);
        jul.setLevel(null);
        NanologHandler.uninstall();
        for (Handler h : root.getHandlers()) {
            root.removeHandler(h);
        }
        for (Handler h : rootHandlers) {
            root.addHandler(h);
        }
        root.setLevel(rootLevel);
        DefaultLoggerCreator.environment = System::getenv;
        LOG.init();
    }

    private LogEntry single() {
        List<LogEntry> entries = memory.getEntries();
        assertEquals(1, entries.size(), String.valueOf(entries));
        return entries.get(0);
    }

    @Test
    public void testARecordBecomesAnEntryOfItsCaller() {
        jul.info("Started");

        LogEntry entry = single();
        assertEquals(INFO, entry.getLevel());
        assertEquals("Started", entry.getFormattedMessage());
        assertSame(NanologHandlerTest.class, entry.getSourceClass());
        assertEquals(NanologHandlerTest.class.getName(), entry.getCaller().getClassName());
        assertEquals("testARecordBecomesAnEntryOfItsCaller", entry.getCaller().getMethodName());
        assertTrue(entry.getCaller().getLineNumber() > 0, "the frame of the call, with its line");
        assertNull(entry.getThrowable());
    }

    @Test
    public void testLevels() {
        int[] values = {Integer.MAX_VALUE, 1000, 999, 900, 899, 800, 799, 500, 499, 300, Integer.MIN_VALUE};
        LogEntry.LogEntryLevel[] expected = {ERROR, ERROR, WARN, WARN, INFO, INFO, DEBUG, DEBUG, TRACE, TRACE, TRACE};
        for (int value : values) {
            jul.log(new Level("L" + value, value) {
            }, "at " + value);
        }

        List<LogEntry> entries = memory.getEntries();
        assertEquals(values.length, entries.size());
        for (int i = 0; i < values.length; i++) {
            assertEquals(expected[i], entries.get(i).getLevel(), "level " + values[i]);
        }
    }

    @Test
    public void testParametersAreFormattedAsJulFormatsThem() {
        jul.log(Level.INFO, "Loaded {0} items", 3);
        jul.info("A set {} stays as it is");

        assertEquals("Loaded 3 items", memory.getEntries().get(0).getFormattedMessage());
        assertEquals("A set {} stays as it is", memory.getEntries().get(1).getFormattedMessage());
    }

    @Test
    public void testTheThrownExceptionGoesWithTheEntry() {
        IllegalStateException failure = new IllegalStateException("boom");

        jul.log(Level.SEVERE, "Failed", failure);

        assertEquals(ERROR, single().getLevel());
        assertSame(failure, single().getThrowable());
    }

    @Test
    public void testARecordWhoseCallerIsNotOnTheStack() {
        LogRecord record = new LogRecord(Level.WARNING, "Elsewhere");
        record.setSourceClassName(NanologHandlerTest.class.getName());
        record.setSourceMethodName("run");

        handler.publish(record);

        LogEntry entry = single();
        assertSame(NanologHandlerTest.class, entry.getSourceClass());
        assertEquals("run", entry.getCaller().getMethodName());
        assertEquals(-1, entry.getCaller().getLineNumber());
    }

    @Test
    public void testARecordWithoutACallerTakesItsLoggerName() {
        LogRecord named = new LogRecord(Level.INFO, "Named");
        named.setLoggerName(NanologHandlerTest.class.getName());
        named.setSourceClassName(null);
        handler.publish(named);

        LogRecord unnamed = new LogRecord(Level.INFO, "Unnamed");
        unnamed.setLoggerName("com.acme.no.such.Logger");
        unnamed.setSourceClassName(null);
        handler.publish(unnamed);

        LogRecord methodOnly = new LogRecord(Level.INFO, "Method only");
        methodOnly.setLoggerName(NanologHandlerTest.class.getName());
        methodOnly.setSourceClassName(null);
        methodOnly.setSourceMethodName("run");
        handler.publish(methodOnly);

        LogRecord classOnly = new LogRecord(Level.INFO, "Class only");
        classOnly.setSourceClassName(NanologHandlerTest.class.getName());
        classOnly.setSourceMethodName(null);
        handler.publish(classOnly);

        List<LogEntry> entries = memory.getEntries();
        assertSame(NanologHandlerTest.class, entries.get(3).getSourceClass());
        assertNull(entries.get(3).getCaller(), "a class without its method is no call site");
        assertSame(NanologHandlerTest.class, entries.get(0).getSourceClass());
        assertNull(entries.get(0).getCaller());
        assertNull(entries.get(1).getSourceClass(), "a logger name that is no class gives no source");
        assertNull(entries.get(1).getCaller());
        assertNull(entries.get(2).getCaller(), "a method without its class is no call site");
    }

    @Test
    public void testACallerClassThatCannotBeLoaded() {
        LogRecord record = new LogRecord(Level.INFO, "Gone");
        record.setLoggerName(NanologHandlerTest.class.getName());
        record.setSourceClassName("com.acme.no.such.Caller");
        record.setSourceMethodName("run");

        handler.publish(record);

        assertSame(NanologHandlerTest.class, single().getSourceClass(), "the logger name instead");
        assertEquals("com.acme.no.such.Caller", single().getCaller().getClassName());
    }

    @Test
    public void testANestedCallerCountsAsItsEnclosingClass() {
        new Runnable() {
            @Override
            public void run() {
                jul.info("Inside");
            }
        }.run();

        assertSame(NanologHandlerTest.class, single().getSourceClass());
        assertEquals("run", single().getCaller().getMethodName());
    }

    @Test
    public void testNullAndRecordsBelowTheLevelOfTheHandler() {
        handler.publish(null);
        handler.setLevel(Level.WARNING);
        jul.info("Below");

        assertTrue(memory.getEntries().isEmpty());
    }

    @Test
    public void testALoopThroughJulLoggerIsReported() {
        memory.close();
        JulLogger julLogger = new JulLogger();
        LOG.init(julLogger);

        LOG.info("Round and round");
        LOG.info("Again");

        String err = getErrAndClear();
        assertTrue(err.contains("[NANOLOG] java.util.logging loops back into Nanolog: JulLogger writes to JUL and NanologHandler sends JUL to Nanolog; the record is dropped"), err);
        assertEquals(err.indexOf("loops back"), err.lastIndexOf("loops back"), "reported once");
        memory = MemoryLogger.capture();
    }

    @Test
    public void testInstallRoutesTheRootLoggerAndUninstallPutsItBack() {
        Handler console = new java.util.logging.ConsoleHandler();
        root.addHandler(console);
        Logger library = Logger.getLogger("com.acme.library");

        NanologHandler.install();
        NanologHandler.install();
        library.info("From a library");

        assertEquals(1, memory.getEntries().size(), "installed once");
        assertEquals("From a library", single().getFormattedMessage());
        assertSame(NanologHandlerTest.class, single().getSourceClass());
        assertEquals(1, root.getHandlers().length);
        assertTrue(root.getHandlers()[0] instanceof NanologHandler);
        assertEquals("", getErrAndClear(), "the console handler of the root logger is removed");

        NanologHandler.uninstall();
        NanologHandler.uninstall();
        NanologHandler.install();
        NanologHandler.uninstall();

        assertEquals(1, Arrays.stream(root.getHandlers()).filter(h -> h == console).count(), "put back once");
        assertFalse(Arrays.stream(root.getHandlers()).anyMatch(h -> h instanceof NanologHandler));
        root.removeHandler(console);
    }

    @Test
    public void testSystemLoggerReachesNanologThroughJul() throws Exception {
        assumeTrue(!System.getProperty("java.specification.version").startsWith("1."), "System.Logger is Java 9+");
        NanologHandler.install();

        Object logger = System.class.getMethod("getLogger", String.class).invoke(null, "com.acme.platform");
        Class<?> levelClass = Class.forName("java.lang.System$Logger$Level");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object info = Enum.valueOf((Class) levelClass, "INFO");
        Method log = Class.forName("java.lang.System$Logger").getMethod("log", levelClass, String.class, Object[].class);
        log.invoke(logger, info, "From {0}", new Object[]{"System.Logger"});

        assertEquals("From System.Logger", single().getFormattedMessage());
        assertEquals(INFO, single().getLevel());
    }

    @Test
    public void testFlushAndCloseFlushNanolog() {
        AtomicInteger flushes = new AtomicInteger();
        memory.close();
        LOG.init(new ILogger() {
            @Override
            public void log(LogEntry entry) {
            }

            @Override
            public void flush() {
                flushes.incrementAndGet();
            }
        });

        handler.flush();
        assertEquals(1, flushes.get());
        handler.close();
        assertEquals(2, flushes.get());
        memory = MemoryLogger.capture();
    }
}
