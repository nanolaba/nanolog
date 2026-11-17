package com.nanolaba.logging;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Function;

/**
 * Chooses the logger that {@link LOG} starts with, and reads the settings of Nanolog.
 * <p>
 * Settings are looked up in system properties first, then in environment variables (upper case, dots replaced by
 * underscores: {@code nanolog.logger} is {@code NANOLOG_LOGGER}), then in {@code nanolog.properties} on the classpath.
 * The file contributes only keys with the {@code nanolog.} prefix, and only those not set otherwise.
 */
public class DefaultLoggerCreator {

    /**
     * The prefix of every Nanolog setting; other keys of {@code nanolog.properties} are ignored.
     */
    public static final String PROPERTY_PREFIX = "nanolog.";
    /**
     * The class name of the {@link ILogger} to start with; it needs a public no-argument constructor.
     */
    public static final String PROPERTY_LOGGER_CLASS = PROPERTY_PREFIX + "logger";
    /**
     * The classpath resource with the settings, {@value #DEFAULT_CONFIGURATION_FILE} by default.
     */
    public static final String PROPERTY_LOGGER_CONFIG_FILE = PROPERTY_PREFIX + "config";
    /**
     * The lowest level {@link ConsoleLogger} logs: {@code TRACE}, {@code DEBUG}, {@code INFO}, {@code WARN}, {@code ERROR} or {@code OFF}.
     */
    public static final String PROPERTY_LEVEL = PROPERTY_PREFIX + "level";
    /**
     * Levels for packages or classes in one value: {@code com.acme.payments=DEBUG,com.acme.db=TRACE}.
     */
    public static final String PROPERTY_LEVELS = PROPERTY_PREFIX + "levels";
    /**
     * {@code true} reports calls whose {@code {}} placeholders and arguments do not match.
     */
    public static final String PROPERTY_STRICT = PROPERTY_PREFIX + "strict";
    /**
     * The source directories {@link LOG#dbg(Object)} reads the text of its arguments from, comma-separated. Without
     * it they are derived from the location of the calling class ({@code target/classes} leads to
     * {@code src/main/java}); an empty value turns the reading off.
     */
    public static final String PROPERTY_SOURCES = PROPERTY_PREFIX + "sources";
    /**
     * {@code auto} renders {@code {}} arguments whose class has no {@code toString()} of its own field by field, as
     * {@link LOG#dbg(Object)} does, instead of {@code Order@2f92e0f4}; {@code off}, the default, leaves them to
     * {@code toString()}.
     */
    public static final String PROPERTY_DUMP = PROPERTY_PREFIX + "dump";
    /**
     * What {@link LOG#dbg(Object)} calls do: {@code on}, the default, prints them; {@code off} prints nothing, for
     * production; {@code fail} prints them and reports each call as a diagnostic, which fails
     * {@link MemoryLogger#assertNoDiagnostics()}.
     */
    public static final String PROPERTY_DBG = PROPERTY_PREFIX + "dbg";

    /**
     * The resource read when {@value #PROPERTY_LOGGER_CONFIG_FILE} is not set.
     */
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
     * Reads {@value #PROPERTY_DUMP}: {@code true} for {@code auto}, {@code false} for {@code off} or no value, ignoring
     * case and surrounding spaces. An invalid value prints a diagnostic and gives {@code false}.
     */
    static boolean readDumpAuto() {
        String value = getProperty(PROPERTY_DUMP);
        if (value == null || "off".equalsIgnoreCase(value.trim())) {
            return false;
        } else if ("auto".equalsIgnoreCase(value.trim())) {
            return true;
        }
        printDiagnostic("Invalid value \"" + value + "\" of \"" + PROPERTY_DUMP + "\": expected auto or off");
        return false;
    }

    /**
     * Reads {@value #PROPERTY_DBG}: {@code on}, {@code off} or {@code fail}, ignoring case and surrounding spaces. No
     * value gives {@code on}; an invalid one prints a diagnostic and gives {@code on} as well.
     */
    static Dbg.Mode readDbgMode() {
        String value = getProperty(PROPERTY_DBG);
        if (value != null) {
            for (Dbg.Mode mode : Dbg.Mode.values()) {
                if (mode.name().equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }
            printDiagnostic("Invalid value \"" + value + "\" of \"" + PROPERTY_DBG + "\": expected on, off or fail");
        }
        return Dbg.Mode.ON;
    }

    /**
     * Prints a {@code [NANOLOG]} diagnostic to {@code System.err}. Configuration mistakes must not break logging.
     * <p>
     * A diagnostic echoes values it did not choose: settings, a level passed to {@code LOG.setLevel} from a request,
     * an exception message, a template. Control characters and the Unicode line separators are printed as
     * {@code \}{@code u000a} escapes, so a diagnostic is always one line and no value can forge a line of its own.
     */
    static void printDiagnostic(String message) {
        System.err.println("[NANOLOG] " + escapeControlCharacters(message));
    }

    private static String escapeControlCharacters(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c) || c == ' ' || c == ' ') {
                result.append(String.format("\\u%04x", (int) c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Loads {@code nanolog.properties}, then creates the logger named by {@value #PROPERTY_LOGGER_CLASS}, or a
     * {@link ConsoleLogger}. A class that cannot be created prints a {@code [NANOLOG]} diagnostic and falls back to
     * {@code ConsoleLogger}.
     *
     * @return the new logger
     */
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
