package com.nanolaba.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultLoggerCreatorTest extends AbstractLoggerTest {

    @Test
    public void systemPropertyTest() {
        System.setProperty(DefaultLoggerCreator.LOGGER_CLASS_PROPERTY, Slf4jLogger.class.getName());
        LOG.init();
        assertEquals(LOG.getLogger().getClass(), Slf4jLogger.class);

        System.clearProperty(DefaultLoggerCreator.LOGGER_CLASS_PROPERTY);
        LOG.init();
        assertEquals(LOG.getLogger().getClass(), ConsoleLogger.class);
    }

    @Test
    public void systemPropertyFailTest() {
        System.setProperty(DefaultLoggerCreator.LOGGER_CLASS_PROPERTY, "Unexisted class");
        LOG.init();
        assertEquals(LOG.getLogger().getClass(), ConsoleLogger.class);
        assertTrue(getErrAndClear().contains("java.lang.ClassNotFoundException: Unexisted class"));
    }
}