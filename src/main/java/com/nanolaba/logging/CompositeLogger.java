package com.nanolaba.logging;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Forwards every entry to several loggers, for example to capture entries in a test and still print them:
 * {@code LOG.init(new CompositeLogger(memoryLogger, new ConsoleLogger()))}.
 * <p>
 * An entry is built when at least one delegate has its level enabled, and each delegate receives only the entries
 * it has enabled itself. A composite without delegates logs nothing. The delegates are fixed at construction, so the
 * composite is as thread-safe as they are.
 */
public class CompositeLogger implements ILogger {

    private final ILogger[] loggers;

    /**
     * @param loggers the delegates, in the order they receive each entry; none of them may be {@code null}
     * @throws NullPointerException if the array or any delegate is {@code null}
     */
    public CompositeLogger(ILogger... loggers) {
        this.loggers = loggers.clone();
        for (ILogger logger : this.loggers) {
            Objects.requireNonNull(logger, "a CompositeLogger delegate must not be null");
        }
    }

    /**
     * @return the delegates in the order they receive each entry, as an unmodifiable list
     */
    public List<ILogger> getLoggers() {
        return Collections.unmodifiableList(Arrays.asList(loggers));
    }

    @Override
    public void log(LogEntry entry) {
        for (ILogger logger : loggers) {
            if (logger.isEnabled(entry.getLevel(), entry.getSourceClass())) {
                logger.log(entry);
            }
        }
    }

    @Override
    public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
        for (ILogger logger : loggers) {
            if (logger.isEnabled(level, sourceClass)) {
                return true;
            }
        }
        return false;
    }
}
