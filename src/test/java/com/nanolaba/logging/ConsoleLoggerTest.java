package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.FieldPosition;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    public void testLocationIsOffByDefault() {
        assertFalse(new ConsoleLogger().isShowLocation());

        LOG.init(createLogger());
        LOG.info("plain");
        assertEquals("INFO 01.01.2010 00:00:00 [ConsoleLoggerTest] plain" + System.lineSeparator(), getOutAndClear());
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