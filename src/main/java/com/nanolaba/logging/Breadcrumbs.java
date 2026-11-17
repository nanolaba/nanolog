package com.nanolaba.logging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_BUFFER;
import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_BUFFER_SIZE;
import static com.nanolaba.logging.DefaultLoggerCreator.readChoice;
import static com.nanolaba.logging.DefaultLoggerCreator.readPositive;

/**
 * The breadcrumbs of {@code nanolog.buffer}: entries at the buffered level and below are held back in a bounded ring
 * instead of reaching the logger, and written only when an error follows. Outside units each thread has a ring, which
 * the next {@code ERROR} of the thread writes before itself; inside a unit of {@link LOG#unit(String)} the unit has the
 * ring, which it drops when it succeeds and writes after its line when it fails. A held-back entry is a
 * {@linkplain LogEntry#snapshot() snapshot}: its values are rendered when it is held back, since objects may change
 * before the error.
 */
final class Breadcrumbs {

    /**
     * The ordinal of the highest level held back, {@code -1} when nothing is; read by {@link LOG#init()}.
     */
    static volatile int threshold = -1;

    /**
     * The most entries a ring keeps; the oldest go first. Read by {@link LOG#init()}.
     */
    static volatile int size = 200;

    /**
     * The most characters of a message or a value kept in a snapshot.
     */
    static final int MAX_CHARS = 2000;

    private static final ThreadLocal<Ring> RINGS = new ThreadLocal<>();

    private Breadcrumbs() {/**/}

    /**
     * Reads {@code nanolog.buffer} and {@code nanolog.buffer.size}.
     */
    static void configure() {
        String level = readChoice(PROPERTY_BUFFER, "off", "TRACE", "DEBUG", "INFO", "WARN");
        threshold = level.equals("off") ? -1 : LogEntry.LogEntryLevel.valueOf(level).ordinal();
        size = readPositive(PROPERTY_BUFFER_SIZE, 200);
    }

    /**
     * Whether a level is held back, and therefore counts as enabled.
     */
    static boolean buffers(LogEntry.LogEntryLevel level) {
        return level != null && level.ordinal() <= threshold;
    }

    /**
     * Whether an entry is held back: one at a held-back level, except the entries of {@code LOG.dbg}, which print at
     * once.
     */
    static boolean buffers(LogEntry entry) {
        return !entry.isDbg() && buffers(entry.getLevel());
    }

    /**
     * Holds an entry back in the ring of its unit, or of the thread outside units.
     */
    static void keep(LogEntry entry) {
        Units.Scope unit = entry.getUnit();
        Ring ring = unit == null ? threadRing() : unit.ring();
        ring.add(entry.snapshot());
    }

    private static Ring threadRing() {
        Ring ring = RINGS.get();
        if (ring == null) {
            ring = new Ring();
            RINGS.set(ring);
        }
        return ring;
    }

    /**
     * Takes the entries held back in the ring of the current thread.
     *
     * @return a header and the entries, oldest first; empty if there are none
     */
    static List<LogEntry> drainThread() {
        Ring ring = RINGS.get();
        return ring == null ? Collections.<LogEntry>emptyList() : ring.drain();
    }

    /**
     * The entries held back for a thread or a unit, at most {@link #size}.
     */
    static final class Ring {

        private final Deque<LogEntry> entries = new ArrayDeque<>();
        private long dropped;

        synchronized void add(LogEntry entry) {
            while (entries.size() >= size) {
                entries.removeFirst();
                dropped++;
            }
            entries.addLast(entry);
        }

        /**
         * Empties the ring.
         *
         * @return a header that says how many entries follow, and the entries, oldest first; empty if there are none
         */
        synchronized List<LogEntry> drain() {
            List<LogEntry> drained = new ArrayList<>();
            if (!entries.isEmpty()) {
                int highest = 0;
                for (LogEntry entry : entries) {
                    highest = Math.max(highest, entry.getLevel().ordinal());
                }
                LogEntry.LogEntryLevel level = LogEntry.LogEntryLevel.values()[highest];
                drained.add(new LogEntry(level, null, null, header(entries.size(), level, dropped)));
                drained.addAll(entries);
                entries.clear();
                dropped = 0;
            }
            return drained;
        }

        private static String header(int count, LogEntry.LogEntryLevel level, long dropped) {
            return "--- " + count + " earlier " + level.name() + (count == 1 ? " entry" : " entries") +
                   (dropped > 0 ? ", " + dropped + " older dropped" : "") + " ---";
        }
    }
}
