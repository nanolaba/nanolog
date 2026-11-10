package com.nanolaba.logging;

import java.util.Arrays;

public class LogEntry {

    public enum LogEntryLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }

    private final LogEntryLevel level;
    private final Class sourceClass;
    private final Throwable throwable;
    private final Object message;
    private final String formattedMessage;
    private final Object[] args;

    private static final char ESCAPE_CHAR = '\\';

    public LogEntry(LogEntryLevel level, Class sourceClass, Throwable throwable, Object message, Object... args) {
        this.level = level;
        this.sourceClass = sourceClass;
        this.message = message;

        String pattern = message == null ? null : safeToString(message);
        // formatted with the original arguments: an unused trailing throwable changes nothing in the text
        this.formattedMessage = formatMessage(pattern, args);

        if (throwable == null && endsWithUnusedThrowable(pattern, args)) {
            // the SLF4J convention: LOG.error("Failed to load {}", id, e) keeps the stack trace of e
            this.throwable = (Throwable) args[args.length - 1];
            this.args = Arrays.copyOf(args, args.length - 1);
        } else {
            this.throwable = throwable;
            this.args = args;
        }
    }

    public LogEntryLevel getLevel() {
        return level;
    }

    public Class getSourceClass() {
        return sourceClass;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public Object getMessage() {
        return message;
    }

    public Object[] getArgs() {
        return args;
    }

    public String getFormattedMessage() {
        return formattedMessage;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
               "level=" + getLevel() +
               ", sourceClass=" + (getSourceClass() == null ? null : getSourceClass().getCanonicalName()) +
               ", throwable=" + safeToString(getThrowable()) +
               ", message=" + safeToString(getMessage()) +
               ", args=" + arrayToString(getArgs()) +
               '}';
    }

    private static String formatMessage(String msg, Object[] args) {
        if (msg == null) {
            return "null";
        } else if (args == null || args.length == 0) {
            return msg;
        } else {

            int currentDelimiterIndex = 0;
            int nextDelimiterIndex;

            StringBuilder result = new StringBuilder(msg.length() + 50);

            for (int argIndex = 0; argIndex < args.length; argIndex++) {

                nextDelimiterIndex = msg.indexOf("{}", currentDelimiterIndex);

                if (nextDelimiterIndex == -1) {
                    if (currentDelimiterIndex == 0) {
                        return msg;
                    }
                } else {
                    if (isEscapedDelimiter(msg, nextDelimiterIndex)) {
                        result.append(msg, currentDelimiterIndex, nextDelimiterIndex - 1);
                        if (isDoubleEscaped(msg, nextDelimiterIndex)) {
                            result.append(argToString(args[argIndex]));
                            currentDelimiterIndex = nextDelimiterIndex + 2;
                        } else {
                            argIndex--;
                            result.append('{');
                            currentDelimiterIndex = nextDelimiterIndex + 1;
                        }
                    } else {
                        result.append(msg, currentDelimiterIndex, nextDelimiterIndex);
                        result.append(argToString(args[argIndex]));
                        currentDelimiterIndex = nextDelimiterIndex + 2;
                    }
                }
            }

            result.append(msg, currentDelimiterIndex, msg.length());

            return result.toString();
        }
    }

    private static boolean endsWithUnusedThrowable(String pattern, Object[] args) {
        return args != null
               && args.length > 0
               && args[args.length - 1] instanceof Throwable
               && countPlaceholders(pattern) < args.length;
    }

    /**
     * Counts the {@code {}} placeholders that consume an argument, with the same escaping rules as the formatter:
     * {@code \{}} is a literal and consumes nothing, {@code \\{}} is a literal backslash followed by a placeholder.
     */
    static int countPlaceholders(String msg) {
        int count = 0;
        if (msg != null) {
            int index = msg.indexOf("{}");
            while (index != -1) {
                if (isEscapedDelimiter(msg, index) && !isDoubleEscaped(msg, index)) {
                    index = msg.indexOf("{}", index + 1);
                } else {
                    count++;
                    index = msg.indexOf("{}", index + 2);
                }
            }
        }
        return count;
    }

    private static boolean isEscapedDelimiter(String msg, int delimiterStartIndex) {
        return delimiterStartIndex != 0 && msg.charAt(delimiterStartIndex - 1) == ESCAPE_CHAR;
    }

    private static boolean isDoubleEscaped(String msg, int delimiterStartIndex) {
        return delimiterStartIndex >= 2 && msg.charAt(delimiterStartIndex - 2) == ESCAPE_CHAR;
    }

    private static String argToString(Object o) {
        if (o == null) {
            return "null";
        } else if (!o.getClass().isArray()) {
            return safeToString(o);
        } else if (o instanceof boolean[]) {
            return Arrays.toString((boolean[]) o);
        } else if (o instanceof byte[]) {
            return Arrays.toString((byte[]) o);
        } else if (o instanceof char[]) {
            return Arrays.toString((char[]) o);
        } else if (o instanceof short[]) {
            return Arrays.toString((short[]) o);
        } else if (o instanceof int[]) {
            return Arrays.toString((int[]) o);
        } else if (o instanceof long[]) {
            return Arrays.toString((long[]) o);
        } else if (o instanceof float[]) {
            return Arrays.toString((float[]) o);
        } else if (o instanceof double[]) {
            return Arrays.toString((double[]) o);
        } else {
            return arrayToString((Object[]) o);
        }
    }

    private static String arrayToString(Object[] array) {
        try {
            return Arrays.deepToString(array);
        } catch (Throwable t) {
            // some element has a broken toString(): render element by element, so only that element is replaced
            StringBuilder result = new StringBuilder("[");
            for (int i = 0; i < array.length; i++) {
                if (i > 0) {
                    result.append(", ");
                }
                result.append(argToString(array[i]));
            }
            return result.append(']').toString();
        }
    }

    private static String safeToString(Object o) {
        try {
            return o == null ? "null" : o.toString();
        } catch (Throwable t) {
            return "[FAILED toString()]";
        }
    }
}
