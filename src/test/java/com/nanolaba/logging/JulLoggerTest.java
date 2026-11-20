package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/**
 * {@code JulLogger}: entries written to java.util.logging, to the logger named after the source class.
 */
class JulLoggerTest extends AbstractLoggerTest {

    private final Map<String, String> environment = new HashMap<>();
    private final List<LogRecord> records = new ArrayList<>();
    private final Handler capture = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    // a strong reference: java.util.logging forgets the level of a logger that is collected
    private final Logger jul = Logger.getLogger(JulLoggerTest.class.getName());

    @BeforeEach
    public void setUp() {
        DefaultLoggerCreator.environment = environment::get;
        jul.setUseParentHandlers(false);
        jul.setLevel(Level.ALL);
        jul.addHandler(capture);
        LOG.init(new JulLogger());
    }

    @AfterEach
    public void tearDown() {
        jul.removeHandler(capture);
        jul.setUseParentHandlers(true);
        jul.setLevel(null);
        DefaultLoggerCreator.environment = System::getenv;
        System.clearProperty(DefaultLoggerCreator.PROPERTY_LOGGER_CLASS);
        LOG.init();
    }

    private LogRecord single() {
        assertEquals(1, records.size(), String.valueOf(records));
        return records.get(0);
    }

    @Test
    public void testAnEntryGoesToTheLoggerOfItsSourceClass() {
        LOG.info("Loaded {} items", 3);

        LogRecord record = single();
        assertEquals(Level.INFO, record.getLevel());
        assertEquals("Loaded 3 items", record.getMessage());
        assertNull(record.getParameters(), "formatted already: a JUL formatter must not format it again");
        assertEquals(JulLoggerTest.class.getName(), record.getLoggerName());
        assertEquals(JulLoggerTest.class.getName(), record.getSourceClassName());
        assertEquals("testAnEntryGoesToTheLoggerOfItsSourceClass", record.getSourceMethodName());
        assertNull(record.getThrown());
    }

    @Test
    public void testLevels() {
        LOG.trace("t");
        LOG.debug("d");
        LOG.info("i");
        LOG.warn("w");
        LOG.error("e");

        assertEquals(5, records.size());
        assertEquals(Level.FINEST, records.get(0).getLevel());
        assertEquals(Level.FINE, records.get(1).getLevel());
        assertEquals(Level.INFO, records.get(2).getLevel());
        assertEquals(Level.WARNING, records.get(3).getLevel());
        assertEquals(Level.SEVERE, records.get(4).getLevel());
    }

    @Test
    public void testJulDecidesWhichLevelsAreEnabled() {
        JulLogger logger = new JulLogger();
        Class<?> source = JulLoggerTest.class;

        jul.setLevel(Level.FINE);
        assertFalse(logger.isEnabled(TRACE, source));
        assertTrue(logger.isEnabled(DEBUG, source));

        jul.setLevel(Level.INFO);
        assertFalse(logger.isEnabled(DEBUG, source));
        assertTrue(logger.isEnabled(INFO, source));

        jul.setLevel(Level.WARNING);
        assertFalse(logger.isEnabled(INFO, source));
        assertTrue(logger.isEnabled(WARN, source));

        jul.setLevel(Level.SEVERE);
        assertFalse(logger.isEnabled(WARN, source));
        assertTrue(logger.isEnabled(ERROR, source));

        jul.setLevel(Level.FINEST);
        assertTrue(logger.isEnabled(TRACE, source));

        jul.setLevel(Level.WARNING);
        assertFalse(LOG.isInfoEnabled(), "LOG asks the installed logger");
        assertTrue(LOG.isWarnEnabled());
    }

    @Test
    public void testAnEntryBelowTheLevelOfJulBuildsNoRecord() {
        jul.setLevel(Level.WARNING);

        new JulLogger().log(LogEntry.create(INFO, JulLoggerTest.class, null, null, "Quiet", new Object[0], 0));

        assertTrue(records.isEmpty());
    }

    @Test
    public void testTheExceptionIsThrownOfTheRecord() {
        IllegalStateException failure = new IllegalStateException("boom");

        LOG.error(failure, "Failed {}", 7);

        LogRecord record = single();
        assertEquals("Failed 7", record.getMessage());
        assertSame(failure, record.getThrown());
    }

    @Test
    public void testFieldsAndContextFollowTheMessage() {
        try (LOG.Context ignored = LOG.context("requestId", "r-17")) {
            LOG.info("Paid", LOG.kv("orderId", "A-17"));
        }

        assertEquals("Paid orderId=A-17 requestId=r-17", single().getMessage());
    }

    @Test
    public void testSkippedCalls() {
        new JulLogger().log(LogEntry.create(INFO, JulLoggerTest.class, null, null, "Polled", new Object[0], 41));

        assertEquals("Polled [skipped 41]", single().getMessage());
    }

    @Test
    public void testAnEntryWithoutAMessage() {
        IllegalStateException failure = new IllegalStateException("boom");

        new JulLogger().log(LogEntry.create(ERROR, JulLoggerTest.class, null, failure, null, new Object[0], 0));

        assertEquals("", single().getMessage());
        assertSame(failure, single().getThrown());

        new JulLogger().log(LogEntry.create(ERROR, JulLoggerTest.class, null, failure, null, new Object[0], 3));
        assertEquals("[skipped 3]", records.get(1).getMessage(), "no space before the first part");
    }

    @Test
    public void testAnEntryWithoutACallSiteNamesItsSourceClass() {
        new JulLogger().log(LogEntry.create(INFO, JulLoggerTest.class, null, null, "No call site", new Object[0], 0));

        LogRecord record = single();
        assertEquals(JulLoggerTest.class.getName(), record.getSourceClassName());
        assertNull(record.getSourceMethodName());
    }

    @Test
    public void testAnEntryWithoutASourceClassGoesToTheRootLogger() {
        Logger root = Logger.getLogger("");
        Level rootLevel = root.getLevel();
        root.addHandler(capture);
        root.setLevel(Level.ALL);
        try {
            new JulLogger().log(LogEntry.create(INFO, null, null, null, "Rootless", new Object[0], 0));
        } finally {
            root.removeHandler(capture);
            root.setLevel(rootLevel);
        }

        LogRecord record = single();
        assertEquals("", record.getLoggerName());
        assertNull(record.getSourceClassName(), "not inferred: the frames of Nanolog are not the caller");
        assertEquals("Rootless", record.getMessage());
        assertTrue(new JulLogger().isEnabled(ERROR, null));
        getErrAndClear();
    }

    @Test
    public void testChosenByASetting() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_LOGGER_CLASS, JulLogger.class.getName());
        LOG.init();

        LOG.warn("Configured");

        assertEquals("Configured", single().getMessage());
        assertEquals(Level.WARNING, single().getLevel());
    }

    @Test
    public void testInAgentModeADbgEntryThatJulDropsIsPrintedOnTheConsole() {
        StackTraceElement frame = new StackTraceElement(JulLoggerTest.class.getName(), "checkout", "JulLoggerTest.java", 9);
        LogEntry dbg = LogEntry.createDbg(JulLoggerTest.class, frame, "cart.size() = 2 (Integer)", 0, LogEntry.LOG_BOUNDARY);
        JulLogger logger = new JulLogger();
        jul.setLevel(Level.INFO);
        getOutAndClear();

        logger.log(dbg);
        assertEquals("", getOutAndClear(), "outside agent mode the levels decide");

        Dbg.ignoreLevels = true;
        try {
            logger.log(dbg);
            assertEquals("DBG [JulLoggerTest.checkout(JulLoggerTest.java:9)] cart.size() = 2 (Integer)" + System.lineSeparator(),
                         getOutAndClear());
            logger.log(LogEntry.create(DEBUG, JulLoggerTest.class, frame, null, "plain", new Object[0], 0));
            assertEquals("", getOutAndClear(), "only an entry of LOG.dbg");
            jul.setLevel(Level.ALL);
            logger.log(dbg);
            assertEquals("", getOutAndClear(), "JUL writes what it takes");
        } finally {
            Dbg.ignoreLevels = false;
        }
        assertEquals(1, records.size());
        assertEquals("cart.size() = 2 (Integer)", single().getMessage());
    }
}
