package com.nanolaba.logging;

/**
 * Receives the entries of {@link LOG}: the one extension point of Nanolog. It has a single abstract method, so the
 * simplest logger is a lambda: {@code LOG.init(entry -> System.out.println(entry.getFormattedMessage()))}.
 * <p>
 * Implementations are called from every logging thread and must be thread-safe. Every class implementing this
 * interface is skipped by caller detection, so a logger that itself logs through {@code LOG} must pass its source
 * class explicitly.
 */
public interface ILogger {

    /**
     * Writes an entry. Called only for entries whose level passed {@link #isEnabled}.
     *
     * @param entry the entry, with its message already resolved and formatted
     */
    void log(LogEntry entry);

    /**
     * Decides whether an entry is logged at all. {@code LOG} asks before it builds the {@link LogEntry} and before a
     * lazy message is evaluated, so returning {@code false} skips the whole cost of the call.
     *
     * @param level       the level of the entry
     * @param sourceClass the source class; may be {@code null}
     * @return {@code true} to log the entry; the default enables everything
     */
    default boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
        return true;
    }
}
