package com.nanolaba.logging;

import java.util.concurrent.atomic.AtomicBoolean;

import static com.nanolaba.logging.LOG.kv;

/**
 * The timers of {@link LOG#time(String)}: the duration of a block as one entry, {@code Load users took 123 ms}, with
 * the fields {@code timer} and {@code duration_ms}, the field model of the units of {@link LOG#unit(String)}.
 */
final class Timers {

    static final String TOOK = "{} took {} ms";
    static final String FAILED = "{} failed after {} ms";

    /**
     * The timer of a level that is off: it reads no clock and writes nothing.
     */
    static final LOG.Timer NOTHING = () -> {
    };

    private Timers() {/**/}

    /**
     * A running timer.
     */
    static final class Timing implements LOG.Timer {

        /**
         * The caller boundary of the line that {@link #close()} writes: the frame after those of the timer is the code
         * that closes it, the end of a try-with-resources block.
         */
        static final String BOUNDARY = Timing.class.getName();

        final LogEntry.LogEntryLevel level;
        final Class sourceClass;
        final StackTraceElement frame;
        /**
         * The caller boundary of the line that {@code LOG.timed} writes, from frames of {@code LOG} only: the skipped
         * helper that called it, or {@code LOG}.
         */
        final String timedBoundary;
        private final String name;
        private final long start;
        private final AtomicBoolean closed = new AtomicBoolean();

        Timing(LogEntry.LogEntryLevel level, String name, CallerDetection.Caller caller, long start) {
            this.level = level;
            this.name = name;
            this.sourceClass = caller.sourceClass;
            this.frame = caller.frame;
            this.timedBoundary = caller.boundaryOr(LogEntry.LOG_BOUNDARY);
            this.start = start;
        }

        @Override
        public void close() {
            if (stop()) {
                LOG.writeTimer(this, null, BOUNDARY);
            }
        }

        /**
         * Stops the timer: {@code true} the first time only, and then the caller writes the line.
         */
        boolean stop() {
            return closed.compareAndSet(false, true);
        }

        /**
         * The fields of the line: the name and the milliseconds since the start.
         */
        Object[] fields() {
            long millis = (LOG.nanoClock.getAsLong() - start) / 1_000_000;
            return new Object[]{kv("timer", name), kv("duration_ms", millis)};
        }
    }
}
