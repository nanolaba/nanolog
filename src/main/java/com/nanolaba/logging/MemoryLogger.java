package com.nanolaba.logging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Keeps log entries in memory, so a test can assert what the code under test logged without a mocking framework.
 * <pre>{@code
 * try (MemoryLogger log = MemoryLogger.install()) {
 *     service.charge(42);
 *     assertTrue(log.contains(LogEntryLevel.WARN, "retrying"));
 * }
 * }</pre>
 * Every level is enabled, so the entries are captured whatever the production configuration says. The logger is
 * thread-safe; with a capacity it keeps only the newest entries. Nothing is printed: use {@link #installForwarding()}
 * to capture and keep the previous output.
 */
public class MemoryLogger implements ILogger, AutoCloseable {

    private final int capacity;
    private final Deque<LogEntry> entries = new ArrayDeque<>();

    private ILogger previous;
    private boolean installed;

    /**
     * Creates a logger without a capacity limit.
     */
    public MemoryLogger() {
        this(Integer.MAX_VALUE);
    }

    /**
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

    private void installInstead(ILogger replacement) {
        previous = LOG.getLogger();
        installed = true;
        LOG.init(replacement);
    }

    /**
     * @return the maximum number of entries kept, {@link Integer#MAX_VALUE} without a limit
     */
    public int getCapacity() {
        return capacity;
    }

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
     * @return a snapshot of the captured entries, oldest first; it does not change when more entries arrive
     */
    public List<LogEntry> getEntries() {
        synchronized (entries) {
            return Collections.unmodifiableList(new ArrayList<>(entries));
        }
    }

    /**
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
     * Drops all captured entries.
     */
    public void clear() {
        synchronized (entries) {
            entries.clear();
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
    }
}
