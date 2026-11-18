package com.nanolaba.logging;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

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
     * Runs a body with a timer and closes it; an exception the body throws is written with the line and thrown on.
     */
    static <T> T run(LOG.Timer timer, Supplier<T> body) {
        try {
            return body.get();
        } catch (Throwable t) {
            if (timer instanceof Timing) {
                ((Timing) timer).finish(t);
            }
            throw t;
        } finally {
            timer.close();
        }
    }

    /**
     * A running timer.
     */
    static final class Timing implements LOG.Timer {

        private final LogEntry.LogEntryLevel level;
        private final String name;
        private final Class sourceClass;
        private final StackTraceElement frame;
        private final long start;
        private final AtomicBoolean closed = new AtomicBoolean();

        Timing(LogEntry.LogEntryLevel level, String name, CallerDetection.Caller caller, long start) {
            this.level = level;
            this.name = name;
            this.sourceClass = caller.sourceClass;
            this.frame = caller.frame;
            this.start = start;
        }

        @Override
        public void close() {
            finish(null);
        }

        /**
         * Writes the line, once.
         *
         * @param error the exception that ended the block; {@code null} if it ended normally
         */
        void finish(Throwable error) {
            if (closed.compareAndSet(false, true)) {
                long millis = (LOG.nanoClock.getAsLong() - start) / 1_000_000;
                LOG.writeTimer(level, sourceClass, frame, error, error == null ? TOOK : FAILED,
                               new Object[]{kv("timer", name), kv("duration_ms", millis)});
            }
        }
    }
}
