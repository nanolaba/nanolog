package com.nanolaba.logging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * The context of each thread: the pairs of {@link LOG#context(String, Object)}, which every entry of the thread
 * carries, and the open unit of {@link LOG#unit(String)}, which counts them. {@link LOG#wrap(Runnable)} carries both
 * into a task.
 * <p>
 * A plain {@code ThreadLocal}, not an {@code InheritableThreadLocal}: a pool thread would inherit the context of the
 * code that happened to create it, not of the code that submitted the task. The state of a thread is never modified:
 * each change installs a new one, so an entry keeps the map of its creation as its snapshot without copying. An empty
 * state removes the thread local, so threads without a context, virtual threads among them, keep nothing.
 */
final class Contexts {

    /**
     * The state of a thread: its pairs, oldest key first, and its open unit. Never empty: no pairs and no unit is no
     * state.
     */
    static final class State {

        final Map<String, Object> pairs;
        final Units.Scope unit;

        State(Map<String, Object> pairs, Units.Scope unit) {
            this.pairs = pairs;
            this.unit = unit;
        }
    }

    /**
     * The state of the thread; {@code null} when it has no pairs and no unit.
     */
    static final ThreadLocal<State> CURRENT = new ThreadLocal<>();

    private static final LOG.Context NOTHING = () -> {
    };

    private Contexts() {/**/}

    /**
     * The pairs of a state.
     *
     * @return an unmodifiable map, empty without a state
     */
    static Map<String, Object> pairs(State state) {
        return state == null ? Collections.<String, Object>emptyMap() : state.pairs;
    }

    /**
     * The unit of a state.
     *
     * @return the unit; {@code null} without one
     */
    static Units.Scope unit(State state) {
        return state == null ? null : state.unit;
    }

    /**
     * The pairs of the current thread.
     */
    static Map<String, Object> current() {
        return pairs(CURRENT.get());
    }

    /**
     * The unit of the current thread; {@code null} without one.
     */
    static Units.Scope unit() {
        return unit(CURRENT.get());
    }

    private static void set(Map<String, Object> pairs, Units.Scope unit) {
        if (pairs.isEmpty() && unit == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(new State(pairs, unit));
        }
    }

    /**
     * Adds a pair to the context of the current thread until the returned scope is closed.
     *
     * @param key   the key; {@code null} changes nothing
     * @param value the value; may be {@code null}
     * @return the scope that puts the previous value of the key back
     */
    static LOG.Context put(String key, Object value) {
        if (key == null) {
            return NOTHING;
        }
        Map<String, Object> context = current();
        LOG.Context scope = new Scope(key, context.containsKey(key), context.get(key));
        set(with(context, key, value), unit());
        return scope;
    }

    private static Map<String, Object> with(Map<String, Object> context, String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(context);
        copy.put(key, value);
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Object> without(Map<String, Object> context, String key) {
        Map<String, Object> copy = new LinkedHashMap<>(context);
        copy.remove(key);
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Puts back what one key held before its scope opened, whatever happened to the other keys meanwhile, so scopes
     * of different keys may close in any order.
     */
    private static final class Scope implements LOG.Context {

        private final String key;
        private final boolean present;
        private final Object previous;
        private boolean closed;

        Scope(String key, boolean present, Object previous) {
            this.key = key;
            this.present = present;
            this.previous = previous;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                Map<String, Object> context = current();
                set(present ? with(context, key, previous) : without(context, key), unit());
            }
        }
    }

    /**
     * Makes a unit the open unit of the current thread.
     */
    static void enter(Units.Scope unit) {
        set(current(), unit);
    }

    /**
     * Makes the nearest open ancestor of a closing unit the open unit of the current thread, if the closing unit is
     * the open one; a unit closed out of order leaves the thread as it is.
     */
    static void leave(Units.Scope unit) {
        if (unit() == unit) {
            set(current(), unit.openAncestor());
        }
    }

    /**
     * Makes a task run with the context the current thread has now, in whatever thread runs it, and gives that
     * thread its own context back afterwards.
     */
    static Runnable wrap(Runnable task) {
        Map<String, Object> pairs = current();
        Units.Scope unit = unit();
        return () -> {
            Map<String, Object> previousPairs = current();
            Units.Scope previousUnit = unit();
            set(pairs, unit);
            try {
                task.run();
            } finally {
                set(previousPairs, previousUnit);
            }
        };
    }

    /**
     * Like {@link #wrap(Runnable)}, for a task that returns a value.
     */
    static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, Object> pairs = current();
        Units.Scope unit = unit();
        return () -> {
            Map<String, Object> previousPairs = current();
            Units.Scope previousUnit = unit();
            set(pairs, unit);
            try {
                return task.call();
            } finally {
                set(previousPairs, previousUnit);
            }
        };
    }
}
