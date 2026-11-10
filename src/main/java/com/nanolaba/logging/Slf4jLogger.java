package com.nanolaba.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class Slf4jLogger implements ILogger {

    private final Map<Class, Logger> loggers = new ConcurrentHashMap<>();

    @Override
    public void log(LogEntry entry) {

        Logger logger = getLogger(entry.getSourceClass());
        // an entry may carry only a throwable; SLF4J backends would print the literal word "null" otherwise
        String message = entry.getMessage() == null ? "" : entry.getFormattedMessage();

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
               ? LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)
               : loggers.computeIfAbsent(sourceClass, LoggerFactory::getLogger);
    }
}
