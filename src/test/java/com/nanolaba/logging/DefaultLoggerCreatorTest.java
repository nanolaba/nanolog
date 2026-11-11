package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_LOGGER_CLASS;
import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_LOGGER_CONFIG_FILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultLoggerCreatorTest extends AbstractLoggerTest {

    private final Map<String, String> environment = new HashMap<>();

    @AfterEach
    public void clearProperties() {
        DefaultLoggerCreator.environment = System::getenv;
        System.clearProperty(PROPERTY_LOGGER_CONFIG_FILE);
        System.clearProperty(PROPERTY_LOGGER_CLASS);
        System.clearProperty("other.property");
    }

    @Test
    public void onlyNanologKeysAreCopiedFromTheFileTest() {
        System.setProperty(PROPERTY_LOGGER_CONFIG_FILE, "mixed.properties");
        LOG.init();

        assertEquals(Slf4jLogger.class, LOG.getLogger().getClass());
        assertNull(System.getProperty("other.property"), "keys outside the nanolog.* namespace must not leak into system properties");
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
        assertTrue(getErrAndClear().contains("[NANOLOG] Property \"nanolog.config\" has been defined, but the file \"unexisted.properties\" cannot be found"));
    }

    @Test
    public void nullContextClassLoaderTest() {
        ClassLoader original = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            System.setProperty(PROPERTY_LOGGER_CONFIG_FILE, "test.properties");
            LOG.init();
            assertEquals(Slf4jLogger.class, LOG.getLogger().getClass());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
        }
    }

    @Test
    public void contextClassLoaderIsPreferredTest() throws IOException {
        Path dir = Files.createTempDirectory("nanolog");
        Path file = dir.resolve("custom-nanolog.properties");
        Files.write(file, (PROPERTY_LOGGER_CLASS + "=" + Slf4jLogger.class.getName()).getBytes(StandardCharsets.ISO_8859_1));

        ClassLoader original = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader isolated = new URLClassLoader(new URL[]{dir.toUri().toURL()}, null)) {
            Thread.currentThread().setContextClassLoader(isolated);
            System.setProperty(PROPERTY_LOGGER_CONFIG_FILE, "custom-nanolog.properties");
            LOG.init();
            assertEquals(Slf4jLogger.class, LOG.getLogger().getClass());
        } finally {
            Thread.currentThread().setContextClassLoader(original);
            Files.deleteIfExists(file);
            Files.deleteIfExists(dir);
        }
    }

    @Test
    public void environmentNamesTest() {
        assertEquals("NANOLOG_LOGGER", DefaultLoggerCreator.toEnvironmentName("nanolog.logger"));
        assertEquals("NANOLOG_CONSOLE_SHOWDATE", DefaultLoggerCreator.toEnvironmentName("nanolog.console.showDate"));
    }

    @Test
    public void loggerFromEnvironmentTest() {
        DefaultLoggerCreator.environment = environment::get;
        environment.put("NANOLOG_LOGGER", Slf4jLogger.class.getName());
        LOG.init();
        assertEquals(Slf4jLogger.class, LOG.getLogger().getClass());

        // a system property beats the environment
        System.setProperty(PROPERTY_LOGGER_CLASS, ConsoleLogger.class.getName());
        LOG.init();
        assertEquals(ConsoleLogger.class, LOG.getLogger().getClass());
    }

    @Test
    public void configFileFromEnvironmentTest() {
        DefaultLoggerCreator.environment = environment::get;
        environment.put("NANOLOG_CONFIG", "test.properties");
        LOG.init();
        assertEquals(Slf4jLogger.class, LOG.getLogger().getClass());
    }

    @Test
    public void environmentBeatsTheFileTest() {
        DefaultLoggerCreator.environment = environment::get;
        environment.put("NANOLOG_LOGGER", ConsoleLogger.class.getName());
        System.setProperty(PROPERTY_LOGGER_CONFIG_FILE, "test.properties");
        LOG.init();

        assertEquals(ConsoleLogger.class, LOG.getLogger().getClass());
        assertNull(System.getProperty(PROPERTY_LOGGER_CLASS), "a key set in the environment is not copied from the file");
    }

    @Test
    public void getPropertyTest() {
        DefaultLoggerCreator.environment = environment::get;
        assertNull(DefaultLoggerCreator.getProperty("nanolog.test"));

        environment.put("NANOLOG_TEST", "from the environment");
        assertEquals("from the environment", DefaultLoggerCreator.getProperty("nanolog.test"));

        System.setProperty("nanolog.test", "from a system property");
        try {
            assertEquals("from a system property", DefaultLoggerCreator.getProperty("nanolog.test"));
        } finally {
            System.clearProperty("nanolog.test");
        }
    }
}
