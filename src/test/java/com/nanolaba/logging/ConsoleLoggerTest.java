package com.nanolaba.logging;

import com.nanolaba.logging.util.AnonymousSource;
import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.FieldPosition;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConsoleLoggerTest extends AbstractLoggerTest {

    private static final Logger log = LoggerFactory.getLogger(ConsoleLoggerTest.class);

    @Test
    public void testInfoLevel() {
        ConsoleLogger logger = createLogger();

        LOG.init(logger);

        LOG.info(InfoObject.class, "");
        assertEquals("INFO 01.01.2010 00:00:00 [InfoObject]" + System.lineSeparator(), getOutAndClear());

        LOG.info(InfoObject.class, "some message {} next {}", "abc", "123");
        assertEquals("INFO 01.01.2010 00:00:00 [InfoObject] some message abc next 123" + System.lineSeparator(), getOutAndClear());

        logger.setShowLevel(false);
        LOG.info(InfoObject.class, "some message {} next {}", "abc", "123");
        assertEquals("01.01.2010 00:00:00 [InfoObject] some message abc next 123" + System.lineSeparator(), getOutAndClear());

        logger.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
        LOG.info(InfoObject.class, "some message {} next {}", "abc", "123");
        assertEquals("2010-01-01 [InfoObject] some message abc next 123" + System.lineSeparator(), getOutAndClear());

        logger.setShowDate(false);
        LOG.info(InfoObject.class, "some message {} next {}", "abc", "123");
        assertEquals("[InfoObject] some message abc next 123" + System.lineSeparator(), getOutAndClear());

        logger.setShowSourceFullName(true);
        LOG.info(InfoObject.class, "some message {} next {}", "abc", "123");
        assertEquals("[com.nanolaba.logging.util.InfoObject] some message abc next 123" + System.lineSeparator(), getOutAndClear());

        logger.setShowSource(false);
        LOG.info(InfoObject.class, "some message {} next {}", "abc", "123");
        assertEquals("some message abc next 123" + System.lineSeparator(), getOutAndClear());

        LOG.info(InfoObject.class, new Throwable("test"));
        assertTrue(getOutAndClear().startsWith("java.lang.Throwable: test" + System.lineSeparator() +
                                               "\tat com.nanolaba.logging.ConsoleLoggerTest.testInfoLevel"));

        LOG.info(InfoObject.class, new Throwable("test"), "ABC");
        assertTrue(getOutAndClear().startsWith("ABC java.lang.Throwable: test" + System.lineSeparator() +
                                               "\tat com.nanolaba.logging.ConsoleLoggerTest.testInfoLevel"));

    }

    @Test
    public void testErrorLevel() {
        ConsoleLogger logger = createLogger();

        LOG.init(logger);

        LOG.error(InfoObject.class, "");
        assertEquals("ERROR 01.01.2010 00:00:00 [InfoObject]" + System.lineSeparator(), getErrAndClear());

        LOG.error(InfoObject.class, "some message {} next {}", "abc", "123");
        assertEquals("ERROR 01.01.2010 00:00:00 [InfoObject] some message abc next 123" + System.lineSeparator(), getErrAndClear());
    }

    @Test
    public void testTrailingThrowablePrintsStackTrace() {
        LOG.init(createLogger());

        LOG.error(InfoObject.class, "Failed to load {}", "order", new Throwable("test"));
        assertTrue(getErrAndClear().startsWith("ERROR 01.01.2010 00:00:00 [InfoObject] Failed to load order java.lang.Throwable: test" + System.lineSeparator() +
                                               "\tat com.nanolaba.logging.ConsoleLoggerTest.testTrailingThrowablePrintsStackTrace"));
    }

    @Test
    public void testBrokenThrowableNeverThrows() {
        LOG.init(createLogger());

        LOG.error(InfoObject.class, new RuntimeException() {
            @Override
            public String toString() {
                throw new IllegalStateException("broken toString");
            }
        }, "message");

        assertEquals("ERROR 01.01.2010 00:00:00 [InfoObject] message [FAILED printStackTrace()]" + System.lineSeparator(), getErrAndClear());
    }

    @Test
    public void testLocationIsOffByDefault() {
        assertFalse(new ConsoleLogger().isShowLocation());

        LOG.init(createLogger());
        LOG.info("plain");
        assertEquals("INFO 01.01.2010 00:00:00 [ConsoleLoggerTest] plain" + System.lineSeparator(), getOutAndClear());
    }

    @Test
    public void testStreamDefaultSendsOnlyErrorsToStandardError() {
        ConsoleLogger logger = createLogger();
        assertEquals("err@ERROR", logger.getStream());

        assertStreams(logger, "out", "out", "out", "out", "err");
    }

    @Test
    public void testStreamOutSendsEverythingToStandardOutput() {
        ConsoleLogger logger = createLogger();
        assertSame(logger, logger.setStream("out"));

        assertEquals("out", logger.getStream());
        assertStreams(logger, "out", "out", "out", "out", "out");

        logger.setStream("err@OFF");
        assertEquals("out", logger.getStream(), "no level goes to System.err");
    }

    @Test
    public void testStreamErrSendsEverythingToStandardError() {
        ConsoleLogger logger = createLogger().setStream(" ERR ");

        assertEquals("err", logger.getStream());
        assertStreams(logger, "err", "err", "err", "err", "err");

        logger.setStream("err@trace");
        assertEquals("err", logger.getStream(), "TRACE and above is every level");
    }

    @Test
    public void testStreamErrAtALevel() {
        ConsoleLogger logger = createLogger().setStream("Err@ warn ");

        assertEquals("err@WARN", logger.getStream());
        assertStreams(logger, "out", "out", "out", "err", "err");

        logger.setStream("err@DEBUG");
        assertEquals("err@DEBUG", logger.getStream());
        assertStreams(logger, "out", "err", "err", "err", "err");
    }

    @Test
    public void testInvalidStreamChangesNothing() {
        ConsoleLogger logger = createLogger().setStream("err@WARN");

        assertSame(logger, logger.setStream("both"));
        logger.setStream("err@LOUD");
        logger.setStream("err@");
        logger.setStream(null);

        assertEquals("err@WARN", logger.getStream());
        assertEquals(Arrays.asList(
                "[NANOLOG] Invalid stream \"both\" in setStream: expected out, err or err@<level>; nothing changed",
                "[NANOLOG] Invalid stream \"err@LOUD\" in setStream: expected out, err or err@<level>; nothing changed",
                "[NANOLOG] Invalid stream \"err@\" in setStream: expected out, err or err@<level>; nothing changed",
                "[NANOLOG] Invalid stream null in setStream: expected out, err or err@<level>; nothing changed"),
                     Arrays.asList(getErrAndClear().split(System.lineSeparator())));
    }

    @Test
    public void testEntryWithoutALevelGoesToStandardOutput() {
        ConsoleLogger logger = createLogger().setStream("err");

        logger.log(new LogEntry(null, InfoObject.class, null, "no level"));

        assertEquals("01.01.2010 00:00:00 [InfoObject] no level" + System.lineSeparator(), getOutAndClear());
        assertEquals("", getErrAndClear());
    }

    /**
     * Logs one entry per level and checks which stream each one reached.
     */
    private void assertStreams(ConsoleLogger logger, String trace, String debug, String info, String warn, String error) {
        LOG.init(logger);
        String[] expected = {trace, debug, info, warn, error};
        LogEntry.LogEntryLevel[] levels = LogEntry.LogEntryLevel.values();
        for (int i = 0; i < levels.length; i++) {
            LOG.log(levels[i], InfoObject.class, null, () -> "line");
            String out = getOutAndClear();
            String err = getErrAndClear();
            assertEquals(expected[i], out.isEmpty() ? "err" : "out", levels[i] + ": out=" + out + " err=" + err);
            assertEquals(1, (out + err).split(System.lineSeparator()).length, levels[i].name());
        }
    }

    @Test
    public void testThreadIsOffByDefault() {
        assertFalse(new ConsoleLogger().isShowThread());
    }

    @Test
    public void testShowThread() throws InterruptedException {
        ConsoleLogger logger = createLogger().setShowThread(true);
        assertTrue(logger.isShowThread());
        LOG.init(logger);

        LOG.info(InfoObject.class, "from the test thread");
        assertEquals("INFO 01.01.2010 00:00:00 [" + Thread.currentThread().getName() + "] [InfoObject] from the test thread" +
                     System.lineSeparator(), getOutAndClear());

        Thread worker = new Thread(() -> LOG.info(InfoObject.class, "from a worker"), "worker-1");
        worker.start();
        worker.join();
        assertEquals("INFO 01.01.2010 00:00:00 [worker-1] [InfoObject] from a worker" + System.lineSeparator(), getOutAndClear());

        // the thread keeps its place between the level and the message when the parts around it are off
        logger.setShowDate(false).setShowSource(false);
        LOG.info("bare");
        assertEquals("INFO [" + Thread.currentThread().getName() + "] bare" + System.lineSeparator(), getOutAndClear());

        logger.setShowThread(false);
        LOG.info("bare");
        assertEquals("INFO bare" + System.lineSeparator(), getOutAndClear());
    }

    @Test
    public void testShowLocation() {
        ConsoleLogger logger = createLogger().setShowLocation(true);
        assertTrue(logger.isShowLocation());
        LOG.init(logger);

        int line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        LOG.info("located");
        assertEquals("INFO 01.01.2010 00:00:00 [ConsoleLoggerTest.testShowLocation(ConsoleLoggerTest.java:" + line + ")] located" + System.lineSeparator(),
                     getOutAndClear());

        logger.setShowSourceFullName(true);
        line = new Throwable().getStackTrace()[0].getLineNumber() + 1;
        LOG.info("located");
        assertEquals("INFO 01.01.2010 00:00:00 [com.nanolaba.logging.ConsoleLoggerTest.testShowLocation(ConsoleLoggerTest.java:" + line + ")] located" +
                     System.lineSeparator(), getOutAndClear());

        // an explicit source class carries no caller frame, so the line keeps its plain form
        logger.setShowSourceFullName(false);
        LOG.info(InfoObject.class, "explicit");
        assertEquals("INFO 01.01.2010 00:00:00 [InfoObject] explicit" + System.lineSeparator(), getOutAndClear());

        // the location belongs to the source block
        logger.setShowSource(false);
        LOG.info("no source");
        assertEquals("INFO 01.01.2010 00:00:00 no source" + System.lineSeparator(), getOutAndClear());
    }

    @Test
    public void testLocationWithoutFullDebugInformation() {
        ConsoleLogger logger = createLogger().setShowLocation(true);

        logger.log(LogEntry.create(INFO, InfoObject.class, new StackTraceElement("com.acme.Foo", "bar", null, -1), null, "no file", new Object[0]));
        assertEquals("INFO 01.01.2010 00:00:00 [InfoObject.bar(Unknown Source)] no file" + System.lineSeparator(), getOutAndClear());

        logger.log(LogEntry.create(INFO, InfoObject.class, new StackTraceElement("com.acme.Foo", "bar", "Foo.java", -1), null, "no line", new Object[0]));
        assertEquals("INFO 01.01.2010 00:00:00 [InfoObject.bar(Foo.java)] no line" + System.lineSeparator(), getOutAndClear());

        logger.log(LogEntry.create(INFO, InfoObject.class, new StackTraceElement("com.acme.Foo", "bar", "Foo.java", 0), null, "line zero", new Object[0]));
        assertEquals("INFO 01.01.2010 00:00:00 [InfoObject.bar(Foo.java:0)] line zero" + System.lineSeparator(), getOutAndClear());
    }

    @Test
    public void testGetCurrentDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy");
        String currentYear = dateFormat.format(new Date());
        ConsoleLogger logger = new ConsoleLogger().setDateFormat(dateFormat);
        LOG.init(logger);

        LOG.error(InfoObject.class, "");
        assertEquals("ERROR " + currentYear + " [InfoObject]" + System.lineSeparator(), getErrAndClear());
    }

    @Test
    public void testAnonymousAndLocalSourceClassesArePrintedByName() {
        ConsoleLogger logger = createLogger();
        LOG.init(logger);

        LOG.info(AnonymousSource.ANONYMOUS, "anonymous");
        assertEquals("INFO 01.01.2010 00:00:00 [AnonymousSource$1] anonymous" + System.lineSeparator(), getOutAndClear());
        LOG.info(AnonymousSource.LOCAL, "local");
        assertEquals("INFO 01.01.2010 00:00:00 [Local] local" + System.lineSeparator(), getOutAndClear());

        logger.setShowSourceFullName(true);
        LOG.info(AnonymousSource.ANONYMOUS, "anonymous");
        assertEquals("INFO 01.01.2010 00:00:00 [com.nanolaba.logging.util.AnonymousSource$1] anonymous" + System.lineSeparator(), getOutAndClear());
        LOG.info(AnonymousSource.LOCAL, "local");
        assertEquals("INFO 01.01.2010 00:00:00 [com.nanolaba.logging.util.AnonymousSource$1Local] local" + System.lineSeparator(), getOutAndClear());
        LOG.info(InfoObject.class, "canonical");
        assertEquals("INFO 01.01.2010 00:00:00 [com.nanolaba.logging.util.InfoObject] canonical" + System.lineSeparator(), getOutAndClear());
    }

    @Test
    public void testDateFormatIsGuardedByItsMonitor() {
        // SimpleDateFormat is not thread-safe; the logger must hold the format's monitor while formatting
        AtomicBoolean lockHeld = new AtomicBoolean();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy") {
            @Override
            public StringBuffer format(Date date, StringBuffer toAppendTo, FieldPosition pos) {
                lockHeld.set(Thread.holdsLock(this));
                return super.format(date, toAppendTo, pos);
            }
        };
        LOG.init(new ConsoleLogger().setDateFormat(dateFormat));

        LOG.info(InfoObject.class, "");
        assertTrue(lockHeld.get(), "ConsoleLogger must synchronize on the shared SimpleDateFormat");
    }

    @Test
    public void testLevelDisabling() {
        ConsoleLogger logger = createLogger();
        LOG.init(logger);

        LOG.trace(InfoObject.class, "");
        assertEquals("TRACE 01.01.2010 00:00:00 [InfoObject]" + System.lineSeparator(), getOutAndClear());

        logger.setTraceEnabled(false);

        LOG.trace(InfoObject.class, "");
        assertEquals("", getOutAndClear());

        LOG.debug(InfoObject.class, "");
        assertEquals("DEBUG 01.01.2010 00:00:00 [InfoObject]" + System.lineSeparator(), getOutAndClear());

        logger.setDebugEnabled(false);

        LOG.debug(InfoObject.class, "");
        assertEquals("", getOutAndClear());

        LOG.info(InfoObject.class, "");
        assertEquals("INFO 01.01.2010 00:00:00 [InfoObject]" + System.lineSeparator(), getOutAndClear());

        logger.setInfoEnabled(false);

        LOG.info(InfoObject.class, "");
        assertEquals("", getOutAndClear());

        LOG.warn(InfoObject.class, "");
        assertEquals("WARN 01.01.2010 00:00:00 [InfoObject]" + System.lineSeparator(), getOutAndClear());

        logger.setWarnEnabled(false);

        LOG.warn(InfoObject.class, "");
        assertEquals("", getOutAndClear());

        LOG.error(InfoObject.class, "");
        assertEquals("ERROR 01.01.2010 00:00:00 [InfoObject]" + System.lineSeparator(), getErrAndClear());

        logger.setErrorEnabled(false);

        LOG.error(InfoObject.class, "");
        assertEquals("", getErrAndClear());
    }

    protected ConsoleLogger createLogger() {
        ConsoleLogger logger = new ConsoleLogger() {
            @Override
            protected Date getCurrentDate() {
                try {
                    return new SimpleDateFormat("dd.MM.yyyy").parse("01.01.2010");
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        return logger
                .setShowLevel(true)
                .setShowDate(true)
                .setShowSource(true)
                .setShowSourceFullName(false)
                .setDateFormat(new SimpleDateFormat("dd.MM.yyyy HH:mm:ss"))
                .setTraceEnabled(true)
                .setDebugEnabled(true)
                .setInfoEnabled(true)
                .setWarnEnabled(true)
                .setErrorEnabled(true);
    }
}