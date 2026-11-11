package com.nanolaba.logging;

import java.util.Arrays;

/**
 * One log record, as {@link ILogger#log(LogEntry)} receives it: level, source class, call site, exception, message
 * and its arguments, creation time and thread. The message is resolved from its {@code Supplier} and formatted once,
 * when the entry is created. Entries are immutable.
 */
public class LogEntry {

    /**
     * The levels, from the most to the least detailed.
     */
    public enum LogEntryLevel {
        /**
         * The finest detail, for tracing the flow of a program.
         */
        TRACE,
        /**
         * Information for diagnosing problems.
         */
        DEBUG,
        /**
         * Normal events worth recording.
         */
        INFO,
        /**
         * Something unexpected that the program can handle.
         */
        WARN,
        /**
         * A failure.
         */
        ERROR
    }

    private final LogEntryLevel level;
    private final Class sourceClass;
    private final Throwable throwable;
    private final Object message;
    private final String formattedMessage;
    private final Object[] args;
    private final StackTraceElement caller;
    private final long timestamp;
    private final String threadName;

    private static final char ESCAPE_CHAR = '\\';

    /**
     * Creates an entry and formats its message. When no throwable is given and the last argument is a
     * {@link Throwable} that no {@code {}} placeholder consumes, that argument becomes the throwable of the entry and
     * is removed from the arguments, as in SLF4J.
     *
     * @param level       the level
     * @param sourceClass the source class; may be {@code null}
     * @param throwable   the exception; may be {@code null}
     * @param message     the message, any object; may be {@code null}
     * @param args        the arguments for {@code {}} placeholders in the message
     */
    public LogEntry(LogEntryLevel level, Class sourceClass, Throwable throwable, Object message, Object... args) {
        this(level, sourceClass, null, throwable, message, args);
    }

    private LogEntry(LogEntryLevel level, Class sourceClass, StackTraceElement caller, Throwable throwable, Object message, Object[] args) {
        this.level = level;
        this.sourceClass = sourceClass;
        this.caller = caller;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.threadName = Thread.currentThread().getName();

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

    /**
     * Returns the level.
     *
     * @return the level
     */
    public LogEntryLevel getLevel() {
        return level;
    }

    /**
     * Returns the detected or explicitly passed source class; {@code null} only if {@code null} was passed.
     *
     * @return the detected or explicitly passed source class; {@code null} only if {@code null} was passed
     */
    public Class getSourceClass() {
        return sourceClass;
    }

    /**
     * Returns the exception, passed first or taken from the last argument; {@code null} if there is none.
     *
     * @return the exception, passed first or taken from the last argument; {@code null} if there is none
     */
    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * Returns the raw message object, already resolved from its {@code Supplier}; may be {@code null}.
     *
     * @return the raw message object, already resolved from its {@code Supplier}; may be {@code null}
     */
    public Object getMessage() {
        return message;
    }

    /**
     * The stack frame of the logging call, found while detecting the source class; {@code null} when the source class
     * was passed explicitly, because then no stack walk takes place.
     *
     * @return the frame: class, method, file and line of the call
     */
    public StackTraceElement getCaller() {
        return caller;
    }

    /**
     * Creates an entry that also carries the frame of the logging call. Package-private: only {@link LOG} knows it.
     */
    static LogEntry create(LogEntryLevel level, Class sourceClass, StackTraceElement caller, Throwable throwable, Object message, Object[] args) {
        return new LogEntry(level, sourceClass, caller, throwable, message, args);
    }

    /**
     * The time the entry was created, in milliseconds since the epoch, like {@link System#currentTimeMillis()}.
     *
     * @return the creation time
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * The name of the thread that created the entry, so a logger that writes later or elsewhere still knows it.
     *
     * @return the thread name
     */
    public String getThreadName() {
        return threadName;
    }

    /**
     * Returns the arguments for the placeholders, without a trailing throwable taken as the exception.
     *
     * @return the arguments for the placeholders, without a trailing throwable taken as the exception
     */
    public Object[] getArgs() {
        return args;
    }

    /**
     * Returns the message with the placeholders substituted; {@code "null"} for a {@code null} message.
     *
     * @return the message with the placeholders substituted; {@code "null"} for a {@code null} message
     */
    public String getFormattedMessage() {
        return formattedMessage;
    }

    /**
     * A representation for debugging; never throws, even if the message or an argument has a broken
     * {@code toString()}.
     *
     * @return the level, source class, throwable, message and arguments
     */
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

    static String argToString(Object o) {
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

    static String safeToString(Object o) {
        try {
            return o == null ? "null" : o.toString();
        } catch (Throwable t) {
            return "[FAILED toString()]";
        }
    }
}
