package com.nanolaba.logging;

import java.io.Console;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.nanolaba.logging.DefaultLoggerCreator.environment;

/**
 * Whether the console gets ANSI colours, for {@code nanolog.console.color}, and the colours of the level words.
 */
final class Colors {

    static final String AUTO = "auto";
    static final String ALWAYS = "always";
    static final String NEVER = "never";

    static final String RESET = "\u001b[0m";

    /**
     * The console of the JVM; replaced in tests.
     */
    static volatile Supplier<Object> console = System::console;

    /**
     * {@code Console.isTerminal()}, since JDK 22, where {@code System.console()} returns a console for redirected
     * output too; {@code null} before it. Replaced in tests.
     */
    static volatile Method isTerminal = method(Console.class, "isTerminal");

    /**
     * The system properties, for {@code os.name}; replaced in tests.
     */
    static volatile Function<String, String> properties = System::getProperty;

    private Colors() {/**/}

    /**
     * Whether a mode colours the console. {@code auto} colours a terminal: not when {@code NO_COLOR} is set, always
     * when {@code FORCE_COLOR} is set to anything but {@code 0} or {@code false}, and otherwise only when the console
     * is a terminal whose {@code TERM} is not {@code dumb}.
     *
     * @param mode {@code auto}, {@code always} or {@code never}
     */
    static boolean enabled(String mode) {
        if (!AUTO.equals(mode)) {
            return ALWAYS.equals(mode);
        }
        if (isSet(environment.apply("NO_COLOR"))) {
            return false;
        }
        String force = environment.apply("FORCE_COLOR");
        if (isSet(force)) {
            String value = force.trim().toLowerCase(Locale.ROOT);
            return !value.equals("0") && !value.equals("false");
        }
        return !"dumb".equals(environment.apply("TERM")) && isTerminal();
    }

    private static boolean isSet(String variable) {
        return variable != null && !variable.isEmpty();
    }

    /**
     * Whether standard output is a terminal that shows ANSI colours. Up to JDK 21 the JVM has a console only when
     * standard input and output are a terminal; since JDK 22 the console says so itself. A Windows console shows the
     * escape codes as text unless the program turns their processing on, which Java does not do.
     */
    static boolean isTerminal() {
        Object terminal = console.get();
        if (terminal == null || String.valueOf(properties.apply("os.name")).startsWith("Windows")) {
            return false;
        }
        Method method = isTerminal;
        if (method == null) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(terminal));
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * A public method without parameters; {@code null} if the class has none of that name.
     */
    static Method method(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The colour of the level word of an entry: red {@code ERROR}, yellow {@code WARN}, green {@code INFO}, dim
     * {@code DEBUG} and {@code TRACE}, magenta {@code DBG}.
     */
    static String of(LogEntry entry) {
        LogEntry.LogEntryLevel level = entry.getLevel();
        if (entry.isDbg()) {
            return "\u001b[35m";
        } else if (level == LogEntry.LogEntryLevel.ERROR) {
            return "\u001b[31m";
        } else if (level == LogEntry.LogEntryLevel.WARN) {
            return "\u001b[33m";
        } else if (level == LogEntry.LogEntryLevel.INFO) {
            return "\u001b[32m";
        }
        return "\u001b[2m";
    }
}
