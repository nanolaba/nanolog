package com.nanolaba.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class DefaultLoggerCreator {

    public static final String PROPERTY_LOGGER_CLASS = "nanolog.logger";
    public static final String PROPERTY_LOGGER_CONFIG_FILE = "nanolog.config";

    public static final String DEFAULT_CONFIGURATION_FILE = "nanolog.properties";

    private DefaultLoggerCreator() {
    }

    public static ILogger createDefaultLogger() {

        loadPropertiesFromFile();

        String logger = System.getProperty(PROPERTY_LOGGER_CLASS);
        if (logger != null && !logger.isEmpty()) {
            try {
                Class<?> loggerClass = Class.forName(logger);
                return (ILogger) loggerClass.getConstructor().newInstance();
            } catch (Throwable e) {
                System.err.printf("[NANOLOG] Property \"%s\" has been defined, but an object of class \"%s\" cannot be created %n", PROPERTY_LOGGER_CLASS, logger);
                e.printStackTrace(System.err);
            }
        }

        return new ConsoleLogger();
    }

    private static void loadPropertiesFromFile() {
        String config = System.getProperty(PROPERTY_LOGGER_CONFIG_FILE, DEFAULT_CONFIGURATION_FILE);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader.getResource(config) != null) {
            try (InputStream stream = classLoader.getResourceAsStream(config)) {
                Properties props = new Properties();
                props.load(stream);
                props.forEach((name, value) -> {
                    if (System.getProperty((String) name) == null) {
                        System.setProperty((String) name, (String) value);
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (!config.equals(DEFAULT_CONFIGURATION_FILE)) {
            System.err.printf("[NANOLOG] Property \"%s\" has been defined, but the file \"%s\" cannot be found %n", DEFAULT_CONFIGURATION_FILE, config);
        }
    }
}
