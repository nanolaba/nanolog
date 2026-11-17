package com.nanolaba.logging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The entries of {@link LOG#dbg(Object)}: each argument as written in the source, followed by its value and type,
 * {@code order.total() = 129.90 (BigDecimal)}. Without the source text only the values are printed.
 * <p>
 * {@code dbg} is meant to be temporary, so its call sites are remembered and listed when the JVM exits, and
 * {@code nanolog.dbg} switches the calls off or reports each of them.
 */
final class Dbg {

    /**
     * The message of {@code LOG.dbg()} without arguments.
     */
    static final String REACHED = "reached";

    /**
     * What {@code dbg} calls do, {@code nanolog.dbg}.
     */
    enum Mode {
        /**
         * Print; the default.
         */
        ON,
        /**
         * Print nothing and cost nothing beyond the call, for production.
         */
        OFF,
        /**
         * Print, and report every call as a diagnostic, so {@code MemoryLogger.assertNoDiagnostics()} fails.
         */
        FAIL
    }

    /**
     * The mode, read by {@link LOG#init()}; {@code volatile} for the same reason as its logger.
     */
    static volatile Mode mode = Mode.ON;

    /**
     * Set in agent mode, read by {@link LOG#init()}: a {@code dbg} call prints whatever the levels, because the agent
     * put it there to see the value. {@code volatile} like {@link #mode}.
     */
    static volatile boolean ignoreLevels;

    /**
     * Registers the hook that lists the call sites at exit. Tests replace it to run the hook themselves and must
     * restore it afterwards.
     */
    static Consumer<Thread> shutdownHooks = Runtime.getRuntime()::addShutdownHook;

    /**
     * The call sites of the {@code dbg} entries written in this JVM, in the order they first ran. Call sites are lines
     * of code, so the set stays as small as the code.
     */
    private static final Set<String> CALLS = Collections.synchronizedSet(new LinkedHashSet<>());

    private static final AtomicBoolean HOOK_REGISTERED = new AtomicBoolean();

    private Dbg() {/**/}

    /**
     * Remembers that a {@code dbg} call site wrote an entry. The first call registers the hook that lists them at
     * exit; a JVM that is already shutting down, or a security manager that forbids hooks, leaves the list unprinted.
     *
     * @param frame the frame of the call; {@code null} if unknown
     * @return {@code true} the first time the call site runs
     */
    static boolean ran(StackTraceElement frame) {
        if (HOOK_REGISTERED.compareAndSet(false, true)) {
            try {
                shutdownHooks.accept(new Thread(Dbg::printCalls, "nanolog-dbg"));
            } catch (RuntimeException e) {
                // IllegalStateException during shutdown, SecurityException: the calls are still printed
            }
        }
        return CALLS.add(site(frame));
    }

    /**
     * The call site as listed at exit: {@code OrderService.java:87}.
     */
    static String site(StackTraceElement frame) {
        if (frame == null) {
            return "unknown source";
        }
        String file = frame.getFileName() == null ? frame.getClassName() : frame.getFileName();
        return frame.getLineNumber() < 0 ? file : file + ":" + frame.getLineNumber();
    }

    /**
     * Prints the call sites that ran, the shutdown hook; nothing if none did.
     */
    static void printCalls() {
        List<String> calls;
        synchronized (CALLS) {
            calls = new ArrayList<>(CALLS);
        }
        if (!calls.isEmpty()) {
            DefaultLoggerCreator.printDiagnostic(calls.size() + (calls.size() == 1 ? " LOG.dbg call" : " LOG.dbg calls") +
                                                 " ran in this JVM: " + String.join(", ", calls));
        }
    }

    /**
     * Forgets the call sites and the hook; for tests.
     */
    static void clearCalls() {
        CALLS.clear();
        HOOK_REGISTERED.set(false);
    }

    /**
     * The entry of a call whose level is enabled for its caller.
     */
    static LogEntry entry(CallerDetection.Caller caller, Object[] values, long skippedCount, String callerBoundary) {
        String text = caller.frame == null ? null : DbgSource.argumentsText(caller.frame, caller.sourceClass);
        return LogEntry.createDbg(caller.sourceClass, caller.frame, message(text, values), skippedCount, callerBoundary);
    }

    /**
     * The values of {@code dbg(first, second, more...)}. A {@code null} array stands for one {@code null} value: javac
     * passes a trailing {@code null} argument as the array itself.
     */
    static Object[] values(Object first, Object second, Object[] more) {
        Object[] rest = more == null ? new Object[]{null} : more;
        Object[] values = new Object[rest.length + 2];
        values[0] = first;
        values[1] = second;
        System.arraycopy(rest, 0, values, 2, rest.length);
        return values;
    }

    /**
     * Pairs the values with the text of the arguments, if the text splits into one expression per value.
     *
     * @param text   the text of the arguments from the source; {@code null} if unknown
     * @param values the values of the arguments
     */
    static String message(String text, Object[] values) {
        if (values.length == 0) {
            return REACHED;
        }
        List<String> expressions;
        if (text == null || text.isEmpty()) {
            expressions = Collections.emptyList();
        } else if (values.length == 1) {
            // one argument: the whole text, even if it has commas outside parentheses, as in new HashMap<K, V>()
            expressions = Collections.singletonList(text);
        } else {
            expressions = DbgSource.splitArguments(text);
        }
        boolean named = expressions.size() == values.length;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                out.append(", ");
            }
            if (named) {
                out.append(expressions.get(i)).append(" = ");
            }
            out.append(render(values[i]));
        }
        return out.toString();
    }

    /**
     * A value as {@code dbg} prints it: strings and chars quoted and escaped, so the value stays on one line; objects
     * without a {@code toString()} of their own field by field; each followed by its type unless the text starts
     * with the type name. A string passed to {@code dbg} itself is never cut.
     */
    static String render(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return Dump.quote((String) value);
        } else if (value instanceof Character) {
            return Dump.quote((Character) value);
        }
        String text = Dump.dump(value);
        // an enum constant with a body is an instance of an anonymous subclass: name the enum, not Mode$1
        Class type = value instanceof Enum ? ((Enum) value).getDeclaringClass() : value.getClass();
        String name = LogEntry.simpleName(type);
        return text.startsWith(name) ? text : text + " (" + name + ")";
    }
}
