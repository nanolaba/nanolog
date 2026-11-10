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

        switch (entry.getLevel()) {
            case TRACE:
                logger.trace(entry.getFormattedMessage(), entry.getThrowable());
                break;
            case DEBUG:
                logger.debug(entry.getFormattedMessage(), entry.getThrowable());
                break;
            case WARN:
                logger.warn(entry.getFormattedMessage(), entry.getThrowable());
                break;
            case ERROR:
                logger.error(entry.getFormattedMessage(), entry.getThrowable());
                break;
            default:
                logger.info(entry.getFormattedMessage(), entry.getThrowable());
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
