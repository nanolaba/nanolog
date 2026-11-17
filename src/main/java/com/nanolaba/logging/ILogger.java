package com.nanolaba.logging;

/**
 * Receives the entries of {@link LOG}: the one extension point of Nanolog. It has a single abstract method, so the
 * simplest logger is a lambda: {@code LOG.init(entry -> System.out.println(entry.getFormattedMessage()))}.
 * <p>
 * Implementations are called from every logging thread and must be thread-safe. Every class implementing this
 * interface is skipped by caller detection, so a logger that itself logs through {@code LOG} must pass its source
 * class explicitly. An exception thrown by an implementation does not reach the code that logged: {@code LOG} reports
 * it once per logger class with a {@code [NANOLOG]} line and prints the entry to {@code System.err} instead.
 */
public interface ILogger {

    /**
     * Writes an entry. Called only for entries whose level passed {@link #isEnabled}.
     *
     * @param entry the entry, with its message already resolved and formatted
     */
    void log(LogEntry entry);

    /**
     * Decides whether an entry is logged at all. {@code LOG} asks before it builds the {@link LogEntry}, evaluates a
     * lazy message or formats the arguments, so returning {@code false} skips all of that. For a call without an
     * explicit source class the class is detected first, so the stack walk of caller detection has already happened;
     * {@link #mayBeEnabled} is the check that comes before the walk.
     *
     * @param level       the level of the entry
     * @param sourceClass the source class; may be {@code null}
     * @return {@code true} to log the entry; the default enables everything
     */
    default boolean isEnabled(LogEntry.LogEntryLevel level, Class sourceClass) {
        return true;
    }

    /**
     * Tells whether {@link #isEnabled} can return {@code true} for the level and some source class, {@code null}
     * included. {@code LOG} asks it before caller detection walks the stack, so returning {@code false} makes a call
     * without an explicit source class cost no more than this check. Calls with an explicit class go straight to
     * {@link #isEnabled}.
     * <p>
     * {@code true} is always safe and is the default: {@link #isEnabled} then decides alone, after the walk.
     * {@code false} for a level that {@link #isEnabled} accepts for some class loses those entries.
     * {@link ConsoleLogger}, {@link JsonLogger} and {@link CompositeLogger} answer from their switches, package rules
     * and delegates.
     *
     * @param level the level
     * @return {@code false} only if no entry of the level is logged, whatever its source class
     */
    default boolean mayBeEnabled(LogEntry.LogEntryLevel level) {
        return true;
    }

    /**
     * Receives a finding about the entry this logger is about to receive: with {@code nanolog.strict=true}, a call
     * whose {@code {}} placeholders and arguments do not match; with {@code nanolog.dbg=fail}, a {@code LOG.dbg} call.
     * {@code LOG} prints such a finding to {@code System.err} once per template or call site; this method gets every
     * occurrence, so a test can fail on it even when an earlier test met the same finding first. {@link MemoryLogger}
     * keeps them for {@link MemoryLogger#assertNoDiagnostics()}; the default ignores them.
     *
     * @param message the finding, as printed after {@code [NANOLOG]}: the call site, then what is wrong
     */
    default void onDiagnostic(String message) {
    }
}
