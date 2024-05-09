package com.nanolaba.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class Slf4jLogger implements ILogger {

    private final Map<Class, Logger> loggers = new ConcurrentHashMap<>();

    @Override
    public void log(LogEntry entry) {

        Logger logger = loggers.computeIfAbsent(entry.getSourceClass(), LoggerFactory::getLogger);

        switch (entry.getLevel()) {
            case TRACE -> trace(logger, entry);
            case DEBUG -> debug(logger, entry);
            case WARN -> warn(logger, entry);
            case ERROR -> error(logger, entry);
            default -> info(logger, entry);
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

    private void trace(Logger logger, LogEntry entry) {
        if (logger.isTraceEnabled()) {
            if (haveArgs(entry)) {
                logger.trace(trim(entry.getMessage()), entry.getArgs());
            } else {
                logger.trace(trim(entry.getMessage()), entry.getThrowable());
            }
        }
    }

    private void debug(Logger logger, LogEntry entry) {
        if (logger.isDebugEnabled()) {
            if (haveArgs(entry)) {
                logger.debug(trim(entry.getMessage()), entry.getArgs());
            } else {
                logger.debug(trim(entry.getMessage()), entry.getThrowable());
            }
        }
    }

    private void info(Logger logger, LogEntry entry) {
        if (logger.isInfoEnabled()) {
            if (haveArgs(entry)) {
                logger.info(trim(entry.getMessage()), entry.getArgs());
            } else {
                logger.info(trim(entry.getMessage()), entry.getThrowable());
            }
        }
    }

    private void warn(Logger logger, LogEntry entry) {
        if (logger.isWarnEnabled()) {
            if (haveArgs(entry)) {
                logger.warn(trim(entry.getMessage()), entry.getArgs());
            } else {
                logger.warn(trim(entry.getMessage()), entry.getThrowable());
            }
        }
    }

    private void error(Logger logger, LogEntry entry) {
        if (logger.isErrorEnabled()) {
            if (haveArgs(entry)) {
                logger.error(trim(entry.getMessage()), entry.getArgs());
            } else {
                logger.error(trim(entry.getMessage()), entry.getThrowable());
            }
        }
    }

    private String trim(Object message) {
        return message == null ? "" : message.toString();
    }

    private boolean haveArgs(LogEntry entry) {
        return entry.getArgs() != null &&
               Arrays.stream(entry.getArgs()).anyMatch(arg -> arg != null && !String.valueOf(arg).isEmpty());
    }
}
