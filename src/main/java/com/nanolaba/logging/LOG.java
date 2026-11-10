package com.nanolaba.logging;

import java.util.function.Supplier;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.*;

public class LOG {

    private static ILogger logger;

    static {
        init();
    }

    private LOG() {/**/}

    public static void init() {
        LOG.logger = DefaultLoggerCreator.createDefaultLogger();
    }

    public static void init(ILogger logger) {
        LOG.logger = logger;
    }

    public static ILogger getLogger() {
        return logger;
    }

    // TRACE
    public static void trace(Class targetClass, Throwable t, Supplier<Object> message) {
        log(TRACE, targetClass, t, message);
    }

    public static void trace(Class targetClass, Throwable t, Object message) {
        log(TRACE, targetClass, t, () -> message);
    }

    public static void trace(Class targetClass, Supplier<Object> message) {
        log(TRACE, targetClass, null, message);
    }

    public static void trace(Class targetClass, Object message) {
        log(TRACE, targetClass, null, () -> message);
    }

    public static void trace(Class targetClass, String message, Object... args) {
        log(TRACE, targetClass, null, () -> message, args);
    }

    public static void trace(Class targetClass, Throwable throwable) {
        log(TRACE, targetClass, throwable, null);
    }

    public static void trace(Throwable t, Supplier<Object> message) {
        logFromCaller(TRACE, findCaller(), t, message);
    }

    public static void trace(Throwable t, Object message) {
        logFromCaller(TRACE, findCaller(), t, () -> message);
    }

    public static void trace(Throwable t) {
        logFromCaller(TRACE, findCaller(), t, null);
    }

    public static void trace(Supplier<Object> message) {
        logFromCaller(TRACE, findCaller(), null, message);
    }

    public static void trace(Object message) {
        logFromCaller(TRACE, findCaller(), null, () -> message);
    }

    public static void trace(String message, Object... args) {
        logFromCaller(TRACE, findCaller(), null, () -> message, args);
    }

    public static void trace(Throwable t, String message, Object... args) {
        logFromCaller(TRACE, findCaller(), t, () -> message, args);
    }

    public static void trace(Class targetClass, Throwable t, String message, Object... args) {
        log(TRACE, targetClass, t, () -> message, args);
    }

    public static boolean isTraceEnabled(Class targetClass) {
        return logger.isEnabled(TRACE, targetClass);
    }

    public static boolean isTraceEnabled() {
        return logger.isEnabled(TRACE, getCurrentClass());
    }

    // DEBUG
    public static void debug(Class targetClass, Throwable t, Supplier<Object> message) {
        log(DEBUG, targetClass, t, message);
    }

    public static void debug(Class targetClass, Throwable t, Object message) {
        log(DEBUG, targetClass, t, () -> message);
    }

    public static void debug(Class targetClass, Supplier<Object> message) {
        log(DEBUG, targetClass, null, message);
    }

    public static void debug(Class targetClass, Object message) {
        log(DEBUG, targetClass, null, () -> message);
    }

    public static void debug(Class targetClass, String message, Object... args) {
        log(DEBUG, targetClass, null, () -> message, args);
    }

    public static void debug(Class targetClass, Throwable throwable) {
        log(DEBUG, targetClass, throwable, null);
    }

    public static void debug(Throwable t, Supplier<Object> message) {
        logFromCaller(DEBUG, findCaller(), t, message);
    }

    public static void debug(Throwable t, Object message) {
        logFromCaller(DEBUG, findCaller(), t, () -> message);
    }

    public static void debug(Throwable t) {
        logFromCaller(DEBUG, findCaller(), t, null);
    }

    public static void debug(Supplier<Object> message) {
        logFromCaller(DEBUG, findCaller(), null, message);
    }

    public static void debug(Object message) {
        logFromCaller(DEBUG, findCaller(), null, () -> message);
    }

    public static void debug(String message, Object... args) {
        logFromCaller(DEBUG, findCaller(), null, () -> message, args);
    }

    public static void debug(Throwable t, String message, Object... args) {
        logFromCaller(DEBUG, findCaller(), t, () -> message, args);
    }

    public static void debug(Class targetClass, Throwable t, String message, Object... args) {
        log(DEBUG, targetClass, t, () -> message, args);
    }

    public static boolean isDebugEnabled(Class targetClass) {
        return logger.isEnabled(DEBUG, targetClass);
    }

    public static boolean isDebugEnabled() {
        return logger.isEnabled(DEBUG, getCurrentClass());
    }

    // INFO
    public static void info(Class targetClass, Throwable t, Supplier<Object> message) {
        log(INFO, targetClass, t, (message));
    }

    public static void info(Class targetClass, Throwable t, Object message) {
        log(INFO, targetClass, t, () -> message);
    }

    public static void info(Class targetClass, Supplier<Object> message) {
        log(INFO, targetClass, null, message);
    }

    public static void info(Class targetClass, Object message) {
        log(INFO, targetClass, null, () -> message);
    }

    public static void info(Class targetClass, String message, Object... args) {
        log(INFO, targetClass, null, () -> message, args);
    }

    public static void info(Class targetClass, Throwable throwable) {
        log(INFO, targetClass, throwable, null);
    }

    public static void info(Throwable t, Supplier<Object> message) {
        logFromCaller(INFO, findCaller(), t, message);
    }

    public static void info(Throwable t, Object message) {
        logFromCaller(INFO, findCaller(), t, () -> message);
    }

    public static void info(Throwable t) {
        logFromCaller(INFO, findCaller(), t, null);
    }

    public static void info(Supplier<Object> message) {
        logFromCaller(INFO, findCaller(), null, message);
    }


    public static void info(Object message) {
        logFromCaller(INFO, findCaller(), null, () -> message);
    }

    public static void info(String message, Object... args) {
        logFromCaller(INFO, findCaller(), null, () -> message, args);
    }

    public static void info(Throwable t, String message, Object... args) {
        logFromCaller(INFO, findCaller(), t, () -> message, args);
    }

    public static void info(Class targetClass, Throwable t, String message, Object... args) {
        log(INFO, targetClass, t, () -> message, args);
    }

    public static boolean isInfoEnabled(Class targetClass) {
        return logger.isEnabled(INFO, targetClass);
    }

    public static boolean isInfoEnabled() {
        return logger.isEnabled(INFO, getCurrentClass());
    }

    // WARN
    public static void warn(Class targetClass, Throwable t, Supplier<Object> message) {
        log(WARN, targetClass, t, message);
    }

    public static void warn(Class targetClass, Throwable t, Object message) {
        log(WARN, targetClass, t, () -> message);
    }

    public static void warn(Class targetClass, Supplier<Object> message) {
        log(WARN, targetClass, null, message);
    }

    public static void warn(Class targetClass, Object message) {
        log(WARN, targetClass, null, () -> message);
    }

    public static void warn(Class targetClass, String message, Object... args) {
        log(WARN, targetClass, null, () -> message, args);
    }

    public static void warn(Class targetClass, Throwable throwable) {
        log(WARN, targetClass, throwable, null);
    }

    public static void warn(Throwable t, Supplier<Object> message) {
        logFromCaller(WARN, findCaller(), t, message);
    }

    public static void warn(Throwable t, Object message) {
        logFromCaller(WARN, findCaller(), t, () -> message);
    }

    public static void warn(Throwable t) {
        logFromCaller(WARN, findCaller(), t, null);
    }

    public static void warn(Supplier<Object> message) {
        logFromCaller(WARN, findCaller(), null, message);
    }

    public static void warn(Object message) {
        logFromCaller(WARN, findCaller(), null, () -> message);
    }

    public static void warn(String message, Object... args) {
        logFromCaller(WARN, findCaller(), null, () -> message, args);
    }

    public static void warn(Throwable t, String message, Object... args) {
        logFromCaller(WARN, findCaller(), t, () -> message, args);
    }

    public static void warn(Class targetClass, Throwable t, String message, Object... args) {
        log(WARN, targetClass, t, () -> message, args);
    }

    public static boolean isWarnEnabled(Class targetClass) {
        return logger.isEnabled(WARN, targetClass);
    }

    public static boolean isWarnEnabled() {
        return logger.isEnabled(WARN, getCurrentClass());
    }

    // ERROR
    public static void error(Class targetClass, Throwable t, Supplier<Object> message) {
        log(ERROR, targetClass, t, message);
    }

    public static void error(Class targetClass, Throwable t, Object message) {
        log(ERROR, targetClass, t, () -> message);
    }

    public static void error(Class targetClass, Supplier<Object> message) {
        log(ERROR, targetClass, null, message);
    }

    public static void error(Class targetClass, Object message) {
        log(ERROR, targetClass, null, () -> message);
    }

    public static void error(Class targetClass, String message, Object... args) {
        log(ERROR, targetClass, null, () -> message, args);
    }

    public static void error(Class targetClass, Throwable throwable) {
        log(ERROR, targetClass, throwable, null);
    }

    public static void error(Throwable t, Supplier<Object> message) {
        logFromCaller(ERROR, findCaller(), t, message);
    }

    public static void error(Throwable t, Object message) {
        logFromCaller(ERROR, findCaller(), t, () -> message);
    }

    public static void error(Throwable t) {
        logFromCaller(ERROR, findCaller(), t, null);
    }

    public static void error(Supplier<Object> message) {
        logFromCaller(ERROR, findCaller(), null, message);
    }

    public static void error(Object message) {
        logFromCaller(ERROR, findCaller(), null, () -> message);
    }

    public static void error(String message, Object... args) {
        logFromCaller(ERROR, findCaller(), null, () -> message, args);
    }

    public static void error(Throwable t, String message, Object... args) {
        logFromCaller(ERROR, findCaller(), t, () -> message, args);
    }

    public static void error(Class targetClass, Throwable t, String message, Object... args) {
        log(ERROR, targetClass, t, () -> message, args);
    }

    public static boolean isErrorEnabled(Class targetClass) {
        return logger.isEnabled(ERROR, targetClass);
    }

    public static boolean isErrorEnabled() {
        return logger.isEnabled(ERROR, getCurrentClass());
    }

    // UNIVERSAL LOG

    public static void log(LogEntry.LogEntryLevel level, Class loggedClass, Throwable throwable, Supplier<Object> message, Object... args) {
        doLog(level, loggedClass, null, throwable, message, args);
    }

    private static void logFromCaller(LogEntry.LogEntryLevel level, Caller caller, Throwable throwable, Supplier<Object> message, Object... args) {
        doLog(level, caller.sourceClass, caller.frame, throwable, message, args);
    }

    private static void doLog(LogEntry.LogEntryLevel level, Class loggedClass, StackTraceElement frame, Throwable throwable, Supplier<Object> message, Object[] args) {
        if (logger.isEnabled(level, loggedClass)) {
            logger.log(LogEntry.create(level, loggedClass, frame, throwable, message == null ? null : message.get(), args));
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


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
