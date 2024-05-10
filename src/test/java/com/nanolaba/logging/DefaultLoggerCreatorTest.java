package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_LOGGER_CLASS;
import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_LOGGER_CONFIG_FILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultLoggerCreatorTest extends AbstractLoggerTest {

    @AfterEach
    public void clearProperties() {
        System.clearProperty(PROPERTY_LOGGER_CONFIG_FILE);
        System.clearProperty(PROPERTY_LOGGER_CLASS);
    }

    @Test
    public void systemPropertyTest() {
        System.setProperty(PROPERTY_LOGGER_CLASS, Slf4jLogger.class.getName());
        LOG.init();
        assertEquals(LOG.getLogger().getClass(), Slf4jLogger.class);

        System.clearProperty(PROPERTY_LOGGER_CLASS);
        LOG.init();
        assertEquals(LOG.getLogger().getClass(), ConsoleLogger.class);
    }

    @Test
    public void systemPropertyFailTest() {
        System.setProperty(PROPERTY_LOGGER_CLASS, "Unexisted class");
        LOG.init();

        assertEquals(LOG.getLogger().getClass(), ConsoleLogger.class);
        assertTrue(getErrAndClear().contains("java.lang.ClassNotFoundException: Unexisted class"));
    }

    @Test
    public void filePropertyTest() {
        System.setProperty(PROPERTY_LOGGER_CONFIG_FILE, "test.properties");
        LOG.init();
        assertEquals(Slf4jLogger.class, LOG.getLogger().getClass());

        System.clearProperty(PROPERTY_LOGGER_CONFIG_FILE);
        System.clearProperty(PROPERTY_LOGGER_CLASS);
        LOG.init();
        assertEquals(ConsoleLogger.class, LOG.getLogger().getClass());
    }

    @Test
    public void existingPropertyIsNotOverwrittenByTheFileTest() {
        System.setProperty(PROPERTY_LOGGER_CLASS, ConsoleLogger.class.getName());
        System.setProperty(PROPERTY_LOGGER_CONFIG_FILE, "test.properties");
        LOG.init();
        assertEquals(ConsoleLogger.class, LOG.getLogger().getClass());

        System.clearProperty(PROPERTY_LOGGER_CONFIG_FILE);
        System.clearProperty(PROPERTY_LOGGER_CLASS);
        LOG.init();
        assertEquals(ConsoleLogger.class, LOG.getLogger().getClass());
    }

    @Test
    public void unexistedFilePropertyFailTest() {
        System.setProperty(PROPERTY_LOGGER_CONFIG_FILE, "unexisted.properties");
        LOG.init();

        assertEquals(LOG.getLogger().getClass(), ConsoleLogger.class);
        assertTrue(getErrAndClear().contains("[NANOLOG] Property \"nanolog.properties\" has been defined, but the file \"unexisted.properties\" cannot be found"));
    }
}