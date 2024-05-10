package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    public void testGetCurrentDate() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy");
        String currentYear = dateFormat.format(new Date());
        ConsoleLogger logger = new ConsoleLogger().setDateFormat(dateFormat);
        LOG.init(logger);

        LOG.error(InfoObject.class, "");
        assertEquals("ERROR " + currentYear + " [InfoObject]" + System.lineSeparator(), getErrAndClear());
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