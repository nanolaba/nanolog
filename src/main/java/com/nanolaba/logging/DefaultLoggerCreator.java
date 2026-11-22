package com.nanolaba.logging;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Chooses the logger that {@link LOG} starts with, and reads the settings of Nanolog.
 * <p>
 * Settings are looked up in system properties first, then in environment variables (upper case, dots replaced by
 * underscores: {@code nanolog.logger} is {@code NANOLOG_LOGGER}), then in {@code nanolog.properties} on the classpath.
 * The file contributes only keys with the {@code nanolog.} prefix, and only those not set otherwise. Keys that Nanolog
 * does not know are reported with a {@code [NANOLOG]} line when the default logger is created.
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
     * {@code true} keeps the logger of the configuration: {@code LOG.init} calls are ignored, with a diagnostic, as
     * after {@code LOG.lock()}.
     */
    public static final String PROPERTY_LOGGER_LOCK = PROPERTY_LOGGER_CLASS + ".lock";
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
     * {@code on} also writes every entry of the run to {@code latest.log} in {@value #PROPERTY_RUN_DIR}, for a coding
     * agent to read back instead of running again; {@code off}, the default, writes no file.
     */
    public static final String PROPERTY_RUN = PROPERTY_PREFIX + "run";
    /**
     * Agent mode, for a program run by a coding agent: {@code auto}, the default, turns it on when the environment
     * names an agent ({@code AI_AGENT}, {@code CLAUDECODE} and others); {@code on} and {@code off} decide instead. It
     * keeps the console short ({@code WARN} and above, no date, short stack traces), prints every
     * {@link LOG#dbg(Object)} whatever the levels, and switches {@value #PROPERTY_RUN} on.
     */
    public static final String PROPERTY_AGENT = PROPERTY_PREFIX + "agent";
    /**
     * The run summary at the end of the run, the part of the output that survives when a coding agent keeps only the
     * end: {@code on}, the default in agent mode, {@code off}, the default otherwise, or {@code errors}, printed only
     * after a {@code WARN} or {@code ERROR}.
     */
    public static final String PROPERTY_SUMMARY = PROPERTY_PREFIX + "summary";
    /**
     * Which logs of tests {@link NanologExtension} prints: {@code failed}, the default, only those of failing tests;
     * {@code all} every log, as it is written; {@code none} no log.
     */
    public static final String PROPERTY_JUNIT_OUTPUT = PROPERTY_PREFIX + "junit.output";
    /**
     * How often a {@code [NANOLOG]} diagnostic is printed to {@code System.err}: {@code once}, the default, each
     * distinct one once per JVM; {@code all} every time; {@code off} never. {@link ILogger#onDiagnostic} gets the
     * findings of strict mode and of {@code nanolog.dbg=fail} whatever the setting.
     */
    public static final String PROPERTY_DIAGNOSTICS = PROPERTY_PREFIX + "diagnostics";
    /**
     * The breadcrumbs: entries at this level and below ({@code TRACE}, {@code DEBUG}, {@code INFO} or {@code WARN})
     * are held back and written only when an {@code ERROR} follows; {@code off}, the default, holds nothing back.
     */
    public static final String PROPERTY_BUFFER = PROPERTY_PREFIX + "buffer";
    /**
     * How many entries the buffer of {@value #PROPERTY_BUFFER} keeps per thread or unit, the newest; 200 by default.
     */
    public static final String PROPERTY_BUFFER_SIZE = PROPERTY_BUFFER + ".size";
    /**
     * The share of the units of {@code LOG.unit(...)} that succeed fast which are written, from {@code 0} to
     * {@code 1}; {@code 1}, every unit, by default. Failed and slow units are always written.
     */
    public static final String PROPERTY_UNIT_SAMPLE = PROPERTY_PREFIX + "unit.sample";
    /**
     * The duration in milliseconds from which a unit of {@code LOG.unit(...)} is slow and always written, whatever
     * {@value #PROPERTY_UNIT_SAMPLE} says; not set by default.
     */
    public static final String PROPERTY_UNIT_SLOW_MS = PROPERTY_PREFIX + "unit.slowMs";
    /**
     * How much of a payload of {@code LOG.content(...)} is written: {@code hide}, the default, only its length and
     * hash; {@code truncate:<characters>} its start; {@code show} all of it. {@code nanolog.content.<package or class>}
     * sets it for a package or a class.
     */
    public static final String PROPERTY_CONTENT = PROPERTY_PREFIX + "content";
    /**
     * The words that name a secret, comma-separated: a field of {@code LOG.kv(...)} or a context value whose key, or a
     * word of it, is one of them is written as {@code ***}. Without it {@code password}, {@code passwd},
     * {@code secret}, {@code token}, {@code apikey}, {@code credential} and {@code authorization}; empty turns masking
     * off.
     */
    public static final String PROPERTY_REDACT = PROPERTY_PREFIX + "redact";
    /**
     * Classes and packages whose frames caller detection skips like those of {@code LOG}, comma-separated: the logging
     * helpers of an application, so an entry logged through one belongs to the code that called it.
     */
    public static final String PROPERTY_CALLER_SKIP = PROPERTY_PREFIX + "caller.skip";
    /**
     * Levels per tag of {@code LOG.tag(...)}, comma-separated: {@code audit=WARN,sql=DEBUG}. A rule for the tag of an
     * entry decides over the levels of its class in {@link ConsoleLogger}.
     */
    public static final String PROPERTY_TAGS = PROPERTY_PREFIX + "tags";
    /**
     * {@code true} turns the lines printed to {@code System.out} and {@code System.err} into entries, as
     * {@code LOG.captureSystemStreams()} does.
     */
    public static final String PROPERTY_CAPTURE_SYSTEM_STREAMS = PROPERTY_PREFIX + "captureSystemStreams";
    /**
     * Context providers, comma-separated class names: each class implements {@code Supplier<Map<String, ?>>}, has a
     * public no-argument constructor, and adds its keys to every entry, see {@link LOG#addContextProvider}.
     */
    public static final String PROPERTY_CONTEXT_PROVIDER = PROPERTY_PREFIX + "context.provider";
    /**
     * The directory of the run log, relative to the working directory; without it {@code target/nanolog} in a Maven
     * module, {@code build/nanolog} in a Gradle one and {@code .nanolog} otherwise.
     */
    public static final String PROPERTY_RUN_DIR = PROPERTY_RUN + ".dir";
    /**
     * The format of the run log: {@code text}, the default, in the layout of {@link ConsoleLogger} with the thread and
     * the call site, or {@code json}, the lines of {@link JsonLogger}.
     */
    public static final String PROPERTY_RUN_FORMAT = PROPERTY_RUN + ".format";
    /**
     * How many runs the run log keeps, this one included; 10 by default.
     */
    public static final String PROPERTY_RUN_KEEP = PROPERTY_RUN + ".keep";
    /**
     * The size in megabytes at which a file of the run log stops, with a note; 100 by default.
     */
    public static final String PROPERTY_RUN_MAX_SIZE = PROPERTY_RUN + ".maxSize";

    /**
     * The resource read when {@value #PROPERTY_LOGGER_CONFIG_FILE} is not set.
     */
    public static final String DEFAULT_CONFIGURATION_FILE = "nanolog.properties";

    /**
     * The source of environment variables; replaced in tests, since a JVM cannot change its own environment.
     */
    static Function<String, String> environment = System::getenv;

    /**
     * The names of the environment variables, for the check of unknown settings; values are still read through
     * {@link #environment}, and a name it does not know is skipped. Replaced in tests together with it.
     */
    static Supplier<Set<String>> environmentNames = () -> System.getenv().keySet();

    /**
     * Set on a thread that creates a second logger from the same settings, whose invalid values the first one has
     * reported already.
     */
    private static final ThreadLocal<Boolean> QUIET = new ThreadLocal<>();

    /**
     * The values of {@value #PROPERTY_DIAGNOSTICS}.
     */
    enum Diagnostics {
        ONCE, ALL, OFF
    }

    /**
     * Read when the default logger is created; {@code volatile}, since any thread may print a diagnostic.
     */
    static volatile Diagnostics diagnostics = Diagnostics.ONCE;

    /**
     * The diagnostics printed so far, for {@code once}.
     */
    private static final Set<String> PRINTED = ConcurrentHashMap.newKeySet();

    /**
     * The most diagnostics remembered; past it, a new one is printed without being remembered. Tests lower it.
     */
    static int maxRemembered = 10_000;

    /**
     * The keys copied from {@code nanolog.properties} into the system properties, with their value and the location of
     * the file, to name where an invalid value came from.
     */
    private static final Map<String, String[]> FROM_FILE = new ConcurrentHashMap<>();

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
        printInvalid(property, value, "expected true or false");
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
        printInvalid(PROPERTY_DUMP, value, "expected auto or off");
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
            printInvalid(PROPERTY_DBG, value, "expected on, off or fail");
        }
        return Dbg.Mode.ON;
    }

    /**
     * Reads {@value #PROPERTY_SUMMARY}: {@code on} by default in agent mode, {@code off} otherwise. An invalid value
     * prints a diagnostic and gives the default.
     */
    static Summary.Mode readSummaryMode(boolean agent) {
        String mode = agent ? readChoice(PROPERTY_SUMMARY, "on", "off", "errors")
                            : readChoice(PROPERTY_SUMMARY, "off", "on", "errors");
        return Summary.Mode.valueOf(mode.toUpperCase(Locale.ROOT));
    }

    /**
     * Reads a setting with a fixed set of values, ignoring case and surrounding spaces. No value gives the first one;
     * an invalid one prints a diagnostic and gives the first one as well.
     *
     * @param choices the values, the default first
     * @return the value as it is spelled in {@code choices}
     */
    static String readChoice(String property, String... choices) {
        String value = getProperty(property);
        if (value != null) {
            for (String choice : choices) {
                if (choice.equalsIgnoreCase(value.trim())) {
                    return choice;
                }
            }
            String expected = String.join(", ", Arrays.asList(choices).subList(0, choices.length - 1)) + " or " +
                              choices[choices.length - 1];
            printInvalid(property, value, "expected " + expected);
        }
        return choices[0];
    }

    /**
     * Reads a positive whole number, ignoring surrounding spaces. No value gives the default; an invalid one prints a
     * diagnostic and gives the default as well.
     */
    static int readPositive(String property, int defaultValue) {
        String value = getProperty(property);
        if (value != null) {
            try {
                int number = Integer.parseInt(value.trim());
                if (number > 0) {
                    return number;
                }
            } catch (NumberFormatException e) {
                // reported below
            }
            printInvalid(property, value, "expected a positive number");
        }
        return defaultValue;
    }

    /**
     * Runs an action with the diagnostics of this thread muted: for a second logger created from the same settings,
     * whose invalid values the first one has reported already.
     */
    static <T> T quietly(Supplier<T> action) {
        QUIET.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            QUIET.remove();
        }
    }

    /**
     * Prints a {@code [NANOLOG]} diagnostic to {@code System.err}. Configuration mistakes must not break logging.
     * <p>
     * A diagnostic echoes values it did not choose: settings, a level passed to {@code LOG.setLevel} from a request,
     * an exception message, a template. Control characters, the Unicode line separators and the format characters
     * are printed as {@code \}{@code u000a} escapes, so a diagnostic is always one line, no value can forge a line of
     * its own, and a bidi override cannot make the line read in another order.
     */
    static boolean printDiagnostic(String message) {
        if (QUIET.get() == null && isPrinted(message)) {
            SystemStreams.err().println("[NANOLOG] " + escapeControlCharacters(message));
            return true;
        }
        return false;
    }

    /**
     * Whether {@value #PROPERTY_DIAGNOSTICS} lets a diagnostic through: under {@code once}, if it was not printed
     * before.
     */
    private static boolean isPrinted(String message) {
        Diagnostics mode = diagnostics;
        if (mode == Diagnostics.OFF) {
            return false;
        } else if (mode == Diagnostics.ALL) {
            return true;
        }
        return PRINTED.size() < maxRemembered ? PRINTED.add(message) : !PRINTED.contains(message);
    }

    /**
     * Prints a {@code [NANOLOG]} line that reports what Nanolog does, not a problem: the run summary, the line of agent
     * mode, the run log. {@value #PROPERTY_DIAGNOSTICS} does not apply; those features have switches of their own.
     */
    static void printNotice(String message) {
        SystemStreams.err().println("[NANOLOG] " + escapeControlCharacters(message));
    }

    /**
     * Prints {@code [NANOLOG]} lines of a notice with one {@code println}: Surefire relays it as one piece, so the lines
     * of two forks that print at the same moment do not mix (#93).
     */
    static void printNotices(List<String> messages) {
        StringBuilder block = new StringBuilder();
        for (String message : messages) {
            if (block.length() > 0) {
                block.append(System.lineSeparator());
            }
            block.append("[NANOLOG] ").append(escapeControlCharacters(message));
        }
        SystemStreams.err().println(block.toString());
    }

    /**
     * Prints that a setting has an invalid value, and where the value came from.
     *
     * @param expected what was expected, such as {@code expected true or false}
     */
    static void printInvalid(String property, String value, String expected) {
        printDiagnostic("Invalid value \"" + value + "\" of \"" + property + "\": " + expected + "; " + origin(property));
    }

    /**
     * Where the value of a setting came from: {@code set as a system property}, {@code set by the environment variable
     * NANOLOG_LEVEL} or {@code set in file:/.../nanolog.properties}.
     */
    static String origin(String property) {
        String value = System.getProperty(property);
        if (value == null) {
            return "set by the environment variable " + toEnvironmentName(property);
        }
        String[] file = FROM_FILE.get(property);
        return file != null && file[0].equals(value) ? "set in " + file[1] : "set as a system property";
    }

    /**
     * Forgets the diagnostics printed so far, so {@code once} prints them again; for tests.
     */
    static void forgetDiagnostics() {
        PRINTED.clear();
    }

    /**
     * Reads {@value #PROPERTY_DIAGNOSTICS}.
     */
    private static void readDiagnostics() {
        diagnostics = Diagnostics.valueOf(readChoice(PROPERTY_DIAGNOSTICS, "once", "all", "off").toUpperCase(Locale.ROOT));
    }

    /**
     * Control characters, U+2028/U+2029 and {@link #isFormat format characters} as {@code \}{@code u000a} escapes; a
     * character outside the BMP as its two UTF-16 units, as Java and JSON write it.
     */
    private static String escapeControlCharacters(String text) {
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int c = text.codePointAt(i);
            int next = i + Character.charCount(c);
            if (Character.isISOControl(c) || c == 0x2028 || c == 0x2029 || isFormat(c)) {
                for (int unit = i; unit < next; unit++) {
                    result.append(String.format("\\u%04x", (int) text.charAt(unit)));
                }
            } else {
                result.append(text, i, next);
            }
            i = next;
        }
        return result.toString();
    }

    /**
     * The format characters of Unicode 16, the general category Cf, as the first and the last code point of each run;
     * Java 25 has the same. They are invisible or change what follows: a bidi override such as U+202E makes a terminal
     * show a line in another order than it holds. {@code Character.getType} would not do: Java 8 knows Unicode 6.2,
     * without the bidi isolates U+2066 to U+2069, and the output would depend on the Java that runs it.
     */
    private static final int[] FORMAT = {
            0x00AD, 0x00AD, 0x0600, 0x0605, 0x061C, 0x061C, 0x06DD, 0x06DD, 0x070F, 0x070F, 0x0890, 0x0891,
            0x08E2, 0x08E2, 0x180E, 0x180E, 0x200B, 0x200F, 0x202A, 0x202E, 0x2060, 0x2064, 0x2066, 0x206F,
            0xFEFF, 0xFEFF, 0xFFF9, 0xFFFB, 0x110BD, 0x110BD, 0x110CD, 0x110CD, 0x13430, 0x1343F, 0x1BCA0, 0x1BCA3,
            0x1D173, 0x1D17A, 0xE0001, 0xE0001, 0xE0020, 0xE007F};

    /**
     * Whether a code point is a format character, Unicode category Cf: a zero-width space, a bidi control, a tag.
     */
    static boolean isFormat(int codePoint) {
        for (int i = 0; i < FORMAT.length; i += 2) {
            if (codePoint < FORMAT[i]) {
                return false;
            }
            if (codePoint <= FORMAT[i + 1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * Loads {@code nanolog.properties} and reports the settings Nanolog does not know, then creates the logger named by
     * {@value #PROPERTY_LOGGER_CLASS}, or a {@link ConsoleLogger}. With {@value #PROPERTY_RUN}{@code =on} the result is a
     * {@link CompositeLogger} of that logger and the run log, which is opened once per JVM. In agent mode
     * ({@value #PROPERTY_AGENT}) the console gets shorter defaults, the run log is on unless switched off, and one
     * {@code [NANOLOG]} line per JVM says so. A class that cannot be created prints a {@code [NANOLOG]} diagnostic and
     * falls back to {@code ConsoleLogger}.
     *
     * @return the new logger
     */
    public static ILogger createDefaultLogger() {
        // before the file, so the diagnostics about the file follow -D and the environment
        readDiagnostics();
        loadPropertiesFromFile();
        String agent = Agent.detect();
        ILogger logger = createConfiguredLogger();
        String console = agent == null ? null : Agent.shorten(logger);
        logger = RunLog.attach(logger, agent != null);
        if (agent != null) {
            Agent.announce(agent, console, RunLog.current());
        }
        return logger;
    }

    private static ILogger createConfiguredLogger() {
        String logger = getProperty(PROPERTY_LOGGER_CLASS);
        if (logger != null && !logger.isEmpty()) {
            try {
                Class<?> loggerClass = Class.forName(logger);
                return (ILogger) loggerClass.getConstructor().newInstance();
            } catch (Throwable e) {
                if (printDiagnostic("Property \"" + PROPERTY_LOGGER_CLASS + "\" has been defined, but an object of class \"" + logger +
                                    "\" cannot be created: " + e + "; " + origin(PROPERTY_LOGGER_CLASS))) {
                    e.printStackTrace(SystemStreams.err());
                }
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
        URL resource = classLoader.getResource(config);
        Properties props = new Properties();
        if (resource != null) {
            try (InputStream stream = classLoader.getResourceAsStream(config)) {
                props.load(stream);
                props.forEach((name, value) -> {
                    String key = (String) name;
                    // only our own namespace: a config file must not be able to alter arbitrary JVM-wide settings
                    // a key already set through -D or the environment wins over the file
                    if (key.startsWith(PROPERTY_PREFIX) && getProperty(key) == null) {
                        System.setProperty(key, (String) value);
                        FROM_FILE.put(key, new String[]{(String) value, String.valueOf(resource)});
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (!config.equals(DEFAULT_CONFIGURATION_FILE)) {
            printDiagnostic("Property \"" + PROPERTY_LOGGER_CONFIG_FILE + "\" has been defined, but the file \"" + config +
                            "\" cannot be found; " + origin(PROPERTY_LOGGER_CONFIG_FILE));
        }
        // the file may set it too, and its own keys are reported next
        readDiagnostics();
        if (resource != null) {
            reportSeveralFiles(classLoader, config, resource);
        }
        reportUnknownSettings(props, String.valueOf(resource));
    }

    /**
     * Reports a configuration file that is on the classpath more than once with a copy in a jar: a library that ships
     * its own file may win over the file of the application, by the order of the classpath. Two directories, such as
     * the main and the test resources, are a deliberate override and stay quiet. A class loader that cannot list the
     * files skips the check.
     *
     * @param read the file that was read
     */
    private static void reportSeveralFiles(ClassLoader classLoader, String config, URL read) {
        List<String> ignored = new ArrayList<>();
        // compared as text: URL.equals may resolve host names
        String readName = String.valueOf(read);
        boolean inJar = false;
        try {
            Enumeration<URL> files = classLoader.getResources(config);
            while (files.hasMoreElements()) {
                URL file = files.nextElement();
                inJar |= "jar".equals(file.getProtocol());
                String name = String.valueOf(file);
                if (!name.equals(readName)) {
                    ignored.add(name);
                }
            }
        } catch (IOException e) {
            return;
        }
        if (inJar && !ignored.isEmpty()) {
            printDiagnostic("Several files \"" + config + "\" are on the classpath, one in a jar at least; read: " + read + "; ignored: " +
                            String.join(", ", ignored) + ". The order of the classpath decides which one is read: a library should " +
                            "not ship this file, and " + PROPERTY_LOGGER_CONFIG_FILE + " names another one");
        }
    }

    /**
     * Prints a diagnostic for each setting Nanolog does not know: a mistyped key would otherwise be ignored without a
     * word. A failure, such as a security manager that forbids reading the environment, skips the check.
     *
     * @param file     the keys of the configuration file, empty without one
     * @param location where the file was found
     */
    private static void reportUnknownSettings(Properties file, String location) {
        try {
            Map<String, String> environmentVariables = new HashMap<>();
            for (String name : environmentNames.get()) {
                String value = environment.apply(name);
                if (value != null) {
                    environmentVariables.put(name, value);
                }
            }
            for (String finding : Settings.findings(nanologProperties(System.getProperties()), environmentVariables,
                                                    toMap(file), location)) {
                printDiagnostic(finding);
            }
        } catch (RuntimeException e) {
            // the check is a convenience: logging starts without it
        }
    }

    private static Map<String, String> nanologProperties(Properties properties) {
        Map<String, String> result = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(PROPERTY_PREFIX)) {
                result.put(key, properties.getProperty(key));
            }
        }
        return result;
    }

    private static Map<String, String> toMap(Properties properties) {
        Map<String, String> result = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            result.put(key, properties.getProperty(key));
        }
        return result;
    }
}
