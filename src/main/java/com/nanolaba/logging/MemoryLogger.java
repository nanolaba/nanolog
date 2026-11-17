package com.nanolaba.logging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Keeps log entries in memory, so a test can assert what the code under test logged without a mocking framework.
 * <pre>{@code
 * try (MemoryLogger log = MemoryLogger.capture()) {
 *     service.charge(42);
 *     log.assertLogged(LogEntryLevel.WARN, "retrying");
 *     log.assertNoneAtOrAbove(LogEntryLevel.ERROR);
 * }
 * }</pre>
 * {@link #capture()} keeps the entries of the current thread, so tests that run in parallel in one JVM each see their
 * own; {@link #install()} replaces the logger of the whole JVM. Every level is enabled, so the entries are captured
 * whatever the production configuration says. The logger is thread-safe; with a capacity it keeps only the newest
 * entries. Nothing is printed: {@link #captureForwarding()} and {@link #installForwarding()} keep the usual output.
 */
public class MemoryLogger implements ILogger, AutoCloseable {

    private final int capacity;
    private final Deque<LogEntry> entries = new ArrayDeque<>();
    private final Deque<String> diagnostics = new ArrayDeque<>();

    private ILogger previous;
    private boolean installed;

    /**
     * The captures of the thread this one captures, which {@link #close()} leaves from any thread; {@code null} for
     * a logger that is not a capture.
     */
    List<MemoryLogger> capture;
    /**
     * The logger a capture keeps the entries of its thread from: the logger of {@code LOG} when it opened, or
     * {@code null} for one that forwards them. A logger installed later, such as {@link #install()} in the test,
     * still receives them.
     */
    ILogger keptFrom;
    /**
     * Whether a capture takes every level; a buffer of {@link NanologExtension} takes the levels of the logger.
     */
    boolean everyLevel;

    /**
     * Creates a logger without a capacity limit.
     */
    public MemoryLogger() {
        this(Integer.MAX_VALUE);
    }

    /**
     * Creates a logger that keeps only the newest entries.
     *
     * @param capacity the number of newest entries to keep; older ones are dropped
     * @throws IllegalArgumentException if the capacity is less than 1
     */
    public MemoryLogger(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
    }

    /**
     * Replaces the logger of {@link LOG} with a new memory logger. {@link #close()} puts the previous logger back,
     * so use it in try-with-resources. {@code LOG.init} is JVM-wide: do not run such tests in parallel.
     *
     * @return the installed logger
     */
    public static MemoryLogger install() {
        MemoryLogger memory = new MemoryLogger();
        memory.installInstead(memory);
        return memory;
    }

    /**
     * Like {@link #install()}, but every entry still reaches the previous logger as well, at the levels that logger
     * has enabled, so the usual output stays visible while it is captured.
     *
     * @return the installed logger
     */
    public static MemoryLogger installForwarding() {
        MemoryLogger memory = new MemoryLogger();
        memory.installInstead(new CompositeLogger(memory, LOG.getLogger()));
        return memory;
    }

    /**
     * Captures the entries the current thread logs until {@link #close()}, every level, for a test that may run in
     * parallel with others: the logger of {@code LOG} stays installed for the other threads, and the entries of this
     * thread do not reach it; a logger installed while the capture is open, {@link #install()} for one, receives
     * them. Entries logged by other threads, a pool the code under test hands work to included,
     * are not captured. Captures nest: the entries of an inner one reach the outer ones as well. The findings of
     * strict mode and of {@code nanolog.dbg=fail} on this thread go to the capture too.
     *
     * @return the capture, to close in try-with-resources
     */
    public static MemoryLogger capture() {
        return startCapture(false);
    }

    /**
     * Like {@link #capture()}, but the entries still reach the logger of {@code LOG} as well, at the levels that logger
     * has enabled, so the usual output stays visible while it is captured.
     *
     * @return the capture, to close in try-with-resources
     */
    public static MemoryLogger captureForwarding() {
        return startCapture(true);
    }

    private static MemoryLogger startCapture(boolean forwarding) {
        return startCapture(forwarding ? null : LOG.getLogger(), true);
    }

    /**
     * Holds back what the current thread writes to the logger of {@code LOG}, at the levels of that logger, until
     * {@link NanologExtension} decides what to do with it; for that extension.
     */
    static MemoryLogger buffer() {
        return startCapture(LOG.getLogger(), false);
    }

    private static MemoryLogger startCapture(ILogger keptFrom, boolean everyLevel) {
        MemoryLogger memory = new MemoryLogger();
        memory.keptFrom = keptFrom;
        memory.everyLevel = everyLevel;
        LOG.startCapture(memory);
        return memory;
    }

    private void installInstead(ILogger replacement) {
        previous = LOG.getLogger();
        installed = true;
        LOG.init(replacement);
    }

    /**
     * Returns the maximum number of entries kept, {@link Integer#MAX_VALUE} without a limit.
     *
     * @return the maximum number of entries kept, {@link Integer#MAX_VALUE} without a limit
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Keeps the entry, dropping the oldest one if the capacity is exceeded.
     *
     * @param entry the entry
     */
    @Override
    public void log(LogEntry entry) {
        synchronized (entries) {
            entries.addLast(entry);
            if (entries.size() > capacity) {
                entries.removeFirst();
            }
        }
    }

    /**
     * Returns a snapshot of the captured entries, oldest first; it does not change when more entries arrive.
     *
     * @return a snapshot of the captured entries, oldest first; it does not change when more entries arrive
     */
    public List<LogEntry> getEntries() {
        synchronized (entries) {
            return Collections.unmodifiableList(new ArrayList<>(entries));
        }
    }

    /**
     * Returns a snapshot of the captured entries of one level, oldest first.
     *
     * @param level the level to select
     * @return a snapshot of the captured entries of one level, oldest first
     */
    public List<LogEntry> getEntries(LogEntry.LogEntryLevel level) {
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry entry : getEntries()) {
            if (entry.getLevel() == level) {
                result.add(entry);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the formatted messages of the captured entries, oldest first.
     *
     * @return the formatted messages of the captured entries, oldest first
     */
    public List<String> getMessages() {
        List<String> result = new ArrayList<>();
        for (LogEntry entry : getEntries()) {
            result.add(entry.getFormattedMessage());
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns {@code true} if an entry of this level has a formatted message containing {@code messagePart}.
     *
     * @param level       the level the entry must have
     * @param messagePart the text its formatted message must contain
     * @return {@code true} if an entry of this level has a formatted message containing {@code messagePart}
     */
    public boolean contains(LogEntry.LogEntryLevel level, String messagePart) {
        for (LogEntry entry : getEntries(level)) {
            if (entry.getFormattedMessage().contains(messagePart)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a snapshot of the captured entries of one source class, oldest first.
     *
     * @param sourceClass the source class to select, detected or passed explicitly; {@code null} selects the entries
     *                    without one
     * @return a snapshot of the captured entries of the class, oldest first
     */
    public List<LogEntry> getEntries(Class sourceClass) {
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry entry : getEntries()) {
            if (entry.getSourceClass() == sourceClass) {
                result.add(entry);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Fails unless no entry of this level or a more severe one was captured: {@code assertNoneAtOrAbove(WARN)} checks
     * that the code logged no warning and no error.
     *
     * @param level the least severe level that fails the assertion
     * @throws AssertionError with every captured entry in the console format, stack traces included
     */
    public void assertNoneAtOrAbove(LogEntry.LogEntryLevel level) {
        List<LogEntry> captured = getEntries();
        int found = 0;
        for (LogEntry entry : captured) {
            if (entry.getLevel() != null && entry.getLevel().compareTo(level) >= 0) {
                found++;
            }
        }
        if (found > 0) {
            throw failure("Expected no entry at " + level + " or above, but " + found + (found == 1 ? " was" : " were") + " logged.",
                          captured);
        }
    }

    /**
     * Fails unless an entry of this level has a formatted message containing {@code messagePart}, as
     * {@link #contains(LogEntry.LogEntryLevel, String)} tells.
     *
     * @param level       the level the entry must have
     * @param messagePart the text its formatted message must contain
     * @throws AssertionError with every captured entry in the console format, stack traces included
     */
    public void assertLogged(LogEntry.LogEntryLevel level, String messagePart) {
        if (!contains(level, messagePart)) {
            throw failure("Expected a " + level + " entry containing \"" + messagePart + "\".", getEntries());
        }
    }

    /**
     * Fails unless an entry of this level has exactly this template, its message before the arguments were
     * substituted: {@code assertTemplateLogged(WARN, "Retry {} of {}")} matches whatever the arguments were.
     *
     * @param level    the level the entry must have
     * @param template the raw message the entry must have
     * @throws AssertionError with every captured entry in the console format, stack traces included
     */
    public void assertTemplateLogged(LogEntry.LogEntryLevel level, String template) {
        for (LogEntry entry : getEntries(level)) {
            if (entry.getMessage() != null && LogEntry.safeToString(entry.getMessage()).equals(template)) {
                return;
            }
        }
        throw failure("Expected a " + level + " entry with the template \"" + template + "\".", getEntries());
    }

    /**
     * An assertion failure that shows what was logged, so a failing test needs no second run to find out.
     */
    private static AssertionError failure(String expectation, List<LogEntry> captured) {
        if (captured.isEmpty()) {
            return new AssertionError(expectation + " Captured entries: none");
        }
        // the level, the source class with the call site, the message, the context and the stack trace; no date,
        // since the console prints the time of writing, not the time the entry was logged
        ConsoleLogger format = new ConsoleLogger().setShowLevel(true).setShowDate(false).setShowThread(false)
                                                  .setShowSource(true).setShowSourceFullName(false).setShowLocation(true)
                                                  .setShowContext(true);
        StringBuilder message = new StringBuilder(expectation).append(" Captured entries:");
        for (LogEntry entry : captured) {
            message.append(System.lineSeparator()).append(format.createLogString(entry));
        }
        return new AssertionError(message.toString());
    }

    /**
     * Keeps a finding of strict mode or of {@code nanolog.dbg=fail}, dropping the oldest one if the capacity is
     * exceeded.
     *
     * @param message the finding
     */
    @Override
    public void onDiagnostic(String message) {
        synchronized (entries) {
            diagnostics.addLast(message);
            if (diagnostics.size() > capacity) {
                diagnostics.removeFirst();
            }
        }
    }

    /**
     * Returns a snapshot of the findings, oldest first: one per call whose {@code {}} placeholders and arguments did
     * not match, while {@code nanolog.strict=true}, and one per {@code LOG.dbg} call, while {@code nanolog.dbg=fail}.
     *
     * @return a snapshot of the findings, oldest first
     */
    public List<String> getDiagnostics() {
        synchronized (entries) {
            return Collections.unmodifiableList(new ArrayList<>(diagnostics));
        }
    }

    /**
     * Fails if Nanolog reported a finding: a call whose {@code {}} placeholders and arguments do not match, with
     * {@code -Dnanolog.strict=true}, or a {@code LOG.dbg} call, with {@code -Dnanolog.dbg=fail}. The checks must be on,
     * for example in the Surefire configuration; without them there is nothing to
     * report and the assertion passes.
     *
     * @throws AssertionError with every finding, each naming its call site
     */
    public void assertNoDiagnostics() {
        List<String> found = getDiagnostics();
        if (!found.isEmpty()) {
            StringBuilder message = new StringBuilder("Expected no diagnostics, but Nanolog reported ").append(found.size()).append(':');
            for (String diagnostic : found) {
                message.append(System.lineSeparator()).append(diagnostic);
            }
            throw new AssertionError(message.toString());
        }
    }

    /**
     * Drops all captured entries and findings.
     */
    public void clear() {
        synchronized (entries) {
            entries.clear();
            diagnostics.clear();
        }
    }

    /**
     * Puts back the logger that {@link #install()} or {@link #installForwarding()} replaced. Does nothing for a
     * logger that was not installed by them.
     */
    @Override
    public void close() {
        if (installed) {
            LOG.init(previous);
        }
        if (capture != null) {
            LOG.endCapture(this);
        }
    }
}
