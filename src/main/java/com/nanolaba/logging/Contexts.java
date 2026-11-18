package com.nanolaba.logging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.nanolaba.logging.DefaultLoggerCreator.PROPERTY_CONTEXT_PROVIDER;
import static com.nanolaba.logging.DefaultLoggerCreator.getProperty;
import static com.nanolaba.logging.DefaultLoggerCreator.printDiagnostic;
import static com.nanolaba.logging.DefaultLoggerCreator.printInvalid;

/**
 * The context of each thread: the pairs of {@link LOG#context(String, Object)}, which every entry of the thread
 * carries, and the open unit of {@link LOG#unit(String)}, which counts them. {@link LOG#wrap(Runnable)} carries both
 * into a task.
 * <p>
 * A plain {@code ThreadLocal}, not an {@code InheritableThreadLocal}: a pool thread would inherit the context of the
 * code that happened to create it, not of the code that submitted the task. The state of a thread is never modified:
 * each change installs a new one, so an entry keeps the map of its creation as its snapshot without copying. An empty
 * state removes the thread local, so threads without a context, virtual threads among them, keep nothing.
 * <p>
 * The context providers of {@link LOG#addContextProvider(Supplier)} and {@code nanolog.context.provider} add the keys of
 * a context of their own, such as the trace ID of OpenTelemetry, to each entry when it is created.
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

    // PROVIDERS

    /**
     * The providers of {@link LOG#addContextProvider(Supplier)}, which {@link LOG#init()} keeps.
     */
    private static final List<Supplier<? extends Map<String, ?>>> ADDED = new ArrayList<>();

    /**
     * The providers of {@code nanolog.context.provider}, read by {@link LOG#init()}.
     */
    private static List<Supplier<? extends Map<String, ?>>> configured = Collections.emptyList();

    /**
     * Every provider, the configured ones first; replaced, never modified, so an entry reads it without a lock.
     */
    private static volatile List<Supplier<? extends Map<String, ?>>> providers = Collections.emptyList();

    /**
     * Set while the providers of the thread are asked: an entry logged by a provider gets no keys from them, and does
     * not ask them again.
     */
    private static final ThreadLocal<Boolean> ASKING = new ThreadLocal<>();

    /**
     * The classes of the providers that failed, each reported once.
     */
    private static final Set<Class> FAILED = ConcurrentHashMap.newKeySet();

    static synchronized void addProvider(Supplier<? extends Map<String, ?>> provider) {
        if (provider != null && !ADDED.contains(provider)) {
            ADDED.add(provider);
            updateProviders();
        }
    }

    static synchronized void removeProvider(Supplier<? extends Map<String, ?>> provider) {
        ADDED.remove(provider);
        updateProviders();
    }

    /**
     * Forgets the added providers and the failures reported; for tests.
     */
    static synchronized void forgetProviders() {
        ADDED.clear();
        FAILED.clear();
        updateProviders();
    }

    /**
     * Reads {@code nanolog.context.provider}: class names separated by commas. A class that cannot be loaded, created or
     * used as a provider prints a diagnostic and is left out; empty items are ignored.
     */
    static synchronized void configureProviders() {
        String value = getProperty(PROPERTY_CONTEXT_PROVIDER);
        List<Supplier<? extends Map<String, ?>>> read = new ArrayList<>();
        if (value != null) {
            for (String item : value.split(",")) {
                String name = item.trim();
                if (!name.isEmpty()) {
                    try {
                        read.add(create(name));
                    } catch (Throwable t) {
                        printInvalid(PROPERTY_CONTEXT_PROVIDER, name, "expected a class implementing Supplier<Map<String, ?>> " +
                                                                      "with a public no-argument constructor (" + LogEntry.safeToString(t) + ")");
                    }
                }
            }
        }
        configured = read;
        updateProviders();
    }

    @SuppressWarnings("unchecked")
    private static Supplier<? extends Map<String, ?>> create(String name) throws Exception {
        Class<?> type = CallerDetection.ThrowableWalk.resolveClass(name);
        if (type == null) {
            throw new ClassNotFoundException(name);
        }
        return Supplier.class.cast(type.getConstructor().newInstance());
    }

    private static void updateProviders() {
        List<Supplier<? extends Map<String, ?>>> all = new ArrayList<>(configured);
        all.addAll(ADDED);
        providers = all;
    }

    /**
     * The pairs of a thread with the keys of the providers added after them. A pair of the thread wins over a provider,
     * and an earlier provider over a later one. Without providers, or without a key to add, the pairs themselves.
     */
    static Map<String, Object> withProviders(Map<String, Object> pairs) {
        List<Supplier<? extends Map<String, ?>>> current = providers;
        if (current.isEmpty() || ASKING.get() != null) {
            return pairs;
        }
        Map<String, Object> merged = new LinkedHashMap<>(pairs);
        ASKING.set(Boolean.TRUE);
        try {
            for (Supplier<? extends Map<String, ?>> provider : current) {
                for (Map.Entry<String, Object> pair : ask(provider).entrySet()) {
                    if (!merged.containsKey(pair.getKey())) {
                        merged.put(pair.getKey(), pair.getValue());
                    }
                }
            }
        } finally {
            ASKING.remove();
        }
        return merged.size() == pairs.size() ? pairs : Collections.unmodifiableMap(merged);
    }

    /**
     * The keys of one provider, copied before any is used, so a provider that fails halfway, or gives a key that is
     * not a string, adds none. A failure is reported once per class of provider, or each time with
     * {@code nanolog.diagnostics=all}.
     */
    private static Map<String, Object> ask(Supplier<? extends Map<String, ?>> provider) {
        try {
            Map<String, Object> keys = new LinkedHashMap<>();
            Map<String, ?> provided = provider.get();
            if (provided != null) {
                for (Map.Entry<String, ?> pair : provided.entrySet()) {
                    String key = pair.getKey();
                    if (key != null) {
                        keys.put(key, pair.getValue());
                    }
                }
            }
            return keys;
        } catch (Throwable t) {
            if (FAILED.add(provider.getClass()) || DefaultLoggerCreator.diagnostics == DefaultLoggerCreator.Diagnostics.ALL) {
                printDiagnostic("Context provider " + provider.getClass().getName() + " threw " + LogEntry.safeToString(t) +
                                "; entries are logged without its keys");
            }
            return Collections.emptyMap();
        }
    }

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
