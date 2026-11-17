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
    private final long skippedCount;
    private final String callerBoundary;
    private final boolean dbg;

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
        this(level, sourceClass, null, throwable, message, args, 0, LOG_BOUNDARY, false);
    }

    private LogEntry(LogEntryLevel level, Class sourceClass, StackTraceElement caller, Throwable throwable, Object message, Object[] args,
                     long skippedCount, String callerBoundary, boolean dbg) {
        this.level = level;
        this.sourceClass = sourceClass;
        this.caller = caller;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
        this.threadName = Thread.currentThread().getName();
        this.skippedCount = skippedCount;
        this.callerBoundary = callerBoundary;
        this.dbg = dbg;

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
        return create(level, sourceClass, caller, throwable, message, args, 0);
    }

    /**
     * Creates an entry that also carries the frame of the logging call and the number of calls skipped before it.
     * Package-private: only {@link LOG} knows them.
     */
    static LogEntry create(LogEntryLevel level, Class sourceClass, StackTraceElement caller, Throwable throwable, Object message, Object[] args,
                           long skippedCount) {
        return create(level, sourceClass, caller, throwable, message, args, skippedCount, LOG_BOUNDARY);
    }

    /**
     * Like {@link #create(LogEntryLevel, Class, StackTraceElement, Throwable, Object, Object[], long)}, for an entry
     * that entered Nanolog through another class than {@code LOG}.
     *
     * @param callerBoundary the class whose frames end Nanolog's part of the stack, see {@link #getCallerBoundary()}
     */
    static LogEntry create(LogEntryLevel level, Class sourceClass, StackTraceElement caller, Throwable throwable, Object message, Object[] args,
                           long skippedCount, String callerBoundary) {
        return new LogEntry(level, sourceClass, caller, throwable, message, args, skippedCount, callerBoundary, false);
    }

    /**
     * Creates the entry of a {@code LOG.dbg(...)} call: a {@code DEBUG} entry whose message is complete, so it has no
     * arguments to format.
     */
    static LogEntry createDbg(Class sourceClass, StackTraceElement caller, String message, long skippedCount, String callerBoundary) {
        return new LogEntry(LogEntryLevel.DEBUG, sourceClass, caller, null, message, NO_ARGS, skippedCount, callerBoundary, true);
    }

    static final String LOG_BOUNDARY = LOG.class.getName();

    private static final Object[] NO_ARGS = new Object[0];

    /**
     * Whether the entry comes from {@code LOG.dbg(...)}: a temporary debug print of expressions and their values,
     * logged at {@code DEBUG}. {@link ConsoleLogger} marks it {@code DBG} and prints its call site even without {@code showLocation}, and
     * {@link JsonLogger} adds {@code "dbg":true}.
     * @return {@code true} for an entry of {@code LOG.dbg(...)}
     */
    public boolean isDbg() {
        return dbg;
    }

    /**
     * The number of calls of a rate-limited statement ({@code LOG.once()}, {@code LOG.atMostEvery(...)}) that were
     * skipped since the previous entry of that statement; {@code 0} for every other entry.
     *
     * @return the number of skipped calls
     */
    public long getSkippedCount() {
        return skippedCount;
    }

    /**
     * The class whose frames end Nanolog's part of the stack: {@code LOG}, or {@code LOG.Limited} for a rate-limited
     * call. A location-aware SLF4J backend takes the first frame after it as the call site.
     */
    String getCallerBoundary() {
        return callerBoundary;
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
               ", sourceClass=" + (getSourceClass() == null ? null : fullName(getSourceClass())) +
               ", throwable=" + safeToString(getThrowable()) +
               ", message=" + safeToString(getMessage()) +
               ", args=" + arrayToString(getArgs()) +
               (getSkippedCount() > 0 ? ", skipped=" + getSkippedCount() : "") +
               (isDbg() ? ", dbg=true" : "") +
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

    /**
     * A {@code {}} argument as text: its guarded {@code toString()}, arrays element by element. With
     * {@code nanolog.dump=auto}, values without a {@code toString()} of their own are dumped field by field.
     */
    static String argToString(Object o) {
        if (Dump.auto) {
            return Dump.argument(o);
        } else if (o == null) {
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

    /**
     * The canonical name of a class, or its binary name ({@code com.acme.Foo$1}) for anonymous and local classes,
     * which have no canonical name.
     */
    static String fullName(Class sourceClass) {
        String canonical = sourceClass.getCanonicalName();
        return canonical == null ? sourceClass.getName() : canonical;
    }

    /**
     * The simple name of a class, or the last part of its binary name ({@code Foo$1}) for anonymous classes, whose
     * simple name is empty.
     */
    static String simpleName(Class sourceClass) {
        String simple = sourceClass.getSimpleName();
        if (!simple.isEmpty()) {
            return simple;
        }
        String name = sourceClass.getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }
}
