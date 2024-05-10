package com.nanolaba.logging;

import com.nanolaba.logging.util.InfoObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Slf4jLoggerTest extends AbstractLoggerTest {

    @BeforeAll
    public static void init() {
        System.setProperty(org.slf4j.simple.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
    }

    @Test
    public void testInfoLevel() {
        Slf4jLogger logger = createLogger();

        LOG.init(logger);

        LOG.info(InfoObject.class, "");
        assertEquals("[main] INFO com.nanolaba.logging.util.InfoObject - " + System.lineSeparator(), getErrAndClear());

        LOG.info(InfoObject.class, "some message {} next {}", "abc", "123");
        assertEquals("[main] INFO com.nanolaba.logging.util.InfoObject - some message abc next 123" + System.lineSeparator(), getErrAndClear());

        LOG.info(InfoObject.class, new Throwable("test"));
        assertTrue(getErrAndClear().startsWith("[main] INFO com.nanolaba.logging.util.InfoObject - null" + System.lineSeparator() +
                                               "java.lang.Throwable: test" + System.lineSeparator() +
                                               "\tat com.nanolaba.logging.Slf4jLoggerTest.testInfoLevel"));


        for (LogEntry.LogEntryLevel level : LogEntry.LogEntryLevel.values()) {
            LOG.log(level, InfoObject.class, null, () -> "some message {} next {}", "abc", "123");
            assertEquals("[main] " + level + " com.nanolaba.logging.util.InfoObject - some message abc next 123" + System.lineSeparator(), getErrAndClear());
        }
    }

    protected Slf4jLogger createLogger() {
        return new Slf4jLogger();
    }
}