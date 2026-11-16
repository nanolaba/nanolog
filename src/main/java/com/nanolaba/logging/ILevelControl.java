package com.nanolaba.logging;

/**
 * A logger whose levels can change while the application runs. The level methods of {@link LOG}
 * ({@link LOG#setLevel(LogEntry.LogEntryLevel)}, {@link LOG#withLevel(String, LogEntry.LogEntryLevel)} and the others)
 * delegate to the installed logger through this interface. {@link ConsoleLogger}, and so {@link JsonLogger},
 * implements it; {@link CompositeLogger} forwards to the delegates that implement it.
 * <p>
 * A change takes effect on the next logging call in every thread. A level set for a package or class works like
 * {@code nanolog.level.<name>}: it beats the global level, the most specific name wins, and a name matches whole
 * segments only. A {@code null} or blank name and a {@code null} level print a {@code [NANOLOG]} diagnostic and change
 * nothing.
 */
public interface ILevelControl {

    /**
     * Sets the global level: entries of this level and above are logged, those below are not. Packages and classes
     * with a rule of their own keep it.
     *
     * @param level the lowest level to log
     * @return this logger, for chaining
     */
    ILevelControl setLevel(LogEntry.LogEntryLevel level);

    /**
     * Sets the level of a package or class, as {@code nanolog.level.<name>} does, and replaces its rule if it has one.
     *
     * @param name  the package or class, such as {@code com.acme.payments}; surrounding spaces are ignored
     * @param level the lowest level to log for it
     * @return this logger, for chaining
     */
    ILevelControl setLevel(String name, LogEntry.LogEntryLevel level);

    /**
     * Removes the rule of a package or class, whether it was configured or set at run time. The next most specific
     * rule, or the global level, applies to it again. A name without a rule is not an error.
     *
     * @param name the package or class
     * @return this logger, for chaining
     */
    ILevelControl clearLevel(String name);

    /**
     * Restores the global level and the package and class rules from the settings: system properties, environment
     * variables and {@code nanolog.properties} as it was read when {@code LOG} initialised. Rules set at run time are
     * dropped, and the level switches are reset.
     *
     * @return this logger, for chaining
     */
    ILevelControl resetLevels();

    /**
     * Sets the level of a package or class until the returned override is closed. Closing restores the rule the name
     * had before, or removes the rule if it had none; closing again changes nothing. Overrides of the same name must
     * be closed in the reverse order of their creation, as try-with-resources does.
     *
     * @param name  the package or class
     * @param level the lowest level to log for it while the override is open
     * @return the override to close
     */
    LOG.LevelOverride withLevel(String name, LogEntry.LogEntryLevel level);
}
