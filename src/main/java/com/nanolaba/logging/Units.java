package com.nanolaba.logging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_UNIT_SAMPLE;
import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_UNIT_SLOW_MS;
import static com.nanolaba.logging.DefaultLoggerCreator.getProperty;
import static com.nanolaba.logging.DefaultLoggerCreator.printDiagnostic;
import static com.nanolaba.logging.LOG.kv;

/**
 * The units of work of {@link LOG#unit(String)}: one line per request or job, the wide events. A unit counts the
 * warnings and errors logged inside it, in its own thread and in the tasks that {@link LOG#wrap(Runnable)} carries it
 * into, and when it closes writes one entry with its name, outcome, duration, fields and counts. The units still open
 * when the run ends are written with the outcome {@code interrupted} by {@link Summary#printOnce()}.
 */
final class Units {

    static final String OK = "ok";
    static final String ERROR = "error";
    static final String INTERRUPTED = "interrupted";

    /**
     * The message of a unit line: name, outcome, duration in milliseconds.
     */
    static final String TEMPLATE = "{} {} {} ms";

    /**
     * The fields a unit sets itself; {@link Scope#put} ignores them.
     */
    static final List<String> RESERVED = Arrays.asList("unit", "outcome", "duration_ms", "parent", "warn", "error",
                                                       "error.type", "error.message", "error.key");

    /**
     * The share of the units that succeed fast which are written, {@code nanolog.unit.sample}; read by
     * {@link LOG#init()}.
     */
    static volatile double sample = 1;

    /**
     * The duration from which a unit is slow and always written, {@code nanolog.unit.slowMs}; {@code Long.MAX_VALUE}
     * when it is not set. Read by {@link LOG#init()}.
     */
    static volatile long slowNanos = Long.MAX_VALUE;

    /**
     * Draws a number from 0 inclusive to 1 exclusive for sampling; replaced in tests.
     */
    static DoubleSupplier random = () -> ThreadLocalRandom.current().nextDouble();

    /**
     * The units not closed yet, for the end of the run.
     */
    static final Set<Scope> OPEN = ConcurrentHashMap.newKeySet();

    private Units() {/**/}

    /**
     * Reads {@code nanolog.unit.sample} and {@code nanolog.unit.slowMs}.
     */
    static void configure() {
        sample = readSample();
        slowNanos = readSlowNanos();
    }

    private static double readSample() {
        String value = getProperty(PROPERTY_UNIT_SAMPLE);
        if (value != null) {
            try {
                double share = Double.parseDouble(value.trim());
                if (share >= 0 && share <= 1) {
                    return share;
                }
            } catch (NumberFormatException e) {
                // reported below
            }
            printDiagnostic("Invalid value \"" + value + "\" of \"" + PROPERTY_UNIT_SAMPLE + "\": expected a fraction from 0 to 1");
        }
        return 1;
    }

    private static long readSlowNanos() {
        String value = getProperty(PROPERTY_UNIT_SLOW_MS);
        if (value != null) {
            try {
                long millis = Long.parseLong(value.trim());
                if (millis >= 0) {
                    return TimeUnit.MILLISECONDS.toNanos(millis);
                }
            } catch (NumberFormatException e) {
                // reported below
            }
            printDiagnostic("Invalid value \"" + value + "\" of \"" + PROPERTY_UNIT_SLOW_MS + "\": expected a number of milliseconds");
        }
        return Long.MAX_VALUE;
    }

    /**
     * Opens a unit in the current thread, inside the unit open there, if any.
     */
    static Scope open(String name, CallerDetection.Caller caller) {
        Scope unit = new Scope(String.valueOf(name), Contexts.unit(), caller, LOG.nanoClock.getAsLong());
        OPEN.add(unit);
        Contexts.enter(unit);
        Summary.registerHook();
        return unit;
    }

    /**
     * Writes the units open in the current thread with the outcome {@code interrupted}, the innermost first; for a
     * thread that dies of an uncaught exception.
     */
    static void interruptThread() {
        for (Scope unit = Contexts.unit(); unit != null; unit = unit.parent) {
            unit.finish(true);
        }
    }

    /**
     * Writes every open unit with the outcome {@code interrupted}, the innermost first.
     */
    static void interruptOpen() {
        List<Scope> open = new ArrayList<>(OPEN);
        open.sort(Comparator.comparingInt((Scope unit) -> -unit.depth));
        for (Scope unit : open) {
            unit.finish(true);
        }
    }

    /**
     * A stable key of a failure, eight hexadecimal digits: the same text gives the same key in every run.
     */
    static String key(String text) {
        return String.format(Locale.ROOT, "%08x", text.hashCode());
    }

    /**
     * The first error of a unit: the type, the message and the key, {@code null} for what is not known.
     */
    private static String[] describe(LogEntry entry) {
        Throwable throwable = entry.getThrowable();
        String type = throwable == null ? null : throwable.getClass().getName();
        String message = throwable == null ? entry.getFormattedMessage() : messageOf(throwable);
        return new String[]{type, message, key(Summary.template(entry) + " at " + Summary.where(entry))};
    }

    private static String[] describe(Throwable throwable) {
        String type = throwable.getClass().getName();
        return new String[]{type, messageOf(throwable), key(type + " at " + Dbg.site(topFrame(throwable)))};
    }

    private static String messageOf(Throwable throwable) {
        try {
            return throwable.getMessage();
        } catch (Throwable t) {
            return "[FAILED getMessage()]";
        }
    }

    private static StackTraceElement topFrame(Throwable throwable) {
        StackTraceElement[] trace;
        try {
            trace = throwable.getStackTrace();
        } catch (Throwable t) {
            return null;
        }
        return trace != null && trace.length > 0 ? trace[0] : null;
    }

    /**
     * An open or closed unit.
     */
    static final class Scope implements LOG.Unit {

        private final String name;
        private final Scope parent;
        private final int depth;
        private final Class sourceClass;
        private final StackTraceElement frame;
        private final long start;

        /**
         * The fields of {@link #put}, guarded by itself.
         */
        private final Map<String, Object> fields = new LinkedHashMap<>();
        private final AtomicInteger warnings = new AtomicInteger();
        private final AtomicInteger errors = new AtomicInteger();
        private final AtomicReference<String[]> firstError = new AtomicReference<>();
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        /**
         * The breadcrumbs of the unit, created by the first entry held back.
         */
        private Breadcrumbs.Ring ring;

        Scope(String name, Scope parent, CallerDetection.Caller caller, long start) {
            this.name = name;
            this.parent = parent;
            this.depth = parent == null ? 0 : parent.depth + 1;
            this.sourceClass = caller.sourceClass;
            this.frame = caller.frame;
            this.start = start;
        }

        @Override
        public LOG.Unit put(String key, Object value) {
            if (key != null && !RESERVED.contains(key)) {
                synchronized (fields) {
                    fields.put(key, value);
                }
            }
            return this;
        }

        @Override
        public void fail(Throwable error) {
            if (error != null && failure.compareAndSet(null, error)) {
                firstError.compareAndSet(null, describe(error));
            }
        }

        @Override
        public void run(Runnable body) {
            try {
                body.run();
            } catch (Throwable t) {
                fail(t);
                throw t;
            } finally {
                close();
            }
        }

        @Override
        public <T> T run(Supplier<T> body) {
            try {
                return body.get();
            } catch (Throwable t) {
                fail(t);
                throw t;
            } finally {
                close();
            }
        }

        @Override
        public void close() {
            Contexts.leave(this);
            finish(false);
        }

        /**
         * The ring of the breadcrumbs held back inside this unit.
         */
        synchronized Breadcrumbs.Ring ring() {
            if (ring == null) {
                ring = new Breadcrumbs.Ring();
            }
            return ring;
        }

        private synchronized List<LogEntry> takeBreadcrumbs() {
            return ring == null ? Collections.<LogEntry>emptyList() : ring.drain();
        }

        /**
         * The nearest unit around this one that is still open; {@code null} if there is none.
         */
        Scope openAncestor() {
            Scope ancestor = parent;
            while (ancestor != null && ancestor.closed.get()) {
                ancestor = ancestor.parent;
            }
            return ancestor;
        }

        /**
         * Counts an entry logged inside this unit, in this unit and in every unit around it.
         */
        void count(LogEntry entry) {
            for (Scope unit = this; unit != null; unit = unit.parent) {
                unit.add(entry);
            }
        }

        private void add(LogEntry entry) {
            if (entry.getLevel() == LogEntry.LogEntryLevel.WARN) {
                warnings.incrementAndGet();
            } else if (entry.getLevel() == LogEntry.LogEntryLevel.ERROR) {
                errors.incrementAndGet();
                firstError.compareAndSet(null, describe(entry));
            }
        }

        /**
         * Writes the line of the unit, once: unless sampling drops a unit that succeeded fast. A failed unit writes the
         * breadcrumbs held back inside it after its line; a successful one drops them.
         */
        void finish(boolean interrupted) {
            if (closed.compareAndSet(false, true)) {
                OPEN.remove(this);
                long nanos = LOG.nanoClock.getAsLong() - start;
                String outcome = interrupted ? INTERRUPTED : errors.get() > 0 || failure.get() != null ? ERROR : OK;
                List<LogEntry> breadcrumbs = takeBreadcrumbs();
                if (outcome.equals(OK) && nanos < slowNanos && random.getAsDouble() >= sample) {
                    return;
                }
                LOG.writeUnit(outcome.equals(OK) ? LogEntry.LogEntryLevel.INFO : LogEntry.LogEntryLevel.ERROR,
                              sourceClass, frame, failure.get(), arguments(outcome, nanos / 1_000_000));
                if (!outcome.equals(OK)) {
                    LOG.deliver(breadcrumbs);
                }
            }
        }

        private Object[] arguments(String outcome, long millis) {
            List<Object> arguments = new ArrayList<>();
            arguments.add(kv("unit", name));
            arguments.add(kv("outcome", outcome));
            arguments.add(kv("duration_ms", millis));
            if (parent != null) {
                arguments.add(kv("parent", parent.name));
            }
            synchronized (fields) {
                for (Map.Entry<String, Object> field : fields.entrySet()) {
                    arguments.add(kv(field.getKey(), field.getValue()));
                }
            }
            if (warnings.get() > 0) {
                arguments.add(kv("warn", warnings.get()));
            }
            if (errors.get() > 0) {
                arguments.add(kv("error", errors.get()));
            }
            String[] error = firstError.get();
            if (error != null) {
                if (error[0] != null) {
                    arguments.add(kv("error.type", error[0]));
                }
                if (error[1] != null) {
                    arguments.add(kv("error.message", error[1]));
                }
                arguments.add(kv("error.key", error[2]));
            }
            return arguments.toArray();
        }
    }
}
