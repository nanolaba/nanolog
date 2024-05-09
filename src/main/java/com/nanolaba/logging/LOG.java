package com.nanolaba.logging;

import java.util.function.Supplier;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.*;

public class LOG {

    private static ILogger logger = new SimpleConsoleLogger();

    private LOG() {/**/}

    public static void init(ILogger logger) {
        LOG.logger = logger;
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
        log(TRACE, getCurrentClass(), t, message);
    }

    public static void trace(Throwable t, Object message) {
        log(TRACE, getCurrentClass(), t, () -> message);
    }

    public static void trace(Throwable t) {
        log(TRACE, getCurrentClass(), t, null);
    }

    public static void trace(Supplier<Object> message) {
        log(TRACE, getCurrentClass(), null, message);
    }

    public static void trace(Object message) {
        log(TRACE, getCurrentClass(), null, () -> message);
    }

    public static void trace(String message, Object... args) {
        log(TRACE, getCurrentClass(), null, () -> message, args);
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
        log(DEBUG, getCurrentClass(), t, message);
    }

    public static void debug(Throwable t, Object message) {
        log(DEBUG, getCurrentClass(), t, () -> message);
    }

    public static void debug(Throwable t) {
        log(DEBUG, getCurrentClass(), t, null);
    }

    public static void debug(Supplier<Object> message) {
        log(DEBUG, getCurrentClass(), null, message);
    }

    public static void debug(Object message) {
        log(DEBUG, getCurrentClass(), null, () -> message);
    }

    public static void debug(String message, Object... args) {
        log(DEBUG, getCurrentClass(), null, () -> message, args);
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
        log(INFO, getCurrentClass(), t, message);
    }

    public static void info(Throwable t, Object message) {
        log(INFO, getCurrentClass(), t, () -> message);
    }

    public static void info(Throwable t) {
        log(INFO, getCurrentClass(), t, null);
    }

    public static void info(Supplier<Object> message) {
        log(INFO, getCurrentClass(), null, message);
    }


    public static void info(Object message) {
        log(INFO, getCurrentClass(), null, () -> message);
    }

    public static void info(String message, Object... args) {
        log(INFO, getCurrentClass(), null, () -> message, args);
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
        log(WARN, getCurrentClass(), t, message);
    }

    public static void warn(Throwable t, Object message) {
        log(WARN, getCurrentClass(), t, () -> message);
    }

    public static void warn(Throwable t) {
        log(WARN, getCurrentClass(), t, null);
    }

    public static void warn(Supplier<Object> message) {
        log(WARN, getCurrentClass(), null, message);
    }

    public static void warn(Object message) {
        log(WARN, getCurrentClass(), null, () -> message);
    }

    public static void warn(String message, Object... args) {
        log(WARN, getCurrentClass(), null, () -> message, args);
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
        log(ERROR, getCurrentClass(), t, message);
    }

    public static void error(Throwable t, Object message) {
        log(ERROR, getCurrentClass(), t, () -> message);
    }

    public static void error(Throwable t) {
        log(ERROR, getCurrentClass(), t, null);
    }

    public static void error(Supplier<Object> message) {
        log(ERROR, getCurrentClass(), null, message);
    }

    public static void error(Object message) {
        log(ERROR, getCurrentClass(), null, () -> message);
    }

    public static void error(String message, Object... args) {
        log(ERROR, getCurrentClass(), null, () -> message, args);
    }

    public static boolean isErrorEnabled(Class targetClass) {
        return logger.isEnabled(ERROR, targetClass);
    }

    public static boolean isErrorEnabled() {
        return logger.isEnabled(ERROR, getCurrentClass());
    }

    // UNIVERSAL LOG

    public static void log(LogEntry.LogEntryLevel level, Class loggedClass, Throwable throwable, Supplier<Object> message, Object... args) {
        if (logger.isEnabled(level, loggedClass)) {
            logger.log(new LogEntry(level, loggedClass, throwable, message == null ? null : message.get(), args));
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    private static Class getCurrentClass() {
        try {
            throw new Throwable();
        } catch (Throwable t) {
            for (StackTraceElement element : t.getStackTrace()) {
                try {
                    Class result = Class.forName(element.getClassName());
                    while (result.getEnclosingClass() != null) {
                        result = result.getEnclosingClass();
                    }

                    if (result.equals(LOG.class) || result.equals(LogEntry.class) || ILogger.class.isAssignableFrom(result)) {
                        continue;
                    }

                    return result;
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Unable to determine the current class", e);
                }
            }
        }

        throw new IllegalStateException("Unable to determine the current class");
    }
}
