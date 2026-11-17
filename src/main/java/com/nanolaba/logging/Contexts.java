package com.nanolaba.logging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * The context of each thread, for {@link LOG#context(String, Object)} and {@link LOG#wrap(Runnable)}: the pairs every
 * entry of the thread carries.
 * <p>
 * A plain {@code ThreadLocal}, not an {@code InheritableThreadLocal}: a pool thread would inherit the context of the
 * code that happened to create it, not of the code that submitted the task, so tasks take theirs through
 * {@link #wrap(Runnable)}. The map of a thread is never modified: each change installs a new one, so an entry keeps
 * the map of its creation as its snapshot without copying. An empty context removes the thread local, so threads
 * without a context, virtual threads among them, keep nothing.
 */
final class Contexts {

    /**
     * The context of the thread, oldest key first; {@code null} when it is empty.
     */
    static final ThreadLocal<Map<String, Object>> CURRENT = new ThreadLocal<>();

    private static final LOG.Context NOTHING = () -> {
    };

    private Contexts() {/**/}

    /**
     * The context of the current thread.
     *
     * @return an unmodifiable map, empty when the thread has no context
     */
    static Map<String, Object> current() {
        Map<String, Object> context = CURRENT.get();
        return context == null ? Collections.<String, Object>emptyMap() : context;
    }

    private static void set(Map<String, Object> context) {
        if (context.isEmpty()) {
            CURRENT.remove();
        } else {
            CURRENT.set(context);
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
        set(with(context, key, value));
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
                set(present ? with(context, key, previous) : without(context, key));
            }
        }
    }

    /**
     * Makes a task run with the context the current thread has now, in whatever thread runs it, and gives that
     * thread its own context back afterwards.
     */
    static Runnable wrap(Runnable task) {
        Map<String, Object> snapshot = current();
        return () -> {
            Map<String, Object> previous = replace(snapshot);
            try {
                task.run();
            } finally {
                set(previous);
            }
        };
    }

    /**
     * Like {@link #wrap(Runnable)}, for a task that returns a value.
     */
    static <T> Callable<T> wrap(Callable<T> task) {
        Map<String, Object> snapshot = current();
        return () -> {
            Map<String, Object> previous = replace(snapshot);
            try {
                return task.call();
            } finally {
                set(previous);
            }
        };
    }

    private static Map<String, Object> replace(Map<String, Object> context) {
        Map<String, Object> previous = current();
        set(context);
        return previous;
    }
}
