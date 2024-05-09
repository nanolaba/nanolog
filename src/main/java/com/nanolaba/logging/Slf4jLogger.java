package com.nanolaba.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class Slf4jLogger implements ILogger {

    private final Map<Class, Logger> loggers = new ConcurrentHashMap<>();

    @Override
    public void log(LogEntry entry) {

        Logger logger = loggers.computeIfAbsent(entry.getSourceClass(), LoggerFactory::getLogger);

        switch (entry.getLevel()) {
            case TRACE -> logger.trace(entry.getFormattedMessage(), entry.getThrowable());
            case DEBUG -> logger.debug(entry.getFormattedMessage(), entry.getThrowable());
            case WARN -> logger.warn(entry.getFormattedMessage(), entry.getThrowable());
            case ERROR -> logger.error(entry.getFormattedMessage(), entry.getThrowable());
            default -> logger.info(entry.getFormattedMessage(), entry.getThrowable());
        }
    }

    @Override
    public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
        Logger logger = loggers.computeIfAbsent(sourceClass, LoggerFactory::getLogger);

        return switch (level) {
            case TRACE -> logger.isTraceEnabled();
            case DEBUG -> logger.isDebugEnabled();
            case WARN -> logger.isWarnEnabled();
            case ERROR -> logger.isErrorEnabled();
            default -> logger.isInfoEnabled();
        };
    }
}
