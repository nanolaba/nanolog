package com.nanolaba.logging;

import java.util.ArrayList;
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
public class CompositeLogger implements ILogger, ILevelControl {

    private final ILogger[] loggers;

    /**
     * The delegates that support level changes at run time, in order.
     */
    private final List<ILevelControl> levelControls = new ArrayList<>();

    /**
     * Set for a subclass with its own {@code isEnabled}, whose answers {@link #mayBeEnabled} cannot predict.
     */
    private final boolean isEnabledOverridden;

    /**
     * Creates a composite of the given loggers.
     *
     * @param loggers the delegates, in the order they receive each entry; none of them may be {@code null}
     * @throws NullPointerException if the array or any delegate is {@code null}
     */
    public CompositeLogger(ILogger... loggers) {
        this.loggers = loggers.clone();
        for (ILogger logger : this.loggers) {
            Objects.requireNonNull(logger, "a CompositeLogger delegate must not be null");
            if (logger instanceof ILevelControl) {
                levelControls.add((ILevelControl) logger);
            }
        }
        isEnabledOverridden = LoggerIntrospection.overridesIsEnabled(this, CompositeLogger.class);
    }

    /**
     * Returns the delegates in the order they receive each entry, as an unmodifiable list.
     *
     * @return the delegates in the order they receive each entry, as an unmodifiable list
     */
    public List<ILogger> getLoggers() {
        return Collections.unmodifiableList(Arrays.asList(loggers));
    }

    /**
     * Passes the entry to every delegate that has its level enabled, in order. A delegate that throws is reported once
     * per class and skipped; the others still receive the entry.
     *
     * @param entry the entry
     */
    @Override
    public void log(LogEntry entry) {
        for (ILogger logger : loggers) {
            if (isEnabled(logger, entry.getLevel(), entry.getSourceClass())) {
                try {
                    logger.log(entry);
                } catch (Throwable t) {
                    LOG.reportLoggerFailure(logger, t, DELEGATE_NOTE);
                }
            }
        }
    }

    /**
     * Returns {@code true} if at least one delegate has the level enabled. A delegate whose check throws counts as
     * enabled.
     *
     * @param level       the level of the entry
     * @param sourceClass the source class; may be {@code null}
     * @return {@code true} if at least one delegate has the level enabled
     */
    @Override
    public boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
        for (ILogger logger : loggers) {
            if (isEnabled(logger, level, sourceClass)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if at least one delegate may have the level enabled for some class. A delegate whose check
     * throws counts as enabled. A subclass that overrides {@link #isEnabled} without overriding this method always
     * gets {@code true}.
     *
     * @param level the level
     * @return {@code false} if no delegate logs entries of the level, whatever their source class
     */
    @Override
    public boolean mayBeEnabled(LogEntry.LogEntryLevel level) {
        if (isEnabledOverridden) {
            return true;
        }
        for (ILogger logger : loggers) {
            if (mayBeEnabled(logger, level)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets the global level of every delegate that implements {@link ILevelControl}; the others are skipped.
     *
     * @param level {@code TRACE}, {@code DEBUG}, {@code INFO}, {@code WARN}, {@code ERROR} or {@code OFF}; anything
     *              else prints one diagnostic and changes nothing
     * @return this logger, for chaining
     */
    @Override
    public CompositeLogger setLevel(String level) {
        if (ConsoleLogger.isValidLevel("setLevel", level)) {
            for (ILevelControl control : levelControls) {
                control.setLevel(level);
            }
        }
        return this;
    }

    /**
     * Sets the global level of every delegate that implements {@link ILevelControl}, as {@link #setLevel(String)}
     * does.
     *
     * @param level the lowest level to log; {@code null} prints one diagnostic and changes nothing
     * @return this logger, for chaining
     */
    @Override
    public CompositeLogger setLevel(LogEntry.LogEntryLevel level) {
        return setLevel(Objects.toString(level, null));
    }

    /**
     * Sets the level of a package or class in every delegate that implements {@link ILevelControl}.
     *
     * @param name  the package or class
     * @param level {@code TRACE}, {@code DEBUG}, {@code INFO}, {@code WARN}, {@code ERROR} or {@code OFF}
     * @return this logger, for chaining
     */
    @Override
    public CompositeLogger setLevel(String name, String level) {
        if (ConsoleLogger.isValidRule("setLevel", name, level)) {
            for (ILevelControl control : levelControls) {
                control.setLevel(name, level);
            }
        }
        return this;
    }

    /**
     * Sets the level of a package or class in every delegate that implements {@link ILevelControl}, as
     * {@link #setLevel(String, String)} does.
     *
     * @param name  the package or class
     * @param level the lowest level to log for it
     * @return this logger, for chaining
     */
    @Override
    public CompositeLogger setLevel(String name, LogEntry.LogEntryLevel level) {
        return setLevel(name, Objects.toString(level, null));
    }

    /**
     * Removes the rule of a package or class in every delegate that implements {@link ILevelControl}.
     *
     * @param name the package or class
     * @return this logger, for chaining
     */
    @Override
    public CompositeLogger clearLevel(String name) {
        if (ConsoleLogger.isValidName("clearLevel", name)) {
            for (ILevelControl control : levelControls) {
                control.clearLevel(name);
            }
        }
        return this;
    }

    /**
     * Restores the levels of every delegate that implements {@link ILevelControl} from the settings.
     *
     * @return this logger, for chaining
     */
    @Override
    public CompositeLogger resetLevels() {
        for (ILevelControl control : levelControls) {
            control.resetLevels();
        }
        return this;
    }

    /**
     * Sets the level of a package or class in every delegate that implements {@link ILevelControl} until the returned
     * override is closed. Closing restores the delegates in reverse order, so a delegate listed twice ends up with its
     * original rule.
     *
     * @param name  the package or class
     * @param level {@code TRACE}, {@code DEBUG}, {@code INFO}, {@code WARN}, {@code ERROR} or {@code OFF}
     * @return the override to close
     */
    @Override
    public LOG.LevelOverride withLevel(String name, String level) {
        List<LOG.LevelOverride> overrides = new ArrayList<>();
        if (ConsoleLogger.isValidRule("withLevel", name, level)) {
            for (ILevelControl control : levelControls) {
                overrides.add(control.withLevel(name, level));
            }
        }
        return () -> {
            for (int i = overrides.size() - 1; i >= 0; i--) {
                overrides.get(i).close();
            }
        };
    }

    /**
     * Sets the level of a package or class in every delegate that implements {@link ILevelControl} until the returned
     * override is closed, as {@link #withLevel(String, String)} does.
     *
     * @param name  the package or class
     * @param level the lowest level to log for it while the override is open
     * @return the override to close
     */
    @Override
    public LOG.LevelOverride withLevel(String name, LogEntry.LogEntryLevel level) {
        return withLevel(name, Objects.toString(level, null));
    }

    private static final String DELEGATE_NOTE = "the other loggers of the CompositeLogger still receive its entries";

    private static boolean isEnabled(ILogger logger, LogEntry.LogEntryLevel level, Class sourceClass) {
        try {
            return logger.isEnabled(level, sourceClass);
        } catch (Throwable t) {
            LOG.reportLoggerFailure(logger, t, DELEGATE_NOTE);
            return true;
        }
    }

    private static boolean mayBeEnabled(ILogger logger, LogEntry.LogEntryLevel level) {
        try {
            return logger.mayBeEnabled(level);
        } catch (Throwable t) {
            LOG.reportLoggerFailure(logger, t, DELEGATE_NOTE);
            return true;
        }
    }
}
