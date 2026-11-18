package com.nanolaba.logging;

import java.io.PrintStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
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


    static {
        configure();
    }

    private LOG() {/**/}

    /**
     * Installs the default logger again, re-reading {@code nanolog.logger}, {@code nanolog.config} with its file,
     * {@code nanolog.strict}, {@code nanolog.dump}, {@code nanolog.dbg}, {@code nanolog.agent},
     * {@code nanolog.unit.*}, {@code nanolog.buffer.*}, {@code nanolog.content.*}, {@code nanolog.redact},
     * {@code nanolog.caller.skip}, {@code nanolog.context.provider} and {@code nanolog.logger.lock}. Called once when
     * the class initialises.
     * <p>
     * The logger it installs is the logger of the configuration, which {@link #init(ILogger)} may replace without a
     * diagnostic. When it replaces a logger that code of another organization installed with {@link #init(ILogger)},
     * it prints one; after {@link #lock()} it is ignored, with a diagnostic.
     */
    public static void init() {
        Origin origin = Origin.ofCaller();
        if (locked) {
            refuse("LOG.init()", origin);
            return;
        }
        ILogger previous = LOG.logger;
        Origin previousOrigin = installedBy;
        configure();
        reportReplacement(previous, previousOrigin, LOG.logger, origin);
    }

    /**
     * Installs the logger of the configuration and reads the settings, for the class initialiser and {@link #init()}.
     */
    private static void configure() {
        flushRepeats(LOG.logger);
        LOG.logger = DefaultLoggerCreator.createDefaultLogger();
        LOG.installedBy = null;
        // after the logger: creating it loads nanolog.properties, which may set the other settings
        LOG.strict = DefaultLoggerCreator.readBoolean(DefaultLoggerCreator.PROPERTY_STRICT, false);
        Dump.auto = DefaultLoggerCreator.readDumpAuto();
        Dbg.mode = DefaultLoggerCreator.readDbgMode();
        // creating the logger has reported an invalid nanolog.agent
        boolean agent = DefaultLoggerCreator.quietly(Agent::detect) != null;
        Dbg.ignoreLevels = agent;
        Summary.mode = DefaultLoggerCreator.readSummaryMode(agent);
        Units.configure();
        Breadcrumbs.configure();
        Contents.configure();
        Redaction.configure();
        CallerDetection.configureSkip();
        Contexts.configureProviders();
        if (Summary.mode != Summary.Mode.OFF) {
            Summary.registerHook();
        }
        LOG.locked = DefaultLoggerCreator.readBoolean(DefaultLoggerCreator.PROPERTY_LOGGER_LOCK, false);
        if (DefaultLoggerCreator.readBoolean(DefaultLoggerCreator.PROPERTY_CAPTURE_SYSTEM_STREAMS, false)) {
            SystemStreams.capture();
        }
    }

    /**
     * Installs a logger for the whole JVM. Call it once at application startup, before other threads log, and never
     * from library code: any library could replace the application's choice.
     * <p>
     * The call site is kept. A logger installed here and replaced later by code of another organization, by this
     * method or by {@link #init()}, is reported with both call sites, so a library that replaces the logger of the
     * application is found. The organization is the first two segments of the package of the calling class,
     * {@code com.acme} for {@code com.acme.billing.Main}, so the code of the application and its tests may replace
     * their own logger without a word, and so may the same logger again. After {@link #lock()} or with
     * {@code nanolog.logger.lock=true} the call is ignored, with a diagnostic. Nothing is thrown.
     *
     * @param logger the logger that receives every entry from now on, for example a lambda:
     *               {@code LOG.init(entry -> System.out.println(entry.getFormattedMessage()))}
     */
    public static void init(ILogger logger) {
        Origin origin = Origin.ofCaller();
        if (locked) {
            refuse("LOG.init(" + className(logger) + ")", origin);
            return;
        }
        ILogger previous = LOG.logger;
        Origin previousOrigin = installedBy;
        install(logger, origin);
        reportReplacement(previous, previousOrigin, logger, origin);
    }

    /**
     * Keeps the installed logger: every later {@link #init(ILogger)} and {@link #init()} is ignored, with a
     * diagnostic that names the call site, so no library can replace the logger the application chose. Call it at
     * startup, after the application installed its logger; {@code nanolog.logger.lock=true} does the same for the
     * logger of the configuration. {@link MemoryLogger#install()} still installs and restores a logger in tests.
     */
    public static void lock() {
        locked = true;
    }

    /**
     * Undoes {@link #lock()}; for tests.
     */
    static void unlock() {
        locked = false;
    }

    // SYSTEM STREAMS

    /**
     * Turns every line printed to {@code System.out} into an {@code INFO} entry and every line printed to
     * {@code System.err} into an {@code ERROR} entry, for libraries and old code that print instead of logging, and
     * for {@code e.printStackTrace()}: their lines get the format of the logger, reach {@link JsonLogger} and
     * {@link MemoryLogger}, and follow the levels. The source class and the call site are those of the code that
     * printed. A line ends at a line break, {@code \n} or {@code \r\n}, or when the stream is flushed; empty lines are
     * dropped, and each line of a stack trace is an entry of its own.
     * <p>
     * Nanolog itself writes to the streams it finds here, so nothing comes back in, and what a logger prints to
     * {@code System.out} while it writes an entry passes straight through. Capturing again changes nothing; also set
     * by {@code nanolog.captureSystemStreams=true}.
     */
    public static void captureSystemStreams() {
        SystemStreams.capture();
    }

    /**
     * Puts back the streams that {@link #captureSystemStreams()} found, each only if no one replaced the capturing
     * stream since. Restoring again changes nothing.
     */
    public static void restoreSystemStreams() {
        SystemStreams.restore();
    }

    /**
     * Writes a line captured from {@code System.out} or {@code System.err}, if its level is enabled; the line is the
     * message as it is, with no placeholders.
     */
    static void writeLine(LogEntry.LogEntryLevel level, Class sourceClass, StackTraceElement frame, String line) {
        ILogger current = logger;
        if (isEnabled(current, level, sourceClass)) {
            write(current, LogEntry.create(level, sourceClass, frame, null, line, NO_ARGS, 0));
        }
    }

    // THE INSTALLED LOGGER

    /**
     * Who installed the installed logger with {@link #init(ILogger)}. {@code null} for the logger of the configuration
     * and for one of {@link MemoryLogger}, whose replacement is not reported.
     */
    private static volatile Origin installedBy;

    /**
     * Set by {@link #lock()} and {@code nanolog.logger.lock}: {@code init} calls are ignored.
     */
    private static volatile boolean locked;

    /**
     * The call site of an {@code init} call and the organization of its class.
     */
    static final class Origin {

        private static final String UNKNOWN_PLACE = "an unknown place";

        final String site;
        final String organization;

        private Origin(String site, String organization) {
            this.site = site;
            this.organization = organization;
        }

        /**
         * The caller of {@code LOG}, found by the {@code Throwable} walk: an {@code init} call is rare, and walking
         * through {@link #callerLocator} would count as a walk of a logging call.
         */
        static Origin ofCaller() {
            StackTraceElement frame = CallerDetection.ThrowableWalk.INSTANCE.find().frame;
            return frame == null ? new Origin(UNKNOWN_PLACE, UNKNOWN_PLACE) : new Origin(frame.toString(), organizationOf(frame.getClassName()));
        }

        /**
         * The first two segments of the package of a class, {@code com.acme} for {@code com.acme.billing.Main}; the
         * whole package when it has fewer.
         */
        static String organizationOf(String className) {
            String packageName = className.substring(0, Math.max(0, className.lastIndexOf('.')));
            int second = packageName.indexOf('.', packageName.indexOf('.') + 1);
            return second == -1 ? packageName : packageName.substring(0, second);
        }
    }

    /**
     * Installs a logger with the origin it is reported by; for {@link MemoryLogger}, which installs and restores
     * loggers under the lock and without a diagnostic.
     */
    static void install(ILogger logger, Origin origin) {
        flushRepeats(LOG.logger);
        LOG.logger = logger;
        LOG.installedBy = origin;
    }

    /**
     * Who installed the installed logger, for {@link MemoryLogger} to restore it with.
     */
    static Origin installedBy() {
        return installedBy;
    }

    private static void reportReplacement(ILogger previous, Origin previousOrigin, ILogger next, Origin origin) {
        if (previousOrigin != null && !previousOrigin.organization.equals(origin.organization) && previous != next) {
            DefaultLoggerCreator.printDiagnostic("Logger replaced: " + describeInstall(previous, previousOrigin) + " -> " + className(next) +
                                                 " (installed at " + origin.site + ")");
        }
    }

    private static void refuse(String call, Origin origin) {
        DefaultLoggerCreator.printDiagnostic("Logger locked: " + describeInstall(logger, installedBy) + " stays; " + call + " at " +
                                             origin.site + " is ignored");
    }

    private static String describeInstall(ILogger installed, Origin origin) {
        return className(installed) + (origin == null ? " (from the configuration)" : " (installed at " + origin.site + ")");
    }

    private static String className(ILogger installed) {
        return installed == null ? "null" : installed.getClass().getName();
    }

    /**
     * Skips the frames of logging helpers in caller detection, like those of {@code LOG}: an entry logged through
     * {@code Audit.record(...)} belongs to the code that called {@code Audit}, its source class, call site and rate
     * limit included. Nested classes of a skipped class are skipped too. For a library that wraps {@code LOG}; an
     * application can name its helpers and packages in {@code nanolog.caller.skip} instead. The classes add up, apply
     * to the whole JVM and stay skipped after {@link #init()}.
     *
     * @param classes the helpers; {@code null}s are ignored
     */
    public static void skipCaller(Class<?>... classes) {
        CallerDetection.skipClasses(classes);
    }

    /**
     * Writes the counts of repeats that the consoles of the installed logger still hold; for the end of the run.
     */
    static void flushRepeats() {
        flushRepeats(logger);
    }

    private static void flushRepeats(ILogger target) {
        if (target instanceof ConsoleLogger) {
            ((ConsoleLogger) target).flushRepeats();
        } else if (target instanceof CompositeLogger) {
            for (ILogger delegate : ((CompositeLogger) target).getLoggers()) {
                flushRepeats(delegate);
            }
        }
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
        if (WITHOUT_LEVEL_CONTROL.add(current.getClass()) || DefaultLoggerCreator.diagnostics == DefaultLoggerCreator.Diagnostics.ALL) {
            DefaultLoggerCreator.printDiagnostic("Logger " + current.getClass().getName() + " does not implement ILevelControl; " +
                                                 "LOG.setLevel, clearLevel, resetLevels and withLevel change nothing");
        }
        return null;
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
            writeDbgEntry(current, Dbg.entry(caller, values, 0, caller.boundaryOr(LogEntry.LOG_BOUNDARY)));
        }
    }

    /**
     * Whether a {@code dbg} call may print: not with {@code nanolog.dbg=off}, and only if {@code DEBUG} may be enabled,
     * except in agent mode, where it ignores the levels. Checked before anything is allocated or the stack is walked.
     */
    private static boolean mayPrintDbg(ILogger current) {
        return Dbg.mode != Dbg.Mode.OFF && (Dbg.ignoreLevels || mayTake(current, DEBUG));
    }

    /**
     * Whether a {@code dbg} call of a class prints: if {@code DEBUG} is enabled for it, or always in agent mode.
     */
    private static boolean printsDbg(ILogger current, Class sourceClass) {
        return Dbg.ignoreLevels || takes(current, DEBUG, sourceClass, null);
    }

    /**
     * Writes the entry of a {@code dbg} call, remembering its call site for the list printed at exit; with
     * {@code nanolog.dbg=fail}, reports the call first.
     */
    private static void writeDbgEntry(ILogger current, LogEntry entry) {
        Dbg.ran(entry.getCaller());
        if (Dbg.mode == Dbg.Mode.FAIL) {
            diagnose(current, describeSource(entry) + ": LOG.dbg call left in the code (nanolog.dbg=fail)");
        }
        write(current, entry);
    }

    // CONTEXT

    /**
     * Takes a pair of {@link #context(String, Object)} out of the context of the thread when closed; meant for
     * try-with-resources.
     */
    public interface Context extends AutoCloseable {

        /**
         * Puts back the value the key had before the scope opened, or removes the key if it had none; closing again
         * changes nothing. Close it in the thread that opened it, as try-with-resources does. Never throws.
         */
        @Override
        void close();
    }

    /**
     * Adds a pair to every entry the current thread logs until the returned scope is closed, so the lines of one
     * request, job or test can be found together:
     * <pre>{@code
     * try (LOG.Context c = LOG.context("requestId", request.getId())) {
     *     LOG.info("Charging {}", amount);
     *     // INFO 16.11.2026 10:15:02 [PaymentService] Charging 42 requestId=r-17
     * }
     * }</pre>
     * Every logger gets the pairs as {@link LogEntry#getContext()}. {@link ConsoleLogger} prints them after the message,
     * {@link JsonLogger} writes a {@code "context"} object, and {@link Slf4jLogger} puts them into the MDC while it
     * forwards the entry, so backend patterns such as {@code %X{requestId}} print them.
     * <p>
     * Scopes nest: an inner scope may add keys or set a key again, and closing it puts the previous value back. The
     * context belongs to the current thread only; a task handed to an executor takes it along through
     * {@link #wrap(Runnable)}. A value is kept as given and written like a {@code {}} argument when an entry is written;
     * numbers and booleans stay numbers and booleans in JSON.
     *
     * @param key   the key, a short name such as {@code requestId}; {@code null} changes nothing
     * @param value the value; may be {@code null}
     * @return the scope to close
     */
    public static Context context(String key, Object value) {
        return Contexts.put(key, value);
    }

    /**
     * Makes a task run with the context the current thread has now, in whatever thread runs it:
     * {@code executor.submit(LOG.wrap(task))}. The context is taken when {@code wrap} is called, replaces the context
     * of the thread that runs the task, and is removed when the task ends, also when it throws, so a pool thread keeps
     * nothing from it.
     * <p>
     * A lambda that returns a value, {@code () -> list.add(item)}, is a {@code Callable} to the compiler and gets
     * {@link #wrap(Callable)}; write a block, {@code () -> { list.add(item); }}, where a {@code Runnable} is needed.
     *
     * @param task the task
     * @return a task that runs {@code task} with the context of the current thread
     */
    public static Runnable wrap(Runnable task) {
        return Contexts.wrap(task);
    }

    /**
     * Makes a task that returns a value run with the context the current thread has now, in whatever thread runs it.
     * See {@link #wrap(Runnable)}.
     *
     * @param task the task
     * @param <T>  the type of the value
     * @return a task that calls {@code task} with the context of the current thread and returns its value
     */
    public static <T> Callable<T> wrap(Callable<T> task) {
        return Contexts.wrap(task);
    }

    /**
     * Adds the keys of a context of its own to every entry, such as the trace ID of OpenTelemetry or the tenant of a
     * request scope: {@code LOG.addContextProvider(() -> Collections.singletonMap("traceId", currentTraceId()))}.
     * <p>
     * The provider is asked when an entry is created, in the thread that logs, so only for an enabled level; keep it
     * cheap. Its keys come after the pairs of {@link #context(String, Object)}, which win over them, and after the keys of
     * the providers added before it, which win as well. A provider that throws is reported once, and the entry is logged
     * without its keys; an entry logged by a provider gets no keys from the providers. Adding a provider again changes
     * nothing. Providers apply to the whole JVM and stay after {@link #init()}; {@code nanolog.context.provider} names
     * provider classes in the configuration.
     *
     * @param provider the provider of keys and values; {@code null} is ignored, and so is a {@code null} map or key
     */
    public static void addContextProvider(Supplier<? extends Map<String, ?>> provider) {
        Contexts.addProvider(provider);
    }

    /**
     * Removes a provider of {@link #addContextProvider(Supplier)}.
     *
     * @param provider the provider added before; another one changes nothing
     */
    public static void removeContextProvider(Supplier<? extends Map<String, ?>> provider) {
        Contexts.removeProvider(provider);
    }

    // FIELDS

    /**
     * A named argument of a logging call, made by {@link LOG#kv(String, Object)}: a field of the entry. Where a
     * {@code {}} placeholder takes it, it prints as its value.
     */
    public static final class Field {

        private final String key;
        private final Object value;

        private Field(String key, Object value) {
            this.key = key;
            this.value = value;
        }

        /**
         * Returns the name of the field.
         *
         * @return the name; {@code null} if none was given, and then the argument is a plain one
         */
        public String getKey() {
            return key;
        }

        /**
         * Returns the value, as it was given.
         *
         * @return the value; may be {@code null}
         */
        public Object getValue() {
            return value;
        }

        /**
         * The value as a {@code {}} placeholder prints it: its guarded {@code toString()}, arrays element by element.
         * Never throws.
         *
         * @return the value as text
         */
        @Override
        public String toString() {
            return LogEntry.argToString(value);
        }
    }

    /**
     * Names an argument, so that it is a field of the entry as well, which scripts and agents can select on:
     * <pre>{@code
     * LOG.info("Loaded {} items from {}", LOG.kv("count", n), LOG.kv("source", src));
     * // INFO 16.11.2026 10:15:02 [Importer] Loaded 3 items from db
     * LOG.info("Payment accepted", LOG.kv("orderId", id));
     * // INFO 16.11.2026 10:15:02 [PaymentService] Payment accepted orderId=A-17
     * }</pre>
     * A {@code {}} placeholder prints the value like any other argument. A field that no placeholder takes is not an
     * extra argument, and strict mode does not report it: {@link ConsoleLogger} and {@link Slf4jLogger} append it to
     * the message as {@code key=value}. {@link JsonLogger} writes every field into a {@code "fields"} object, numbers
     * and booleans as JSON numbers and booleans, and every logger reads them from {@link LogEntry#getFields()}. An
     * exception goes after the fields: the last argument is the exception of the entry when no placeholder takes it.
     *
     * @param key   the name of the field, such as {@code orderId}; {@code null} makes the argument a plain one
     * @param value the value; may be {@code null}
     * @return the argument to pass to a logging method
     */
    public static Field kv(String key, Object value) {
        return new Field(key, value);
    }

    // UNITS OF WORK

    /**
     * A unit of work, opened by {@link LOG#unit(String)}: a request, a job, a step. Closing it writes one line with
     * its name, outcome, duration, fields and the counts of the warnings and errors logged inside it.
     */
    public interface Unit extends AutoCloseable {

        /**
         * Adds a field to the line of the unit; a key given again keeps its place and takes the new value. The fields
         * the unit sets itself ({@code unit}, {@code outcome}, {@code duration_ms}, {@code parent}, {@code warn},
         * {@code error} and {@code error.*}) and a {@code null} key are ignored, as is a field added after the unit
         * was closed. The value is written like a {@code {}} argument; JSON keeps numbers and booleans.
         *
         * @param key   the name of the field
         * @param value the value; may be {@code null}
         * @return this unit, for chaining
         */
        Unit put(String key, Object value);

        /**
         * Marks the unit as failed with an exception, which its line carries with its stack trace. Only the first
         * exception is kept; {@code null} changes nothing. An exception that leaves a try-with-resources block is not
         * seen by {@link #close()}: catch it and pass it here, or use {@link #run(Runnable)}.
         *
         * @param error the exception
         */
        void fail(Throwable error);

        /**
         * Runs a body inside the unit and closes it: an exception the body throws marks the unit as failed and is
         * thrown on.
         *
         * @param body the work of the unit
         */
        void run(Runnable body);

        /**
         * Runs a body that returns a value inside the unit and closes it: an exception the body throws marks the unit
         * as failed and is thrown on.
         *
         * @param body the work of the unit
         * @param <T>  the type of the value
         * @return the value of the body
         */
        <T> T run(Supplier<T> body);

        /**
         * Closes the unit and writes its line, once; closing again changes nothing. Close it in the thread that opened
         * it, as try-with-resources does. Never throws.
         */
        @Override
        void close();
    }

    /**
     * Opens a unit of work in the current thread, for one line per request or job, the canonical log line of wide
     * events:
     * <pre>{@code
     * try (LOG.Unit u = LOG.unit("checkout")) {
     *     u.put("orderId", order.getId()).put("items", cart.size());
     *     ...
     * }
     * // INFO 16.11.2026 10:15:02 [CheckoutService] checkout ok 182 ms orderId=A-17 items=3 warn=1
     * }</pre>
     * Closing the unit writes one entry, {@code <name> <outcome> <duration> ms}, whose fields are {@code unit},
     * {@code outcome}, {@code duration_ms}, the fields of {@link Unit#put}, and the counts {@code warn} and
     * {@code error} of the entries logged inside it. The outcome is {@code ok} at {@code INFO}, or {@code error} at
     * {@code ERROR} once an {@code ERROR} entry was logged inside or {@link Unit#fail} was called; a failed unit adds
     * {@code error.type}, {@code error.message} and {@code error.key}, a key of the template and call site of the first
     * error that stays the same between runs. The line has the source class and call site of this call and the
     * context of the thread that closes it.
     * <p>
     * Units nest: a unit opened inside another names it as {@code parent}, and an entry counts in every unit around it.
     * {@link #wrap(Runnable)} carries the open unit into a task for an executor. A unit still open when the run ends is
     * written with the outcome {@code interrupted}. {@code nanolog.unit.sample} writes only a share of the units that
     * succeed faster than {@code nanolog.unit.slowMs}; failed and slow ones are always written.
     *
     * @param name the name of the unit, such as {@code checkout}
     * @return the unit to close
     */
    public static Unit unit(String name) {
        return Units.open(name, findCaller());
    }

    /**
     * Runs a body as a unit of work: {@code LOG.unit(name).run(body)}. An exception the body throws fails the unit and
     * is thrown on. See {@link #unit(String)}.
     *
     * @param name the name of the unit
     * @param body the work of the unit
     */
    public static void unit(String name, Runnable body) {
        Units.open(name, findCaller()).run(body);
    }

    /**
     * Runs a body that returns a value as a unit of work: {@code LOG.unit(name).run(body)}. An exception the body
     * throws fails the unit and is thrown on. See {@link #unit(String)}.
     *
     * @param name the name of the unit
     * @param body the work of the unit
     * @param <T>  the type of the value
     * @return the value of the body
     */
    public static <T> T unit(String name, Supplier<T> body) {
        return Units.open(name, findCaller()).run(body);
    }

    /**
     * Writes the line of a unit, if its level is enabled for the class that opened the unit.
     */
    static void writeUnit(LogEntry.LogEntryLevel level, Class sourceClass, StackTraceElement frame, Throwable throwable, Object[] args) {
        ILogger current = logger;
        if (isEnabled(current, level, sourceClass)) {
            write(current, LogEntry.createUnitLine(level, sourceClass, frame, throwable, args));
        }
    }

    // PAYLOADS

    /**
     * A payload of a logging call, made by {@link LOG#content(Object)}: a prompt, a response, a request body, a
     * document. Written as its length and hash unless {@code nanolog.content} shows it.
     */
    public static final class Content {

        private final Object value;
        /**
         * The text, taken when the payload was bound to an entry; {@code null} before.
         */
        private final String text;
        private final int limit;

        private Content(Object value, String text, int limit) {
            this.value = value;
            this.text = text;
            this.limit = limit;
        }

        /**
         * This payload bound to the limit of the class of an entry, with its text taken now, so that the entry keeps
         * what the value was when it was logged; a bound payload stays as it is.
         */
        Content bind(Class sourceClass) {
            return isBound() ? this : new Content(null, LogEntry.argToString(value), Contents.limit(sourceClass));
        }

        boolean isBound() {
            return text != null;
        }

        /**
         * The text of the value, as a {@code {}} argument writes it.
         */
        String text() {
            return isBound() ? text : LogEntry.argToString(value);
        }

        /**
         * The limit of the class of the entry, or of {@code nanolog.content} for a payload outside an entry.
         */
        int limit() {
            return isBound() ? limit : Contents.defaultLimit;
        }

        /**
         * The payload as loggers write it: {@code [content 1834 chars #3f2a9c1b]} when hidden, the start of the text
         * with {@code ... [+1754 chars]} when truncated, all of it when shown; line breaks and control characters
         * escaped. Never throws.
         *
         * @return the text to write
         */
        @Override
        public String toString() {
            return Contents.render(text(), limit());
        }
    }

    /**
     * Marks a payload, such as an LLM prompt, a response or a request body, so that the log shows it only on purpose:
     * <pre>{@code
     * LOG.debug("Prompt to {}: {}", model, LOG.content(prompt));
     * // by default:                       Prompt to gpt-x: [content 1834 chars #3f2a9c1b]
     * // with nanolog.content=truncate:20: Prompt to gpt-x: You are a helpful as... [+1814 chars]
     * // with nanolog.content=show:        Prompt to gpt-x: You are a helpful assistant.\nAnswer briefly. ...
     * }</pre>
     * By default a payload is its length and a hash of eight hexadecimal digits, so equal payloads can be told apart
     * without their text within one run; the hash is keyed with a random key of the JVM, so the log does not give a
     * short payload away to someone who tries candidates. {@code nanolog.content} is {@code hide}, {@code truncate:<characters>} or {@code show}, and
     * {@code nanolog.content.<package or class>} sets it for a package or a class, the most specific rule winning, as
     * with levels. Whatever is written of the text has its line breaks and control characters escaped, so a payload
     * cannot forge a log line. The text is taken when the entry is created, also for a payload in the context of
     * {@link #context(String, Object)}, which follows the rule of the class of the entry. In the {@code "fields"} of
     * {@link JsonLogger} a hidden payload is {@code {"content_len":1834,"content_hash":"3f2a9c1b"}}.
     *
     * @param value the payload, written like a {@code {}} argument; may be {@code null}
     * @return the argument to pass to a logging method, or to {@link #kv(String, Object)} as a value
     */
    public static Content content(Object value) {
        return new Content(value, null, 0);
    }

    // CATCHING

    /**
     * An action that may throw a checked exception, for {@link LOG#catching(ThrowingRunnable)}.
     */
    @FunctionalInterface
    public interface ThrowingRunnable {

        /**
         * Runs the action.
         *
         * @throws Exception whatever the action throws
         */
        void run() throws Exception;
    }

    /**
     * A computation that may throw a checked exception, for {@link LOG#catching(ThrowingSupplier, Object)}.
     *
     * @param <T> the type of the value
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {

        /**
         * Computes the value.
         *
         * @return the value
         * @throws Exception whatever the computation throws
         */
        T get() throws Exception;
    }

    /**
     * Runs an action and logs an exception it throws instead of passing it on: the "log and carry on" block, for work
     * whose failure must not stop the caller, such as cleanup or a best-effort notification:
     * {@code LOG.catching(() -> connection.close())}. The exception is logged at {@code ERROR} with its stack trace and
     * the message {@code Caught and ignored}, from the class and call site of this call. An {@code Error} is not caught.
     * An {@code InterruptedException} is caught and the interrupt flag of the thread set again.
     *
     * @param action the action; checked exceptions are allowed
     */
    public static void catching(ThrowingRunnable action) {
        try {
            action.run();
        } catch (Exception e) {
            caught(e, "Caught and ignored", NO_ARGS);
        }
    }

    /**
     * Computes a value and returns a default instead of passing an exception on: {@code LOG.catching(() ->
     * Integer.parseInt(text), -1)}. The exception is logged as by {@link #catching(ThrowingRunnable)}, with the message
     * {@code Caught, returning <default>}.
     *
     * @param action       the computation; checked exceptions are allowed
     * @param defaultValue the value returned when the computation throws an exception
     * @param <T>          the type of the value
     * @return the value of the computation, or {@code defaultValue}
     */
    public static <T> T catching(ThrowingSupplier<T> action, T defaultValue) {
        try {
            return action.get();
        } catch (Exception e) {
            caught(e, "Caught, returning {}", new Object[]{defaultValue});
            return defaultValue;
        }
    }

    private static void caught(Exception e, String message, Object[] args) {
        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        logFromCaller(ERROR, e, () -> message, args);
    }

    /**
     * Logs the exceptions that kill threads: installs a default uncaught exception handler that logs each one at
     * {@code ERROR}, as {@code Uncaught exception in thread pool-1-thread-3}, with its stack trace, in the format of the
     * installed logger, and then passes it to the default handler installed before, so test frameworks and existing
     * handlers still see the failure. Without a handler before, the JVM no longer prints the exception itself. The
     * source class and call site of the entry are those of the first frame of the stack trace outside the JDK.
     * <p>
     * The dying thread first writes its breadcrumbs of {@code nanolog.buffer}, then the exception, then its units of
     * {@link #unit(String)} still open, as {@code interrupted}. Installing again changes nothing. Call it once at
     * application startup, like {@link #init(ILogger)}.
     */
    public static void installUncaughtExceptionHandler() {
        Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
        if (!(current instanceof Uncaught)) {
            Thread.setDefaultUncaughtExceptionHandler(new Uncaught(current));
        }
    }

    /**
     * Writes the exception that kills a thread, for {@link Uncaught}: the breadcrumbs of the thread, the exception,
     * then the units still open in it.
     */
    static void writeUncaught(Thread thread, Throwable error) {
        ILogger current = logger;
        deliver(current, Breadcrumbs.drainThread());
        CallerDetection.Caller origin = Uncaught.origin(error);
        doLog(current, ERROR, origin.sourceClass, origin.frame, LogEntry.LOG_BOUNDARY, error, () -> "Uncaught exception in thread {}",
              new Object[]{thread.getName()});
        Units.interruptThread();
    }

    // TIMERS

    /**
     * A running timer of {@link LOG#time(String)}; closing it writes the line.
     */
    public interface Timer extends AutoCloseable {

        /**
         * Writes {@code <name> took <duration> ms}, once; closing again changes nothing. Never throws.
         */
        @Override
        void close();
    }

    /**
     * Times a block at {@code INFO}: {@code try (LOG.Timer t = LOG.time("Load users")) { ... }} writes
     * {@code Load users took 123 ms} when it closes, with the fields {@code timer} and {@code duration_ms}, as a unit of
     * {@link #unit(String)} writes its duration. See {@link #time(LogEntry.LogEntryLevel, String)}.
     *
     * @param name what is timed
     * @return the timer to close
     */
    public static Timer time(String name) {
        return time(INFO, name);
    }

    /**
     * Times a block at a level: the line has the source class and call site of this call, and a level that is off for
     * the class reads no clock and writes nothing, so a timer left in a hot path costs what a disabled call costs. The
     * clock is that of {@link #atMostEvery}. An exception that leaves a try-with-resources block is not seen; use
     * {@link #timed(String, Supplier)} to have it reported.
     *
     * @param level the level of the line; {@code null} writes nothing
     * @param name  what is timed
     * @return the timer to close
     */
    public static Timer time(LogEntry.LogEntryLevel level, String name) {
        ILogger current = logger;
        if (level == null || !mayBeEnabled(current, level)) {
            return Timers.NOTHING;
        }
        CallerDetection.Caller caller = findCaller();
        if (!isEnabled(current, level, caller.sourceClass)) {
            return Timers.NOTHING;
        }
        return new Timers.Timing(level, String.valueOf(name), caller, nanoClock.getAsLong());
    }

    /**
     * Times a body at {@code INFO}: {@code LOG.timed("Warm up", () -> cache.load())}. An exception the body throws is
     * written as {@code <name> failed after <duration> ms} with its stack trace and thrown on.
     *
     * @param name what is timed
     * @param body the work to time
     */
    public static void timed(String name, Runnable body) {
        Timers.run(time(INFO, name), () -> {
            body.run();
            return null;
        });
    }

    /**
     * Times a body that returns a value at {@code INFO}: {@code int n = LOG.timed("Count users", () -> repo.count())}.
     * An exception the body throws is written as {@code <name> failed after <duration> ms} with its stack trace and
     * thrown on.
     *
     * @param name what is timed
     * @param body the work to time
     * @param <T>  the type of the value
     * @return the value of the body
     */
    public static <T> T timed(String name, Supplier<T> body) {
        return Timers.run(time(INFO, name), body);
    }

    /**
     * Writes the line of a timer, if its level is still enabled for its class.
     */
    static void writeTimer(LogEntry.LogEntryLevel level, Class sourceClass, StackTraceElement frame, Throwable error, String template,
                           Object[] args) {
        ILogger current = logger;
        if (isEnabled(current, level, sourceClass)) {
            write(current, LogEntry.create(level, sourceClass, frame, error, template, args, 0, Timers.Timing.class.getName()));
        }
    }

    // SECRETS

    /**
     * A secret in a logging call, made by {@link LOG#secret(Object)}: it is written as {@code ***} by every logger, in
     * the message, the arguments, the fields and the context.
     */
    public static final class Secret {

        private Secret() {
        }

        /**
         * Returns {@code ***}.
         *
         * @return {@code ***}
         */
        @Override
        public String toString() {
            return "***";
        }
    }

    /**
     * The only {@link Secret}: it keeps no value.
     */
    static final Secret SECRET = new Secret();

    /**
     * Marks a secret, such as a token or a password, so that no logger writes it:
     * {@code LOG.info("Login of {} with {}", user, LOG.secret(token))} writes {@code Login of alice with ***}. The
     * value is not kept and never formatted, so its {@code toString()} is not called. A field or a context value whose
     * key names a secret ({@code nanolog.redact}) is masked the same way without it.
     *
     * @param value the secret; it is thrown away
     * @return the argument to pass to a logging method
     */
    public static Secret secret(Object value) {
        return SECRET;
    }

    // LAZY ARGUMENTS

    /**
     * An argument computed only for an enabled level, made by {@link LOG#lazy(Supplier)}. Its {@code toString()} asks
     * the supplier the first time and keeps the text; the entry calls it when it is created, after the level check.
     */
    public static final class Lazy {

        private Supplier<?> supplier;
        private String text;

        private Lazy(Supplier<?> supplier) {
            this.supplier = supplier;
        }

        /**
         * Returns the value of the supplier, formatted like any argument, asking the supplier only the first time. A
         * supplier that throws gives {@code [FAILED supplier: <exception>]}, kept the same way.
         *
         * @return the text of the value
         */
        @Override
        public synchronized String toString() {
            if (text == null) {
                text = evaluate(supplier);
                supplier = null;
            }
            return text;
        }

        private static String evaluate(Supplier<?> supplier) {
            try {
                return LogEntry.argToString(supplier == null ? null : supplier.get());
            } catch (Throwable t) {
                return "[FAILED supplier: " + LogEntry.safeToString(t) + "]";
            }
        }
    }

    /**
     * Makes one argument lazy: {@code LOG.debug("Stats: {}", LOG.lazy(() -> summarize(stats)))} calls
     * {@code summarize} only when the level is enabled, in the thread that logs, and only once however many loggers
     * format the entry. Unlike a lazy message, {@code LOG.debug(() -> "Stats: " + summarize(stats))}, the template
     * stays a template: strict mode checks it, {@link JsonLogger} writes it with its arguments, and
     * {@code MemoryLogger.assertTemplateLogged} finds it, all without a second level check.
     * <p>
     * There are no overloads that take a {@code Supplier} for the arguments: a lambda has no type as an
     * {@code Object}, and such overloads would make calls ambiguous, from Kotlin as well.
     *
     * @param supplier computes the value; {@code null} is the value {@code null}
     * @return the argument to pass to a logging method
     */
    public static Lazy lazy(Supplier<?> supplier) {
        return new Lazy(supplier);
    }


    // TAGS

    /**
     * The level methods of a tag, returned by {@link LOG#tag(String)}: every entry they log carries the tag. The view
     * has the four shapes of {@link Limited}, and {@link #once()} and {@link #atMostEvery(long, TimeUnit)} limit a
     * tagged statement.
     */
    public static final class Tagged {

        /**
         * Calls of these methods pass through this class, so a location-aware SLF4J backend must look past it, not
         * only past {@code LOG}, to find the call site.
         */
        private static final String CALLER_BOUNDARY = Tagged.class.getName();

        private final String tag;

        private Tagged(String tag) {
            this.tag = tag;
        }

        private void log(LogEntry.LogEntryLevel level, Throwable throwable, Supplier<Object> message, Object[] args) {
            ILogger current = logger;
            if (mayBeEnabled(current, level)) {
                CallerDetection.Caller caller = findCaller();
                if (isEnabled(current, level, caller.sourceClass, tag)) {
                    writeCall(current, LogEntry.create(level, caller.sourceClass, caller.frame, throwable, resolve(message), args, 0,
                                                       caller.boundaryOr(CALLER_BOUNDARY), tag));
                }
            }
        }

        /**
         * The tagged statement logged only the first time it runs, see {@link LOG#once()}.
         *
         * @return the level methods of the statement
         */
        public Limited once() {
            return new Limited(Long.MAX_VALUE, tag);
        }

        /**
         * The tagged statement logged at most once per period, see {@link LOG#atMostEvery(long, TimeUnit)}.
         *
         * @param period the shortest time between two entries of the statement; zero or less logs every call
         * @param unit   the unit of the period; {@code null} logs every call
         * @return the level methods of the statement
         */
        public Limited atMostEvery(long period, TimeUnit unit) {
            return new Limited(unit == null ? 0 : unit.toNanos(period), tag);
        }

        /**
         * Logs a parameterized message with the tag at {@code TRACE} level.
         *
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
         */
        public void trace(String message, Object... args) {
            log(TRACE, null, () -> message, args);
        }

        /**
         * Logs a lazily built message with the tag at {@code TRACE} level.
         *
         * @param message builds the message; called only if the level is enabled for the tag
         */
        public void trace(Supplier<Object> message) {
            log(TRACE, null, message, NO_ARGS);
        }

        /**
         * Logs an exception and a parameterized message with the tag at {@code TRACE} level.
         *
         * @param t       the exception, logged with its stack trace
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders
         */
        public void trace(Throwable t, String message, Object... args) {
            log(TRACE, t, () -> message, args);
        }

        /**
         * Logs an exception and a lazily built message with the tag at {@code TRACE} level.
         *
         * @param t       the exception, logged with its stack trace
         * @param message builds the message; called only if the level is enabled for the tag
         */
        public void trace(Throwable t, Supplier<Object> message) {
            log(TRACE, t, message, NO_ARGS);
        }

        /**
         * Logs a parameterized message with the tag at {@code DEBUG} level.
         *
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
         */
        public void debug(String message, Object... args) {
            log(DEBUG, null, () -> message, args);
        }

        /**
         * Logs a lazily built message with the tag at {@code DEBUG} level.
         *
         * @param message builds the message; called only if the level is enabled for the tag
         */
        public void debug(Supplier<Object> message) {
            log(DEBUG, null, message, NO_ARGS);
        }

        /**
         * Logs an exception and a parameterized message with the tag at {@code DEBUG} level.
         *
         * @param t       the exception, logged with its stack trace
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders
         */
        public void debug(Throwable t, String message, Object... args) {
            log(DEBUG, t, () -> message, args);
        }

        /**
         * Logs an exception and a lazily built message with the tag at {@code DEBUG} level.
         *
         * @param t       the exception, logged with its stack trace
         * @param message builds the message; called only if the level is enabled for the tag
         */
        public void debug(Throwable t, Supplier<Object> message) {
            log(DEBUG, t, message, NO_ARGS);
        }

        /**
         * Logs a parameterized message with the tag at {@code INFO} level.
         *
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
         */
        public void info(String message, Object... args) {
            log(INFO, null, () -> message, args);
        }

        /**
         * Logs a lazily built message with the tag at {@code INFO} level.
         *
         * @param message builds the message; called only if the level is enabled for the tag
         */
        public void info(Supplier<Object> message) {
            log(INFO, null, message, NO_ARGS);
        }

        /**
         * Logs an exception and a parameterized message with the tag at {@code INFO} level.
         *
         * @param t       the exception, logged with its stack trace
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders
         */
        public void info(Throwable t, String message, Object... args) {
            log(INFO, t, () -> message, args);
        }

        /**
         * Logs an exception and a lazily built message with the tag at {@code INFO} level.
         *
         * @param t       the exception, logged with its stack trace
         * @param message builds the message; called only if the level is enabled for the tag
         */
        public void info(Throwable t, Supplier<Object> message) {
            log(INFO, t, message, NO_ARGS);
        }

        /**
         * Logs a parameterized message with the tag at {@code WARN} level.
         *
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
         */
        public void warn(String message, Object... args) {
            log(WARN, null, () -> message, args);
        }

        /**
         * Logs a lazily built message with the tag at {@code WARN} level.
         *
         * @param message builds the message; called only if the level is enabled for the tag
         */
        public void warn(Supplier<Object> message) {
            log(WARN, null, message, NO_ARGS);
        }

        /**
         * Logs an exception and a parameterized message with the tag at {@code WARN} level.
         *
         * @param t       the exception, logged with its stack trace
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders
         */
        public void warn(Throwable t, String message, Object... args) {
            log(WARN, t, () -> message, args);
        }

        /**
         * Logs an exception and a lazily built message with the tag at {@code WARN} level.
         *
         * @param t       the exception, logged with its stack trace
         * @param message builds the message; called only if the level is enabled for the tag
         */
        public void warn(Throwable t, Supplier<Object> message) {
            log(WARN, t, message, NO_ARGS);
        }

        /**
         * Logs a parameterized message with the tag at {@code ERROR} level.
         *
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders; a trailing {@link Throwable} that no placeholder consumes becomes the exception of the entry
         */
        public void error(String message, Object... args) {
            log(ERROR, null, () -> message, args);
        }

        /**
         * Logs a lazily built message with the tag at {@code ERROR} level.
         *
         * @param message builds the message; called only if the level is enabled for the tag
         */
        public void error(Supplier<Object> message) {
            log(ERROR, null, message, NO_ARGS);
        }

        /**
         * Logs an exception and a parameterized message with the tag at {@code ERROR} level.
         *
         * @param t       the exception, logged with its stack trace
         * @param message the message with {@code {}} placeholders
         * @param args    the arguments for the placeholders
         */
        public void error(Throwable t, String message, Object... args) {
            log(ERROR, t, () -> message, args);
        }

        /**
         * Logs an exception and a lazily built message with the tag at {@code ERROR} level.
         *
         * @param t       the exception, logged with its stack trace
         * @param message builds the message; called only if the level is enabled for the tag
         */
        public void error(Throwable t, Supplier<Object> message) {
            log(ERROR, t, message, NO_ARGS);
        }
    }

    /**
     * Tags the entries of a concern rather than of a class, such as audit, security or SQL:
     * {@code LOG.tag("audit").info("User {} changed role to {}", user, role)}. {@link ConsoleLogger} writes
     * {@code [audit]} after the source class, {@link JsonLogger} {@code "tags":["audit"]}, {@link Slf4jLogger} passes
     * an SLF4J {@code Marker}, and any logger reads {@link LogEntry#getTags()}. {@code nanolog.tags=audit=WARN,sql=DEBUG}
     * sets levels per tag, which decide over the levels of the classes.
     *
     * @param tag the tag; {@code null} logs without one
     * @return the level methods of the tag
     */
    public static Tagged tag(String tag) {
        return new Tagged(tag);
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
        return new Limited(unit == null ? 0 : unit.toNanos(period), null);
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

        static final Limited ONCE = new Limited(Long.MAX_VALUE, null);

        /**
         * Calls of these methods pass through this class, so a location-aware SLF4J backend must look past it, not
         * only past {@code LOG}, to find the call site.
         */
        private static final String CALLER_BOUNDARY = Limited.class.getName();

        private final long periodNanos;

        /**
         * The tag of the entries, from {@link Tagged}; {@code null} for none.
         */
        private final String tag;

        private Limited(long periodNanos, String tag) {
            this.periodNanos = periodNanos;
            this.tag = tag;
        }

        private void log(LogEntry.LogEntryLevel level, Throwable throwable, Supplier<Object> message, Object[] args) {
            ILogger current = logger;
            if (mayBeEnabled(current, level)) {
                CallerDetection.Caller caller = findCaller();
                long skipped = admit(isEnabled(current, level, caller.sourceClass, tag), caller);
                if (skipped >= 0) {
                    writeCall(current, LogEntry.create(level, caller.sourceClass, caller.frame, throwable, resolve(message), args, skipped,
                                                       caller.boundaryOr(CALLER_BOUNDARY), tag));
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
                writeDbgEntry(current, Dbg.entry(caller, values, skipped, caller.boundaryOr(CALLER_BOUNDARY)));
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
        doLog(logger, level, loggedClass, null, LogEntry.LOG_BOUNDARY, throwable, message, args);
    }

    /**
     * Logs with the source class detected from the stack. The level is checked against every class first, so a level
     * the logger has off everywhere costs no stack walk.
     */
    private static void logFromCaller(LogEntry.LogEntryLevel level, Throwable throwable, Supplier<Object> message, Object... args) {
        ILogger current = logger;
        if (mayBeEnabled(current, level)) {
            CallerDetection.Caller caller = findCaller();
            doLog(current, level, caller.sourceClass, caller.frame, caller.boundaryOr(LogEntry.LOG_BOUNDARY), throwable, message, args);
        }
    }

    private static boolean isEnabledForCaller(LogEntry.LogEntryLevel level) {
        ILogger current = logger;
        return mayBeEnabled(current, level) && isEnabled(current, level, findCaller().sourceClass);
    }

    private static void doLog(ILogger current, LogEntry.LogEntryLevel level, Class loggedClass, StackTraceElement frame, String boundary,
                              Throwable throwable, Supplier<Object> message, Object[] args) {
        if (isEnabled(current, level, loggedClass)) {
            writeCall(current, LogEntry.create(level, loggedClass, frame, throwable, resolve(message), args, 0, boundary));
        }
    }

    /**
     * Hands the entry of a logging call to the logger after strict mode has checked it. The lines that Nanolog makes
     * itself, of timers and units, skip the check: their templates are Nanolog's.
     */
    private static void writeCall(ILogger current, LogEntry entry) {
        if (strict) {
            checkCall(current, entry);
        }
        write(current, entry);
    }

    /**
     * Hands an entry whose level is enabled to the logger, and writes it to {@code System.err} if the logger throws.
     */
    private static void write(ILogger current, LogEntry entry) {
        if (Summary.mode != Summary.Mode.OFF) {
            Summary.count(entry);
        }
        Units.Scope unit = entry.getUnit();
        if (unit != null) {
            unit.count(entry);
        }
        List<MemoryLogger> captures = captures();
        if (captures != null) {
            // the levels may have been passed only for a capture that takes every level: ask the logger itself
            boolean accepted = entry.isDbg() && Dbg.ignoreLevels || Breadcrumbs.buffers(entry) ||
                               accepts(current, entry.getLevel(), entry.getSourceClass(), entry.getTag());
            for (MemoryLogger capture : captures) {
                if (capture.everyLevel || accepted) {
                    capture.log(entry);
                }
            }
            if (!accepted || keeps(captures, current)) {
                return;
            }
        }
        if (Breadcrumbs.buffers(entry)) {
            Breadcrumbs.keep(entry);
            return;
        }
        if (entry.getLevel() == ERROR && entry.getUnit() == null) {
            // outside units the error brings the breadcrumbs of its thread along; a unit writes its own when it fails
            deliver(current, Breadcrumbs.drainThread());
        }
        deliver(current, entry);
    }

    /**
     * Writes the breadcrumbs of a failed unit to the installed logger: they were counted when they were held back.
     */
    static void deliver(List<LogEntry> entries) {
        deliver(logger, entries);
    }

    private static void deliver(ILogger current, List<LogEntry> entries) {
        for (LogEntry entry : entries) {
            deliver(current, entry);
        }
    }

    /**
     * Hands an entry to the logger, and to {@code System.err} if the logger throws.
     */
    private static void deliver(ILogger current, LogEntry entry) {
        // while System.out is captured, what the logger prints passes through instead of coming back as an entry
        boolean marked = SystemStreams.enterLogging();
        try {
            current.log(entry);
        } catch (Throwable t) {
            reportLoggerFailure(current, t, FALLBACK_NOTE);
            Fallback.LOGGER.log(entry);
        } finally {
            if (marked) {
                SystemStreams.leaveLogging();
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
     * enabled, so the entry still gets a chance to be written. A level held back by {@code nanolog.buffer} is enabled
     * too.
     */
    private static boolean isEnabled(ILogger current, LogEntry.LogEntryLevel level, Class sourceClass) {
        return isEnabled(current, level, sourceClass, null);
    }

    /**
     * Like {@link #isEnabled(ILogger, LogEntry.LogEntryLevel, Class)}, for an entry with a tag.
     */
    private static boolean isEnabled(ILogger current, LogEntry.LogEntryLevel level, Class sourceClass, String tag) {
        return Breadcrumbs.buffers(level) || takes(current, level, sourceClass, tag);
    }

    /**
     * Whether a capture or the logger itself takes a level, whatever {@code nanolog.buffer} holds back; for
     * {@code LOG.dbg}, which is never held back.
     */
    private static boolean takes(ILogger current, LogEntry.LogEntryLevel level, Class sourceClass, String tag) {
        return takesEveryLevel(captures()) || accepts(current, level, sourceClass, tag);
    }

    /**
     * Asks the logger itself whether it takes an entry, with the same guard as {@link #isEnabled}.
     */
    private static boolean accepts(ILogger current, LogEntry.LogEntryLevel level, Class sourceClass, String tag) {
        try {
            return current.isEnabled(level, sourceClass, tag);
        } catch (Throwable t) {
            reportLoggerFailure(current, t, FALLBACK_NOTE);
            return true;
        }
    }

    /**
     * Asks a logger whether a level may be enabled for any class, with the same guard as {@link #isEnabled}; a level
     * held back by {@code nanolog.buffer} may be.
     */
    private static boolean mayBeEnabled(ILogger current, LogEntry.LogEntryLevel level) {
        return Breadcrumbs.buffers(level) || mayTake(current, level);
    }

    /**
     * Like {@link #mayBeEnabled}, whatever {@code nanolog.buffer} holds back; for {@code LOG.dbg}.
     */
    private static boolean mayTake(ILogger current, LogEntry.LogEntryLevel level) {
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
     * class are silent, unless {@code nanolog.diagnostics=all}. Also used by {@link CompositeLogger} for its delegates.
     */
    static void reportLoggerFailure(ILogger failed, Throwable t, String consequence) {
        if (FAILED_LOGGERS.add(failed.getClass()) || DefaultLoggerCreator.diagnostics == DefaultLoggerCreator.Diagnostics.ALL) {
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
        DefaultLoggerCreator.forgetDiagnostics();
    }

    /**
     * Writes the entries a failing logger could not write: the {@link ConsoleLogger} format, every level to
     * {@code System.err}. Created on first use.
     */
    private static final class Fallback {

        static final ConsoleLogger LOGGER = new ConsoleLogger() {
            @Override
            protected PrintStream getOutputStream(LogEntry entry) {
                return SystemStreams.err();
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
     * Strict mode: reports a call whose {@code {}} placeholders do not match its arguments, an argument that is the
     * message of the exception of the entry, and a call site seen with two templates. Only calls with arguments are
     * checked, so a plain message that happens to contain {@code {}}, or that is built anew each time, is never
     * reported. The entry itself is logged unchanged, and nothing is thrown.
     */
    private static void checkCall(ILogger current, LogEntry entry) {
        Object[] args = entry.getArgs();
        if (args == null || args.length == 0) {
            return;
        }
        String template = LogEntry.safeToString(entry.getMessage());
        int placeholders = LogEntry.countPlaceholders(template);
        if (placeholders != args.length) {
            String diagnostic = describeSource(entry) + ": " + count(placeholders, "placeholder") + ", " +
                                count(args.length, "argument") + " in \"" + template + "\"";
            diagnose(current, diagnostic);
        }
        checkExceptionMessage(current, entry, args);
        checkConstantTemplate(current, entry, template);
    }

    /**
     * Reports each argument that is the message of the exception of the entry: the stack trace prints it already. An
     * exception without a message, or whose {@code getMessage()} throws, is not checked.
     */
    private static void checkExceptionMessage(ILogger current, LogEntry entry, Object[] args) {
        Throwable throwable = entry.getThrowable();
        if (throwable == null) {
            return;
        }
        String message;
        try {
            message = throwable.getMessage();
        } catch (Throwable t) {
            return;
        }
        if (message == null || message.isEmpty()) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            if (message.equals(args[i])) {
                diagnose(current, describeSource(entry) + ": argument " + (i + 1) + " repeats the message of the exception");
            }
        }
    }

    /**
     * The first template of each call site checked in strict mode. Call sites are bounded by the size of the code, as
     * in {@link #LIMITS}.
     */
    private static final Map<StackTraceElement, String> TEMPLATES = new ConcurrentHashMap<>();

    /**
     * Reports a call whose template differs from the first one of its call site: a template built from data, which
     * groups nothing. The text names the first template, so {@code System.err} reports a call site once. A call with
     * an explicit class has no call site and is not checked.
     */
    private static void checkConstantTemplate(ILogger current, LogEntry entry, String template) {
        StackTraceElement site = entry.getCaller();
        if (site == null) {
            return;
        }
        String first = TEMPLATES.putIfAbsent(site, template);
        if (first != null && !first.equals(template)) {
            diagnose(current, describeSource(entry) + ": the template is not a constant: \"" + first + "\"");
        }
    }

    /**
     * Reports a finding: to {@code System.err} as {@code nanolog.diagnostics} says, once by default, so a loop does not
     * flood it, and to the logger every time, since a test must see a finding that an earlier test already printed.
     */
    private static void diagnose(ILogger current, String diagnostic) {
        DefaultLoggerCreator.printDiagnostic(diagnostic);
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
