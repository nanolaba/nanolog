package com.nanolaba.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LocationAwareLogger;
import org.slf4j.spi.MDCAdapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


/**
 * Forwards entries to SLF4J, and through it to Logback, Log4j 2 or any other backend. Each entry goes to the SLF4J
 * logger named after its source class, an entry without one to the root logger. Levels, layouts and appenders are
 * configured in the backend; {@link #isEnabled} asks the backend.
 * <p>
 * The message is passed already formatted. A location-aware backend is told that the call site is the first frame
 * after {@link LOG} (after {@link LOG.Limited} for a rate-limited call), so its {@code %file:%line} or {@code %l}
 * shows the code that called {@code LOG}. The number of calls a rate-limited statement skipped is appended to the
 * message as {@code [skipped N]}. SLF4J markers are not supported.
 * <p>
 * The context of {@link LOG#context(String, Object)} is put into the MDC while an entry is forwarded, each value
 * written like a {@code {}} argument, and taken out afterwards; values the MDC held for those keys are put back.
 * Backends that support MDC (Logback, Log4j 2) print it through their patterns, such as {@code %X{requestId}}, as
 * they print values set through {@code org.slf4j.MDC}; slf4j-simple and slf4j-nop discard them, and the JUL binding
 * stores them without printing.
 * {@code slf4j-api} is a {@code provided} dependency of Nanolog.
 */
public class Slf4jLogger implements ILogger {

    private final Map<Class, Logger> loggers = new ConcurrentHashMap<>();
    private final Function<String, Logger> factory;
    private final MDCAdapter mdc;

    /**
     * Creates a bridge that takes its loggers from {@link LoggerFactory}.
     */
    public Slf4jLogger() {
        this(LoggerFactory::getLogger);
    }

    /**
     * Creates a bridge that takes its loggers from the given factory.
     *
     * @param factory returns the SLF4J logger of a name; replaced in tests
     */
    Slf4jLogger(Function<String, Logger> factory) {
        this(factory, MDC.getMDCAdapter());
    }

    /**
     * Creates a bridge that takes its loggers from the given factory and puts the context into the given MDC.
     *
     * @param factory returns the SLF4J logger of a name; replaced in tests
     * @param mdc     the MDC of the backend; replaced in tests, since slf4j-simple keeps no MDC
     */
    Slf4jLogger(Function<String, Logger> factory, MDCAdapter mdc) {
        this.factory = factory;
        this.mdc = mdc;
    }

    /**
     * Passes the entry to the SLF4J logger of its source class at the same level, with its context in the MDC.
     *
     * @param entry the entry; an entry without a message is passed with an empty one
     */
    @Override
    public void log(LogEntry entry) {
        Map<String, Object> context = entry.getContext();
        String[] previous = new String[context.size()];
        int i = 0;
        for (Map.Entry<String, Object> pair : context.entrySet()) {
            previous[i++] = mdc.get(pair.getKey());
            mdc.put(pair.getKey(), LogEntry.argToString(pair.getValue()));
        }
        try {
            forward(entry);
        } finally {
            i = 0;
            for (String key : context.keySet()) {
                restore(key, previous[i++]);
            }
        }
    }

    private void restore(String key, String value) {
        if (value == null) {
            mdc.remove(key);
        } else {
            mdc.put(key, value);
        }
    }

    private void forward(LogEntry entry) {
        Logger logger = getLogger(entry.getSourceClass());
        // an entry may carry only a throwable; SLF4J backends would print the literal word "null" otherwise
        String message = entry.getMessage() == null ? "" : entry.getFormattedMessage();
        if (entry.getSkippedCount() > 0) {
            String skipped = "[skipped " + entry.getSkippedCount() + "]";
            message = message.isEmpty() ? skipped : message + " " + skipped;
        }

        if (logger instanceof LocationAwareLogger) {
            // no arguments: the message is formatted already, and a backend must not format its {} again
            // the boundary: the call site is the first frame after it, so %file:%line (Logback) or %l (Log4j 2) point at
            // the code that called LOG, also when the call passed through LOG.Limited
            ((LocationAwareLogger) logger).log(null, entry.getCallerBoundary(), toLocationAwareLevel(entry.getLevel()), message, null, entry.getThrowable());
            return;
        }

        switch (entry.getLevel()) {
            case TRACE:
                logger.trace(message, entry.getThrowable());
                break;
            case DEBUG:
                logger.debug(message, entry.getThrowable());
                break;
            case WARN:
                logger.warn(message, entry.getThrowable());
                break;
            case ERROR:
                logger.error(message, entry.getThrowable());
                break;
            default:
                logger.info(message, entry.getThrowable());
                break;
        }
    }

    private static int toLocationAwareLevel(LogEntry.LogEntryLevel level) {
        switch (level) {
            case TRACE:
                return LocationAwareLogger.TRACE_INT;
            case DEBUG:
                return LocationAwareLogger.DEBUG_INT;
            case WARN:
                return LocationAwareLogger.WARN_INT;
            case ERROR:
                return LocationAwareLogger.ERROR_INT;
            default:
                return LocationAwareLogger.INFO_INT;
        }
    }

    /**
     * Asks the SLF4J logger of the source class whether the level is enabled.
     *
     * @param level       the level of the entry
     * @param sourceClass the source class; {@code null} asks the root logger
     * @return the answer of the backend
     */
    @Override
    public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
        Logger logger = getLogger(sourceClass);

        switch (level) {
            case TRACE:
                return logger.isTraceEnabled();
            case DEBUG:
                return logger.isDebugEnabled();
            case WARN:
                return logger.isWarnEnabled();
            case ERROR:
                return logger.isErrorEnabled();
            default:
                return logger.isInfoEnabled();
        }
    }

    private Logger getLogger(Class sourceClass) {
        return sourceClass == null
               ? factory.apply(Logger.ROOT_LOGGER_NAME)
               : loggers.computeIfAbsent(sourceClass, c -> factory.apply(c.getName()));
    }
}
