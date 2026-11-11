package com.nanolaba.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;


public class Slf4jLogger implements ILogger {

    /**
     * The caller boundary for location-aware backends: the call site is the first frame after the frames of
     * {@code LOG}, so {@code %file:%line} (Logback) or {@code %l} (Log4j 2) point at the code that called {@code LOG}.
     */
    private static final String CALLER_BOUNDARY = LOG.class.getName();

    private final Map<Class, Logger> loggers = new ConcurrentHashMap<>();
    private final Function<String, Logger> factory;

    /**
     * Creates a bridge that takes its loggers from {@link LoggerFactory}.
     */
    public Slf4jLogger() {
        this(LoggerFactory::getLogger);
    }

    /**
     * @param factory returns the SLF4J logger of a name; replaced in tests
     */
    Slf4jLogger(Function<String, Logger> factory) {
        this.factory = factory;
    }

    @Override
    public void log(LogEntry entry) {

        Logger logger = getLogger(entry.getSourceClass());
        // an entry may carry only a throwable; SLF4J backends would print the literal word "null" otherwise
        String message = entry.getMessage() == null ? "" : entry.getFormattedMessage();

        if (logger instanceof LocationAwareLogger) {
            // no arguments: the message is formatted already, and a backend must not format its {} again
            ((LocationAwareLogger) logger).log(null, CALLER_BOUNDARY, toLocationAwareLevel(entry.getLevel()), message, null, entry.getThrowable());
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
