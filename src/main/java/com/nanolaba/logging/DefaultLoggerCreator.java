package com.nanolaba.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;

public class DefaultLoggerCreator {

    public static final String PROPERTY_PREFIX = "nanolog.";
    public static final String PROPERTY_LOGGER_CLASS = PROPERTY_PREFIX + "logger";
    public static final String PROPERTY_LOGGER_CONFIG_FILE = PROPERTY_PREFIX + "config";
    public static final String PROPERTY_LEVEL = PROPERTY_PREFIX + "level";
    public static final String PROPERTY_LEVELS = PROPERTY_PREFIX + "levels";
    public static final String PROPERTY_STRICT = PROPERTY_PREFIX + "strict";

    public static final String DEFAULT_CONFIGURATION_FILE = "nanolog.properties";

    /**
     * The source of environment variables; replaced in tests, since a JVM cannot change its own environment.
     */
    static Function<String, String> environment = System::getenv;

    private DefaultLoggerCreator() {
    }

    /**
     * Looks a setting up in the system properties first, then in the environment variable of the same name.
     * Keys from {@code nanolog.properties} reach this method as system properties, but only those the environment
     * does not define, so the precedence is: {@code -D}, environment, file.
     */
    static String getProperty(String name) {
        String value = System.getProperty(name);
        return value != null ? value : environment.apply(toEnvironmentName(name));
    }

    /**
     * {@code nanolog.console.showDate} becomes {@code NANOLOG_CONSOLE_SHOWDATE}.
     */
    static String toEnvironmentName(String property) {
        return property.toUpperCase(Locale.ROOT).replace('.', '_');
    }

    /**
     * Reads a {@code true} / {@code false} setting, ignoring case and surrounding spaces. A missing setting gives the
     * default; an invalid one prints a diagnostic and gives the default as well.
     */
    static boolean readBoolean(String property, boolean defaultValue) {
        String value = getProperty(property);
        if (value == null) {
            return defaultValue;
        } else if ("true".equalsIgnoreCase(value.trim())) {
            return true;
        } else if ("false".equalsIgnoreCase(value.trim())) {
            return false;
        }
        printDiagnostic("Invalid value \"" + value + "\" of \"" + property + "\": expected true or false");
        return defaultValue;
    }

    /**
     * Prints a {@code [NANOLOG]} diagnostic to {@code System.err}. Configuration mistakes must not break logging.
     */
    static void printDiagnostic(String message) {
        System.err.println("[NANOLOG] " + message);
    }

    public static ILogger createDefaultLogger() {

        loadPropertiesFromFile();

        String logger = getProperty(PROPERTY_LOGGER_CLASS);
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
        String config = getProperty(PROPERTY_LOGGER_CONFIG_FILE);
        if (config == null) {
            config = DEFAULT_CONFIGURATION_FILE;
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = DefaultLoggerCreator.class.getClassLoader();
        }
        if (classLoader.getResource(config) != null) {
            try (InputStream stream = classLoader.getResourceAsStream(config)) {
                Properties props = new Properties();
                props.load(stream);
                props.forEach((name, value) -> {
                    String key = (String) name;
                    // only our own namespace: a config file must not be able to alter arbitrary JVM-wide settings
                    // a key already set through -D or the environment wins over the file
                    if (key.startsWith(PROPERTY_PREFIX) && getProperty(key) == null) {
                        System.setProperty(key, (String) value);
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (!config.equals(DEFAULT_CONFIGURATION_FILE)) {
            System.err.printf("[NANOLOG] Property \"%s\" has been defined, but the file \"%s\" cannot be found %n", PROPERTY_LOGGER_CONFIG_FILE, config);
        }
    }
}
