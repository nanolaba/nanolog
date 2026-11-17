package com.nanolaba.logging;

import java.util.Collections;
import java.util.List;

/**
 * The entries of {@link LOG#dbg(Object)}: each argument as written in the source, followed by its value and type,
 * {@code order.total() = 129.90 (BigDecimal)}. Without the source text only the values are printed.
 */
final class Dbg {

    /**
     * The message of {@code LOG.dbg()} without arguments.
     */
    static final String REACHED = "reached";

    private Dbg() {/**/}

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
     * A value as {@code dbg} prints it: strings and chars quoted and escaped, so the value stays on one line;
     * anything else as in a message, followed by its type unless the text starts with the type name.
     */
    static String render(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return quote((String) value);
        } else if (value instanceof Character) {
            return quote((Character) value);
        }
        String text = LogEntry.argToString(value);
        // an enum constant with a body is an instance of an anonymous subclass: name the enum, not Mode$1
        Class type = value instanceof Enum ? ((Enum) value).getDeclaringClass() : value.getClass();
        String name = LogEntry.simpleName(type);
        return text.startsWith(name) ? text : text + " (" + name + ")";
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder();
        JsonLogger.appendString(out, value);
        return out.toString();
    }

    private static String quote(char value) {
        if (value == '\'') {
            return "'\\''";
        } else if (value == '"') {
            return "'\"'";
        }
        String quoted = quote(String.valueOf(value));
        return "'" + quoted.substring(1, quoted.length() - 1) + "'";
    }
}
