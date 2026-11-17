package com.nanolaba.logging;

import java.io.PrintStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.*;

/**
 * The single entry point of Nanolog: static logging methods, so no class needs a logger field.
 * <pre>{@code
 * LOG.info("Application started");
 * LOG.debug("Loaded {} items in {} ms", count, millis);
 * LOG.debug(() -> "Expensive report: " + buildReport());   // built only if DEBUG is enabled
 * LOG.error(e, "Failed to load {}", id);                   // or LOG.error("Failed to load {}", id, e)
 * }</pre>
 * Every level ({@code trace}, {@code debug}, {@code info}, {@code warn}, {@code error}) has the same overloads:
 * <ul>
 *     <li>the message is any object, a {@code Supplier} evaluated only when the level is enabled, or a string with
 *     {@code {}} placeholders followed by its arguments;</li>
 *     <li>an exception may be passed first, or last after the arguments (the SLF4J order);</li>
 *     <li>the source class is detected from the stack unless it is passed as the first argument. Detection walks
 *     the stack on every call whose level the logger may have enabled for some class
 *     ({@link ILogger#mayBeEnabled}), so pass the class in hot paths.</li>
 * </ul>
 * Formatting rules: {@code {}} takes the next argument, {@code \{}} is a literal {@code {}}, {@code \\{}} is a
 * literal backslash followed by a placeholder, missing arguments leave {@code {}} in place, extra ones are ignored,
 * arrays are rendered with {@code Arrays.toString} / {@code Arrays.deepToString}. Logging never throws: a failing
 * {@code toString()} becomes {@code [FAILED toString()]}, and a message {@code Supplier} that throws becomes the
 * message {@code [FAILED supplier: <exception>]}.
 * <p>
 * For print debugging, {@link #dbg(Object)} prints an expression as written in the source together with its value
 * and the call site, and returns the value: {@code LOG.dbg(order.total())}.
 * <p>
 * The logger behind these methods is chosen at class initialisation by {@link DefaultLoggerCreator}
 * ({@code nanolog.logger}, {@code nanolog.properties}, {@link ConsoleLogger} by default) and can be replaced with
 * {@link #init(ILogger)}. Its levels can change while the application runs: {@link #setLevel(LogEntry.LogEntryLevel)},
 * {@link #setLevel(String, LogEntry.LogEntryLevel)}, {@link #clearLevel(String)}, {@link #resetLevels()} and
 * {@link #withLevel(String, LogEntry.LogEntryLevel)}. All methods are thread-safe as long as the installed logger is.
 */
public class LOG {

    /**
     * The installed logger. {@code volatile}, because {@link #init(ILogger)} and {@code MemoryLogger.install()} may
     * replace it while other threads are logging, and those threads must see the new one.
     */
    private static volatile ILogger logger;

    /**
     * {@code nanolog.strict}: report calls whose placeholders and arguments do not match. Read by {@link #init()};
     * {@code volatile} for the same reason as {@link #logger}.
     */
    private static volatile boolean strict;

    /**
     * Finds the caller of a call without an explicit class. Chosen when the class initialises; tests replace it to
     * exercise one locator in particular and must restore it afterwards.
     */
    static CallerDetection.Locator callerLocator = CallerDetection.best();

    private static final int REPORTED_TEMPLATES_LIMIT = 1000;

    /**
     * Templates already reported in strict mode, so a loop does not flood the output; the oldest are forgotten
     * beyond the limit.
     */
    private static final Map<String, Boolean> REPORTED_TEMPLATES = Collections.synchronizedMap(new LinkedHashMap<String, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > REPORTED_TEMPLATES_LIMIT;
        }
    });

    static {
        init();
    }

    private LOG() {/**/}

    /**
     * Installs the default logger again, re-reading {@code nanolog.logger}, {@code nanolog.config} with its file,
     * {@code nanolog.strict}, {@code nanolog.dump}, {@code nanolog.dbg} and {@code nanolog.agent}. Called once when the
     * class initialises.
     */
    public static void init() {
        LOG.logger = DefaultLoggerCreator.createDefaultLogger();
        // after the logger: creating it loads nanolog.properties, which may set the other settings
        LOG.strict = DefaultLoggerCreator.readBoolean(DefaultLoggerCreator.PROPERTY_STRICT, false);
        Dump.auto = DefaultLoggerCreator.readDumpAuto();
        Dbg.mode = DefaultLoggerCreator.readDbgMode();
        // creating the logger has reported an invalid nanolog.agent
        boolean agent = DefaultLoggerCreator.quietly(Agent::detect) != null;
        Dbg.ignoreLevels = agent;
        Summary.mode = DefaultLoggerCreator.readSummaryMode(agent);
        if (Summary.mode != Summary.Mode.OFF) {
            Summary.registerHook();
        }
    }

    /**
     * Installs a logger for the whole JVM. Call it once at application startup, before other threads log, and never
     * from library code: any library could replace the application's choice.
     *
     * @param logger the logger that receives every entry from now on, for example a lambda:
     *               {@code LOG.init(entry -> System.out.println(entry.getFormattedMessage()))}
     */
    public static void init(ILogger logger) {
        LOG.logger = logger;
    }

    // LEVELS AT RUN TIME

    /**
     * Undoes a {@link #withLevel(String, LogEntry.LogEntryLevel)} change when closed; meant for try-with-resources.
     */
    public interface LevelOverride extends AutoCloseable {

        /**
         * Restores the rule the name had before the override; closing again changes nothing. Never throws.
         */
        @Override
        void close();
    }

    /**
     * Sets the global level of the installed logger while the application runs: entries of this level and above are
     * logged, those below are not. Packages and classes with a rule of their own keep it. Takes effect on the next
     * call in every thread.
     * <p>
     * This and the other level methods need a logger that implements {@link ILevelControl}: {@link ConsoleLogger},
     * {@link JsonLogger}, or a {@link CompositeLogger}, which passes the change to those of its delegates that
     * implement it. Any other logger is left unchanged and reported once with a {@code [NANOLOG]} line.
     *
     * @param level the lowest level to log; {@code null} prints a diagnostic and changes nothing
     */
    public static void setLevel(LogEntry.LogEntryLevel level) {
        ILevelControl control = levelControl();
        if (control != null) {
            control.setLevel(level);
        }
    }

    /**
     * Sets the global level of the installed logger from its name, as {@code nanolog.level} does, so {@code OFF}
     * works too and a value from a request or a command line needs no parsing.
     *
     * @param level {@code TRACE}, {@code DEBUG}, {@code INFO}, {@code WARN}, {@code ERROR} or {@code OFF}, ignoring
     *              case and surrounding spaces; anything else prints a diagnostic and changes nothing
     * @see #setLevel(LogEntry.LogEntryLevel)
     */
    public static void setLevel(String level) {
        ILevelControl control = levelControl();
        if (control != null) {
            control.setLevel(level);
        }
    }

    /**
     * Sets the level of a package or class from its name, as {@code nanolog.level.<name>} does;
     * {@code LOG.setLevel("com.acme.noisy", "OFF")} switches a package off.
     *
     * @param name  the package or class
     * @param level {@code TRACE}, {@code DEBUG}, {@code INFO}, {@code WARN}, {@code ERROR} or {@code OFF}, ignoring
     *              case and surrounding spaces
     * @see #setLevel(String, LogEntry.LogEntryLevel)
     */
    public static void setLevel(String name, String level) {
        ILevelControl control = levelControl();
        if (control != null) {
            control.setLevel(name, level);
        }
    }

    /**
     * Sets the level of a package or class from its name until the returned override is closed.
     *
     * @param name  the package or class
     * @param level {@code TRACE}, {@code DEBUG}, {@code INFO}, {@code WARN}, {@code ERROR} or {@code OFF}, ignoring
     *              case and surrounding spaces
     * @return the override to close; it does nothing if the logger does not support level changes
     * @see #withLevel(String, LogEntry.LogEntryLevel)
     */
    public static LevelOverride withLevel(String name, String level) {
        ILevelControl control = levelControl();
        return control == null ? () -> {
        } : control.withLevel(name, level);
    }

    /**
     * Sets the level of a package or class while the application runs, as {@code nanolog.level.<name>} does at
     * startup: it beats the global level, and the most specific rule wins. Replaces the rule the name had.
     *
     * @param name  the package or class, such as {@code com.acme.payments}
     * @param level the lowest level to log for it
     * @see #setLevel(LogEntry.LogEntryLevel)
     */
    public static void setLevel(String name, LogEntry.LogEntryLevel level) {
        ILevelControl control = levelControl();
        if (control != null) {
            control.setLevel(name, level);
        }
    }

    /**
     * Removes the rule of a package or class, configured or set at run time; the global level or a less specific
     * rule applies to it again.
     *
     * @param name the package or class
     * @see #setLevel(LogEntry.LogEntryLevel)
     */
    public static void clearLevel(String name) {
        ILevelControl control = levelControl();
        if (control != null) {
            control.clearLevel(name);
        }
    }

    /**
     * Restores the levels of the installed logger from the settings: {@code -D}, environment variables and
     * {@code nanolog.properties}. Every change made at run time is undone.
     *
     * @see #setLevel(LogEntry.LogEntryLevel)
     */
    public static void resetLevels() {
        ILevelControl control = levelControl();
        if (control != null) {
            control.resetLevels();
        }
    }

    /**
     * Sets the level of a package or class until the returned override is closed, for a block of code or a test:
     * <pre>{@code
     * try (LOG.LevelOverride ignored = LOG.withLevel("com.acme.payments", LogEntryLevel.TRACE)) {
     *     ...
     * }
     * }</pre>
     * The change is JVM-wide, not limited to the current thread. Closing restores the rule the name had before;
     * nested overrides of the same name must close in reverse order, as try-with-resources does.
     *
     * @param name  the package or class
     * @param level the lowest level to log for it while the override is open
     * @return the override to close; it does nothing if the logger does not support level changes
     * @see #setLevel(LogEntry.LogEntryLevel)
     */
    public static LevelOverride withLevel(String name, LogEntry.LogEntryLevel level) {
        ILevelControl control = levelControl();
        return control == null ? () -> {
        } : control.withLevel(name, level);
    }

    /**
     * Logger classes already reported as not supporting level changes.
     */
    private static final Set<Class> WITHOUT_LEVEL_CONTROL = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static ILevelControl levelControl() {
        ILogger current = logger;
        if (current instanceof ILevelControl) {
            return (ILevelControl) current;
        }
        if (WITHOUT_LEVEL_CONTROL.add(current.getClass())) {
            DefaultLoggerCreator.printDiagnostic("Logger " + current.getClass().getName() + " does not implement ILevelControl; " +
                                                 "LOG.setLevel, clearLevel, resetLevels and withLevel change nothing");
        }
        return null;
    }

    /**
     * Forgets the templates reported in strict mode; for tests.
     */
    static void clearReportedTemplates() {
        REPORTED_TEMPLATES.clear();
    }

    /**
     * Returns the installed logger.
     *
     * @return the installed logger
     */
    public static ILogger getLogger() {
        return logger;
    }

    // TRACE
    /**
     * Logs a lazily built message and an exception at {@code TRACE} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param t           the exception, logged with its stack trace
     * @param message     builds the message; called only if {@code TRACE} is enabled
     */
    public static void trace(Class targetClass, Throwable t, Supplier<Object> message) {
        log(TRACE, targetClass, t, message);
    }

    /**
     * Logs a message and an exception at {@code TRACE} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param t           the exception, logged with its stack trace
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void trace(Class targetClass, Throwable t, Object message) {
        log(TRACE, targetClass, t, () -> message);
    }

    /**
     * Logs a lazily built message at {@code TRACE} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param message     builds the message; called only if {@code TRACE} is enabled
     */
    public static void trace(Class targetClass, Supplier<Object> message) {
        log(TRACE, targetClass, null, message);
    }

    /**
     * Logs a message at {@code TRACE} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void trace(Class targetClass, Object message) {
        log(TRACE, targetClass, null, () -> message);
    }

    /**
     * Logs a parameterized message at {@code TRACE} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void trace(Class targetClass, String message, Object... args) {
        log(TRACE, targetClass, null, () -> message, args);
    }

    /**
     * Logs an exception without a message at {@code TRACE} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param throwable   the exception, logged with its stack trace
     */
    public static void trace(Class targetClass, Throwable throwable) {
        log(TRACE, targetClass, throwable, null);
    }

    /**
     * Logs a lazily built message and an exception at {@code TRACE} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     builds the message; called only if {@code TRACE} is enabled
     */
    public static void trace(Throwable t, Supplier<Object> message) {
        logFromCaller(TRACE,t, message);
    }

    /**
     * Logs a message and an exception at {@code TRACE} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void trace(Throwable t, Object message) {
        logFromCaller(TRACE,t, () -> message);
    }

    /**
     * Logs an exception without a message at {@code TRACE} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     */
    public static void trace(Throwable t) {
        logFromCaller(TRACE,t, null);
    }

    /**
     * Logs a lazily built message at {@code TRACE} level. The source class is detected from the stack.
     *
     * @param message     builds the message; called only if {@code TRACE} is enabled
     */
    public static void trace(Supplier<Object> message) {
        logFromCaller(TRACE,null, message);
    }

    /**
     * Logs a message at {@code TRACE} level. The source class is detected from the stack.
     *
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void trace(Object message) {
        logFromCaller(TRACE,null, () -> message);
    }

    /**
     * Logs a parameterized message at {@code TRACE} level. The source class is detected from the stack.
     *
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void trace(String message, Object... args) {
        logFromCaller(TRACE,null, () -> message, args);
    }

    /**
     * Logs an exception and a parameterized message at {@code TRACE} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void trace(Throwable t, String message, Object... args) {
        logFromCaller(TRACE,t, () -> message, args);
    }

    /**
     * Logs an exception and a parameterized message at {@code TRACE} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param t           the exception, logged with its stack trace
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void trace(Class targetClass, Throwable t, String message, Object... args) {
        log(TRACE, targetClass, t, () -> message, args);
    }

    /**
     * Checks whether {@code TRACE} entries of a source class would be logged.
     *
     * @param targetClass the source class
     * @return {@code true} if the installed logger has {@code TRACE} enabled for the class
     */
    public static boolean isTraceEnabled(Class targetClass) {
        return isEnabled(logger, TRACE, targetClass);
    }

    /**
     * Checks whether {@code TRACE} entries of the calling class would be logged. Unless the installed logger has
     * {@code TRACE} off for every class ({@link ILogger#mayBeEnabled}), the class is detected from the stack, which
     * costs one stack walk per call; in hot paths prefer {@link #isTraceEnabled(Class)}.
     *
     * @return {@code true} if the installed logger has {@code TRACE} enabled for the calling class
     */
    public static boolean isTraceEnabled() {
        return isEnabledForCaller(TRACE);
    }

    // DEBUG
    /**
     * Logs a lazily built message and an exception at {@code DEBUG} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param t           the exception, logged with its stack trace
     * @param message     builds the message; called only if {@code DEBUG} is enabled
     */
    public static void debug(Class targetClass, Throwable t, Supplier<Object> message) {
        log(DEBUG, targetClass, t, message);
    }

    /**
     * Logs a message and an exception at {@code DEBUG} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param t           the exception, logged with its stack trace
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void debug(Class targetClass, Throwable t, Object message) {
        log(DEBUG, targetClass, t, () -> message);
    }

    /**
     * Logs a lazily built message at {@code DEBUG} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param message     builds the message; called only if {@code DEBUG} is enabled
     */
    public static void debug(Class targetClass, Supplier<Object> message) {
        log(DEBUG, targetClass, null, message);
    }

    /**
     * Logs a message at {@code DEBUG} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void debug(Class targetClass, Object message) {
        log(DEBUG, targetClass, null, () -> message);
    }

    /**
     * Logs a parameterized message at {@code DEBUG} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void debug(Class targetClass, String message, Object... args) {
        log(DEBUG, targetClass, null, () -> message, args);
    }

    /**
     * Logs an exception without a message at {@code DEBUG} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param throwable   the exception, logged with its stack trace
     */
    public static void debug(Class targetClass, Throwable throwable) {
        log(DEBUG, targetClass, throwable, null);
    }

    /**
     * Logs a lazily built message and an exception at {@code DEBUG} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     builds the message; called only if {@code DEBUG} is enabled
     */
    public static void debug(Throwable t, Supplier<Object> message) {
        logFromCaller(DEBUG,t, message);
    }

    /**
     * Logs a message and an exception at {@code DEBUG} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void debug(Throwable t, Object message) {
        logFromCaller(DEBUG,t, () -> message);
    }

    /**
     * Logs an exception without a message at {@code DEBUG} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     */
    public static void debug(Throwable t) {
        logFromCaller(DEBUG,t, null);
    }

    /**
     * Logs a lazily built message at {@code DEBUG} level. The source class is detected from the stack.
     *
     * @param message     builds the message; called only if {@code DEBUG} is enabled
     */
    public static void debug(Supplier<Object> message) {
        logFromCaller(DEBUG,null, message);
    }

    /**
     * Logs a message at {@code DEBUG} level. The source class is detected from the stack.
     *
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void debug(Object message) {
        logFromCaller(DEBUG,null, () -> message);
    }

    /**
     * Logs a parameterized message at {@code DEBUG} level. The source class is detected from the stack.
     *
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void debug(String message, Object... args) {
        logFromCaller(DEBUG,null, () -> message, args);
    }

    /**
     * Logs an exception and a parameterized message at {@code DEBUG} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void debug(Throwable t, String message, Object... args) {
        logFromCaller(DEBUG,t, () -> message, args);
    }

    /**
     * Logs an exception and a parameterized message at {@code DEBUG} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param t           the exception, logged with its stack trace
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void debug(Class targetClass, Throwable t, String message, Object... args) {
        log(DEBUG, targetClass, t, () -> message, args);
    }

    /**
     * Checks whether {@code DEBUG} entries of a source class would be logged.
     *
     * @param targetClass the source class
     * @return {@code true} if the installed logger has {@code DEBUG} enabled for the class
     */
    public static boolean isDebugEnabled(Class targetClass) {
        return isEnabled(logger, DEBUG, targetClass);
    }

    /**
     * Checks whether {@code DEBUG} entries of the calling class would be logged. Unless the installed logger has
     * {@code DEBUG} off for every class ({@link ILogger#mayBeEnabled}), the class is detected from the stack, which
     * costs one stack walk per call; in hot paths prefer {@link #isDebugEnabled(Class)}.
     *
     * @return {@code true} if the installed logger has {@code DEBUG} enabled for the calling class
     */
    public static boolean isDebugEnabled() {
        return isEnabledForCaller(DEBUG);
    }

    // INFO
    /**
     * Logs a lazily built message and an exception at {@code INFO} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param t           the exception, logged with its stack trace
     * @param message     builds the message; called only if {@code INFO} is enabled
     */
    public static void info(Class targetClass, Throwable t, Supplier<Object> message) {
        log(INFO, targetClass, t, (message));
    }

    /**
     * Logs a message and an exception at {@code INFO} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param t           the exception, logged with its stack trace
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void info(Class targetClass, Throwable t, Object message) {
        log(INFO, targetClass, t, () -> message);
    }

    /**
     * Logs a lazily built message at {@code INFO} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param message     builds the message; called only if {@code INFO} is enabled
     */
    public static void info(Class targetClass, Supplier<Object> message) {
        log(INFO, targetClass, null, message);
    }

    /**
     * Logs a message at {@code INFO} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void info(Class targetClass, Object message) {
        log(INFO, targetClass, null, () -> message);
    }

    /**
     * Logs a parameterized message at {@code INFO} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void info(Class targetClass, String message, Object... args) {
        log(INFO, targetClass, null, () -> message, args);
    }

    /**
     * Logs an exception without a message at {@code INFO} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param throwable   the exception, logged with its stack trace
     */
    public static void info(Class targetClass, Throwable throwable) {
        log(INFO, targetClass, throwable, null);
    }

    /**
     * Logs a lazily built message and an exception at {@code INFO} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     builds the message; called only if {@code INFO} is enabled
     */
    public static void info(Throwable t, Supplier<Object> message) {
        logFromCaller(INFO,t, message);
    }

    /**
     * Logs a message and an exception at {@code INFO} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void info(Throwable t, Object message) {
        logFromCaller(INFO,t, () -> message);
    }

    /**
     * Logs an exception without a message at {@code INFO} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     */
    public static void info(Throwable t) {
        logFromCaller(INFO,t, null);
    }

    /**
     * Logs a lazily built message at {@code INFO} level. The source class is detected from the stack.
     *
     * @param message     builds the message; called only if {@code INFO} is enabled
     */
    public static void info(Supplier<Object> message) {
        logFromCaller(INFO,null, message);
    }


    /**
     * Logs a message at {@code INFO} level. The source class is detected from the stack.
     *
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void info(Object message) {
        logFromCaller(INFO,null, () -> message);
    }

    /**
     * Logs a parameterized message at {@code INFO} level. The source class is detected from the stack.
     *
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void info(String message, Object... args) {
        logFromCaller(INFO,null, () -> message, args);
    }

    /**
     * Logs an exception and a parameterized message at {@code INFO} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void info(Throwable t, String message, Object... args) {
        logFromCaller(INFO,t, () -> message, args);
    }

    /**
     * Logs an exception and a parameterized message at {@code INFO} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param t           the exception, logged with its stack trace
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void info(Class targetClass, Throwable t, String message, Object... args) {
        log(INFO, targetClass, t, () -> message, args);
    }

    /**
     * Checks whether {@code INFO} entries of a source class would be logged.
     *
     * @param targetClass the source class
     * @return {@code true} if the installed logger has {@code INFO} enabled for the class
     */
    public static boolean isInfoEnabled(Class targetClass) {
        return isEnabled(logger, INFO, targetClass);
    }

    /**
     * Checks whether {@code INFO} entries of the calling class would be logged. Unless the installed logger has
     * {@code INFO} off for every class ({@link ILogger#mayBeEnabled}), the class is detected from the stack, which
     * costs one stack walk per call; in hot paths prefer {@link #isInfoEnabled(Class)}.
     *
     * @return {@code true} if the installed logger has {@code INFO} enabled for the calling class
     */
    public static boolean isInfoEnabled() {
        return isEnabledForCaller(INFO);
    }

    // WARN
    /**
     * Logs a lazily built message and an exception at {@code WARN} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param t           the exception, logged with its stack trace
     * @param message     builds the message; called only if {@code WARN} is enabled
     */
    public static void warn(Class targetClass, Throwable t, Supplier<Object> message) {
        log(WARN, targetClass, t, message);
    }

    /**
     * Logs a message and an exception at {@code WARN} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param t           the exception, logged with its stack trace
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void warn(Class targetClass, Throwable t, Object message) {
        log(WARN, targetClass, t, () -> message);
    }

    /**
     * Logs a lazily built message at {@code WARN} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param message     builds the message; called only if {@code WARN} is enabled
     */
    public static void warn(Class targetClass, Supplier<Object> message) {
        log(WARN, targetClass, null, message);
    }

    /**
     * Logs a message at {@code WARN} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void warn(Class targetClass, Object message) {
        log(WARN, targetClass, null, () -> message);
    }

    /**
     * Logs a parameterized message at {@code WARN} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void warn(Class targetClass, String message, Object... args) {
        log(WARN, targetClass, null, () -> message, args);
    }

    /**
     * Logs an exception without a message at {@code WARN} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param throwable   the exception, logged with its stack trace
     */
    public static void warn(Class targetClass, Throwable throwable) {
        log(WARN, targetClass, throwable, null);
    }

    /**
     * Logs a lazily built message and an exception at {@code WARN} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     builds the message; called only if {@code WARN} is enabled
     */
    public static void warn(Throwable t, Supplier<Object> message) {
        logFromCaller(WARN,t, message);
    }

    /**
     * Logs a message and an exception at {@code WARN} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void warn(Throwable t, Object message) {
        logFromCaller(WARN,t, () -> message);
    }

    /**
     * Logs an exception without a message at {@code WARN} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     */
    public static void warn(Throwable t) {
        logFromCaller(WARN,t, null);
    }

    /**
     * Logs a lazily built message at {@code WARN} level. The source class is detected from the stack.
     *
     * @param message     builds the message; called only if {@code WARN} is enabled
     */
    public static void warn(Supplier<Object> message) {
        logFromCaller(WARN,null, message);
    }

    /**
     * Logs a message at {@code WARN} level. The source class is detected from the stack.
     *
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void warn(Object message) {
        logFromCaller(WARN,null, () -> message);
    }

    /**
     * Logs a parameterized message at {@code WARN} level. The source class is detected from the stack.
     *
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void warn(String message, Object... args) {
        logFromCaller(WARN,null, () -> message, args);
    }

    /**
     * Logs an exception and a parameterized message at {@code WARN} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void warn(Throwable t, String message, Object... args) {
        logFromCaller(WARN,t, () -> message, args);
    }

    /**
     * Logs an exception and a parameterized message at {@code WARN} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param t           the exception, logged with its stack trace
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void warn(Class targetClass, Throwable t, String message, Object... args) {
        log(WARN, targetClass, t, () -> message, args);
    }

    /**
     * Checks whether {@code WARN} entries of a source class would be logged.
     *
     * @param targetClass the source class
     * @return {@code true} if the installed logger has {@code WARN} enabled for the class
     */
    public static boolean isWarnEnabled(Class targetClass) {
        return isEnabled(logger, WARN, targetClass);
    }

    /**
     * Checks whether {@code WARN} entries of the calling class would be logged. Unless the installed logger has
     * {@code WARN} off for every class ({@link ILogger#mayBeEnabled}), the class is detected from the stack, which
     * costs one stack walk per call; in hot paths prefer {@link #isWarnEnabled(Class)}.
     *
     * @return {@code true} if the installed logger has {@code WARN} enabled for the calling class
     */
    public static boolean isWarnEnabled() {
        return isEnabledForCaller(WARN);
    }

    // ERROR
    /**
     * Logs a lazily built message and an exception at {@code ERROR} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param t           the exception, logged with its stack trace
     * @param message     builds the message; called only if {@code ERROR} is enabled
     */
    public static void error(Class targetClass, Throwable t, Supplier<Object> message) {
        log(ERROR, targetClass, t, message);
    }

    /**
     * Logs a message and an exception at {@code ERROR} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param t           the exception, logged with its stack trace
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void error(Class targetClass, Throwable t, Object message) {
        log(ERROR, targetClass, t, () -> message);
    }

    /**
     * Logs a lazily built message at {@code ERROR} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param message     builds the message; called only if {@code ERROR} is enabled
     */
    public static void error(Class targetClass, Supplier<Object> message) {
        log(ERROR, targetClass, null, message);
    }

    /**
     * Logs a message at {@code ERROR} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void error(Class targetClass, Object message) {
        log(ERROR, targetClass, null, () -> message);
    }

    /**
     * Logs a parameterized message at {@code ERROR} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void error(Class targetClass, String message, Object... args) {
        log(ERROR, targetClass, null, () -> message, args);
    }

    /**
     * Logs an exception without a message at {@code ERROR} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param throwable   the exception, logged with its stack trace
     */
    public static void error(Class targetClass, Throwable throwable) {
        log(ERROR, targetClass, throwable, null);
    }

    /**
     * Logs a lazily built message and an exception at {@code ERROR} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     builds the message; called only if {@code ERROR} is enabled
     */
    public static void error(Throwable t, Supplier<Object> message) {
        logFromCaller(ERROR,t, message);
    }

    /**
     * Logs a message and an exception at {@code ERROR} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void error(Throwable t, Object message) {
        logFromCaller(ERROR,t, () -> message);
    }

    /**
     * Logs an exception without a message at {@code ERROR} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     */
    public static void error(Throwable t) {
        logFromCaller(ERROR,t, null);
    }

    /**
     * Logs a lazily built message at {@code ERROR} level. The source class is detected from the stack.
     *
     * @param message     builds the message; called only if {@code ERROR} is enabled
     */
    public static void error(Supplier<Object> message) {
        logFromCaller(ERROR,null, message);
    }

    /**
     * Logs a message at {@code ERROR} level. The source class is detected from the stack.
     *
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void error(Object message) {
        logFromCaller(ERROR,null, () -> message);
    }

    /**
     * Logs a parameterized message at {@code ERROR} level. The source class is detected from the stack.
     *
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void error(String message, Object... args) {
        logFromCaller(ERROR,null, () -> message, args);
    }

    /**
     * Logs an exception and a parameterized message at {@code ERROR} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void error(Throwable t, String message, Object... args) {
        logFromCaller(ERROR,t, () -> message, args);
    }

    /**
     * Logs an exception and a parameterized message at {@code ERROR} level.
     *
     * @param targetClass the source class of the entry; with an explicit class no stack walk takes place
     * @param t           the exception, logged with its stack trace
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void error(Class targetClass, Throwable t, String message, Object... args) {
        log(ERROR, targetClass, t, () -> message, args);
    }

    /**
     * Checks whether {@code ERROR} entries of a source class would be logged.
     *
     * @param targetClass the source class
     * @return {@code true} if the installed logger has {@code ERROR} enabled for the class
     */
    public static boolean isErrorEnabled(Class targetClass) {
        return isEnabled(logger, ERROR, targetClass);
    }

    /**
     * Checks whether {@code ERROR} entries of the calling class would be logged. Unless the installed logger has
     * {@code ERROR} off for every class ({@link ILogger#mayBeEnabled}), the class is detected from the stack, which
     * costs one stack walk per call; in hot paths prefer {@link #isErrorEnabled(Class)}.
     *
     * @return {@code true} if the installed logger has {@code ERROR} enabled for the calling class
     */
    public static boolean isErrorEnabled() {
        return isEnabledForCaller(ERROR);
    }

    // PRINT DEBUGGING

    /**
     * Prints the expression, its value and the call site, then returns the value, so it can wrap any expression in
     * place, like Rust's {@code dbg!} and Python's icecream:
     * <pre>{@code
     * BigDecimal total = LOG.dbg(order.total());
     * }</pre>
     * prints {@code DBG 17.11.2026 10:15:02 [OrderService.checkout(OrderService.java:87)] order.total() = 129.90 (BigDecimal)}.
     * <p>
     * The text of the expression is read from the source file of the call site, found through the location of the
     * calling class ({@code target/classes} leads to {@code src/main/java}) or {@code nanolog.sources}, and cached per
     * call site. Without it only the value is printed: when no source directory is found (a production deployment),
     * the source file is newer than its class, or the line holds two {@code dbg} calls. Strings and chars are quoted
     * and escaped; other values are followed by their type unless their text starts with it.
     * <p>
     * The entry is logged at {@code DEBUG}, so {@code nanolog.level} and package rules apply to it, except in agent
     * mode ({@code nanolog.agent}), where it prints whatever the levels, and {@link LogEntry#isDbg()} marks it: {@link ConsoleLogger} prints {@code DBG} and the call site, even without
     * {@code showLocation}, and {@link JsonLogger} adds {@code "dbg":true}.
     * <p>
     * Meant for temporary debug output, so the calls are easy to get rid of: when the JVM exits, one {@code [NANOLOG]}
     * line on {@code System.err} lists the call sites that printed; {@code nanolog.dbg=off} silences every call, and
     * {@code nanolog.dbg=fail} reports each one as a diagnostic, which fails {@link MemoryLogger#assertNoDiagnostics()};
     * {@link NanologChecks#assertNoDbgCalls} fails a test while the sources still call it.
     *
     * @param value the value to print
     * @param <T>   the type of the value
     * @return {@code value}, unchanged
     */
    public static <T> T dbg(T value) {
        ILogger current = logger;
        if (mayPrintDbg(current)) {
            writeDbg(current, new Object[]{value});
        }
        return value;
    }

    /**
     * Prints several expressions with their values and the call site:
     * {@code LOG.dbg(user, cart.size())} prints {@code user = User{id=17}, cart.size() = 3 (Integer)}. See
     * {@link #dbg(Object)}.
     *
     * @param first  the first value
     * @param second the second value
     * @param more   further values
     */
    public static void dbg(Object first, Object second, Object... more) {
        ILogger current = logger;
        if (mayPrintDbg(current)) {
            writeDbg(current, Dbg.values(first, second, more));
        }
    }

    /**
     * Prints that the call site was reached: {@code DBG ... [OrderService.checkout(OrderService.java:89)] reached}.
     * See {@link #dbg(Object)}.
     */
    public static void dbg() {
        ILogger current = logger;
        if (mayPrintDbg(current)) {
            writeDbg(current, NO_ARGS);
        }
    }

    private static void writeDbg(ILogger current, Object[] values) {
        CallerDetection.Caller caller = findCaller();
        if (printsDbg(current, caller.sourceClass)) {
            writeDbgEntry(current, Dbg.entry(caller, values, 0, LogEntry.LOG_BOUNDARY));
        }
    }

    /**
     * Whether a {@code dbg} call may print: not with {@code nanolog.dbg=off}, and only if {@code DEBUG} may be enabled,
     * except in agent mode, where it ignores the levels. Checked before anything is allocated or the stack is walked.
     */
    private static boolean mayPrintDbg(ILogger current) {
        return Dbg.mode != Dbg.Mode.OFF && (Dbg.ignoreLevels || mayBeEnabled(current, DEBUG));
    }

    /**
     * Whether a {@code dbg} call of a class prints: if {@code DEBUG} is enabled for it, or always in agent mode.
     */
    private static boolean printsDbg(ILogger current, Class sourceClass) {
        return Dbg.ignoreLevels || isEnabled(current, DEBUG, sourceClass);
    }

    /**
     * Writes the entry of a {@code dbg} call, remembering its call site for the list printed at exit; with
     * {@code nanolog.dbg=fail}, reports the call first.
     */
    private static void writeDbgEntry(ILogger current, LogEntry entry) {
        boolean first = Dbg.ran(entry.getCaller());
        if (Dbg.mode == Dbg.Mode.FAIL) {
            diagnose(current, describeSource(entry) + ": LOG.dbg call left in the code (nanolog.dbg=fail)", first);
        }
        write(current, entry);
    }

    // RATE LIMITING

    /**
     * Logs a statement only the first time it runs, for a warning that a loop or a retry would otherwise repeat:
     * {@code LOG.once().warn("Option {} is deprecated and ignored", key)}. Every later call of that statement is
     * skipped before its message is built.
     * <p>
     * A statement is its call site, the file and line of the call: two calls on one line share it, and a helper
     * method that logs for others shares it between all its callers. The source class is detected from the stack, so
     * a disabled level costs as little as with the other methods; disabled calls are not counted. A call with no
     * call site to tell it by, which happens only when the stack cannot be read, is not limited.
     *
     * @return the level methods of a statement logged once
     */
    public static Limited once() {
        return Limited.ONCE;
    }

    /**
     * Logs a statement at most once per period, for a message that a busy loop repeats:
     * {@code LOG.atMostEvery(10, TimeUnit.SECONDS).warn("Queue is full, dropping {}", item)}. The next entry that gets
     * through carries the number of calls skipped since the previous one, {@link LogEntry#getSkippedCount()}, and the
     * loggers print it: {@code Queue is full, dropping 17 [skipped 241]}.
     * <p>
     * Statements are told apart by their call site, as with {@link #once()}. A count left at the end of a burst is
     * printed only when the statement runs again.
     *
     * @param period the minimum time between two entries of the statement; zero or less limits nothing
     * @param unit   the unit of the period; {@code null} limits nothing
     * @return the level methods of a statement logged at most once per period
     */
    public static Limited atMostEvery(long period, TimeUnit unit) {
        return new Limited(unit == null ? 0 : unit.toNanos(period));
    }

    /**
     * Reads the time for {@link #atMostEvery}, in nanoseconds. Tests replace it with a clock of their own and must
     * restore it afterwards.
     */
    static LongSupplier nanoClock = System::nanoTime;

    /**
     * The state of every rate-limited statement, by call site. Call sites are bounded by the size of the code, so the
     * map needs no eviction; a key built from data could grow without bound.
     */
    private static final Map<StackTraceElement, RateState> LIMITS = new ConcurrentHashMap<>();

    /**
     * Forgets the state of every rate-limited statement; for tests.
     */
    static void clearLimits() {
        LIMITS.clear();
    }

    /**
     * Decides whether a call of a rate-limited statement gets through.
     *
     * @return the number of calls skipped since the previous entry of the statement, or {@code -1} to skip this one
     */
    private static long acquire(StackTraceElement callSite, long periodNanos) {
        return LIMITS.computeIfAbsent(callSite, site -> new RateState()).acquire(nanoClock.getAsLong(), periodNanos);
    }

    /**
     * The time of the last entry of a statement and the number of calls skipped since. Updated without locks: a call
     * gets through only by winning the compare-and-set of the time, and every other call adds one to the count, so each
     * call is either logged or counted exactly once.
     */
    private static final class RateState {

        private static final long NEVER = Long.MIN_VALUE;

        private final AtomicLong lastEntry = new AtomicLong(NEVER);
        private final AtomicLong skipped = new AtomicLong();

        long acquire(long now, long periodNanos) {
            long last = lastEntry.get();
            if ((last == NEVER || now - last >= periodNanos) && lastEntry.compareAndSet(last, now)) {
                return skipped.getAndSet(0);
            }
            skipped.incrementAndGet();
            return -1;
        }
    }

    /**
     * The level methods of a rate-limited statement, returned by {@link LOG#once()} and
     * {@link LOG#atMostEvery(long, TimeUnit)}. Each method detects its call site and logs or skips the call; the
     * message is built only for a call that gets through.
     */
    public static final class Limited {

        static final Limited ONCE = new Limited(Long.MAX_VALUE);

        /**
         * Calls of these methods pass through this class, so a location-aware SLF4J backend must look past it, not
         * only past {@code LOG}, to find the call site.
         */
        private static final String CALLER_BOUNDARY = Limited.class.getName();

        private final long periodNanos;

        private Limited(long periodNanos) {
            this.periodNanos = periodNanos;
        }

        private void log(LogEntry.LogEntryLevel level, Throwable throwable, Supplier<Object> message, Object[] args) {
            ILogger current = logger;
            if (mayBeEnabled(current, level)) {
                CallerDetection.Caller caller = findCaller();
                long skipped = admit(isEnabled(current, level, caller.sourceClass), caller);
                if (skipped >= 0) {
                    write(current, LogEntry.create(level, caller.sourceClass, caller.frame, throwable, resolve(message), args, skipped,
                                                   CALLER_BOUNDARY));
                }
            }
        }

        /**
         * Decides whether a call whose level may be enabled gets through.
         *
         * @param enabled whether the logger takes the entry of the caller
         * @return the number of calls skipped since the previous entry of the statement, or {@code -1} if the level is
         * off for the caller or the call is skipped
         */
        private long admit(boolean enabled, CallerDetection.Caller caller) {
            if (!enabled) {
                return -1;
            }
            return caller.frame == null ? 0 : acquire(caller.frame, periodNanos);
        }

        private void writeDbg(ILogger current, Object[] values) {
            CallerDetection.Caller caller = findCaller();
            long skipped = admit(printsDbg(current, caller.sourceClass), caller);
            if (skipped >= 0) {
                writeDbgEntry(current, Dbg.entry(caller, values, skipped, CALLER_BOUNDARY));
            }
        }

        /**
         * Prints the expression, its value and the call site, unless the statement is skipped, and returns the value.
         * See {@link LOG#dbg(Object)}.
         *
         * @param value the value to print
         * @param <T>   the type of the value
         * @return {@code value}, unchanged, also when the statement is skipped
         */
        public <T> T dbg(T value) {
            ILogger current = logger;
            if (mayPrintDbg(current)) {
                writeDbg(current, new Object[]{value});
            }
            return value;
        }

        /**
         * Prints several expressions with their values and the call site, unless the statement is skipped. See
         * {@link LOG#dbg(Object, Object, Object...)}.
         *
         * @param first  the first value
         * @param second the second value
         * @param more   further values
         */
        public void dbg(Object first, Object second, Object... more) {
            ILogger current = logger;
            if (mayPrintDbg(current)) {
                writeDbg(current, Dbg.values(first, second, more));
            }
        }

        /**
         * Prints that the call site was reached, unless the statement is skipped. See {@link LOG#dbg()}.
         */
        public void dbg() {
            ILogger current = logger;
            if (mayPrintDbg(current)) {
                writeDbg(current, NO_ARGS);
            }
        }

        /**
         * Logs a parameterized message at {@code TRACE} level, unless the statement is skipped.
         *
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
         */
        public void trace(String message, Object... args) {
            log(TRACE, null, () -> message, args);
        }

        /**
         * Logs a lazily built message at {@code TRACE} level, unless the statement is skipped.
         *
         * @param message builds the message; called only if the level is enabled and the statement is not skipped
         */
        public void trace(Supplier<Object> message) {
            log(TRACE, null, message, NO_ARGS);
        }

        /**
         * Logs an exception and a parameterized message at {@code TRACE} level, unless the statement is skipped.
         *
         * @param t       the exception, logged with its stack trace
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders
         */
        public void trace(Throwable t, String message, Object... args) {
            log(TRACE, t, () -> message, args);
        }

        /**
         * Logs an exception and a lazily built message at {@code TRACE} level, unless the statement is skipped.
         *
         * @param t       the exception, logged with its stack trace
         * @param message builds the message; called only if the level is enabled and the statement is not skipped
         */
        public void trace(Throwable t, Supplier<Object> message) {
            log(TRACE, t, message, NO_ARGS);
        }

        /**
         * Logs a parameterized message at {@code DEBUG} level, unless the statement is skipped.
         *
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
         */
        public void debug(String message, Object... args) {
            log(DEBUG, null, () -> message, args);
        }

        /**
         * Logs a lazily built message at {@code DEBUG} level, unless the statement is skipped.
         *
         * @param message builds the message; called only if the level is enabled and the statement is not skipped
         */
        public void debug(Supplier<Object> message) {
            log(DEBUG, null, message, NO_ARGS);
        }

        /**
         * Logs an exception and a parameterized message at {@code DEBUG} level, unless the statement is skipped.
         *
         * @param t       the exception, logged with its stack trace
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders
         */
        public void debug(Throwable t, String message, Object... args) {
            log(DEBUG, t, () -> message, args);
        }

        /**
         * Logs an exception and a lazily built message at {@code DEBUG} level, unless the statement is skipped.
         *
         * @param t       the exception, logged with its stack trace
         * @param message builds the message; called only if the level is enabled and the statement is not skipped
         */
        public void debug(Throwable t, Supplier<Object> message) {
            log(DEBUG, t, message, NO_ARGS);
        }

        /**
         * Logs a parameterized message at {@code INFO} level, unless the statement is skipped.
         *
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
         */
        public void info(String message, Object... args) {
            log(INFO, null, () -> message, args);
        }

        /**
         * Logs a lazily built message at {@code INFO} level, unless the statement is skipped.
         *
         * @param message builds the message; called only if the level is enabled and the statement is not skipped
         */
        public void info(Supplier<Object> message) {
            log(INFO, null, message, NO_ARGS);
        }

        /**
         * Logs an exception and a parameterized message at {@code INFO} level, unless the statement is skipped.
         *
         * @param t       the exception, logged with its stack trace
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders
         */
        public void info(Throwable t, String message, Object... args) {
            log(INFO, t, () -> message, args);
        }

        /**
         * Logs an exception and a lazily built message at {@code INFO} level, unless the statement is skipped.
         *
         * @param t       the exception, logged with its stack trace
         * @param message builds the message; called only if the level is enabled and the statement is not skipped
         */
        public void info(Throwable t, Supplier<Object> message) {
            log(INFO, t, message, NO_ARGS);
        }

        /**
         * Logs a parameterized message at {@code WARN} level, unless the statement is skipped.
         *
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
         */
        public void warn(String message, Object... args) {
            log(WARN, null, () -> message, args);
        }

        /**
         * Logs a lazily built message at {@code WARN} level, unless the statement is skipped.
         *
         * @param message builds the message; called only if the level is enabled and the statement is not skipped
         */
        public void warn(Supplier<Object> message) {
            log(WARN, null, message, NO_ARGS);
        }

        /**
         * Logs an exception and a parameterized message at {@code WARN} level, unless the statement is skipped.
         *
         * @param t       the exception, logged with its stack trace
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders
         */
        public void warn(Throwable t, String message, Object... args) {
            log(WARN, t, () -> message, args);
        }

        /**
         * Logs an exception and a lazily built message at {@code WARN} level, unless the statement is skipped.
         *
         * @param t       the exception, logged with its stack trace
         * @param message builds the message; called only if the level is enabled and the statement is not skipped
         */
        public void warn(Throwable t, Supplier<Object> message) {
            log(WARN, t, message, NO_ARGS);
        }

        /**
         * Logs a parameterized message at {@code ERROR} level, unless the statement is skipped.
         *
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
         */
        public void error(String message, Object... args) {
            log(ERROR, null, () -> message, args);
        }

        /**
         * Logs a lazily built message at {@code ERROR} level, unless the statement is skipped.
         *
         * @param message builds the message; called only if the level is enabled and the statement is not skipped
         */
        public void error(Supplier<Object> message) {
            log(ERROR, null, message, NO_ARGS);
        }

        /**
         * Logs an exception and a parameterized message at {@code ERROR} level, unless the statement is skipped.
         *
         * @param t       the exception, logged with its stack trace
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders
         */
        public void error(Throwable t, String message, Object... args) {
            log(ERROR, t, () -> message, args);
        }

        /**
         * Logs an exception and a lazily built message at {@code ERROR} level, unless the statement is skipped.
         *
         * @param t       the exception, logged with its stack trace
         * @param message builds the message; called only if the level is enabled and the statement is not skipped
         */
        public void error(Throwable t, Supplier<Object> message) {
            log(ERROR, t, message, NO_ARGS);
        }
    }

    private static final Object[] NO_ARGS = new Object[0];

    // UNIVERSAL LOG

    /**
     * The universal entry point: logs an entry with every part given explicitly. Nothing is detected from the stack,
     * so the entry has no caller frame.
     *
     * @param level       the level of the entry
     * @param loggedClass the source class; may be {@code null}
     * @param throwable   the exception; may be {@code null}
     * @param message     builds the message, called only if the level is enabled; may be {@code null}
     * @param args        the arguments for {@code {}} placeholders in the message
     */
    public static void log(LogEntry.LogEntryLevel level, Class loggedClass, Throwable throwable, Supplier<Object> message, Object... args) {
        doLog(logger, level, loggedClass, null, throwable, message, args);
    }

    /**
     * Logs with the source class detected from the stack. The level is checked against every class first, so a level
     * the logger has off everywhere costs no stack walk.
     */
    private static void logFromCaller(LogEntry.LogEntryLevel level, Throwable throwable, Supplier<Object> message, Object... args) {
        ILogger current = logger;
        if (mayBeEnabled(current, level)) {
            CallerDetection.Caller caller = findCaller();
            doLog(current, level, caller.sourceClass, caller.frame, throwable, message, args);
        }
    }

    private static boolean isEnabledForCaller(LogEntry.LogEntryLevel level) {
        ILogger current = logger;
        return mayBeEnabled(current, level) && isEnabled(current, level, findCaller().sourceClass);
    }

    private static void doLog(ILogger current, LogEntry.LogEntryLevel level, Class loggedClass, StackTraceElement frame, Throwable throwable, Supplier<Object> message, Object[] args) {
        if (isEnabled(current, level, loggedClass)) {
            write(current, LogEntry.create(level, loggedClass, frame, throwable, resolve(message), args, 0));
        }
    }

    /**
     * Hands an entry whose level is enabled to the logger: checks its placeholders in strict mode, and writes it to
     * {@code System.err} if the logger throws.
     */
    private static void write(ILogger current, LogEntry entry) {
        if (strict) {
            checkPlaceholders(current, entry);
        }
        if (Summary.mode != Summary.Mode.OFF) {
            Summary.count(entry);
        }
        List<MemoryLogger> captures = captures();
        if (captures != null) {
            // the levels may have been passed only for a capture that takes every level: ask the logger itself
            boolean accepted = entry.isDbg() && Dbg.ignoreLevels || accepts(current, entry.getLevel(), entry.getSourceClass());
            for (MemoryLogger capture : captures) {
                if (capture.everyLevel || accepted) {
                    capture.log(entry);
                }
            }
            if (!accepted || keeps(captures, current)) {
                return;
            }
        }
        try {
            current.log(entry);
        } catch (Throwable t) {
            reportLoggerFailure(current, t, FALLBACK_NOTE);
            Fallback.LOGGER.log(entry);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private static final String FALLBACK_NOTE = "entries it fails to write are printed to System.err";

    /**
     * Logger classes already reported as failing, so a broken logger produces one diagnostic, not one per entry.
     */
    private static final Set<Class> FAILED_LOGGERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Asks a logger whether a level is enabled. A logger whose check throws is reported, and the level counts as
     * enabled, so the entry still gets a chance to be written.
     */
    private static boolean isEnabled(ILogger current, LogEntry.LogEntryLevel level, Class sourceClass) {
        return takesEveryLevel(captures()) || accepts(current, level, sourceClass);
    }

    /**
     * Asks the logger itself whether it takes an entry, with the same guard as {@link #isEnabled}.
     */
    private static boolean accepts(ILogger current, LogEntry.LogEntryLevel level, Class sourceClass) {
        try {
            return current.isEnabled(level, sourceClass);
        } catch (Throwable t) {
            reportLoggerFailure(current, t, FALLBACK_NOTE);
            return true;
        }
    }

    /**
     * Asks a logger whether a level may be enabled for any class, with the same guard as {@link #isEnabled}.
     */
    private static boolean mayBeEnabled(ILogger current, LogEntry.LogEntryLevel level) {
        if (takesEveryLevel(captures())) {
            return true;
        }
        try {
            return current.mayBeEnabled(level);
        } catch (Throwable t) {
            reportLoggerFailure(current, t, FALLBACK_NOTE);
            return true;
        }
    }

    /**
     * Prints one {@code [NANOLOG]} line the first time a logger of a given class throws; later failures of the same
     * class are silent. Also used by {@link CompositeLogger} for its delegates.
     */
    static void reportLoggerFailure(ILogger failed, Throwable t, String consequence) {
        if (FAILED_LOGGERS.add(failed.getClass())) {
            DefaultLoggerCreator.printDiagnostic("Logger " + failed.getClass().getName() + " threw " + LogEntry.safeToString(t) +
                                                 "; " + consequence);
        }
    }

    /**
     * Forgets the logger classes reported as failing or as not supporting level changes; for tests.
     */
    static void clearReportedLoggerFailures() {
        FAILED_LOGGERS.clear();
        WITHOUT_LEVEL_CONTROL.clear();
    }

    /**
     * Writes the entries a failing logger could not write: the {@link ConsoleLogger} format, every level to
     * {@code System.err}. Created on first use.
     */
    private static final class Fallback {

        static final ConsoleLogger LOGGER = new ConsoleLogger() {
            @Override
            protected PrintStream getOutputStream(LogEntry entry) {
                return System.err;
            }
        };
    }

    /**
     * Evaluates a lazy message. A supplier that throws must not fail the logging call, so its exception becomes the
     * message {@code [FAILED supplier: <exception>]}, in the spirit of {@code [FAILED toString()]}.
     */
    private static Object resolve(Supplier<Object> message) {
        if (message == null) {
            return null;
        }
        try {
            return message.get();
        } catch (Throwable t) {
            return "[FAILED supplier: " + LogEntry.safeToString(t) + "]";
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Strict mode: prints one {@code [NANOLOG]} line when the {@code {}} placeholders of a parameterized message do
     * not match its arguments. Only calls with arguments are checked, so a plain message that happens to contain
     * {@code {}} is never reported. The entry itself is logged unchanged, and nothing is thrown.
     */
    private static void checkPlaceholders(ILogger current, LogEntry entry) {
        Object[] args = entry.getArgs();
        if (args == null || args.length == 0) {
            return;
        }
        String template = LogEntry.safeToString(entry.getMessage());
        int placeholders = LogEntry.countPlaceholders(template);
        if (placeholders != args.length) {
            String diagnostic = describeSource(entry) + ": " + count(placeholders, "placeholder") + ", " +
                                count(args.length, "argument") + " in \"" + template + "\"";
            diagnose(current, diagnostic, REPORTED_TEMPLATES.put(template, Boolean.TRUE) == null);
        }
    }

    /**
     * Reports a finding: to {@code System.err} the first time only, so a loop does not flood it, and to the logger
     * every time, since a test must see a finding that an earlier test already printed.
     */
    private static void diagnose(ILogger current, String diagnostic, boolean first) {
        if (first) {
            DefaultLoggerCreator.printDiagnostic(diagnostic);
        }
        List<MemoryLogger> captures = captures();
        if (captures != null) {
            for (MemoryLogger capture : captures) {
                capture.onDiagnostic(diagnostic);
            }
            if (keeps(captures, current)) {
                return;
            }
        }
        try {
            current.onDiagnostic(diagnostic);
        } catch (Throwable t) {
            reportLoggerFailure(current, t, "its diagnostics are printed to System.err only");
        }
    }

    // CAPTURES OF ONE THREAD

    /**
     * The number of captures open in all threads. Without one, a logging call reads this and nothing else of the
     * captures, so they cost nothing until a test opens one.
     */
    private static final AtomicInteger CAPTURES = new AtomicInteger();

    /**
     * The open captures of each thread, outermost first. A list stays with its thread once created, empty when its
     * captures are closed; it is copied on write, because another thread may close a capture.
     */
    private static final ThreadLocal<List<MemoryLogger>> CAPTURED = new ThreadLocal<>();

    /**
     * Opens a capture of the current thread; for {@link MemoryLogger#capture()}.
     */
    static void startCapture(MemoryLogger memory) {
        List<MemoryLogger> captures = CAPTURED.get();
        if (captures == null) {
            captures = new CopyOnWriteArrayList<>();
            CAPTURED.set(captures);
        }
        memory.capture = captures;
        captures.add(memory);
        CAPTURES.incrementAndGet();
    }

    /**
     * Closes a capture, from any thread; closing it again changes nothing.
     */
    static void endCapture(MemoryLogger memory) {
        if (memory.capture.remove(memory)) {
            CAPTURES.decrementAndGet();
        }
    }

    /**
     * The number of captures open in all threads; zero keeps logging on its fast path. For tests.
     */
    static int openCaptures() {
        return CAPTURES.get();
    }

    /**
     * The open captures of the current thread.
     *
     * @return the captures; {@code null} if there are none
     */
    private static List<MemoryLogger> captures() {
        if (CAPTURES.get() == 0) {
            return null;
        }
        List<MemoryLogger> captures = CAPTURED.get();
        return captures == null || captures.isEmpty() ? null : captures;
    }

    /**
     * Whether one of the captures of the thread keeps its entries from this logger: the logger that was installed
     * when it opened.
     */
    private static boolean keeps(List<MemoryLogger> captures, ILogger current) {
        for (MemoryLogger capture : captures) {
            if (capture.keptFrom == current) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether one of the captures of the thread takes every level, so that every level counts as enabled.
     */
    private static boolean takesEveryLevel(List<MemoryLogger> captures) {
        if (captures != null) {
            for (MemoryLogger capture : captures) {
                if (capture.everyLevel) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String describeSource(LogEntry entry) {
        if (entry.getCaller() != null) {
            return entry.getCaller().toString();
        } else if (entry.getSourceClass() != null) {
            return entry.getSourceClass().getName();
        }
        return "unknown source";
    }

    private static String count(int number, String noun) {
        return number + " " + noun + (number == 1 ? "" : "s");
    }

    /**
     * Finds the caller through {@link #callerLocator}. A locator that fails gives way to the {@code Throwable} walk,
     * which works on every JVM: logging never throws.
     */
    private static CallerDetection.Caller findCaller() {
        try {
            return callerLocator.find();
        } catch (Throwable t) {
            return CallerDetection.ThrowableWalk.INSTANCE.find();
        }
    }
}
