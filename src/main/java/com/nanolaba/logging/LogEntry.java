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
        this.throwable = throwable;
        this.message = message;
        this.args = args;
        this.formattedMessage = formatMessage();
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

    private String formatMessage() {
        if (message == null) {
            return "null";
        } else if (args == null || args.length == 0) {
            return safeToString(message);
        } else {

            String msg = safeToString(message);
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
