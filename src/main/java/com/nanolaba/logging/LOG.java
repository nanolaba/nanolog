package com.nanolaba.logging;

import java.io.PrintStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
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
     * Reads the frames of the {@code Throwable} that caller detection creates. Tests replace it to simulate a JVM
     * that records no frames ({@code -XX:-StackTraceInThrowable}) and must restore it afterwards.
     */
    static Function<Throwable, StackTraceElement[]> stackTraceOf = Throwable::getStackTrace;

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
     * Installs the default logger again, re-reading {@code nanolog.logger}, {@code nanolog.config} with its file and
     * {@code nanolog.strict}. Called once when the class initialises.
     */
    public static void init() {
        LOG.logger = DefaultLoggerCreator.createDefaultLogger();
        // after the logger: creating it loads nanolog.properties, which may set nanolog.strict
        LOG.strict = DefaultLoggerCreator.readBoolean(DefaultLoggerCreator.PROPERTY_STRICT, false);
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
            Caller caller = findCaller();
            doLog(current, level, caller.sourceClass, caller.frame, throwable, message, args);
        }
    }

    private static boolean isEnabledForCaller(LogEntry.LogEntryLevel level) {
        ILogger current = logger;
        return mayBeEnabled(current, level) && isEnabled(current, level, findCaller().sourceClass);
    }

    private static void doLog(ILogger current, LogEntry.LogEntryLevel level, Class loggedClass, StackTraceElement frame, Throwable throwable, Supplier<Object> message, Object[] args) {
        if (isEnabled(current, level, loggedClass)) {
            LogEntry entry = LogEntry.create(level, loggedClass, frame, throwable, resolve(message), args);
            if (strict) {
                checkPlaceholders(entry);
            }
            try {
                current.log(entry);
            } catch (Throwable t) {
                reportLoggerFailure(current, t, FALLBACK_NOTE);
                Fallback.LOGGER.log(entry);
            }
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
    private static void checkPlaceholders(LogEntry entry) {
        Object[] args = entry.getArgs();
        if (args == null || args.length == 0) {
            return;
        }
        String template = LogEntry.safeToString(entry.getMessage());
        int placeholders = LogEntry.countPlaceholders(template);
        if (placeholders != args.length && REPORTED_TEMPLATES.put(template, Boolean.TRUE) == null) {
            DefaultLoggerCreator.printDiagnostic(describeSource(entry) + ": " +
                                                 count(placeholders, "placeholder") + ", " + count(args.length, "argument") +
                                                 " in \"" + template + "\"");
        }
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
     * Walks the stack to the first frame outside Nanolog: {@code LOG}, {@code LogEntry} and any {@code ILogger}.
     * The source class is the top-level class of that frame, the frame itself is kept as the call site. When no frame
     * qualifies, the caller has neither a class nor a frame.
     */
    private static Caller findCaller() {
        try {
            throw new Throwable();
        } catch (Throwable t) {
            for (StackTraceElement element : stackTraceOf.apply(t)) {
                Class result = resolveClass(element.getClassName());
                if (result == null) {
                    // the frame belongs to a class loader we cannot see (e.g. a child loader in an app server): skip it
                    continue;
                }

                while (result.getEnclosingClass() != null) {
                    result = result.getEnclosingClass();
                }

                if (result.equals(LOG.class) || result.equals(LogEntry.class) || ILogger.class.isAssignableFrom(result)) {
                    continue;
                }

                return new Caller(result, element);
            }
        }

        // no frame qualifies (stack traces disabled, or the code runs entirely inside an ILogger): logging must not
        // throw, so the entry goes out without a source class and call site, like LOG.log(level, null, ...)
        return NO_CALLER;
    }

    private static final Caller NO_CALLER = new Caller(null, null);

    private static final class Caller {

        final Class sourceClass;
        final StackTraceElement frame;

        Caller(Class sourceClass, StackTraceElement frame) {
            this.sourceClass = sourceClass;
            this.frame = frame;
        }
    }

    /**
     * Resolves a stack frame class, preferring the thread's context class loader (the application's loader in
     * containers) and falling back to the loader of {@code LOG} itself. Returns {@code null} if neither can see or
     * load it, whatever the failure.
     */
    private static Class resolveClass(String className) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            try {
                return Class.forName(className, false, contextClassLoader);
            } catch (Throwable e) {
                // not visible from the context class loader, or it fails to load the class: try our own
            }
        }
        try {
            return Class.forName(className, false, LOG.class.getClassLoader());
        } catch (Throwable e) {
            // besides ClassNotFoundException: a LinkageError, GraalVM's MissingReflectionRegistrationError (an Error),
            // a SecurityException. Logging never throws, so the frame is skipped instead
            return null;
        }
    }
}
