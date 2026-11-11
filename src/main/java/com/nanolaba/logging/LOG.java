package com.nanolaba.logging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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
 *     the stack on every call, so pass the class in hot paths.</li>
 * </ul>
 * Formatting rules: {@code {}} takes the next argument, {@code \{}} is a literal {@code {}}, {@code \\{}} is a
 * literal backslash followed by a placeholder, missing arguments leave {@code {}} in place, extra ones are ignored,
 * arrays are rendered with {@code Arrays.toString} / {@code Arrays.deepToString}. Logging never throws: a failing
 * {@code toString()} becomes {@code [FAILED toString()]}.
 * <p>
 * The logger behind these methods is chosen at class initialisation by {@link DefaultLoggerCreator}
 * ({@code nanolog.logger}, {@code nanolog.properties}, {@link ConsoleLogger} by default) and can be replaced with
 * {@link #init(ILogger)}. All methods are thread-safe as long as the installed logger is.
 */
public class LOG {

    private static ILogger logger;

    /**
     * {@code nanolog.strict}: report calls whose placeholders and arguments do not match. Read by {@link #init()}.
     */
    private static boolean strict;

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
        logFromCaller(TRACE, findCaller(), t, message);
    }

    /**
     * Logs a message and an exception at {@code TRACE} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void trace(Throwable t, Object message) {
        logFromCaller(TRACE, findCaller(), t, () -> message);
    }

    /**
     * Logs an exception without a message at {@code TRACE} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     */
    public static void trace(Throwable t) {
        logFromCaller(TRACE, findCaller(), t, null);
    }

    /**
     * Logs a lazily built message at {@code TRACE} level. The source class is detected from the stack.
     *
     * @param message     builds the message; called only if {@code TRACE} is enabled
     */
    public static void trace(Supplier<Object> message) {
        logFromCaller(TRACE, findCaller(), null, message);
    }

    /**
     * Logs a message at {@code TRACE} level. The source class is detected from the stack.
     *
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void trace(Object message) {
        logFromCaller(TRACE, findCaller(), null, () -> message);
    }

    /**
     * Logs a parameterized message at {@code TRACE} level. The source class is detected from the stack.
     *
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void trace(String message, Object... args) {
        logFromCaller(TRACE, findCaller(), null, () -> message, args);
    }

    /**
     * Logs an exception and a parameterized message at {@code TRACE} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void trace(Throwable t, String message, Object... args) {
        logFromCaller(TRACE, findCaller(), t, () -> message, args);
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
        return logger.isEnabled(TRACE, targetClass);
    }

    /**
     * Checks whether {@code TRACE} entries of the calling class would be logged. The class is detected from the
     * stack, which costs one stack walk per call; in hot paths prefer {@link #isTraceEnabled(Class)}.
     *
     * @return {@code true} if the installed logger has {@code TRACE} enabled for the calling class
     */
    public static boolean isTraceEnabled() {
        return logger.isEnabled(TRACE, getCurrentClass());
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
        logFromCaller(DEBUG, findCaller(), t, message);
    }

    /**
     * Logs a message and an exception at {@code DEBUG} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void debug(Throwable t, Object message) {
        logFromCaller(DEBUG, findCaller(), t, () -> message);
    }

    /**
     * Logs an exception without a message at {@code DEBUG} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     */
    public static void debug(Throwable t) {
        logFromCaller(DEBUG, findCaller(), t, null);
    }

    /**
     * Logs a lazily built message at {@code DEBUG} level. The source class is detected from the stack.
     *
     * @param message     builds the message; called only if {@code DEBUG} is enabled
     */
    public static void debug(Supplier<Object> message) {
        logFromCaller(DEBUG, findCaller(), null, message);
    }

    /**
     * Logs a message at {@code DEBUG} level. The source class is detected from the stack.
     *
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void debug(Object message) {
        logFromCaller(DEBUG, findCaller(), null, () -> message);
    }

    /**
     * Logs a parameterized message at {@code DEBUG} level. The source class is detected from the stack.
     *
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void debug(String message, Object... args) {
        logFromCaller(DEBUG, findCaller(), null, () -> message, args);
    }

    /**
     * Logs an exception and a parameterized message at {@code DEBUG} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void debug(Throwable t, String message, Object... args) {
        logFromCaller(DEBUG, findCaller(), t, () -> message, args);
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
        return logger.isEnabled(DEBUG, targetClass);
    }

    /**
     * Checks whether {@code DEBUG} entries of the calling class would be logged. The class is detected from the
     * stack, which costs one stack walk per call; in hot paths prefer {@link #isDebugEnabled(Class)}.
     *
     * @return {@code true} if the installed logger has {@code DEBUG} enabled for the calling class
     */
    public static boolean isDebugEnabled() {
        return logger.isEnabled(DEBUG, getCurrentClass());
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
        logFromCaller(INFO, findCaller(), t, message);
    }

    /**
     * Logs a message and an exception at {@code INFO} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void info(Throwable t, Object message) {
        logFromCaller(INFO, findCaller(), t, () -> message);
    }

    /**
     * Logs an exception without a message at {@code INFO} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     */
    public static void info(Throwable t) {
        logFromCaller(INFO, findCaller(), t, null);
    }

    /**
     * Logs a lazily built message at {@code INFO} level. The source class is detected from the stack.
     *
     * @param message     builds the message; called only if {@code INFO} is enabled
     */
    public static void info(Supplier<Object> message) {
        logFromCaller(INFO, findCaller(), null, message);
    }


    /**
     * Logs a message at {@code INFO} level. The source class is detected from the stack.
     *
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void info(Object message) {
        logFromCaller(INFO, findCaller(), null, () -> message);
    }

    /**
     * Logs a parameterized message at {@code INFO} level. The source class is detected from the stack.
     *
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void info(String message, Object... args) {
        logFromCaller(INFO, findCaller(), null, () -> message, args);
    }

    /**
     * Logs an exception and a parameterized message at {@code INFO} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void info(Throwable t, String message, Object... args) {
        logFromCaller(INFO, findCaller(), t, () -> message, args);
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
        return logger.isEnabled(INFO, targetClass);
    }

    /**
     * Checks whether {@code INFO} entries of the calling class would be logged. The class is detected from the
     * stack, which costs one stack walk per call; in hot paths prefer {@link #isInfoEnabled(Class)}.
     *
     * @return {@code true} if the installed logger has {@code INFO} enabled for the calling class
     */
    public static boolean isInfoEnabled() {
        return logger.isEnabled(INFO, getCurrentClass());
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
        logFromCaller(WARN, findCaller(), t, message);
    }

    /**
     * Logs a message and an exception at {@code WARN} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void warn(Throwable t, Object message) {
        logFromCaller(WARN, findCaller(), t, () -> message);
    }

    /**
     * Logs an exception without a message at {@code WARN} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     */
    public static void warn(Throwable t) {
        logFromCaller(WARN, findCaller(), t, null);
    }

    /**
     * Logs a lazily built message at {@code WARN} level. The source class is detected from the stack.
     *
     * @param message     builds the message; called only if {@code WARN} is enabled
     */
    public static void warn(Supplier<Object> message) {
        logFromCaller(WARN, findCaller(), null, message);
    }

    /**
     * Logs a message at {@code WARN} level. The source class is detected from the stack.
     *
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void warn(Object message) {
        logFromCaller(WARN, findCaller(), null, () -> message);
    }

    /**
     * Logs a parameterized message at {@code WARN} level. The source class is detected from the stack.
     *
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void warn(String message, Object... args) {
        logFromCaller(WARN, findCaller(), null, () -> message, args);
    }

    /**
     * Logs an exception and a parameterized message at {@code WARN} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void warn(Throwable t, String message, Object... args) {
        logFromCaller(WARN, findCaller(), t, () -> message, args);
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
        return logger.isEnabled(WARN, targetClass);
    }

    /**
     * Checks whether {@code WARN} entries of the calling class would be logged. The class is detected from the
     * stack, which costs one stack walk per call; in hot paths prefer {@link #isWarnEnabled(Class)}.
     *
     * @return {@code true} if the installed logger has {@code WARN} enabled for the calling class
     */
    public static boolean isWarnEnabled() {
        return logger.isEnabled(WARN, getCurrentClass());
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
        logFromCaller(ERROR, findCaller(), t, message);
    }

    /**
     * Logs a message and an exception at {@code ERROR} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void error(Throwable t, Object message) {
        logFromCaller(ERROR, findCaller(), t, () -> message);
    }

    /**
     * Logs an exception without a message at {@code ERROR} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     */
    public static void error(Throwable t) {
        logFromCaller(ERROR, findCaller(), t, null);
    }

    /**
     * Logs a lazily built message at {@code ERROR} level. The source class is detected from the stack.
     *
     * @param message     builds the message; called only if {@code ERROR} is enabled
     */
    public static void error(Supplier<Object> message) {
        logFromCaller(ERROR, findCaller(), null, message);
    }

    /**
     * Logs a message at {@code ERROR} level. The source class is detected from the stack.
     *
     * @param message     the message: any object, converted with a guarded {@code toString()}
     */
    public static void error(Object message) {
        logFromCaller(ERROR, findCaller(), null, () -> message);
    }

    /**
     * Logs a parameterized message at {@code ERROR} level. The source class is detected from the stack.
     *
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void error(String message, Object... args) {
        logFromCaller(ERROR, findCaller(), null, () -> message, args);
    }

    /**
     * Logs an exception and a parameterized message at {@code ERROR} level. The source class is detected from the stack.
     *
     * @param t           the exception, logged with its stack trace
     * @param message     the message with {@code {}} placeholders
     * @param args        the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
     */
    public static void error(Throwable t, String message, Object... args) {
        logFromCaller(ERROR, findCaller(), t, () -> message, args);
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
        return logger.isEnabled(ERROR, targetClass);
    }

    /**
     * Checks whether {@code ERROR} entries of the calling class would be logged. The class is detected from the
     * stack, which costs one stack walk per call; in hot paths prefer {@link #isErrorEnabled(Class)}.
     *
     * @return {@code true} if the installed logger has {@code ERROR} enabled for the calling class
     */
    public static boolean isErrorEnabled() {
        return logger.isEnabled(ERROR, getCurrentClass());
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
        doLog(level, loggedClass, null, throwable, message, args);
    }

    private static void logFromCaller(LogEntry.LogEntryLevel level, Caller caller, Throwable throwable, Supplier<Object> message, Object... args) {
        doLog(level, caller.sourceClass, caller.frame, throwable, message, args);
    }

    private static void doLog(LogEntry.LogEntryLevel level, Class loggedClass, StackTraceElement frame, Throwable throwable, Supplier<Object> message, Object[] args) {
        if (logger.isEnabled(level, loggedClass)) {
            LogEntry entry = LogEntry.create(level, loggedClass, frame, throwable, message == null ? null : message.get(), args);
            if (strict) {
                checkPlaceholders(entry);
            }
            logger.log(entry);
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


    private static Class getCurrentClass() {
        return findCaller().sourceClass;
    }

    /**
     * Walks the stack to the first frame outside Nanolog: {@code LOG}, {@code LogEntry} and any {@code ILogger}.
     * The source class is the top-level class of that frame, the frame itself is kept as the call site.
     */
    private static Caller findCaller() {
        try {
            throw new Throwable();
        } catch (Throwable t) {
            for (StackTraceElement element : t.getStackTrace()) {
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

        throw new IllegalStateException("Unable to determine the current class");
    }

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
     * containers) and falling back to the loader of {@code LOG} itself. Returns {@code null} if neither can see it.
     */
    private static Class resolveClass(String className) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            try {
                return Class.forName(className, false, contextClassLoader);
            } catch (ClassNotFoundException e) {
                // not visible from the context class loader, try our own
            }
        }
        try {
            return Class.forName(className, false, LOG.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
